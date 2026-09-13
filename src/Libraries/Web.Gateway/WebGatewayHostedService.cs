using System.Net;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.FileProviders;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using QuantumCore.API;
using QuantumCore.API.Packets;
using QuantumCore.API.PluginTypes;
using QuantumCore.Game;

namespace QuantumCore.Web;

/// <summary>
/// Hosts the browser-facing side of the server: the static web client and the WebSocket endpoint the
/// client talks to. It runs beside the Metin2 TCP listeners rather than replacing them, so the original
/// client keeps working exactly as before.
/// </summary>
public sealed class WebGatewayHostedService : IHostedService
{
    private readonly IOptions<WebGatewayOptions> _options;
    private readonly IServiceProvider _services;
    private readonly ILogger<WebGatewayHostedService> _logger;
    private WebApplication? _app;

    public WebGatewayHostedService(IOptions<WebGatewayOptions> options, IServiceProvider services,
        ILogger<WebGatewayHostedService> logger)
    {
        _options = options;
        _services = services;
        _logger = logger;
    }

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        var options = _options.Value;
        if (!options.Enabled)
        {
            _logger.LogDebug("Web gateway is disabled");
            return;
        }

        var builder = WebApplication.CreateSlimBuilder();
        builder.WebHost.ConfigureKestrel(kestrel =>
            kestrel.Listen(IPAddress.Parse(options.Host), options.Port));
        builder.Logging.ClearProviders();

        _app = builder.Build();
        _app.UseWebSockets();

        var webRoot = ResolveWebRoot(options);
        if (webRoot is not null)
        {
            var files = new PhysicalFileProvider(webRoot);
            _app.UseDefaultFiles(new DefaultFilesOptions { FileProvider = files });
            _app.UseStaticFiles(new StaticFileOptions { FileProvider = files });
        }
        else
        {
            _logger.LogWarning("No web client directory found, only the WebSocket endpoint is served");
        }

        _app.Map("/ws", HandleWebSocketAsync);

        await _app.StartAsync(cancellationToken);
        _logger.LogInformation("Web gateway listening on http://{Host}:{Port}", options.Host, options.Port);
    }

    /// <summary>
    /// Prefers an operator-supplied directory so artwork can be swapped without rebuilding, and falls
    /// back to the client shipped next to the assembly.
    /// </summary>
    private string? ResolveWebRoot(WebGatewayOptions options)
    {
        if (!string.IsNullOrWhiteSpace(options.StaticFilesPath))
        {
            var configured = Path.GetFullPath(options.StaticFilesPath);
            if (Directory.Exists(configured)) return configured;

            _logger.LogWarning("Configured StaticFilesPath {Path} does not exist", configured);
        }

        var bundled = Path.Combine(AppContext.BaseDirectory, "wwwroot");
        return Directory.Exists(bundled) ? bundled : null;
    }

    private async Task HandleWebSocketAsync(HttpContext context)
    {
        if (!context.WebSockets.IsWebSocketRequest)
        {
            context.Response.StatusCode = StatusCodes.Status400BadRequest;
            return;
        }

        using var socket = await context.WebSockets.AcceptWebSocketAsync();

        // A scope per connection matches how the TCP server treats a client and keeps scoped
        // repositories from being shared between players.
        await using var scope = _services.CreateAsyncScope();
        var sp = scope.ServiceProvider;

        var connection = new WebGameConnection(socket, sp.GetRequiredService<IServerBase>(),
            context.Connection.RemoteIpAddress ?? IPAddress.None, _logger);

        var session = new WebGatewaySession(connection, socket,
            sp.GetRequiredService<IAccountRepository>(),
            sp.GetRequiredService<IPasswordHasher>(),
            sp.GetRequiredService<IPlayerManager>(),
            sp.GetRequiredService<IPlayerFactory>(),
            sp.GetRequiredService<IGamePacketHandler<EnterGame>>(),
            sp.GetRequiredService<IGamePacketHandler<CharacterMove>>(),
            sp.GetRequiredService<IGamePacketHandler<ChatIncoming>>(),
            _logger);

        using var linked = CancellationTokenSource.CreateLinkedTokenSource(
            context.RequestAborted, connection.Closed);

        var pump = connection.PumpOutgoingAsync(linked.Token);
        try
        {
            await session.RunAsync(linked.Token);
        }
        finally
        {
            await DespawnAsync(connection);
            connection.Close();
            await pump;
            connection.Dispose();
        }
    }

    private async Task DespawnAsync(WebGameConnection connection)
    {
        if (connection.Player is null) return;

        try
        {
            // persists the character before it leaves the world
            await _services.GetRequiredService<QuantumCore.API.Game.World.IWorld>()
                .DespawnPlayerAsync(connection.Player);
        }
        catch (Exception e)
        {
            _logger.LogError(e, "Failed to despawn web player {Name}", connection.Player.Name);
        }
    }

    public async Task StopAsync(CancellationToken cancellationToken)
    {
        if (_app is null) return;

        await _app.StopAsync(cancellationToken);
        await _app.DisposeAsync();
        _app = null;
    }
}
