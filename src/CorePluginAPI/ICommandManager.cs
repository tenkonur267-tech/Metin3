using System.Reflection;
using QuantumCore.API.Game;
using QuantumCore.API.Game.World;

namespace QuantumCore.API;

public interface ICommandManager
{
    void Register(string ns, Assembly? assembly = null);
    Task ReloadAsync(CancellationToken token = default);
    bool HavePerm(Guid group, string cmd);
    bool CanUseCommand(IPlayerEntity player, string cmd);
    Task HandleAsync(IGameConnection connection, string chatline);
    Dictionary<Guid, PermissionGroup> Groups { get; }

    /// <summary>
    /// All currently registered commands, ordered by name. Use <see cref="CanUseCommand"/> to filter
    /// this down to what a specific player is allowed to invoke.
    /// </summary>
    IReadOnlyList<CommandInfo> Commands { get; }
}