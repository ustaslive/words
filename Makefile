.DEFAULT_GOAL := all

.PHONY: help build install all uninstall test release connect list

DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk

help:
	@echo "Usage: make <target>"
	@echo ""
	@echo "Targets:"
	@echo "  help       Show this help"
	@echo "  build      Build the debug APK"
	@echo "  install    Install the debug APK on connected devices"
	@echo "  all        Build and install the debug APK (default)"
	@echo "  uninstall  Uninstall the debug app"
	@echo "  test       Run debug unit tests"
	@echo "  release    Build the release app bundle"
	@echo "  connect    Connect to a device over TCP/IP"
	@echo "  list       List adb devices"

build:
	./gradlew assembleDebug

install:
	@if [ ! -f "$(DEBUG_APK)" ]; then \
		echo "Missing $(DEBUG_APK). Run 'make' to build before install."; \
		exit 1; \
	fi; \
	devices="$$(adb devices | awk 'NR>1 && $$2=="device" {print $$1}')"; \
	if [ -z "$$devices" ]; then \
		echo "No connected devices found. Run 'make list' to check adb devices."; \
		exit 1; \
	fi; \
	for device in $$devices; do \
		echo "Installing to $$device"; \
		adb -s "$$device" install -r "$(DEBUG_APK)" || exit 1; \
	done

all:
	$(MAKE) build
	$(MAKE) install

uninstall:
	adb uninstall com.familiarapps.words.debug

test:
	./gradlew :app:testDebugUnitTest --rerun-tasks

release:
	./gradlew bundleRelease

connect:
	@read -p "IP address (Enter to cancel): " HOST; \
	if [ -z "$$HOST" ]; then \
		echo "Connection cancelled."; \
		exit 0; \
	fi; \
	read -p "Port for $$HOST (Enter to cancel): " PORT; \
	if [ -z "$$PORT" ]; then \
		echo "Connection cancelled."; \
		exit 0; \
	fi; \
	adb connect "$$HOST:$$PORT"

list:
	adb devices

# All unit tests (debug + release)
# ./gradlew :app:test

# All unit tests (debug + release), forced re-run
# ./gradlew :app:test --rerun-tasks
