import asyncio
import logging
import os
import uuid
from dataclasses import dataclass

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
import uvicorn

DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 9999
DEFAULT_WS_PING_INTERVAL_SECONDS = 20
DEFAULT_WS_PING_TIMEOUT_SECONDS = 20
EMPTY_ROOM_CLIENT_COUNT = 0
DISCONNECT_MESSAGE_TYPE = "websocket.disconnect"
MESSAGE_TYPE_JOIN = "join"
MESSAGE_TYPE_SNAPSHOT = "snapshot"
MESSAGE_TYPE_STATE_UPDATE = "stateUpdate"
MESSAGE_TYPE_NEW_GAME = "newGame"
MESSAGE_TYPE_SUBMIT_WORD = "submitWord"
MESSAGE_TYPE_ERROR = "error"
MESSAGE_ERROR_CONFLICT = "conflict"

ROLE_HOST = "host"
ROLE_GUEST = "guest"

JSON_KEY_TYPE = "type"
JSON_KEY_PLAYER_ID = "playerId"
JSON_KEY_PLAYER_NAME = "playerName"
JSON_KEY_PLAYER_COLOR = "playerColor"
JSON_KEY_ROLE = "role"
JSON_KEY_SNAPSHOT = "snapshot"
JSON_KEY_ACTIVE_COUNT = "activeCount"
JSON_KEY_MESSAGE = "message"
JSON_KEY_STATE_VERSION = "stateVersion"
JSON_KEY_BASE_VERSION = "baseVersion"
JSON_KEY_BASE_VERSION_ALT = "base_version"
JSON_KEY_PLAYERS = "players"
JSON_KEY_SEED_LETTERS = "seedLetters"
JSON_KEY_WHEEL_LETTERS = "wheelLetters"
JSON_KEY_GRID_ROWS = "gridRows"
JSON_KEY_REVEALED = "revealed"
JSON_KEY_WORDS = "words"
JSON_KEY_SETTINGS = "settings"
JSON_KEY_ROW = "row"
JSON_KEY_COL = "col"
CROSSWORD_EMPTY_CELL = "."
SHORT_ID_PREFIX = 5
SHORT_ID_SUFFIX = 5
SERVER_INSTANCE_ID = str(uuid.uuid4())
GRID_ROW_INDEX_PAD = 2
STATE_VERSION_INITIAL = 1
STATE_VERSION_INCREMENT = 1

ENV_HOST = "HOST"
ENV_PORT = "PORT"
ENV_WS_PING_INTERVAL_SECONDS = "WS_PING_INTERVAL_SECONDS"
ENV_WS_PING_TIMEOUT_SECONDS = "WS_PING_TIMEOUT_SECONDS"

LOGGER_NAME = "words.server"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)
LOGGER = logging.getLogger(LOGGER_NAME)

app = FastAPI()


@dataclass
class ClientSession:
    client_id: str
    websocket: WebSocket
    player_id: str
    player_name: str
    player_color: str


class RoomState:
    def __init__(self) -> None:
        self._clients: dict[str, ClientSession] = {}
        self.snapshot: dict | None = None
        self.host_player_id: str | None = None
        self.state_version: int = STATE_VERSION_INITIAL

    def add_client(self, session: ClientSession) -> int:
        self._clients[session.client_id] = session
        return len(self._clients)

    def remove_client(self, client_id: str) -> tuple[int, str | None]:
        session = self._clients.pop(client_id, None)
        player_id = session.player_id if session is not None else None
        return len(self._clients), player_id

    def sessions(self) -> list[ClientSession]:
        return list(self._clients.values())


ROOM = RoomState()
ROOM_LOCK = asyncio.Lock()


def build_error_message(message: str) -> dict:
    return {
        JSON_KEY_TYPE: MESSAGE_TYPE_ERROR,
        JSON_KEY_MESSAGE: message,
    }


def build_conflict_message(snapshot: dict, state_version: int, players: list[dict]) -> dict:
    snapshot[JSON_KEY_STATE_VERSION] = state_version
    return {
        JSON_KEY_TYPE: MESSAGE_TYPE_ERROR,
        JSON_KEY_MESSAGE: MESSAGE_ERROR_CONFLICT,
        JSON_KEY_SNAPSHOT: snapshot,
        JSON_KEY_PLAYERS: players,
    }


def build_snapshot_message(
    role: str, snapshot: dict | None, active_count: int, players: list[dict]
) -> dict:
    return {
        JSON_KEY_TYPE: MESSAGE_TYPE_SNAPSHOT,
        JSON_KEY_ROLE: role,
        JSON_KEY_SNAPSHOT: snapshot,
        JSON_KEY_ACTIVE_COUNT: active_count,
        JSON_KEY_PLAYERS: players,
    }


def build_state_update_message(snapshot: dict, players: list[dict]) -> dict:
    return {
        JSON_KEY_TYPE: MESSAGE_TYPE_STATE_UPDATE,
        JSON_KEY_SNAPSHOT: snapshot,
        JSON_KEY_PLAYERS: players,
    }


def build_players_payload(sessions: list[ClientSession]) -> list[dict]:
    players: list[dict] = []
    for session in sessions:
        players.append(
            {
                JSON_KEY_PLAYER_ID: session.player_id,
                JSON_KEY_PLAYER_NAME: session.player_name,
                JSON_KEY_PLAYER_COLOR: session.player_color,
            }
        )
    return players


def get_required_str(payload: dict, key: str) -> str | None:
    value = payload.get(key)
    if not isinstance(value, str):
        return None
    trimmed = value.strip()
    return trimmed if trimmed else None


def get_optional_str(payload: dict, key: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str):
        return ""
    return value.strip()


def get_optional_int(payload: dict, key: str) -> int | None:
    value = payload.get(key)
    if not isinstance(value, int):
        return None
    return value


def format_short_id(value: str | None) -> str:
    if not value:
        return "(unknown)"
    raw = value.strip()
    if len(raw) <= SHORT_ID_PREFIX + SHORT_ID_SUFFIX + 3:
        return f"({raw})"
    return f"({raw[:SHORT_ID_PREFIX]}...{raw[-SHORT_ID_SUFFIX:]})"


def server_label() -> str:
    return f"server{format_short_id(SERVER_INSTANCE_ID)}"


def format_player_label(player_id: str | None, player_name: str, role: str | None) -> str:
    parts: list[str] = []
    if player_name:
        parts.append(player_name)
    if role == ROLE_HOST:
        parts.append("host")
    short_id = format_short_id(player_id)
    if parts:
        return f"player({','.join(parts)},{short_id})"
    return f"player{short_id}"


def format_connection_label(client_id: str) -> str:
    return f"connection{format_short_id(client_id)}"


def format_client(websocket: WebSocket) -> str:
    client = websocket.client
    if client is None:
        return "unknown"
    try:
        return f"{client.host}:{client.port}"
    except AttributeError:
        try:
            host, port = client
            return f"{host}:{port}"
        except Exception:
            return "unknown"


def summarize_snapshot(snapshot: dict | None) -> str:
    if not isinstance(snapshot, dict):
        return "none"
    state_version = snapshot.get(JSON_KEY_STATE_VERSION)
    seed_letters = snapshot.get(JSON_KEY_SEED_LETTERS)
    grid_rows = snapshot.get(JSON_KEY_GRID_ROWS)
    revealed = snapshot.get(JSON_KEY_REVEALED)
    words = snapshot.get(JSON_KEY_WORDS)
    wheel_letters = snapshot.get(JSON_KEY_WHEEL_LETTERS)
    settings = snapshot.get(JSON_KEY_SETTINGS)
    grid_count = len(grid_rows) if isinstance(grid_rows, list) else None
    grid_cols = (
        len(grid_rows[0])
        if isinstance(grid_rows, list)
        and grid_rows
        and isinstance(grid_rows[0], str)
        else None
    )
    return (
        "stateVersion=%s seedLen=%s grid=%sx%s words=%s revealed=%s wheel=%s settings=%s"
        % (
            state_version,
            len(seed_letters) if isinstance(seed_letters, str) else None,
            grid_count,
            grid_cols,
            len(words) if isinstance(words, list) else None,
            len(revealed) if isinstance(revealed, list) else None,
            len(wheel_letters) if isinstance(wheel_letters, list) else None,
            "yes" if isinstance(settings, dict) else "no",
        )
    )


def build_grid_rows_for_log(snapshot: dict) -> list[str]:
    grid_rows = snapshot.get(JSON_KEY_GRID_ROWS)
    if not isinstance(grid_rows, list) or not grid_rows:
        return []
    rows: list[list[str]] = []
    for row in grid_rows:
        if not isinstance(row, str):
            return []
        rows.append(list(row))

    revealed = snapshot.get(JSON_KEY_REVEALED)
    if isinstance(revealed, list):
        for item in revealed:
            if not isinstance(item, dict):
                continue
            row_index = item.get(JSON_KEY_ROW)
            col_index = item.get(JSON_KEY_COL)
            if not isinstance(row_index, int) or not isinstance(col_index, int):
                continue
            if row_index < 0 or row_index >= len(rows):
                continue
            row_chars = rows[row_index]
            if col_index < 0 or col_index >= len(row_chars):
                continue
            char = row_chars[col_index]
            if char != CROSSWORD_EMPTY_CELL:
                row_chars[col_index] = char.upper()

    return ["".join(row) for row in rows]


def log_snapshot_grid(actor: str, action: str, target: str, snapshot: dict) -> None:
    rows = build_grid_rows_for_log(snapshot)
    if not rows:
        return
    column_count = max((len(row) for row in rows), default=0)
    LOGGER.info(
        "%s -> %s grid -> %s rows=%s cols=%s",
        actor,
        action,
        target,
        len(rows),
        column_count,
    )
    for row_index, row in enumerate(rows):
        LOGGER.info(
            "%s -> %s row[%0*d] -> %s %s",
            actor,
            action,
            GRID_ROW_INDEX_PAD,
            row_index,
            target,
            row,
        )


def read_int_env(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None:
        return default
    try:
        return int(raw)
    except ValueError:
        LOGGER.warning("invalid_env_value name=%s value=%s", name, raw)
        return default


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket) -> None:
    client_id = str(uuid.uuid4())
    await websocket.accept()
    client_address = format_client(websocket)
    path = websocket.url.path
    srv = server_label()
    connection_label = format_connection_label(client_id)
    LOGGER.info(
        "%s -> connect -> %s addr=%s path=%s",
        connection_label,
        srv,
        client_address,
        path,
    )

    disconnect_reason = "client_disconnect"
    close_code = None
    close_reason = None
    session = None
    try:
        join_payload = await receive_join_payload(websocket, client_id)
        player_id = get_required_str(join_payload, JSON_KEY_PLAYER_ID)
        player_color = get_required_str(join_payload, JSON_KEY_PLAYER_COLOR)
        player_name = get_optional_str(join_payload, JSON_KEY_PLAYER_NAME)
        if player_id is None or player_color is None:
            LOGGER.info(
                "%s -> join -> %s rejected reason=invalid_payload addr=%s playerId=%s playerColor=%s",
                connection_label,
                srv,
                client_address,
                join_payload.get(JSON_KEY_PLAYER_ID),
                join_payload.get(JSON_KEY_PLAYER_COLOR),
            )
            await websocket.send_json(build_error_message("invalid_join_payload"))
            await websocket.close()
            return

        async with ROOM_LOCK:
            role = ROLE_GUEST
            if ROOM.host_player_id is None:
                ROOM.host_player_id = player_id
                role = ROLE_HOST
            session = ClientSession(
                client_id=client_id,
                websocket=websocket,
                player_id=player_id,
                player_name=player_name,
                player_color=player_color,
            )
            active_count = ROOM.add_client(session)
            snapshot = ROOM.snapshot
            players = build_players_payload(ROOM.sessions())

        player_label = format_player_label(player_id, player_name, role)
        LOGGER.info(
            "%s -> join -> %s addr=%s",
            player_label,
            srv,
            client_address,
        )
        if role == ROLE_HOST:
            LOGGER.info("%s assigned as host", player_label)
        LOGGER.info(
            "%s connected active=%s",
            player_label,
            active_count,
        )
        LOGGER.info(
            "%s -> snapshot -> %s present=%s info=%s",
            srv,
            player_label,
            snapshot is not None,
            summarize_snapshot(snapshot),
        )
        if snapshot is not None:
            log_snapshot_grid(srv, "snapshot", player_label, snapshot)
        await websocket.send_json(
            build_snapshot_message(role, snapshot, active_count, players)
        )

        while True:
            payload = await receive_payload(websocket, client_id)
            message_type = payload.get(JSON_KEY_TYPE)
            safe_type = message_type if message_type else "unknown"
            if message_type == MESSAGE_TYPE_NEW_GAME:
                LOGGER.info("%s -> newGame -> %s", player_label, srv)
                await handle_new_game_message(session, payload)
            elif message_type == MESSAGE_TYPE_SUBMIT_WORD:
                LOGGER.info("%s -> submitWord -> %s", player_label, srv)
                await handle_submit_word_message(session, payload)
            elif message_type == MESSAGE_TYPE_JOIN:
                LOGGER.info(
                    "%s -> join -> %s rejected reason=already_joined",
                    player_label,
                    srv,
                )
                await websocket.send_json(build_error_message("already_joined"))
            else:
                LOGGER.info(
                    "%s -> %s -> %s rejected reason=unsupported_message",
                    player_label,
                    safe_type,
                    srv,
                )
                await websocket.send_json(build_error_message("unsupported_message"))
    except WebSocketDisconnect as error:
        close_code = error.code
        close_reason = error.reason
    except RuntimeError as error:
        if "disconnect message has been received" not in str(error):
            disconnect_reason = "error"
            error_label = (
                format_player_label(session.player_id, session.player_name, None)
                if session is not None
                else connection_label
            )
            LOGGER.error(
                "%s -> error -> %s error=%s message=%s",
                error_label,
                srv,
                type(error).__name__,
                str(error),
            )
    except Exception as error:
        disconnect_reason = "error"
        error_label = (
            format_player_label(session.player_id, session.player_name, None)
            if session is not None
            else connection_label
        )
        LOGGER.error(
            "%s -> error -> %s error=%s message=%s",
            error_label,
            srv,
            type(error).__name__,
            str(error),
        )
    finally:
        if session is not None:
            was_host = False
            async with ROOM_LOCK:
                active_count, removed_player_id = ROOM.remove_client(client_id)
                was_host = removed_player_id == ROOM.host_player_id
                if was_host:
                    LOGGER.info(
                        "%s released host role",
                        format_player_label(
                            session.player_id,
                            session.player_name,
                            ROLE_HOST,
                        ),
                    )
                    ROOM.host_player_id = None
                if active_count == EMPTY_ROOM_CLIENT_COUNT:
                    if ROOM.snapshot is not None:
                        LOGGER.info(
                            "%s -> room_reset -> snapshot cleared info=%s",
                            srv,
                            summarize_snapshot(ROOM.snapshot),
                        )
                    ROOM.snapshot = None
                    ROOM.state_version = STATE_VERSION_INITIAL
            LOGGER.info(
                "%s disconnected active=%s reason=%s code=%s detail=%s",
                format_player_label(
                    session.player_id,
                    session.player_name,
                    ROLE_HOST if was_host else None,
                ),
                active_count,
                disconnect_reason,
                close_code,
                close_reason,
            )


async def receive_join_payload(websocket: WebSocket, client_id: str) -> dict:
    connection_label = format_connection_label(client_id)
    srv = server_label()
    while True:
        payload = await receive_payload(websocket, client_id)
        if payload.get(JSON_KEY_TYPE) == MESSAGE_TYPE_JOIN:
            return payload
        LOGGER.info(
            "%s -> %s -> %s rejected reason=join_required",
            connection_label,
            payload.get(JSON_KEY_TYPE),
            srv,
        )
        await websocket.send_json(build_error_message("join_required"))


async def receive_payload(websocket: WebSocket, client_id: str) -> dict:
    try:
        return await websocket.receive_json()
    except ValueError:
        LOGGER.info(
            "%s -> invalid_json -> %s",
            format_connection_label(client_id),
            server_label(),
        )
        await websocket.send_json(build_error_message("invalid_json"))
        return {}


async def handle_new_game_message(session: ClientSession, payload: dict) -> None:
    srv = server_label()
    player_role = ROLE_HOST if session.player_id == ROOM.host_player_id else None
    player_label = format_player_label(
        session.player_id,
        session.player_name,
        player_role,
    )
    snapshot = payload.get(JSON_KEY_SNAPSHOT)
    if not isinstance(snapshot, dict):
        LOGGER.info(
            "%s -> newGame -> %s rejected reason=invalid_snapshot type=%s",
            player_label,
            srv,
            type(snapshot).__name__,
        )
        await session.websocket.send_json(build_error_message("invalid_snapshot"))
        return

    async with ROOM_LOCK:
        if session.player_id != ROOM.host_player_id:
            LOGGER.info(
                "%s -> newGame -> %s rejected reason=host_required",
                format_player_label(session.player_id, session.player_name, None),
                srv,
            )
            await session.websocket.send_json(build_error_message("host_required"))
            return
        snapshot[JSON_KEY_STATE_VERSION] = STATE_VERSION_INITIAL
        ROOM.snapshot = snapshot
        ROOM.state_version = STATE_VERSION_INITIAL
        targets = ROOM.sessions()

    LOGGER.info(
        "%s -> newGame -> %s accepted info=%s",
        player_label,
        srv,
        summarize_snapshot(snapshot),
    )
    log_snapshot_grid(player_label, "newGame", srv, snapshot)
    players = build_players_payload(targets)
    message = build_state_update_message(snapshot, players)
    await broadcast_message(message, targets)


async def handle_submit_word_message(session: ClientSession, payload: dict) -> None:
    srv = server_label()
    player_role = ROLE_HOST if session.player_id == ROOM.host_player_id else None
    player_label = format_player_label(
        session.player_id,
        session.player_name,
        player_role,
    )
    snapshot = payload.get(JSON_KEY_SNAPSHOT)
    if not isinstance(snapshot, dict):
        LOGGER.info(
            "%s -> submitWord -> %s rejected reason=invalid_snapshot type=%s",
            player_label,
            srv,
            type(snapshot).__name__,
        )
        await session.websocket.send_json(build_error_message("invalid_snapshot"))
        return
    base_version = get_optional_int(payload, JSON_KEY_BASE_VERSION)
    if base_version is None:
        base_version = get_optional_int(payload, JSON_KEY_BASE_VERSION_ALT)
    if base_version is None:
        base_version = get_optional_int(snapshot, JSON_KEY_STATE_VERSION)
    if base_version is None:
        LOGGER.info(
            "%s -> submitWord -> %s rejected reason=invalid_base_version",
            player_label,
            srv,
        )
        await session.websocket.send_json(build_error_message("invalid_base_version"))
        return

    conflict_message = None
    error_message = None
    targets: list[ClientSession] | None = None
    current_version = None
    players: list[dict] = []
    async with ROOM_LOCK:
        if ROOM.snapshot is None:
            error_message = "room_empty"
        else:
            current_version = ROOM.state_version
            if base_version != current_version:
                players = build_players_payload(ROOM.sessions())
                conflict_message = build_conflict_message(
                    ROOM.snapshot,
                    current_version,
                    players,
                )
            else:
                next_version = current_version + STATE_VERSION_INCREMENT
                snapshot[JSON_KEY_STATE_VERSION] = next_version
                ROOM.snapshot = snapshot
                ROOM.state_version = next_version
                targets = ROOM.sessions()

    if error_message is not None:
        LOGGER.info(
            "%s -> submitWord -> %s rejected reason=%s",
            player_label,
            srv,
            error_message,
        )
        await session.websocket.send_json(build_error_message(error_message))
        return

    if conflict_message is not None:
        LOGGER.info(
            "%s -> submitWord -> %s rejected reason=conflict base=%s current=%s",
            player_label,
            srv,
            base_version,
            current_version,
        )
        await session.websocket.send_json(conflict_message)
        return

    if targets is None:
        await session.websocket.send_json(build_error_message("submit_word_failed"))
        return

    LOGGER.info(
        "%s -> submitWord -> %s accepted info=%s base=%s",
        player_label,
        srv,
        summarize_snapshot(snapshot),
        base_version,
    )
    log_snapshot_grid(player_label, "submitWord", srv, snapshot)
    players = build_players_payload(targets)
    message = build_state_update_message(snapshot, players)
    await broadcast_message(message, targets)


async def broadcast_message(message: dict, sessions: list[ClientSession]) -> None:
    srv = server_label()
    snapshot = message.get(JSON_KEY_SNAPSHOT)
    message_type = message.get(JSON_KEY_TYPE)
    message_label = (
        "updated crossword"
        if message_type == MESSAGE_TYPE_STATE_UPDATE
        else message_type
    )
    LOGGER.info(
        "%s -> broadcast %s -> players=%s info=%s",
        srv,
        message_label,
        len(sessions),
        summarize_snapshot(snapshot),
    )
    if isinstance(snapshot, dict):
        log_snapshot_grid(srv, message_label, "players", snapshot)
    for session in sessions:
        try:
            await session.websocket.send_json(message)
        except Exception as error:
            LOGGER.warning(
                "%s -> %s -> %s failed error=%s message=%s",
                srv,
                message.get(JSON_KEY_TYPE),
                format_player_label(session.player_id, session.player_name, None),
                type(error).__name__,
                str(error),
            )


if __name__ == "__main__":
    host = os.getenv(ENV_HOST, DEFAULT_HOST)
    port = read_int_env(ENV_PORT, DEFAULT_PORT)
    ws_ping_interval = read_int_env(
        ENV_WS_PING_INTERVAL_SECONDS, DEFAULT_WS_PING_INTERVAL_SECONDS
    )
    ws_ping_timeout = read_int_env(
        ENV_WS_PING_TIMEOUT_SECONDS, DEFAULT_WS_PING_TIMEOUT_SECONDS
    )

    uvicorn.run(
        "app.main:app",
        host=host,
        port=port,
        ws_ping_interval=ws_ping_interval,
        ws_ping_timeout=ws_ping_timeout,
    )
