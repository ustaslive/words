# Server

## Install

Choose a target directory that is not the repository checkout, then run:

```bash
mkdir -p "$HOME/xword-server"
cd "$HOME/xword-server"
curl -fsSL https://raw.githubusercontent.com/ustaslive/words/master/server/install.sh | sh
make up
make status
```

## Update

From the installed server directory, run:

```bash
make update
make status
```

`make update` is only for flat installed directories created by `install.sh`.
Do not run it from the repository checkout.

## Repository Checkout

For the development checkout, keep using the server directory inside the repository:

```bash
cd /words/server
make up
make down
make logs
make status
```

## Commands

Available service commands:

```bash
make up
make down
make restart
make logs
make update
make status
make enable
make disable
```

`make enable` sets restart policy to `unless-stopped` for `words-server`.
`make disable` sets restart policy to `no` for `words-server` and stops it if it exists.

WebSocket endpoint: `ws://<host>:9999/ws`
