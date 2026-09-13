using System.Net;
using AwesomeAssertions;
using Game.Commands.Tests.Extensions;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using NSubstitute;
using QuantumCore.API;
using QuantumCore.API.Game.World;
using QuantumCore.API.Packets;
using QuantumCore.Game.Commands;
using QuantumCore.Game.Persistence.Entities;

namespace Game.Commands.Tests;

public class HelpCommandTests
{
    private readonly ICommandManager _commandManager;
    private readonly IGameConnection _connection;
    private readonly IPlayerEntity _player;
    private readonly List<string> _chatInfos = new();

    public HelpCommandTests()
    {
        var services = new ServiceCollection()
            .AddSingleton(_ =>
            {
                var conn = Substitute.For<IGameConnection>();
                var player = Substitute.For<IPlayerEntity>();
                player.Groups.Returns([PermGroup.OperatorGroup]);
                player.Connection.Returns(conn);
                conn.When(x => x.Send(Arg.Any<ChatOutcoming>()))
                    .Do(info => _chatInfos.Add(info.Arg<ChatOutcoming>()!.Message!));
                conn.Player.Returns(player);
                conn.BoundIpAddress.Returns(IPAddress.Loopback);
                return conn;
            })
            .AddSingleton<IConfiguration>(_ => new ConfigurationBuilder().Build())
            .AddGameCommands()
            .AddQuantumCoreTestLogger()
            .BuildServiceProvider();
        _commandManager = services.GetRequiredService<ICommandManager>();
        _commandManager.Register(typeof(HelpCommand).Namespace!, typeof(HelpCommand).Assembly);
        _connection = services.GetRequiredService<IGameConnection>();
        _player = _connection.Player!;
    }

    private int PageCount => (_commandManager.Commands.Count + HelpCommand.CommandsPerPage - 1) /
                             HelpCommand.CommandsPerPage;

    [Fact]
    public async Task Help_ListsFirstPageAsync()
    {
        await _commandManager.HandleAsync(_connection, "/help");

        _chatInfos[0].Should().Be($"The following commands are available (page 1/{PageCount}):");
        _chatInfos.Count(x => x.StartsWith("- /", StringComparison.Ordinal))
            .Should().Be(HelpCommand.CommandsPerPage);
        _chatInfos.Should().Contain($"Use /help <page> for another page (1-{PageCount}).");
        _chatInfos.Should().Contain("Use /help <command> for details about a single command.");
    }

    [Fact]
    public async Task Help_ListsRequestedPageAsync()
    {
        await _commandManager.HandleAsync(_connection, "/help 2");

        _chatInfos[0].Should().Be($"The following commands are available (page 2/{PageCount}):");
    }

    [Fact]
    public async Task Help_PagesDoNotOverlapAndCoverEveryCommandAsync()
    {
        var listed = new List<string>();
        for (var page = 1; page <= PageCount; page++)
        {
            _chatInfos.Clear();
            await _commandManager.HandleAsync(_connection, $"/help {page}");
            listed.AddRange(_chatInfos
                .Where(x => x.StartsWith("- /", StringComparison.Ordinal))
                .Select(x => x[3..].Split(' ')[0]));
        }

        listed.Should().OnlyHaveUniqueItems();
        listed.Should().BeEquivalentTo(_commandManager.Commands.Select(x => x.Name));
    }

    [Fact]
    public async Task Help_RejectsPageOutOfRangeAsync()
    {
        await _commandManager.HandleAsync(_connection, "/help 999");

        _chatInfos.Should().BeEquivalentTo([$"There is no page 999. Pages go from 1 to {PageCount}."]);
    }

    [Fact]
    public async Task Help_ShowsUsageForPositionalArgumentsAsync()
    {
        await _commandManager.HandleAsync(_connection, "/help gold");

        _chatInfos.Should().BeEquivalentTo([
            "/gold - Adds the given amount of gold",
            "Usage: /gold [value] [target]"
        ]);
    }

    [Fact]
    public async Task Help_ShowsUsageForNamedOptionsAsync()
    {
        await _commandManager.HandleAsync(_connection, "/help goto");

        _chatInfos.Should().BeEquivalentTo([
            "/goto - Warp to a position",
            "Usage: /goto [x] [y] [--map <map>]"
        ]);
    }

    [Fact]
    public async Task Help_ShowsFlagsAndTheirDescriptionsAsync()
    {
        await _commandManager.HandleAsync(_connection, "/help notice");

        _chatInfos.Should().Contain("Usage: /notice <message> [--big]");
        _chatInfos.Should().Contain("  -b, --big - Show as big centred notice");
    }

    [Fact]
    public async Task Help_AcceptsALeadingSlashAsync()
    {
        await _commandManager.HandleAsync(_connection, "/help /gold");

        _chatInfos[0].Should().Be("/gold - Adds the given amount of gold");
    }

    [Fact]
    public async Task Help_ReportsUnknownCommandAsync()
    {
        await _commandManager.HandleAsync(_connection, "/help doesnotexist");

        _chatInfos.Should().BeEquivalentTo([
            "Unknown command /doesnotexist. Use /help to see the available commands."
        ]);
    }

    [Fact]
    public async Task Help_HidesCommandsThePlayerCannotUseAsync()
    {
        // a player in no permission group may only use commands marked [CommandNoPermission]
        _player.Groups.Returns([]);

        await _commandManager.HandleAsync(_connection, "/help");

        var listed = _chatInfos
            .Where(x => x.StartsWith("- /", StringComparison.Ordinal))
            .Select(x => x[3..].Split(' ')[0])
            .ToArray();
        listed.Should().NotBeEmpty();
        listed.Should().OnlyContain(x => _commandManager.Commands.Single(c => c.Name == x).BypassPerm);
        listed.Should().NotContain("gold");
    }

    [Fact]
    public async Task Help_DoesNotRevealCommandsThePlayerCannotUseAsync()
    {
        _player.Groups.Returns([]);

        await _commandManager.HandleAsync(_connection, "/help gold");

        _chatInfos.Should().BeEquivalentTo([
            "Unknown command /gold. Use /help to see the available commands."
        ]);
    }
}
