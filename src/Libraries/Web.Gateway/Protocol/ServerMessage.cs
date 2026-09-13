using System.Text.Json.Serialization;
using QuantumCore.API.Core.Models;

namespace QuantumCore.Web.Protocol;

/// <summary>
/// A game packet forwarded to the browser. Packets are serialized generically by name rather than
/// hand-mapped, so the client keeps working when the game server gains new packets - it simply ignores
/// the ones it does not know yet.
/// </summary>
public sealed record PacketMessage(
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("data")] object Data)
{
    public PacketMessage(string name, object data) : this("packet", name, data)
    {
    }
}

public sealed record ErrorMessage([property: JsonPropertyName("message")] string Message)
{
    [JsonPropertyName("type")] public string Type { get; } = "error";
}

/// <summary>
/// A character on the account's selection screen.
/// </summary>
public sealed record CharacterSummary(
    byte Slot,
    uint Id,
    string Name,
    byte Level,
    string PlayerClass,
    string Empire)
{
    public static CharacterSummary From(PlayerData player) => new(
        player.Slot,
        player.Id,
        player.Name,
        player.Level,
        player.PlayerClass.ToString(),
        player.Empire.ToString());
}

public sealed record LoginResultMessage(
    [property: JsonPropertyName("characters")] IReadOnlyList<CharacterSummary> Characters)
{
    [JsonPropertyName("type")] public string Type { get; } = "loginResult";
}

/// <summary>
/// Sent once the player is in the world, so the client knows which entity is itself and how large the
/// map is before the first packets arrive.
/// </summary>
public sealed record EnteredWorldMessage(
    [property: JsonPropertyName("vid")] uint Vid,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("x")] int X,
    [property: JsonPropertyName("y")] int Y,
    [property: JsonPropertyName("map")] string? Map,
    [property: JsonPropertyName("mapX")] uint MapX,
    [property: JsonPropertyName("mapY")] uint MapY,
    [property: JsonPropertyName("mapWidth")] uint MapWidth,
    [property: JsonPropertyName("mapHeight")] uint MapHeight)
{
    [JsonPropertyName("type")] public string Type { get; } = "enteredWorld";
}

/// <summary>
/// The mover's own authoritative position after the server processed a movement intent.
///
/// The game only broadcasts <c>CharacterMoveOut</c> to <em>nearby</em> entities, never back to the
/// player who moved, because the original client predicts its own movement and an echo would fight
/// that prediction. The web client needs the same information, so the gateway reports it separately
/// rather than changing what the game broadcasts.
/// </summary>
public sealed record SelfMovedMessage(
    [property: JsonPropertyName("vid")] uint Vid,
    [property: JsonPropertyName("x")] int X,
    [property: JsonPropertyName("y")] int Y,
    [property: JsonPropertyName("targetX")] int TargetX,
    [property: JsonPropertyName("targetY")] int TargetY,
    [property: JsonPropertyName("duration")] uint Duration,
    [property: JsonPropertyName("rotation")] float Rotation)
{
    [JsonPropertyName("type")] public string Type { get; } = "selfMoved";
}
