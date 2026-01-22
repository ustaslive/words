.DEFAULT_GOAL := all

.PHONY: build install all uninstall test release connect list

DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk
DOTENV_FILE := .env
EXAMPLE_ADB_HOSTS := 203.0.113.10 203.0.113.11

ifneq (,$(wildcard $(DOTENV_FILE)))
	include $(DOTENV_FILE)
endif

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
	@if [ -z "$(ADB_HOSTS)" ]; then \
		echo "Missing ADB_HOSTS in $(DOTENV_FILE)."; \
		echo "Create $(DOTENV_FILE) with:"; \
		echo "  ADB_HOSTS=$(EXAMPLE_ADB_HOSTS)"; \
		echo "This sets the device IPs used by 'make connect' to run adb over TCP/IP."; \
		echo "You can separate IPs with spaces or commas."; \
		exit 1; \
	fi; \
	hosts="$$(echo "$(ADB_HOSTS)" | tr ',' ' ')"; \
	for host in $$hosts; do \
		read -p "Port for $$host (Enter to skip): " PORT; \
		if [ -z "$$PORT" ]; then \
			echo "Skipping $$host"; \
			continue; \
		fi; \
		adb connect "$$host:$$PORT" || exit 1; \
	done

list:
	adb devices

# All unit tests (debug + release)
# ./gradlew :app:test

# All unit tests (debug + release), forced re-run
# ./gradlew :app:test --rerun-tasks
