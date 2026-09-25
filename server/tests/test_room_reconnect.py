import asyncio
import copy
import importlib.util
import logging
import sys
import types
import unittest


if importlib.util.find_spec("fastapi") is None:
    fastapi = types.ModuleType("fastapi")

    class FastAPI:
        def websocket(self, _path):
            return lambda handler: handler

    class WebSocketDisconnect(Exception):
        def __init__(self, code=None, reason=None):
            self.code = code
            self.reason = reason

    fastapi.FastAPI = FastAPI
    fastapi.WebSocket = object
    fastapi.WebSocketDisconnect = WebSocketDisconnect
    sys.modules["fastapi"] = fastapi

if importlib.util.find_spec("uvicorn") is None:
    sys.modules["uvicorn"] = types.ModuleType("uvicorn")

from server.app import main

main.LOGGER.setLevel(logging.CRITICAL)

TEST_TIMEOUT_SECONDS = 2
TEST_CLOSE_CODE = 1000
TEST_SINGLE_CLIENT_COUNT = 1
TEST_FIRST_SESSION_INDEX = 0


class FakeWebSocket:
    def __init__(self):
        self.client = None
        self.url = types.SimpleNamespace(path="/ws")
        self.incoming = asyncio.Queue()
        self.outgoing = asyncio.Queue()
        self.closed = False

    async def accept(self):
        pass

    async def receive_json(self):
        message = await self.incoming.get()
        if isinstance(message, Exception):
            raise message
        return message

    async def send_json(self, message):
        await self.outgoing.put(copy.deepcopy(message))

    async def close(self):
        if not self.closed:
            self.closed = True
            await self.incoming.put(main.WebSocketDisconnect(TEST_CLOSE_CODE))

    async def send(self, message):
        await self.incoming.put(message)

    async def receive_type(self, message_type):
        while True:
            message = await asyncio.wait_for(
                self.outgoing.get(), timeout=TEST_TIMEOUT_SECONDS
            )
            if message[main.JSON_KEY_TYPE] == message_type:
                return message


class RoomReconnectTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        main.ROOM = main.RoomState()
        self.tasks = []
        self.sockets = []

    async def asyncTearDown(self):
        for socket in self.sockets:
            await socket.close()
        await asyncio.wait_for(
            asyncio.gather(*self.tasks), timeout=TEST_TIMEOUT_SECONDS
        )

    async def connect(self, player_id):
        socket = FakeWebSocket()
        self.sockets.append(socket)
        self.tasks.append(asyncio.create_task(main.websocket_endpoint(socket)))
        await socket.send({
            main.JSON_KEY_TYPE: main.MESSAGE_TYPE_JOIN,
            main.JSON_KEY_PLAYER_ID: player_id,
            main.JSON_KEY_PLAYER_NAME: player_id,
            main.JSON_KEY_PLAYER_COLOR: "white",
            main.JSON_KEY_CLIENT_VERSION: main.SERVER_VERSION,
        })
        snapshot = await socket.receive_type(main.MESSAGE_TYPE_SNAPSHOT)
        return socket, snapshot

    async def test_first_return_uploads_game_and_later_player_cannot_replace_it(self):
        first, first_join = await self.connect("player-one")
        self.assertEqual(main.ROLE_HOST, first_join[main.JSON_KEY_ROLE])
        self.assertEqual(TEST_SINGLE_CLIENT_COUNT, first_join[main.JSON_KEY_ACTIVE_COUNT])

        game = {
            main.JSON_KEY_GAME_ID: "shared-game",
            main.JSON_KEY_SEED_LETTERS: "CATEM",
            main.JSON_KEY_GRID_ROWS: ["CAT", "..E"],
            "solvedBy": {"CAT": "player-one"},
            "solvedOrder": ["CAT"],
        }
        await first.send({main.JSON_KEY_TYPE: main.MESSAGE_TYPE_NEW_GAME,
                          main.JSON_KEY_SNAPSHOT: game})
        await first.receive_type(main.MESSAGE_TYPE_STATE_UPDATE)

        second, second_join = await self.connect("player-two")
        self.assertEqual(main.ROLE_GUEST, second_join[main.JSON_KEY_ROLE])
        self.assertEqual("shared-game", second_join[main.JSON_KEY_SNAPSHOT][main.JSON_KEY_GAME_ID])

        await first.close()
        promoted = await second.receive_type(main.MESSAGE_TYPE_ROLE_UPDATE)
        self.assertEqual(main.ROLE_HOST, promoted[main.JSON_KEY_ROLE])
        self.assertTrue(promoted[main.JSON_KEY_HAS_GAME])

        first_return, return_join = await self.connect("player-one")
        self.assertEqual(main.ROLE_GUEST, return_join[main.JSON_KEY_ROLE])
        await first_return.send({
            main.JSON_KEY_TYPE: main.MESSAGE_TYPE_NEW_GAME,
            main.JSON_KEY_SNAPSHOT: {main.JSON_KEY_GAME_ID: "other-game"},
        })
        error = await first_return.receive_type(main.MESSAGE_TYPE_ERROR)
        self.assertEqual("host_required", error[main.JSON_KEY_MESSAGE])
        self.assertEqual("shared-game", main.ROOM.snapshot[main.JSON_KEY_GAME_ID])

        await second.close()
        await first_return.receive_type(main.MESSAGE_TYPE_ROLE_UPDATE)
        await first_return.close()
        await asyncio.wait_for(asyncio.gather(*self.tasks), TEST_TIMEOUT_SECONDS)
        self.assertIsNone(main.ROOM.snapshot)

        first_again, empty_join = await self.connect("player-one")
        self.assertIsNone(empty_join[main.JSON_KEY_SNAPSHOT])
        self.assertEqual(main.ROLE_HOST, empty_join[main.JSON_KEY_ROLE])
        await first_again.send({
            main.JSON_KEY_TYPE: main.MESSAGE_TYPE_NEW_GAME,
            main.JSON_KEY_SNAPSHOT: game,
        })
        await first_again.receive_type(main.MESSAGE_TYPE_STATE_UPDATE)
        _, restored_join = await self.connect("player-two")
        restored = restored_join[main.JSON_KEY_SNAPSHOT]
        self.assertEqual("shared-game", restored[main.JSON_KEY_GAME_ID])
        self.assertEqual({"CAT": "player-one"}, restored["solvedBy"])

    async def test_same_player_connection_replaces_old_socket(self):
        old_socket, _ = await self.connect("player-one")
        new_socket, new_join = await self.connect("player-one")

        self.assertEqual(main.ROLE_HOST, new_join[main.JSON_KEY_ROLE])
        self.assertEqual(TEST_SINGLE_CLIENT_COUNT, new_join[main.JSON_KEY_ACTIVE_COUNT])
        self.assertTrue(old_socket.closed)
        self.assertEqual(TEST_SINGLE_CLIENT_COUNT, len(main.ROOM.sessions()))
        self.assertIs(main.ROOM.sessions()[TEST_FIRST_SESSION_INDEX].websocket, new_socket)

    async def test_waiting_player_can_publish_when_first_leaves_before_upload(self):
        first, _ = await self.connect("player-one")
        second, second_join = await self.connect("player-two")
        self.assertIsNone(second_join[main.JSON_KEY_SNAPSHOT])

        await first.close()
        promoted = await second.receive_type(main.MESSAGE_TYPE_ROLE_UPDATE)
        self.assertEqual(main.ROLE_HOST, promoted[main.JSON_KEY_ROLE])
        self.assertFalse(promoted[main.JSON_KEY_HAS_GAME])

        await second.send({
            main.JSON_KEY_TYPE: main.MESSAGE_TYPE_NEW_GAME,
            main.JSON_KEY_SNAPSHOT: {main.JSON_KEY_GAME_ID: "second-game"},
        })
        update = await second.receive_type(main.MESSAGE_TYPE_STATE_UPDATE)
        self.assertEqual("second-game", update[main.JSON_KEY_SNAPSHOT][main.JSON_KEY_GAME_ID])

    async def test_stale_word_update_cannot_replace_new_game(self):
        host, _ = await self.connect("player-one")
        await host.send({
            main.JSON_KEY_TYPE: main.MESSAGE_TYPE_NEW_GAME,
            main.JSON_KEY_SNAPSHOT: {main.JSON_KEY_GAME_ID: "old-game"},
        })
        await host.receive_type(main.MESSAGE_TYPE_STATE_UPDATE)
        await host.send({
            main.JSON_KEY_TYPE: main.MESSAGE_TYPE_NEW_GAME,
            main.JSON_KEY_SNAPSHOT: {main.JSON_KEY_GAME_ID: "new-game"},
        })
        await host.receive_type(main.MESSAGE_TYPE_STATE_UPDATE)

        await host.send({
            main.JSON_KEY_TYPE: main.MESSAGE_TYPE_SUBMIT_WORD,
            main.JSON_KEY_BASE_VERSION: main.STATE_VERSION_INITIAL,
            main.JSON_KEY_SNAPSHOT: {main.JSON_KEY_GAME_ID: "old-game"},
        })
        conflict = await host.receive_type(main.MESSAGE_TYPE_ERROR)
        self.assertEqual(main.MESSAGE_ERROR_CONFLICT, conflict[main.JSON_KEY_MESSAGE])
        self.assertEqual("new-game", conflict[main.JSON_KEY_SNAPSHOT][main.JSON_KEY_GAME_ID])
        self.assertEqual("new-game", main.ROOM.snapshot[main.JSON_KEY_GAME_ID])

    async def test_legacy_client_update_keeps_server_game_id_for_same_layout(self):
        host, _ = await self.connect("player-one")
        layout = {
            main.JSON_KEY_SEED_LETTERS: "CATEM",
            main.JSON_KEY_GRID_ROWS: ["cat"],
            main.JSON_KEY_WORDS: [],
        }
        await host.send({
            main.JSON_KEY_TYPE: main.MESSAGE_TYPE_NEW_GAME,
            main.JSON_KEY_SNAPSHOT: layout,
        })
        original = await host.receive_type(main.MESSAGE_TYPE_STATE_UPDATE)
        game_id = original[main.JSON_KEY_SNAPSHOT][main.JSON_KEY_GAME_ID]

        await host.send({
            main.JSON_KEY_TYPE: main.MESSAGE_TYPE_SUBMIT_WORD,
            main.JSON_KEY_BASE_VERSION: main.STATE_VERSION_INITIAL,
            main.JSON_KEY_SNAPSHOT: {**layout, main.JSON_KEY_GRID_ROWS: ["CAT"]},
        })
        update = await host.receive_type(main.MESSAGE_TYPE_STATE_UPDATE)
        self.assertEqual(game_id, update[main.JSON_KEY_SNAPSHOT][main.JSON_KEY_GAME_ID])
