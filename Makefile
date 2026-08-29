.DEFAULT_GOAL := all

.PHONY: help build install all uninstall test release connect list play-login play-auth-status play-status play-upload-draft play-publish-internal release-prepare release-server release-publish release-status

DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk
RELEASE_AAB := app/build/outputs/bundle/release/app-release.aab
PLAY_PACKAGE := com.familiarapps.words
PLAY_TRACK := internal
PLAY_CLOUD_SCOPE := https://www.googleapis.com/auth/cloud-platform
PLAY_PUBLISHER_SCOPE := https://www.googleapis.com/auth/androidpublisher

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
	@echo "  play-login Authorize Google Play API access in a browser"
	@echo "  play-auth-status Check Google Play API credentials"
	@echo "  play-status Show Google Play internal-testing releases"
	@echo "  play-upload-draft Upload the release bundle as an internal draft"
	@echo "  play-publish-internal Publish the current internal draft"
	@echo "  release-prepare Prepare and upload a release draft (requires VERSION=x.y.z)"
	@echo "  release-server Update and verify the Plex server"
	@echo "  release-publish Publish, tag, synchronize Git, and return to develop"
	@echo "  release-status Show the current release state"

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

play-login:
	gcloud auth application-default login \
		--no-launch-browser \
		--scopes="$(PLAY_CLOUD_SCOPE),$(PLAY_PUBLISHER_SCOPE)"

play-auth-status:
	@gcloud auth application-default print-access-token >/dev/null
	@echo "Google Play API credentials are available."

play-status:
	python3 tools/google_play.py status \
		--package "$(PLAY_PACKAGE)" \
		--track "$(PLAY_TRACK)"

play-upload-draft:
	python3 tools/google_play.py upload-draft \
		--package "$(PLAY_PACKAGE)" \
		--track "$(PLAY_TRACK)" \
		--aab "$(RELEASE_AAB)"

play-publish-internal:
	python3 tools/google_play.py publish \
		--package "$(PLAY_PACKAGE)" \
		--track "$(PLAY_TRACK)"

release-prepare:
	@if [ -z "$(VERSION)" ]; then \
		echo "Usage: make release-prepare VERSION=x.y.z"; \
		exit 1; \
	fi
	python3 tools/release.py prepare "$(VERSION)"

release-server:
	python3 tools/release.py server

release-publish:
	python3 tools/release.py publish

release-status:
	python3 tools/release.py status

# All unit tests (debug + release)
# ./gradlew :app:test

# All unit tests (debug + release), forced re-run
# ./gradlew :app:test --rerun-tasks
