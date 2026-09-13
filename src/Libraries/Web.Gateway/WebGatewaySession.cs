using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using QuantumCore.API;
using QuantumCore.API.Game.Types;
using QuantumCore.API.Game.Types.Players;
using QuantumCore.API.Packets;
using QuantumCore.API.Game.World;
using QuantumCore.API.PluginTypes;
using QuantumCore.Game;
using QuantumCore.Web.Protocol;

namespace QuantumCore.Web;

/// <summary>
/// Drives one browser connection: reads its JSON commands and turns them into the same calls the
/// Metin2 packet handlers make, so the web client and the original client go through identical
/// gameplay code.
/// </summary>
public sealed class WebGatewaySession
{
    private const int ReceiveBufferSize = 8 * 1024;

    private readonly WebGameConnection _connection;
    private readonly WebSocket _socket;
    private readonly IAccountRepository _accounts;
    private readonly IPasswordHasher _passwordHasher;
    private readonly IPlayerManager _playerManager;
    private readonly IPlayerFactory _playerFactory;
    private readonly IGamePacketHandler<EnterGame> _enterGameHandler;
    private readonly IGamePacketHandler<CharacterMove> _moveHandler;
    private readonly IGamePacketHandler<ChatIncoming> _chatHandler;
    private readonly ILogger _logger;

    public WebGatewaySession(WebGameConnection connection, WebSocket socket, IAccountRepository accounts,
        IPasswordHasher passwordHasher, IPlayerManager playerManager, IPlayerFactory playerFactory,
        IGamePacketHandler<EnterGame> enterGameHandler, IGamePacketHandler<CharacterMove> moveHandler,
        IGamePacketHandler<ChatIncoming> chatHandler, ILogger logger)
    {
        _connection = connection;
        _socket = socket;
        _accounts = accounts;
        _passwordHasher = passwordHasher;
        _playerManager = playerManager;
        _playerFactory = playerFactory;
        _enterGameHandler = enterGameHandler;
        _moveHandler = moveHandler;
        _chatHandler = chatHandler;
        _logger = logger;
    }

    public async Task RunAsync(CancellationToken token)
    {
        var buffer = new byte[ReceiveBufferSize];
        var message = new StringBuilder();

        while (!token.IsCancellationRequested && _socket.State == WebSocketState.Open)
        {
            WebSocketReceiveResult result;
            try
            {
                result = await _socket.ReceiveAsync(buffer, token);
            }
            catch (WebSocketException e)
            {
                _logger.LogDebug(e, "Web client {Id} disconnected while receiving", _connection.Id);
                break;
            }

            if (result.MessageType == WebSocketMessageType.Close) break;

            message.Append(Encoding.UTF8.GetString(buffer, 0, result.Count));
            if (!result.EndOfMessage) continue;

            var payload = message.ToString();
            message.Clear();

            try
            {
                await HandleAsync(payload, token);
            }
            catch (Exception e)
            {
                // one bad command must not drop the player out of the world
                _logger.LogError(e, "Failed to handle message from web client {Id}", _connection.Id);
                _connection.Enqueue(new ErrorMessage("Could not handle that request."));
            }
        }
    }

    private async Task HandleAsync(string payload, CancellationToken token)
    {
        var message = JsonSerializer.Deserialize<ClientMessage>(payload, GatewayJson.Options);
        if (message is null)
        {
            _connection.Enqueue(new ErrorMessage("Malformed message."));
            return;
        }

        switch (message.Type)
        {
            case "login":
                await LoginAsync(message);
                break;
            case "createCharacter":
                await CreateCharacterAsync(message);
                break;
            case "selectCharacter":
                await SelectCharacterAsync(message, token);
                break;
            case "move":
                await MoveAsync(message, token);
                break;
            case "chat":
                await ChatAsync(message, token);
                break;
            default:
                _connection.Enqueue(new ErrorMessage($"Unknown message type '{message.Type}'."));
                break;
        }
    }

    private async Task LoginAsync(ClientMessage message)
    {
        if (string.IsNullOrWhiteSpace(message.Username) || string.IsNullOrEmpty(message.Password))
        {
            _connection.Enqueue(new ErrorMessage("Username and password are required."));
            return;
        }

        var account = await _accounts.FindByNameAsync(message.Username);

        // Verifying against a real hash even when the account is missing would be better still, but the
        // repository gives us nothing to verify against, so keep the message identical either way and do
        // not tell the caller which half was wrong.
        if (account is null || !_passwordHasher.VerifyHash(account.Password, message.Password))
        {
            _connection.Enqueue(new ErrorMessage("Invalid username or password."));
            return;
        }

        _connection.AccountId = account.Id;
        _connection.Username = account.Username;
        _connection.Phase = EPhase.SELECT;

        var characters = await _playerManager.GetPlayersAsync(account.Id);
        _connection.Enqueue(new LoginResultMessage([.. characters.Select(CharacterSummary.From)]));
    }

    private async Task CreateCharacterAsync(ClientMessage message)
    {
        if (_connection.AccountId is not { } accountId)
        {
            _connection.Enqueue(new ErrorMessage("Log in first."));
            return;
        }

        var name = message.Name?.Trim();
        if (string.IsNullOrEmpty(name))
        {
            _connection.Enqueue(new ErrorMessage("A character name is required."));
            return;
        }

        if (await _playerManager.IsNameInUseAsync(name))
        {
            _connection.Enqueue(new ErrorMessage($"The name '{name}' is already taken."));
            return;
        }

        await _playerManager.CreateAsync(accountId, name,
            (EPlayerClassGendered)(message.Class ?? 0), message.Appearance ?? 0);

        var characters = await _playerManager.GetPlayersAsync(accountId);
        _connection.Enqueue(new LoginResultMessage([.. characters.Select(CharacterSummary.From)]));
    }

    private async Task SelectCharacterAsync(ClientMessage message, CancellationToken token)
    {
        if (_connection.AccountId is not { } accountId)
        {
            _connection.Enqueue(new ErrorMessage("Log in first."));
            return;
        }

        if (_connection.Player is not null)
        {
            _connection.Enqueue(new ErrorMessage("Already in the world."));
            return;
        }

        var data = await _playerManager.GetPlayerAsync(accountId, message.Slot ?? 0);
        if (data is null)
        {
            _connection.Enqueue(new ErrorMessage("No character in that slot."));
            return;
        }

        var player = await _playerFactory.CreatePlayerAsync(_connection, data);
        _connection.Player = player;

        // Reuse the handler the original client's EnterGame packet triggers, so spawning, the initial
        // packet burst, inventory and skills all follow exactly the same path.
        await _enterGameHandler.ExecuteAsync(new GamePacketContext<EnterGame>(new EnterGame(), _connection),
            token);

        var map = player.Map;
        _connection.Enqueue(new EnteredWorldMessage(
            player.Vid,
            player.Name,
            player.PositionX,
            player.PositionY,
            map?.Name,
            map?.Position.X ?? 0,
            map?.Position.Y ?? 0,
            map?.Width ?? 0,
            map?.Height ?? 0));
    }

    private async Task MoveAsync(ClientMessage message, CancellationToken token)
    {
        if (_connection.Player is null)
        {
            _connection.Enqueue(new ErrorMessage("Not in the world."));
            return;
        }

        var packet = new CharacterMove
        {
            MovementType = (CharacterMovementType)(message.MovementType ?? (byte)CharacterMovementType.MOVE),
            Argument = 0,
            Rotation = message.Rotation ?? 0,
            PositionX = message.X ?? _connection.Player.PositionX,
            PositionY = message.Y ?? _connection.Player.PositionY,
            Time = (uint)_connection.Server.Clock.Elapsed.TotalMilliseconds
        };

        await _moveHandler.ExecuteAsync(new GamePacketContext<CharacterMove>(packet, _connection), token);

        var player = _connection.Player;
        _connection.Enqueue(new SelfMovedMessage(player.Vid, player.PositionX, player.PositionY,
            player.TargetPositionX, player.TargetPositionY, player.MovementDuration, player.Rotation));
    }

    private async Task ChatAsync(ClientMessage message, CancellationToken token)
    {
        if (_connection.Player is null)
        {
            _connection.Enqueue(new ErrorMessage("Not in the world."));
            return;
        }

        if (string.IsNullOrWhiteSpace(message.Message)) return;

        var packet = new ChatIncoming
        {
            MessageType = ChatMessageType.NORMAL,
            Message = message.Message
        };

        await _chatHandler.ExecuteAsync(new GamePacketContext<ChatIncoming>(packet, _connection), token);
    }
}
