Guidance for contributors:
- Do not create or recommend a .vscode/settings.json file. Keep editor configuration inside .devcontainer/devcontainer.json.
- Add all Android/VS Code tooling, SDK components, and other dependencies through Dockerfile and devcontainer settings so the container is self contained on any machine.
- Keep the devcontainer compose service name and container name as "words" unless there is a strong reason to change it.
- In chat, reply in the same language the user uses unless they explicitly ask otherwise.
- Outside of chat, write all artifacts you create or update (code, code comments, documentation, config text, etc.) in English unless the user explicitly requests another language.
- If the user mentions the agent file informally (agents, agent file, file for the agent, etc.), interpret it as the repository root `AGENTS.md`.
