Android devcontainer for the "words" game.

## What's inside
- Ubuntu Jammy base with OpenJDK 17.
- Android SDK command-line tools, platform-tools, platform android-34, build-tools 34.0.0, and NDK 26.1.10909125.
- VS Code Remote Container settings and extensions for Gradle and C++/NDK work.
- Docker Compose service named `devcontainer` with container name `words`, USB device pass-through enabled for ADB.

## Usage
1) Install Docker and VS Code with the Dev Containers extension.
2) Open the folder in VS Code and reopen in container (or run `docker compose up -d devcontainer` then attach).
3) The workspace mounts at `/words`; all tooling lives inside the container.

## ADB over Wi-Fi
- For first-time pairing via USB inside the container: `adb devices`, then `adb tcpip 5555`.
- Connect over Wi-Fi from the container: `adb connect DEVICE_IP:5555`, then deploy with Gradle/Android Studio tasks as usual.
- If USB permissions block access, run `sudo adb kill-server && sudo adb start-server` inside the container.

## Notes
- Keep VS Code settings in `.devcontainer/devcontainer.json`; avoid `.vscode/settings.json`.
- Add new SDK components or tooling by updating `.devcontainer/Dockerfile` so every machine gets the same setup on rebuild.
