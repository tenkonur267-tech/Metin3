using System.Net;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Threading.Channels;
using Microsoft.Extensions.Logging;
using QuantumCore.API;
using QuantumCore.API.Core.Models;
using QuantumCore.API.Game.Types;
using QuantumCore.API.Game.World;
using QuantumCore.Networking;
using QuantumCore.Web.Protocol;

namespace QuantumCore.Web;

/// <summary>
/// A browser attached to the game as if it were a regular client. The game server keeps talking in
/// packets - this just serializes them to JSON and pushes them down a WebSocket instead of encoding
/// them into the Metin2 binary protocol, so every existing gameplay path works unchanged.
/// </summary>
public sealed class WebGameConnection : IGameConnection, IDisposable
{
    private readonly WebSocket _socket;
    private readonly ILogger _logger;
    private readonly CancellationTokenSource _cts = new();

    /// <summary>
    /// Send is called from the world tick and from packet handlers, which may run concurrently, but a
    /// WebSocket allows only one send at a time. Queueing hands all writes to a single pump.
    /// </summary>
    private readonly Channel<string> _outgoing = Channel.CreateUnbounded<string>(
        new UnboundedChannelOptions { SingleReader = true });

    public WebGameConnection(WebSocket socket, IServerBase server, IPAddress remoteAddress, ILogger logger)
    {
        _socket = socket;
        _logger = logger;
        Server = server;
        BoundIpAddress = remoteAddress;
    }

    public Guid Id { get; } = Guid.NewGuid();
    public EPhase Phase { get; set; } = EPhase.LOGIN;
    public Task? ExecuteTask => null;
    public IServerBase Server { get; }
    public IPAddress BoundIpAddress { get; }
    public Guid? AccountId { get; set; }
    public string Username { get; set; } = "";
    public IPlayerEntity? Player { get; set; }

    public CancellationToken Closed => _cts.Token;

    public void Send<T>(T packet) where T : IPacketSerializable
    {
        Enqueue(new PacketMessage(typeof(T).Name, packet!));
    }

    /// <summary>
    /// Sends a gateway-level message that is not a game packet, such as the character list.
    /// </summary>
    public void Enqueue<T>(T message)
    {
        if (_cts.IsCancellationRequested) return;

        try
        {
            _outgoing.Writer.TryWrite(JsonSerializer.Serialize(message, GatewayJson.Options));
        }
        catch (NotSupportedException e)
        {
            // a packet whose shape System.Text.Json cannot walk should not take the connection down
            _logger.LogWarning(e, "Could not serialize {Type} for the web client", typeof(T).Name);
        }
    }

    /// <summary>
    /// Drains the outgoing queue onto the socket until the connection closes.
    /// </summary>
    public async Task PumpOutgoingAsync(CancellationToken token = default)
    {
        using var linked = CancellationTokenSource.CreateLinkedTokenSource(token, _cts.Token);

        try
        {
            await foreach (var payload in _outgoing.Reader.ReadAllAsync(linked.Token))
            {
                await _socket.SendAsync(Encoding.UTF8.GetBytes(payload), WebSocketMessageType.Text, true,
                    linked.Token);
            }
        }
        catch (OperationCanceledException)
        {
            // connection closed - nothing to do
        }
        catch (WebSocketException e)
        {
            _logger.LogDebug(e, "Web client {Id} disconnected while sending", Id);
        }
    }

    public Task StartAsync(CancellationToken token = default) => Task.CompletedTask;

    public void Close(bool expected = true)
    {
        if (_cts.IsCancellationRequested) return;

        _outgoing.Writer.TryComplete();
        _cts.Cancel();
    }

    /// <summary>
    /// The Metin2 handshake exists to sync the clock with the original client. A browser has no such
    /// requirement, so there is nothing to negotiate.
    /// </summary>
    public bool HandleHandshake(GcHandshakeData handshake) => true;

    public void Dispose() => _cts.Dispose();
}
