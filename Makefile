.DEFAULT_GOAL := all

.PHONY: build install all

build:
	./gradlew assembleDebug

install: build
	./gradlew installDebug

all: build
	./gradlew installDebug
