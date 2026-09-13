using QuantumCore.API.Game;

namespace QuantumCore.Game.Commands;

internal record CommandDescriptor(
    Type Type,
    string Command,
    string? Description = null,
    Type? OptionsType = null,
    bool BypassPerm = false)
{
    /// <summary>
    /// Parameters read off <see cref="OptionsType"/> once at registration time, so <c>/help</c> does not
    /// have to reflect over the options type on every invocation.
    /// </summary>
    public IReadOnlyList<CommandParameterInfo> Parameters { get; } = CommandParameterReader.Read(OptionsType);

    public CommandInfo ToCommandInfo() => new(Command, Description, BypassPerm, Parameters);
}
