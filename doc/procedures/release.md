# Internal release

This procedure publishes a tested `develop` revision to Google Play Internal testing and updates the server on Plex.

## Before starting

- Merge the finished feature into `develop`.
- Keep the working tree clean.
- If the dictionary changed, complete [`update.dicts.md`](update.dicts.md) first. The release script verifies that both copies of `005.stat.txt` match.
- Do not release while a network game is active. Updating the server resets in-memory rooms.

The scripts stop instead of overwriting local changes or unsynchronized Git branches.

## 1. Prepare the release

Choose the next version and run:

```bash
make release-prepare VERSION=1.0.12
```

Enter a short English Google Play release note when prompted. Mention only user-visible changes intended for the store listing.

This command:

- checks that the current branch is a clean and synchronized `develop`;
- creates `release_v<version>`;
- updates `version.txt` and increments Android `versionCode`;
- creates `doc/releases/release_v<version>.md` from the entered text;
- builds and verifies the signed AAB once;
- merges the release into `develop`, then merges `develop` into `master`;
- pushes `develop` and `master` to GitHub;
- uploads the AAB to Google Play Internal testing as a draft;
- returns the checkout to `develop`.

The draft is not available to testers yet.

## 2. Update Plex

Run:

```bash
make release-server
```

Enter the Plex `sudo` password when requested. The command updates `/srv/xword` from `master`, restarts `words-server`, and verifies that it is running with the new version.

## 3. Publish and finish

Run:

```bash
make release-publish
```

This command:

- verifies that Plex is running the new version;
- publishes the Google Play draft to Internal testing;
- confirms that Google Play reports the new `versionCode` as `completed`;
- creates and pushes the `v<version>` tag on `master`;
- returns to `develop`;
- verifies that local `develop` and `master` exactly match GitHub;
- verifies that the working tree is clean.

At this point the release is complete and available to internal testers.

## Status

At any point, inspect Git, Plex, and Google Play without changing the release:

```bash
make release-status
```

## One-time Google Play login

Only when credentials are missing or the devcontainer was recreated:

```bash
make play-login
make play-auth-status
```

## If a step fails

The script does not reset branches, delete commits, or publish the application after a failed check. Read the last error and run `make release-status`. Do not rerun `release-prepare` if it already created the release branch; inspect the state and continue from the failed operation.
