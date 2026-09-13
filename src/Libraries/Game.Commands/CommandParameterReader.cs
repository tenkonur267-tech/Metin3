using System.Reflection;
using CommandLine;
using QuantumCore.API.Game;

namespace QuantumCore.Game.Commands;

/// <summary>
/// Turns the CommandLine attributes declared on a command's options type into the
/// <see cref="CommandParameterInfo"/> descriptions exposed through <see cref="QuantumCore.API.ICommandManager"/>.
/// Reading them once at registration keeps <c>/help</c> free of reflection at call time.
/// </summary>
internal static class CommandParameterReader
{
    public static IReadOnlyList<CommandParameterInfo> Read(Type? optionsType)
    {
        if (optionsType is null) return [];

        var values = new List<(int Index, CommandParameterInfo Parameter)>();
        var options = new List<CommandParameterInfo>();

        foreach (var property in optionsType.GetProperties(BindingFlags.Public | BindingFlags.Instance))
        {
            var valueAttr = property.GetCustomAttribute<ValueAttribute>();
            if (valueAttr is not null)
            {
                values.Add((valueAttr.Index, new CommandParameterInfo(
                    Name: FirstNonEmpty(valueAttr.MetaName, property.Name.ToLowerInvariant()),
                    IsPositional: true,
                    IsRequired: valueAttr.Required,
                    IsFlag: IsFlag(property.PropertyType),
                    ShortName: null,
                    Description: NullIfEmpty(valueAttr.HelpText))));
                continue;
            }

            var optionAttr = property.GetCustomAttribute<OptionAttribute>();
            if (optionAttr is not null)
            {
                options.Add(new CommandParameterInfo(
                    Name: FirstNonEmpty(optionAttr.LongName, property.Name.ToLowerInvariant()),
                    IsPositional: false,
                    IsRequired: optionAttr.Required,
                    IsFlag: IsFlag(property.PropertyType),
                    ShortName: string.IsNullOrEmpty(optionAttr.ShortName) ? null : optionAttr.ShortName[0],
                    Description: NullIfEmpty(optionAttr.HelpText)));
            }
        }

        return
        [
            .. values.OrderBy(x => x.Index).Select(x => x.Parameter),
            .. options.OrderBy(x => x.Name, StringComparer.Ordinal)
        ];
    }

    private static bool IsFlag(Type type) => Nullable.GetUnderlyingType(type) is null
        ? type == typeof(bool)
        : Nullable.GetUnderlyingType(type) == typeof(bool);

    private static string FirstNonEmpty(string? preferred, string fallback) =>
        string.IsNullOrWhiteSpace(preferred) ? fallback : preferred;

    private static string? NullIfEmpty(string? value) => string.IsNullOrWhiteSpace(value) ? null : value;
}
