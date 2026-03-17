#!/bin/sh
set -eu

DEFAULT_REPO_SLUG="ustaslive/words"
DEFAULT_REF="master"
CONFIG_FILE=".xword.env"
ALLOW_SOURCE_TREE_INSTALL="${XWORD_ALLOW_SOURCE_TREE_INSTALL:-0}"
INSTALL_ROOT="$(pwd)"
REPO_SLUG="${XWORD_REPO_SLUG:-$DEFAULT_REPO_SLUG}"
REF="${XWORD_REF:-$DEFAULT_REF}"
ARCHIVE_URL="https://codeload.github.com/${REPO_SLUG}/tar.gz/${REF}"

is_source_checkout() {
    [ -d "${INSTALL_ROOT}/../.git" ] \
        && [ -d "${INSTALL_ROOT}/../app/src" ] \
        && [ -f "${INSTALL_ROOT}/../compose.yaml" ]
}

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Missing required command: $1" >&2
        exit 1
    fi
}

download_archive() {
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$ARCHIVE_URL" -o "$ARCHIVE_FILE"
        return
    fi
    if command -v wget >/dev/null 2>&1; then
        wget -qO "$ARCHIVE_FILE" "$ARCHIVE_URL"
        return
    fi
    echo "Missing downloader. Install curl or wget." >&2
    exit 1
}

write_config() {
    temp_config="${WORK_DIR}/xword.env"
    if [ -f "${INSTALL_ROOT}/${CONFIG_FILE}" ]; then
        grep -v '^XWORD_REF=' "${INSTALL_ROOT}/${CONFIG_FILE}" > "$temp_config" || true
    else
        : > "$temp_config"
    fi
    printf 'XWORD_REF=%s\n' "$REF" >> "$temp_config"
    mv "$temp_config" "${INSTALL_ROOT}/${CONFIG_FILE}"
}

sync_server_files() {
    source_root="$1"

    rm -rf "${INSTALL_ROOT}/app"
    mkdir -p "${INSTALL_ROOT}/app"
    cp -R "${source_root}/server/app/." "${INSTALL_ROOT}/app"

    for file_name in Dockerfile Makefile README.md docker-compose.yml requirements.txt install.sh; do
        cp "${source_root}/server/${file_name}" "${INSTALL_ROOT}/${file_name}"
    done

    cp "${source_root}/version.txt" "${INSTALL_ROOT}/version.txt"
    chmod +x "${INSTALL_ROOT}/install.sh"
}

if [ "$ALLOW_SOURCE_TREE_INSTALL" != "1" ] && is_source_checkout; then
    echo "Refusing to install into the repository source checkout. Use a separate target directory." >&2
    exit 1
fi

require_command tar
require_command mktemp
require_command find
require_command head

WORK_DIR="$(mktemp -d)"
ARCHIVE_FILE="${WORK_DIR}/archive.tar.gz"
trap 'rm -rf "$WORK_DIR"' EXIT HUP INT TERM

download_archive
tar -xzf "$ARCHIVE_FILE" -C "$WORK_DIR"

ARCHIVE_ROOT="$(find "$WORK_DIR" -mindepth 1 -maxdepth 1 -type d | head -n 1)"

if [ -z "$ARCHIVE_ROOT" ]; then
    echo "Failed to extract repository archive." >&2
    exit 1
fi

if [ ! -d "${ARCHIVE_ROOT}/server/app" ]; then
    echo "Archive does not contain server/app." >&2
    exit 1
fi

if [ ! -f "${ARCHIVE_ROOT}/version.txt" ]; then
    echo "Archive does not contain version.txt." >&2
    exit 1
fi

sync_server_files "$ARCHIVE_ROOT"
write_config

echo "Installed server files from ${REPO_SLUG}@${REF} into ${INSTALL_ROOT}"
