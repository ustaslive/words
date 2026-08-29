#!/usr/bin/env python3

import argparse
import re
from pathlib import Path

import google.auth
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload


ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
DEFAULT_LANGUAGE = "en-US"
MAX_RELEASE_NOTES_LENGTH = 500
RELEASE_NOTES_HEADING = "Google Play release notes"
VERSION_CODE_PATTERN = re.compile(r"versionCode\s*=\s*(\d+)")


def project_root() -> Path:
    return Path(__file__).resolve().parent.parent


def app_version() -> str:
    return (project_root() / "version.txt").read_text(encoding="utf-8").strip()


def app_version_code() -> int:
    build_file = project_root() / "app" / "build.gradle.kts"
    match = VERSION_CODE_PATTERN.search(build_file.read_text(encoding="utf-8"))
    if match is None:
        raise RuntimeError(f"Cannot find versionCode in {build_file}")
    return int(match.group(1))


def release_notes() -> str:
    version = app_version()
    notes_file = project_root() / "doc" / "releases" / f"release_v{version}.md"
    text = notes_file.read_text(encoding="utf-8")
    heading = re.search(
        rf"^##\s+{re.escape(RELEASE_NOTES_HEADING)}(?:\s+\([^)]*\))?\s*$",
        text,
        flags=re.MULTILINE,
    )
    if heading is None:
        raise RuntimeError(f"Missing '{RELEASE_NOTES_HEADING}' section in {notes_file}")
    remainder = text[heading.end() :]
    next_heading = re.search(r"^##\s+", remainder, flags=re.MULTILINE)
    notes = remainder[: next_heading.start() if next_heading else None].strip()
    if not notes:
        raise RuntimeError(f"Empty Google Play release notes in {notes_file}")
    if len(notes) > MAX_RELEASE_NOTES_LENGTH:
        raise RuntimeError(
            f"Google Play release notes have {len(notes)} characters; "
            f"the limit is {MAX_RELEASE_NOTES_LENGTH}"
        )
    return notes


def publisher_service():
    credentials, _ = google.auth.default(scopes=[ANDROID_PUBLISHER_SCOPE])
    return build("androidpublisher", "v3", credentials=credentials, cache_discovery=False)


def create_edit(service, package_name: str) -> str:
    return service.edits().insert(packageName=package_name, body={}).execute()["id"]


def delete_edit(service, package_name: str, edit_id: str) -> None:
    service.edits().delete(packageName=package_name, editId=edit_id).execute()


def get_track(service, package_name: str, edit_id: str, track_name: str) -> dict:
    return (
        service.edits()
        .tracks()
        .get(packageName=package_name, editId=edit_id, track=track_name)
        .execute()
    )


def print_track(track: dict) -> None:
    releases = track.get("releases", [])
    if not releases:
        print(f"Track {track.get('track', 'unknown')}: no releases")
        return
    for release in releases:
        codes = ", ".join(release.get("versionCodes", []))
        print(
            f"Track {track.get('track', 'unknown')}: "
            f"versionCodes={codes} status={release.get('status', 'unknown')} "
            f"name={release.get('name', '')}"
        )


def status(args: argparse.Namespace) -> None:
    service = publisher_service()
    edit_id = create_edit(service, args.package)
    try:
        print_track(get_track(service, args.package, edit_id, args.track))
    finally:
        delete_edit(service, args.package, edit_id)


def upload_draft(args: argparse.Namespace) -> None:
    aab_path = (project_root() / args.aab).resolve()
    if not aab_path.is_file():
        raise RuntimeError(f"Missing release bundle: {aab_path}. Run 'make release' first.")

    expected_code = app_version_code()
    version = app_version()
    notes = release_notes()
    service = publisher_service()
    edit_id = create_edit(service, args.package)
    committed = False
    try:
        current_track = get_track(service, args.package, edit_id, args.track)
        media = MediaFileUpload(
            str(aab_path),
            mimetype="application/octet-stream",
            resumable=True,
        )
        uploaded = (
            service.edits()
            .bundles()
            .upload(packageName=args.package, editId=edit_id, media_body=media)
            .execute()
        )
        uploaded_code = int(uploaded["versionCode"])
        if uploaded_code != expected_code:
            raise RuntimeError(
                f"Uploaded versionCode {uploaded_code}, expected {expected_code}"
            )

        previous_releases = [
            release
            for release in current_track.get("releases", [])
            if str(expected_code) not in release.get("versionCodes", [])
        ]
        draft_release = {
            "name": f"{expected_code} ({version})",
            "versionCodes": [str(expected_code)],
            "releaseNotes": [{"language": DEFAULT_LANGUAGE, "text": notes}],
            "status": "draft",
        }
        body = {"track": args.track, "releases": previous_releases + [draft_release]}
        (
            service.edits()
            .tracks()
            .update(
                packageName=args.package,
                editId=edit_id,
                track=args.track,
                body=body,
            )
            .execute()
        )
        service.edits().validate(packageName=args.package, editId=edit_id).execute()
        service.edits().commit(packageName=args.package, editId=edit_id).execute()
        committed = True
        print(f"Uploaded versionCode {expected_code} to {args.track} as draft.")
    finally:
        if not committed:
            delete_edit(service, args.package, edit_id)


def publish(args: argparse.Namespace) -> None:
    expected_code = str(app_version_code())
    service = publisher_service()
    edit_id = create_edit(service, args.package)
    committed = False
    try:
        track = get_track(service, args.package, edit_id, args.track)
        draft = next(
            (
                release
                for release in track.get("releases", [])
                if expected_code in release.get("versionCodes", [])
                and release.get("status") == "draft"
            ),
            None,
        )
        if draft is None:
            raise RuntimeError(
                f"No draft for versionCode {expected_code} on track {args.track}"
            )
        completed_release = dict(draft)
        completed_release["status"] = "completed"
        completed_release.pop("userFraction", None)
        body = {"track": args.track, "releases": [completed_release]}
        (
            service.edits()
            .tracks()
            .update(
                packageName=args.package,
                editId=edit_id,
                track=args.track,
                body=body,
            )
            .execute()
        )
        service.edits().validate(packageName=args.package, editId=edit_id).execute()
        service.edits().commit(packageName=args.package, editId=edit_id).execute()
        committed = True
        print(f"Published versionCode {expected_code} to {args.track}.")
    finally:
        if not committed:
            delete_edit(service, args.package, edit_id)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Manage Google Play internal releases")
    subparsers = parser.add_subparsers(dest="command", required=True)
    for name, handler in (
        ("status", status),
        ("upload-draft", upload_draft),
        ("publish", publish),
    ):
        subparser = subparsers.add_parser(name)
        subparser.add_argument("--package", required=True)
        subparser.add_argument("--track", default="internal")
        if name == "upload-draft":
            subparser.add_argument("--aab", required=True)
        subparser.set_defaults(handler=handler)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    args.handler(args)


if __name__ == "__main__":
    main()
