# Words

Words is an Android word game.

![App screenshot](doc/design/img/primer.jpg)

## Unpack

## Install

## Run

## For developers

### Build

```bash
./gradlew assembleDebug
```

### Run

Connect a device or start an emulator, then:

```bash
./gradlew installDebug
```

### Release requirements (devcontainer only)

Release builds are produced inside the devcontainer. The container expects signing data to be prepared on the host and mounted into the container.
These requirements are needed only for release builds; debug builds do not require them.

- Android keystore available on the host at `~/.android/secrets/words` (mounted into the container).
- Environment variables for keystore passwords on the host:
  - `ANDROID_APP_WORDS_PASS`
  - `ANDROID_STORE_PASS`

## License

Released into the public domain under The Unlicense. See `LICENSE`.
