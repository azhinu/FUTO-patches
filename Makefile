SHELL := /bin/sh
DOCKER := docker compose run --rm dev
APK ?= .local/apks/futo-playstore.apk
OUTPUT ?= build/dist/futo-patched.apk

.PHONY: help image build test integration patch release device-tests clean

help:
	@printf '%s\n' \
	  'make image        Build the pinned Docker toolchain' \
	  'make build        Build the Morphe .mpp bundle' \
	  'make test         Run offline JVM protocol and keyboard tests' \
	  'make integration  Run the Morphe matrix (APK=path/to/original.apk)' \
	  'make patch        Patch an APK (APK=... OUTPUT=...)' \
	  'make release      Package the bundle, sources and checksums' \
	  'make device-tests Build the optional Android test harness' \
	  'make clean        Remove generated build output; preserve .local'

image:
	docker compose build dev

build:
	$(DOCKER) bash scripts/build.sh

test:
	$(DOCKER) bash scripts/test.sh

integration: build
	$(DOCKER) python3 scripts/test-matrix.py "$(APK)"

patch: build
	$(DOCKER) python3 scripts/apply.py "$(APK)" "$(OUTPUT)"

release: build
	$(DOCKER) python3 scripts/package-release.py

device-tests:
	$(DOCKER) bash scripts/build-device-test.sh

clean:
	$(DOCKER) python3 -c 'import shutil; shutil.rmtree("build", ignore_errors=True)'
