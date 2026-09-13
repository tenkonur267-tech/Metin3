# Web client (mobile)

QCX speaks the Metin2 binary protocol to the original Windows client. The web gateway adds a second,
independent front door: a WebSocket endpoint plus a browser client that works on a phone, with no
install and no client patching.

Both run side by side. The TCP listeners are untouched, so the original client keeps connecting
exactly as before.

## Enabling it

Add to `appsettings.json`:

```json
{
  "WebGateway": {
    "Enabled": true,
    "Host": "127.0.0.1",
    "Port": 5000
  }
}
```

It is disabled by default, so a server that only serves the original client is unaffected. Start the
server and open `http://<host>:5000` — on a phone, use the machine's LAN address and set `Host` to
`0.0.0.0` so it is reachable off-box.

## How it fits together

```
browser ──WebSocket/JSON──▶ WebGatewaySession ──▶ existing packet handlers ──▶ world
        ◀──────────────────  WebGameConnection ◀── packets the game already sends
```

* `WebGameConnection` implements `IGameConnection`. Everything the game sends a player goes through it,
  so no gameplay code knows or cares that the client is a browser.
* Outgoing packets are serialized **generically**, as `{"type":"packet","name":"SpawnCharacter",…}`.
  New packets reach the client without touching the gateway; the client ignores names it does not
  handle yet.
* Incoming commands are turned into the same packet objects the original client sends and passed to the
  registered `IGamePacketHandler<T>`. Movement, chat and commands therefore run identical code for both
  clients.

The one place the gateway adds something of its own is `selfMoved`. The game broadcasts
`CharacterMoveOut` only to *nearby* entities, never back to the player who moved, because the original
client predicts its own movement. The web client needs that authoritative position, so the gateway
reports it separately instead of changing what the game broadcasts.

## Artwork

The bundled client draws placeholder geometry — coloured markers on a grid — so it runs with no game
assets at all. Metin2's own art is Webzen's and is not distributed here.

To use your own assets, point `WebGateway:StaticFilesPath` at a directory holding your copy of the
client (`index.html`, `app.js`, `style.css` and whatever art you add). It takes precedence over the
bundled files, so you can iterate on the client without rebuilding the server. `drawEntity` in `app.js`
is the single function to replace with sprite blitting.

## Without the Webzen data files

The server starts without `item_proto`, `mob_proto`, `skilltable.txt` and friends, and logs a warning
for each. The web client works in that state, with two visible consequences:

* No items or monsters exist, so the world is empty apart from players.
* Movement animation data is missing, so the server reports a movement duration of `0` and moves
  characters to their target instantly. The client smooths this over `FALLBACK_MOVE_MS` rather than
  teleporting the marker.

Both resolve themselves once the data files are in place; see the [user guide](./user.md).
