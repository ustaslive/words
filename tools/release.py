#!/usr/bin/env python3

import argparse
import re
import shlex
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
VERSION_FILE = ROOT / "version.txt"
BUILD_FILE = ROOT / "app" / "build.gradle.kts"
RELEASE_AAB = ROOT / "app" / "build" / "outputs" / "bundle" / "release" / "app-release.aab"
APP_STATS_FILE = ROOT / "app" / "src" / "main" / "assets" / "005.stat.txt"
LAB_STATS_FILE = ROOT / "lab" / "crossword_repeatability" / "005.stat.txt"
DEFAULT_SERVER = "ustas@plex"
DEFAULT_SERVER_PATH = "/srv/xword"
MAX_RELEASE_NOTES_LENGTH = 500
VERSION_PATTERN = re.compile(r"\d+\.\d+\.\d+")
VERSION_CODE_PATTERN = re.compile(r"(versionCode\s*=\s*)(\d+)")


class ReleaseError(RuntimeError):
    pass


def run(command: list[str], *, capture: bool = False) -> subprocess.CompletedProcess[str]:
    print(f"+ {shlex.join(command)}", flush=True)
    result = subprocess.run(
        command,
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.STDOUT if capture else None,
    )
    if result.returncode != 0:
        if capture and result.stdout:
            print(result.stdout, file=sys.stderr, end="")
        raise ReleaseError(f"Command failed with exit code {result.returncode}")
    return result


def output(command: list[str]) -> str:
    return run(command, capture=True).stdout.strip()


def git(*arguments: str) -> None:
    run(["git", *arguments])


def git_output(*arguments: str) -> str:
    return output(["git", *arguments])


def ref_exists(ref: str) -> bool:
    result = subprocess.run(
        ["git", "show-ref", "--verify", "--quiet", ref],
        cwd=ROOT,
    )
    return result.returncode == 0


def current_version() -> str:
    return VERSION_FILE.read_text(encoding="utf-8").strip()


def current_version_code() -> int:
    match = VERSION_CODE_PATTERN.search(BUILD_FILE.read_text(encoding="utf-8"))
    if match is None:
        raise ReleaseError(f"Cannot find versionCode in {BUILD_FILE}")
    return int(match.group(2))


def current_branch() -> str:
    return git_output("branch", "--show-current")


def ensure_clean_worktree() -> None:
    if git_output("status", "--porcelain"):
        raise ReleaseError("The Git worktree is not clean. Commit or remove the changes first.")


def ensure_branch(expected: str) -> None:
    actual = current_branch()
    if actual != expected:
        raise ReleaseError(f"Expected branch {expected}, current branch is {actual}")


def fetch_and_verify_branches() -> None:
    git("fetch", "origin")
    for branch in ("develop", "master"):
        local_ref = git_output("rev-parse", branch)
        remote_ref = git_output("rev-parse", f"origin/{branch}")
        if local_ref != remote_ref:
            raise ReleaseError(f"Local {branch} is not synchronized with origin/{branch}")


def ensure_stats_are_synced() -> None:
    if APP_STATS_FILE.read_bytes() != LAB_STATS_FILE.read_bytes():
        raise ReleaseError(
            "The two 005.stat.txt files differ. Regenerate dictionary statistics first."
        )


def parse_version(version: str) -> tuple[int, int, int]:
    if VERSION_PATTERN.fullmatch(version) is None:
        raise ReleaseError("Version must use the MAJOR.MINOR.PATCH format")
    major, minor, patch = (int(part) for part in version.split("."))
    return major, minor, patch


def validate_new_version(version: str) -> None:
    if parse_version(version) <= parse_version(current_version()):
        raise ReleaseError(
            f"New version {version} must be greater than current version {current_version()}"
        )
    release_branch = f"release_v{version}"
    if ref_exists(f"refs/heads/{release_branch}"):
        raise ReleaseError(f"Branch {release_branch} already exists")
    if ref_exists(f"refs/tags/v{version}"):
        raise ReleaseError(f"Tag v{version} already exists")
    release_document = ROOT / "doc" / "releases" / f"release_v{version}.md"
    if release_document.exists():
        raise ReleaseError(f"Release document already exists: {release_document}")


def read_release_notes(argument: str | None) -> str:
    notes = argument
    if notes is None:
        try:
            notes = input("Google Play release notes in English: ")
        except EOFError as error:
            raise ReleaseError("Pass release notes with --notes in a non-interactive shell") from error
    notes = notes.strip()
    if not notes:
        raise ReleaseError("Release notes cannot be empty")
    if len(notes) > MAX_RELEASE_NOTES_LENGTH:
        raise ReleaseError(
            f"Release notes have {len(notes)} characters; the limit is {MAX_RELEASE_NOTES_LENGTH}"
        )
    return notes


def set_release_version(version: str) -> int:
    next_version_code = current_version_code() + 1
    VERSION_FILE.write_text(f"{version}\n", encoding="utf-8")
    build_text = BUILD_FILE.read_text(encoding="utf-8")
    updated_text, replacements = VERSION_CODE_PATTERN.subn(
        rf"\g<1>{next_version_code}",
        build_text,
        count=1,
    )
    if replacements != 1:
        raise ReleaseError(f"Cannot update versionCode in {BUILD_FILE}")
    BUILD_FILE.write_text(updated_text, encoding="utf-8")
    return next_version_code


def write_release_document(version: str, notes: str) -> Path:
    path = ROOT / "doc" / "releases" / f"release_v{version}.md"
    path.write_text(
        "\n".join(
            (
                f"# Release v{version}",
                "",
                "## Changes",
                "",
                f"- {notes}",
                "",
                "## Google Play release notes (en-US)",
                "",
                notes,
                "",
            )
        ),
        encoding="utf-8",
    )
    return path


def verify_bundle_signature() -> None:
    if not RELEASE_AAB.is_file():
        raise ReleaseError(f"Release bundle was not created: {RELEASE_AAB}")
    result = subprocess.run(
        ["jarsigner", "-verify", str(RELEASE_AAB)],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    if result.returncode != 0:
        print(result.stdout, file=sys.stderr, end="")
        raise ReleaseError("Release bundle signature verification failed")
    print("Release bundle signature verified.")


def prepare_release(args: argparse.Namespace) -> None:
    ensure_branch("develop")
    ensure_clean_worktree()
    fetch_and_verify_branches()
    ensure_stats_are_synced()
    validate_new_version(args.version)
    notes = read_release_notes(args.notes)
    run(["make", "play-auth-status"])

    release_branch = f"release_v{args.version}"
    git("switch", "-c", release_branch)
    version_code = set_release_version(args.version)
    notes_file = write_release_document(args.version, notes)
    git(
        "add",
        str(VERSION_FILE.relative_to(ROOT)),
        str(BUILD_FILE.relative_to(ROOT)),
        str(notes_file.relative_to(ROOT)),
    )
    git("commit", "-m", f"Release v{args.version}")

    run(["make", "release"])
    verify_bundle_signature()

    git("switch", "develop")
    git("merge", "--no-ff", release_branch, "-m", f"Merge {release_branch} into develop")
    git("switch", "master")
    git("pull", "--ff-only")
    git("merge", "--no-ff", "develop", "-m", f"Merge develop for v{args.version}")
    tree_diff = subprocess.run(
        ["git", "diff", "--quiet", release_branch, "master", "--", "."],
        cwd=ROOT,
    )
    if tree_diff.returncode != 0:
        raise ReleaseError("Master does not contain exactly the prepared release tree")
    git("push", "origin", "develop", "master")
    git("switch", "develop")

    run(["make", "play-upload-draft"])
    run(["make", "play-status"])
    ensure_clean_worktree()
    print(
        f"Release v{args.version} (versionCode {version_code}) is uploaded as a draft.\n"
        "Next: make release-server"
    )


def remote_command(server_path: str, command: str) -> str:
    return f"cd {shlex.quote(server_path)} && {command}"


def server_status(server: str, server_path: str) -> str:
    return output(
        [
            "ssh",
            "-o",
            "BatchMode=yes",
            "-o",
            "ConnectTimeout=8",
            server,
            remote_command(server_path, "make status"),
        ]
    )


def verify_server_status(status_text: str, expected_version: str) -> None:
    values = {}
    for line in status_text.splitlines():
        key, separator, value = line.partition("=")
        if separator:
            values[key] = value
    if values.get("state") != "running":
        raise ReleaseError(f"Server is not running:\n{status_text}")
    if values.get("version") != expected_version:
        raise ReleaseError(
            f"Server version is {values.get('version', 'unknown')}, expected {expected_version}"
        )


def update_server(args: argparse.Namespace) -> None:
    ensure_branch("develop")
    ensure_clean_worktree()
    fetch_and_verify_branches()
    version = current_version()
    command = remote_command(
        args.server_path,
        "sudo -v && sudo make update XWORD_REF=master && sudo make status",
    )
    run(["ssh", "-t", args.server, command])
    status_text = server_status(args.server, args.server_path)
    verify_server_status(status_text, version)
    print(status_text)
    print("Server update completed. Next: make release-publish")


def publish_release(args: argparse.Namespace) -> None:
    ensure_branch("develop")
    ensure_clean_worktree()
    fetch_and_verify_branches()
    version = current_version()
    version_code = current_version_code()
    master_version = git_output("show", "master:version.txt").strip()
    if master_version != version:
        raise ReleaseError(f"Develop version is {version}, but master version is {master_version}")

    status_text = server_status(args.server, args.server_path)
    verify_server_status(status_text, version)
    run(["make", "play-publish-internal"])
    play_status = output(["make", "--no-print-directory", "play-status"])
    completed_pattern = re.compile(
        rf"versionCodes=(?:[^\n]*,\s*)?{version_code}(?:,|\s).*status=completed"
    )
    if completed_pattern.search(play_status) is None:
        raise ReleaseError(
            f"Google Play does not report versionCode {version_code} as completed:\n{play_status}"
        )
    print(play_status)

    tag = f"v{version}"
    master_commit = git_output("rev-parse", "master")
    if ref_exists(f"refs/tags/{tag}"):
        tag_commit = git_output("rev-list", "-n", "1", tag)
        if tag_commit != master_commit:
            raise ReleaseError(f"Existing tag {tag} does not point to master")
    else:
        git("tag", tag, "master")
    git("push", "origin", tag)

    git("switch", "develop")
    git("pull", "--ff-only")
    fetch_and_verify_branches()
    ensure_clean_worktree()
    print(
        f"Release {tag} completed. Google Play and the server use version {version}.\n"
        "Current branch: develop. Local develop and master match GitHub."
    )


def release_status(args: argparse.Namespace) -> None:
    git("fetch", "origin")
    print(f"branch={current_branch()}")
    print(f"worktree_clean={'yes' if not git_output('status', '--porcelain') else 'no'}")
    for branch in ("develop", "master"):
        local_ref = git_output("rev-parse", branch)
        remote_ref = git_output("rev-parse", f"origin/{branch}")
        print(f"{branch}_synced={'yes' if local_ref == remote_ref else 'no'}")
    print(f"version={current_version()}")
    print(f"version_code={current_version_code()}")
    print(server_status(args.server, args.server_path))
    run(["make", "play-status"])


def add_server_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--server", default=DEFAULT_SERVER)
    parser.add_argument("--server-path", default=DEFAULT_SERVER_PATH)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the Words internal release workflow")
    subparsers = parser.add_subparsers(dest="command", required=True)

    prepare_parser = subparsers.add_parser("prepare")
    prepare_parser.add_argument("version")
    prepare_parser.add_argument("--notes")
    prepare_parser.set_defaults(handler=prepare_release)

    server_parser = subparsers.add_parser("server")
    add_server_arguments(server_parser)
    server_parser.set_defaults(handler=update_server)

    publish_parser = subparsers.add_parser("publish")
    add_server_arguments(publish_parser)
    publish_parser.set_defaults(handler=publish_release)

    status_parser = subparsers.add_parser("status")
    add_server_arguments(status_parser)
    status_parser.set_defaults(handler=release_status)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    try:
        args.handler(args)
    except ReleaseError as error:
        print(f"Release stopped: {error}", file=sys.stderr)
        raise SystemExit(1) from error


if __name__ == "__main__":
    main()
