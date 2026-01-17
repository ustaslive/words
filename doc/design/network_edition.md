# Network Edition Design

## Purpose
Provide a cooperative multiplayer mode for two phones on the same local network. The
network mode is optional and does not replace offline single-player.

## Summary of the Approach
- One Ubuntu server in the LAN runs a small WebSocket service (Docker).
- The first connected player becomes the host and generates the crossword locally.
- The server stores the shared state and relays updates to all clients.
- Words are synchronized only after the wheel gesture completes and the word is valid.
- Hammer reveals are synchronized per cell.

## Roles and Identity
- Host: the player who starts a session and generates the crossword.
- Guest: any other player joining the session.
- PlayerId: a locally generated UUID stored on device (stable per install).
- PlayerName: optional UI label, sent with join for display.

## Server Runtime
- Ubuntu host inside the LAN.
- Docker container exposes a single TCP port for WebSocket clients.
- One room for the first iteration.

## Shared Game State
The server stores a single snapshot for the room. It must be enough to render and
continue the same crossword on any client.

Snapshot fields:
- Grid with letters, isActive, and isRevealed flags.
- CrosswordWords map (word -> positions).
- Wheel letters (ordered list shown in the wheel).
- Generation settings used by the host (for reference).
- StateVersion (monotonic integer).

Missing words are local per player and are not part of the shared snapshot.

## Synchronization Rules
- Invalid word attempts are handled locally and are never sent.
- A valid word is submitted only after the gesture ends.
- Hammer reveals are submitted as single-cell updates.
- The server does not validate crossword logic. Clients apply updates to the snapshot.

## Locking
State changes are serialized with a room lock.

Lock rules:
- A lock is owned by a PlayerId.
- The lock expires after five seconds.
- Only the owner can release the lock.
- Clients retry with a 500 ms delay, up to three attempts.
- After three failures, the client shows an error and keeps local state.

Lock flow:
1) Client requests lock.
2) Server grants lock and returns current snapshot.
3) Client applies its change to the snapshot.
4) Client submits the updated snapshot.
5) Server stores and broadcasts the new snapshot.
6) Client releases the lock.

## Message Types
All messages are JSON. Message names are stable identifiers.

- join: client joins a room with playerId and playerName.
- snapshot: server sends the current snapshot.
- lockRequest: client requests the room lock.
- lockGranted: server grants lock with lockId and expiresAt.
- lockDenied: server denies lock; client retries.
- submitWord: client submits an updated snapshot after a valid word.
- revealCell: client submits an updated snapshot after a hammer reveal.
- newGame: host submits a fresh snapshot.
- stateUpdate: server broadcasts the latest snapshot.
- error: server reports a lock or update error.

## Client Behavior
### Host
- Generates the crossword locally.
- Sends newGame with the initial snapshot.

### Guest
- Requests snapshot on join.
- New Game button is disabled.
- Generation settings are read-only.

### Word Submission
1) Word is validated locally.
2) If valid, run the lock flow and submit the updated snapshot.

### Hammer Reveal
Same as Word Submission, but the update is a single cell reveal.

## UI and Mode Switching
- Offline mode remains the default.
- Network mode connects to a configured server address (manual IP:port).
- A connection status indicator is shown.

## Implementation Notes
### Server
- Python FastAPI with WebSocket.
- In-memory room state and lock state.
- Dockerfile and docker-compose for one-command startup.

### Android
- Add WebSocket client library (OkHttp or Ktor).
- Add a network repository to send and receive updates.
- Gate all shared-state mutations behind the lock flow.
- Keep the existing crossword logic unchanged and reusable in offline mode.

## Glossary
- Snapshot: full game state needed to render and continue a crossword.
- StateVersion: integer that increases with each update.
- Lock: temporary exclusive right to update the shared state.
- Host: player who generates and starts the shared crossword.
- Guest: player who joins an existing session.
- SubmitWord: applying a valid word to the crossword.
- RevealCell: opening a single cell via the hammer.
