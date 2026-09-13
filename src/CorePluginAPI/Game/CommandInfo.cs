namespace QuantumCore.API.Game;

/// <summary>
/// Describes a single parameter of a command, derived from the CommandLine attributes on the
/// command's options type.
/// </summary>
/// <param name="Name">Display name of the parameter. For options this is the long name.</param>
/// <param name="IsPositional">
/// <c>true</c> for positional values (<c>/gold 100</c>), <c>false</c> for named options
/// (<c>/goto --map map_b2</c>).
/// </param>
/// <param name="IsRequired">Whether the command cannot be executed without this parameter.</param>
/// <param name="IsFlag">Whether the parameter takes no value, i.e. it is a boolean switch.</param>
/// <param name="ShortName">Single character alias of a named option, if it declares one.</param>
/// <param name="Description">Help text declared on the parameter, if any.</param>
public sealed record CommandParameterInfo(
    string Name,
    bool IsPositional,
    bool IsRequired,
    bool IsFlag,
    char? ShortName,
    string? Description);

/// <summary>
/// Public description of a registered command. Exposed by <see cref="ICommandManager"/> so commands
/// like <c>/help</c> can enumerate what is available without reaching into the manager's internals.
/// </summary>
/// <param name="Name">Name the command is invoked with, without the leading slash.</param>
/// <param name="Description">Description declared on the <see cref="CommandAttribute"/>.</param>
/// <param name="BypassPerm">Whether the command is usable regardless of the player's permissions.</param>
/// <param name="Parameters">
/// Positional values first (in declaration order), then named options.
/// </param>
public sealed record CommandInfo(
    string Name,
    string? Description,
    bool BypassPerm,
    IReadOnlyList<CommandParameterInfo> Parameters);
