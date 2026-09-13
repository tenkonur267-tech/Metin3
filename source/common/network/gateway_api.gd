class_name GatewayAPI

## Shared keys (client + gateway)
const KEY_REQUEST_ID: String = "r-id"
const KEY_TOKEN_ID: String = "t-id"
const KEY_ACCOUNT_ID: String = "a-id"
const KEY_ACCOUNT_USERNAME: String = "a-u"
const KEY_ACCOUNT_PASSWORD: String  = "a-p"
const KEY_WORLD_ID: String = "w-id"
const KEY_CHAR_ID: String = "c-id"
## Client build version (the project's config/version), sent on login so an
## outdated client gets a clear "please update" message instead of failing deeper.
const KEY_CLIENT_VERSION: String = "c-v"


## Auth/gateway error codes (server → client). Kept here so client and server
## agree on the numbers; the client maps them to localized text in GatewayError.
## Anything not listed falls back to a generic "please try again" on the client.
const ERR_GENERIC: int = 1
const ERR_ACCOUNT_CREATE_FAILED: int = 30
const ERR_BAD_CREDENTIALS: int = 50
const ERR_ALREADY_CONNECTED: int = 51
const ERR_RATE_LIMITED: int = 60
## Client build doesn't match the server's. The boot handshake (and login) return
## this so the client can show a hard "please update" instead of letting them in.
const ERR_OUTDATED_VERSION: int = 70


## This build's version, from project.godot's application/config/version. Same
## call returns the client's version on the client and the server's on the server.
static func game_version() -> String:
	return str(ProjectSettings.get_setting("application/config/version", ""))

const ACTION_LOGIN := "login"
const ACTION_CREATE_ACCOUNT := "create_account"
const ACTION_CREATE_CHARACTER := "create_character"
const ACTION_LIST_CHARACTERS := "list_characters"
const ACTION_ENTER_WORLD := "enter_world"
const ACTION_DISCONNECT := "disconnect"


## Where the client looks for its gateway when the build is a packaged one.
## Left empty on purpose: Metin3 has no deployed gateway yet, and a fork must
## not ship pointing at somebody else's server. Set it before cutting a release
## (see MOBILE.md) — an empty value keeps packaged builds on localhost,
## which fails loudly and locally instead of quietly talking to a stranger.
const GATEWAY_URL_SETTING := "metin3/network/gateway_url"

## Where a from-source run looks. A developer running the server from the same
## checkout needs no configuration at all.
const LOCAL_GATEWAY_URL := "http://127.0.0.1:8088"


static func base_url() -> String:
	if OS.has_feature("metin3") or OS.has_feature("release"):
		var configured: String = str(ProjectSettings.get_setting(GATEWAY_URL_SETTING, ""))
		if not configured.is_empty():
			return configured
		push_warning(
			"No gateway configured: set '%s' in Project Settings before release. Falling back to %s."
			% [GATEWAY_URL_SETTING, LOCAL_GATEWAY_URL]
		)
	return LOCAL_GATEWAY_URL


static func get_endpoint(path: String) -> String:
	return "%s%s" % [base_url().rstrip("/"), path]


# Endpoints
static func login() -> String:
	return get_endpoint("/v1/login")


static func guest() -> String:
	return get_endpoint("/v1/guest")


static func worlds() -> String:
	return get_endpoint("/v1/worlds")


## Lightweight boot healthcheck (no auth): is the gateway reachable + master up, and
## does this build match the server's? Called before the gateway shows any menu.
static func handshake() -> String:
	return get_endpoint("/v1/handshake")


static func account_create() -> String:
	return get_endpoint("/v1/account/create")


static func world_characters() -> String:
	return get_endpoint("/v1/world/characters")


static func world_enter() -> String:
	return get_endpoint("/v1/world/enter")


static func world_create_char() -> String:
	return get_endpoint("/v1/world/character/create")
