# Gemini Code Assistant Context

This document provides context for the Gemini Code Assistant to understand the "Words" Android game project.

## Project Overview

"Words" is an Android word game built with Kotlin and Jetpack Compose. The core gameplay involves finding words within a crossword puzzle. The user is presented with a wheel of letters and must form words that fit into the crossword grid.

The application is architected with a focus on separating concerns:
-   **UI Layer (`MainActivity.kt`):** Built entirely with Jetpack Compose, this file manages the game's UI state, handles user input, and orchestrates the overall user experience.
-   **Game Logic (`CrosswordLogic.kt`):** This file contains the core logic for generating crosswords, validating user-submitted words, and managing the game state (e.g., the grid, discovered words).
-   **Data Management (`DictionaryRepository.kt`):** This component is responsible for loading the word list from assets and handling dynamic updates of the dictionary from a remote URL.

Key features include:
-   Dynamic crossword generation.
-   An interactive letter wheel for word selection.
-   A system for finding "missing" words that can be formed from the letters but are not in the crossword.
-   Sound effects for various game events.
-   Configurable settings, such as word length.
-   A mechanism to update the game's dictionary from a remote source.

## Building and Running

The project uses Gradle as its build system. The following `gradlew` commands are essential for development. The `Makefile` provides convenient shortcuts for these commands.

### Build

To build the debug version of the application:

```bash
./gradlew assembleDebug
```

Alternatively, using the Makefile:

```bash
make build
```

### Install

To install the debug version on a connected emulator or device:

```bash
./gradlew installDebug
```

Alternatively, using the Makefile:

```bash
make install
```

### Testing

To run the unit tests:

```bash
./gradlew test
```

Alternatively, using the Makefile:
```bash
make test
```

### Creating a Release

To create a release bundle:
```bash
./gradlew bundleRelease
```
Or with make:
```bash
make release
```
Note: Release builds require signing credentials to be configured as described in `README.md`.

## Development Conventions

-   **Language:** The project is written entirely in Kotlin.
-   **UI:** The user interface is built with Jetpack Compose.
-   **Asynchronicity:** Coroutines are used for background tasks, such as updating the dictionary.
-   **State Management:** The UI state is managed using Jetpack Compose's state management primitives (e.g., `mutableStateOf`, `remember`).
-   **Immutability:** The application favors immutable data structures (e.g., `data class`) for state representation.
-   **Testing:** Unit tests are located in `app/src/test/java/com/ustas/words/`.
-   **Code Style:** The code is well-formatted and follows standard Kotlin conventions. Key constants are defined at the top of files for easy configuration.
