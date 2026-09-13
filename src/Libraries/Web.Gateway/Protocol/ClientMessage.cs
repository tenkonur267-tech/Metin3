using System.Text.Json;
using System.Text.Json.Serialization;

namespace QuantumCore.Web.Protocol;

/// <summary>
/// Envelope for everything the browser sends. <see cref="Type"/> selects the command, the remaining
/// properties are read per command; unused ones stay null.
/// </summary>
public sealed class ClientMessage
{
    [JsonPropertyName("type")] public string Type { get; set; } = "";

    [JsonPropertyName("username")] public string? Username { get; set; }
    [JsonPropertyName("password")] public string? Password { get; set; }

    [JsonPropertyName("slot")] public byte? Slot { get; set; }
    [JsonPropertyName("name")] public string? Name { get; set; }
    [JsonPropertyName("class")] public byte? Class { get; set; }
    [JsonPropertyName("appearance")] public byte? Appearance { get; set; }

    [JsonPropertyName("x")] public int? X { get; set; }
    [JsonPropertyName("y")] public int? Y { get; set; }
    [JsonPropertyName("rotation")] public byte? Rotation { get; set; }
    [JsonPropertyName("movementType")] public byte? MovementType { get; set; }

    [JsonPropertyName("message")] public string? Message { get; set; }

    [JsonPropertyName("vid")] public uint? Vid { get; set; }
}

/// <summary>
/// Shared serializer settings. camelCase keeps the wire format idiomatic for the browser, and enums go
/// out as strings so the client does not have to mirror the server's numeric values.
/// </summary>
public static class GatewayJson
{
    public static readonly JsonSerializerOptions Options = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        PropertyNameCaseInsensitive = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        Converters = { new JsonStringEnumConverter() }
    };
}
