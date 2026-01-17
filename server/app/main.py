import logging
import os
import uuid

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
import uvicorn

DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 9999
DEFAULT_WS_PING_INTERVAL_SECONDS = 20
DEFAULT_WS_PING_TIMEOUT_SECONDS = 20
DISCONNECT_MESSAGE_TYPE = "websocket.disconnect"

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


class ConnectionRegistry:
    def __init__(self) -> None:
        self._clients: dict[str, WebSocket] = {}

    def add(self, client_id: str, websocket: WebSocket) -> int:
        self._clients[client_id] = websocket
        return len(self._clients)

    def remove(self, client_id: str) -> int:
        self._clients.pop(client_id, None)
        return len(self._clients)


REGISTRY = ConnectionRegistry()


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
    active_count = REGISTRY.add(client_id, websocket)
    LOGGER.info("client_connected id=%s active=%s", client_id, active_count)
    await websocket.send_json(
        {
            "type": "connected",
            "clientId": client_id,
            "activeCount": active_count,
        }
    )

    disconnect_reason = "client_disconnect"
    close_code = None
    close_reason = None
    try:
        while True:
            message = await websocket.receive()
            if message.get("type") == DISCONNECT_MESSAGE_TYPE:
                close_code = message.get("code")
                close_reason = message.get("reason")
                break
    except WebSocketDisconnect as error:
        close_code = error.code
        close_reason = error.reason
    except RuntimeError as error:
        if "disconnect message has been received" not in str(error):
            disconnect_reason = "error"
            LOGGER.error(
                "client_error id=%s error=%s message=%s",
                client_id,
                type(error).__name__,
                str(error),
            )
    except Exception as error:
        disconnect_reason = "error"
        LOGGER.error(
            "client_error id=%s error=%s message=%s",
            client_id,
            type(error).__name__,
            str(error),
        )

    active_count = REGISTRY.remove(client_id)
    LOGGER.info(
        "client_disconnected id=%s active=%s reason=%s code=%s detail=%s",
        client_id,
        active_count,
        disconnect_reason,
        close_code,
        close_reason,
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
