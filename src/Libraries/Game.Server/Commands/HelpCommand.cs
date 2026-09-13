using System.Text;
using CommandLine;
using QuantumCore.API;
using QuantumCore.API.Game;
using QuantumCore.API.Game.World;

namespace QuantumCore.Game.Commands;

[Command("help", "Shows this help message")]
[CommandNoPermission]
public class HelpCommand : ICommandHandler<HelpCommandOptions>
{
    /// <summary>
    /// The client's chat window only keeps a handful of lines visible, so the command list is paged
    /// instead of being dumped in one burst.
    /// </summary>
    public const int CommandsPerPage = 10;

    private readonly ICommandManager _commandManager;

    public HelpCommand(ICommandManager commandManager)
    {
        _commandManager = commandManager;
    }

    public Task ExecuteAsync(CommandContext<HelpCommandOptions> context)
    {
        var player = context.Player;
        var query = context.Arguments.Query?.Trim();

        // Only commands the player may actually invoke are listed. Showing the rest just tells players
        // about staff commands they will never be allowed to run.
        var available = _commandManager.Commands
            .Where(x => _commandManager.CanUseCommand(player, x.Name))
            .OrderBy(x => x.Name, StringComparer.OrdinalIgnoreCase)
            .ToArray();

        if (string.IsNullOrEmpty(query))
        {
            SendPage(player, available, 1);
            return Task.CompletedTask;
        }

        if (int.TryParse(query, out var page))
        {
            SendPage(player, available, page);
            return Task.CompletedTask;
        }

        SendCommandDetails(player, available, query.TrimStart('/'));
        return Task.CompletedTask;
    }

    private static void SendPage(IPlayerEntity player, CommandInfo[] available, int page)
    {
        if (available.Length == 0)
        {
            player.SendChatInfo("The following commands are available: none.");
            return;
        }

        var pageCount = (available.Length + CommandsPerPage - 1) / CommandsPerPage;
        if (page < 1 || page > pageCount)
        {
            player.SendChatInfo($"There is no page {page}. Pages go from 1 to {pageCount}.");
            return;
        }

        player.SendChatInfo($"The following commands are available (page {page}/{pageCount}):");

        foreach (var command in available.Skip((page - 1) * CommandsPerPage).Take(CommandsPerPage))
        {
            player.SendChatInfo(string.IsNullOrWhiteSpace(command.Description)
                ? $"- /{command.Name}"
                : $"- /{command.Name} - {command.Description}");
        }

        if (pageCount > 1)
        {
            player.SendChatInfo($"Use /help <page> for another page (1-{pageCount}).");
        }

        player.SendChatInfo("Use /help <command> for details about a single command.");
    }

    private static void SendCommandDetails(IPlayerEntity player, CommandInfo[] available, string name)
    {
        var command = available.FirstOrDefault(x => x.Name.Equals(name, StringComparison.OrdinalIgnoreCase));
        if (command is null)
        {
            // Commands the player has no permission for are indistinguishable from ones that do not
            // exist, so this message does not leak which staff commands the server has.
            player.SendChatInfo($"Unknown command /{name}. Use /help to see the available commands.");
            return;
        }

        player.SendChatInfo(string.IsNullOrWhiteSpace(command.Description)
            ? $"/{command.Name}"
            : $"/{command.Name} - {command.Description}");
        player.SendChatInfo($"Usage: {BuildUsage(command)}");

        foreach (var parameter in command.Parameters.Where(x => !string.IsNullOrWhiteSpace(x.Description)))
        {
            player.SendChatInfo($"  {DescribeParameter(parameter)} - {parameter.Description}");
        }
    }

    /// <summary>
    /// Renders a usage line such as <c>/goto &lt;x&gt; &lt;y&gt; [--map &lt;map&gt;]</c>. Required parameters are
    /// wrapped in angle brackets, optional ones in square brackets.
    /// </summary>
    internal static string BuildUsage(CommandInfo command)
    {
        var usage = new StringBuilder("/").Append(command.Name);

        foreach (var parameter in command.Parameters)
        {
            usage.Append(' ');
            if (parameter.IsPositional)
            {
                usage.Append(parameter.IsRequired ? $"<{parameter.Name}>" : $"[{parameter.Name}]");
            }
            else
            {
                var option = parameter.IsFlag ? $"--{parameter.Name}" : $"--{parameter.Name} <{parameter.Name}>";
                usage.Append(parameter.IsRequired ? option : $"[{option}]");
            }
        }

        return usage.ToString();
    }

    private static string DescribeParameter(CommandParameterInfo parameter)
    {
        if (parameter.IsPositional) return $"<{parameter.Name}>";

        return parameter.ShortName is null
            ? $"--{parameter.Name}"
            : $"-{parameter.ShortName}, --{parameter.Name}";
    }
}

public class HelpCommandOptions
{
    [Value(0, MetaName = "page or command")]
    public string? Query { get; set; }
}
