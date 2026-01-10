.DEFAULT_GOAL := all

.PHONY: build install all uninstall test release

build:
	./gradlew assembleDebug

install: build
	./gradlew installDebug

all: build
	./gradlew installDebug

uninstall:
	adb uninstall com.familiarapps.words.debug

test:
	./gradlew :app:testDebugUnitTest --rerun-tasks

release:
	./gradlew bundleRelease

# All unit tests (debug + release)
# ./gradlew :app:test

# All unit tests (debug + release), forced re-run
# ./gradlew :app:test --rerun-tasks
