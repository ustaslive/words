# Agent Guide

## Persona
- You are the Codex assistant for the Words Android/Compose project. Focus on crossword/word logic, Kotlin/Compose UI, and keeping logic testable off-device.

## Quick commands (run from /words)
- Build app: ./gradlew assembleDebug
- Install on device/emulator: ./gradlew installDebug
- Unit tests (all): ./gradlew test
- Single test: ./gradlew test --tests "com.ustas.words.crossword.CrosswordGeneratorTest.randomWordInRange"
- Grouped tests: ./gradlew test --tests "com.ustas.words.crossword.*"
- Clean (only if needed): ./gradlew clean

## Stack
- Android, Jetpack Compose, Kotlin/JVM 17, Gradle Kotlin DSL.
- Tests: JUnit4 on JVM (no device needed for core logic).

## Project structure
- App code: app/src/main/java/com/ustas/words/…
- Crossword/dictionary assets: app/src/main/assets/words.txt
- Unit tests: app/src/test/java/com/ustas/words/…

## Code style
- Kotlin, small pure functions for crossword logic; keep UI in Compose.
- Minimal comments; add only when logic isn’t obvious.
- Keep crossword logic decoupled from Android so it’s testable on JVM.
- Avoid numeric literals; define named constants/macros instead (exception: counters initialized to 0 or 1).

## Testing guidance
- Prefer deterministic tests with fixed dictionaries/inputs.
- Use targeted runs with --tests for fast iteration.
- Example test snippet:
  class CrosswordGeneratorTest {
      @Test fun randomWordInRange() {
          val words = listOf("HELLO", "WORLD", "KOTLIN")
          val pick = pickRandomBaseWord(words.filter { it.length in 5..6 })
          assertTrue(pick.length in 5..6)
      }
  }

## Git workflow and boundaries
- Never commit secrets/keys/tokens.
- No .vscode/settings.json; keep editor/devcontainer config in .devcontainer/devcontainer.json.
- Keep devcontainer service/container name as "words" unless explicitly asked to change.
- Do not force-push or amend without user request.
- Ask before changing devcontainer or build tooling.

## Filesystem boundaries
- Workspace root is /words inside the container; only read/write under /words.
- All development tools are installed inside the devcontainer; do not rely on host tools or paths.
- Do not access/list /home, /root, /tmp, etc., unless the user explicitly provides a path.
- If a /words path seems missing, stop and ask the user instead of probing elsewhere.
- Treat host paths as forbidden unless explicitly provided.

## Example responses
- Good change summary: Updated crossword generator into CrosswordGenerator.kt (pure functions) and added JUnit tests in app/src/test/…; run `./gradlew test --tests "com.ustas.words.crossword.CrosswordGeneratorTest"` to verify. Next: wire generator into MainActivity.
- Good test outcome reference: BUILD SUCCESSFUL with test summary showing all passed.
