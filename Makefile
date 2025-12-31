.DEFAULT_GOAL := all

.PHONY: build install all uninstall test

build:
	./gradlew assembleDebug

install: build
	./gradlew installDebug

all: build
	./gradlew installDebug

uninstall:
	adb uninstall com.ustas.words

test:
	./gradlew :app:testDebugUnitTest --rerun-tasks

# All unit tests (debug + release)
# ./gradlew :app:test

# All unit tests (debug + release), forced re-run
# ./gradlew :app:test --rerun-tasks
