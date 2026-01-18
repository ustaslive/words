.DEFAULT_GOAL := all

.PHONY: build install all uninstall test release connect list

DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk

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
	@read -p "Port: " PORT; \
	if [ -z "$$PORT" ]; then \
		echo "Port is required."; \
		exit 1; \
	fi; \
	adb connect 199.99.9.14:$$PORT

list:
	adb devices

# All unit tests (debug + release)
# ./gradlew :app:test

# All unit tests (debug + release), forced re-run
# ./gradlew :app:test --rerun-tasks
