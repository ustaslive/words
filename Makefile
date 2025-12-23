.DEFAULT_GOAL := all

.PHONY: build install all uninstall

build:
	./gradlew assembleDebug

install: build
	./gradlew installDebug

all: build
	./gradlew installDebug

uninstall:
	adb uninstall com.ustas.words
