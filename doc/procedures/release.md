# Release process (internal testing)

## TL;DR (quick checklist)

- Create `release_v<version>` from `develop`, bump versions in `version.txt` and `app/build.gradle.kts`, and add release notes in `doc/releases/release_v<version>.md`. Include a copy-ready Google Play summary of no more than 500 Unicode characters.
- If `app/src/main/assets/words.txt` or `app/src/main/assets/forbidden_words.txt` changed, regenerate `Random letters (adv)` stats and sync `lab/crossword_repeatability/005.stat.txt` with `app/src/main/assets/005.stat.txt`.
- Build and verify the release bundle, then merge to `develop` and `master`, build again, and upload to Google Play (internal testing).
- Tag and push only after the Google Play rollout succeeds, update the production server, then return the development checkout to `develop`.

Command template:

```
git checkout develop
git pull --ff-only
git checkout -b release_v<version>
# Edit version.txt (versionName) and app/build.gradle.kts (versionCode)
# If words.txt or forbidden_words.txt changed, run tools/update_mode005_stats.py
# Create doc/releases/release_v<version>.md with release notes
git add version.txt app/build.gradle.kts doc/releases/release_v<version>.md
# Also add app/src/main/assets/words.txt app/src/main/assets/forbidden_words.txt lab/crossword_repeatability/005.stat.txt app/src/main/assets/005.stat.txt when updated
git commit -m "Release v<version>"
./gradlew bundleRelease
git checkout develop
git merge --no-ff release_v<version> -m "Merge release_v<version> into develop"
git checkout master
git pull --ff-only
git merge --no-ff develop -m "Merge develop for v<version>"
./gradlew bundleRelease
# Upload the AAB to Google Play and roll it out to internal testing before continuing.
git tag v<version>
git push origin develop master
git push origin v<version>
# On the production server:
cd /srv/xword
sudo -v
sudo make update XWORD_REF=master
sudo make status
# Exit SSH and return to the local development checkout:
git checkout develop
git pull --ff-only
```

Files and fields to update:

- `version.txt`: `versionName` value (must match `v<version>` without the `v`)
- `app/build.gradle.kts`: `versionCode` (must strictly increase)
- `doc/releases/release_v<version>.md`: release notes text, including a Google Play summary limited to 500 Unicode characters per language
- If the dictionary or forbidden-word list changed:
  - `app/src/main/assets/words.txt`
  - `app/src/main/assets/forbidden_words.txt`
  - `lab/crossword_repeatability/005.stat.txt`
  - `app/src/main/assets/005.stat.txt`

This guide documents how to prepare a release for internal testing and publish it to Google Play.
`master` contains only stable versions; release builds and version tags are created on `master`.
`develop` may contain unstable work; builds from `develop` are debug-only for local testing via ADB and are not uploaded to Google Play.
Production release steps are not finalized yet. Placeholder sections are marked as TODO.

## Pre-release checks

- All development branches intended for the release are verified to compile in release mode.
- All those branches are merged into `develop`.
- `develop` is used for debug builds and local testing only.

## Pick the new version

The release version must match both the Gradle config and the Git tag.

- Check the latest tag:
  - `git tag --list "v*" --sort=version:refname | tail -n 1`
- Decide the next version by incrementing that tag (patch, minor, or major).
- Read current values:
  - `version.txt` holds `versionName` and must match the tag version without the `v` prefix, for example `1.2.3` with tag `v1.2.3`.
  - `versionCode` in `app/build.gradle.kts` must strictly increase.
- If the version file, Gradle values, and tags do not align, fix them before creating the release branch.

## Create the release branch

- Start from `develop`:
  - `git checkout develop`
  - `git pull --ff-only`
- Create `release_v<version>`:
  - `git checkout -b release_v<version>`
- Only release-specific changes are allowed in this branch:
  - Update `version.txt` with the new `versionName`.
  - Update `versionCode` in `app/build.gradle.kts`.
  - If the dictionary or forbidden-word list changed, regenerate `Random letters (adv)` stats and update both `005.stat.txt` copies.
  - Create `doc/releases` if it does not exist.
  - Create release notes in `doc/releases/release_v<version>.md`.
  - Include a clearly marked, copy-ready Google Play summary of no more than 500 Unicode characters per language.
  - Commit the release changes.

## If the dictionary or forbidden-word list changed: regenerate `Random letters (adv)` stats

The `Random letters (adv)` mode depends on `005.stat.txt`.
When `app/src/main/assets/words.txt` or `app/src/main/assets/forbidden_words.txt` changes, regenerate the stats before building the release.

Run from the repository root (`/words` in the devcontainer):

```bash
python3 tools/update_mode005_stats.py 10000 --verbose
```

What this does:

- Uses `app/src/main/assets/words.txt` as the source dictionary.
- Uses `app/src/main/assets/forbidden_words.txt` if that file exists.
- Produces a timestamped file such as `lab/crossword_repeatability/005.20260304224411.txt`.
- Copies the fresh stats into:
  - `lab/crossword_repeatability/005.stat.txt`
  - `app/src/main/assets/005.stat.txt`

Why both copies matter:

- `app/src/main/assets/005.stat.txt` is bundled into the APK/AAB.
- `lab/crossword_repeatability/005.stat.txt` is the file downloaded by the app's dictionary update flow.

Before committing, verify that these files are in sync:

```bash
cmp -s /words/lab/crossword_repeatability/005.stat.txt /words/app/src/main/assets/005.stat.txt
```

Then add the updated files to the release commit:

```bash
git add app/src/main/assets/words.txt
git add app/src/main/assets/forbidden_words.txt
git add lab/crossword_repeatability/005.stat.txt
git add app/src/main/assets/005.stat.txt
```

## Verify the release branch

- Build the release artifact:
  - `./gradlew bundleRelease`

## Develop branch builds (debug only)

- `develop` is for local testing and ADB installs only.
- Build and install debug builds from `develop`:
  - `./gradlew assembleDebug`
  - `./gradlew installDebug`
- Do not upload builds from `develop` to Google Play Console.

## Merge after verification

- Merge `release_v<version>` into `develop`.
- Merge `develop` into `master`.

## Build the final artifact

- Build from `master`:
  - `git checkout master`
  - `git pull --ff-only`
- Build the release bundle:
  - `./gradlew bundleRelease`
- Output path:
  - `app/build/outputs/bundle/release/app-release.aab`

## Verify the release bundle signature

Run from the repository root (`/words` in the devcontainer):

```
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

## Copy the AAB from the devcontainer

Run on the host:

```
cd ~/Downloads
docker cp words:/words/app/build/outputs/bundle/release/app-release.aab ./app-release-v<version>.aab
```

## Google Play Console steps (internal testing)

- Open Google Play Console and select the app.
- Go to `Test and release` > `Testing` > `Internal testing` and open the `Releases` tab.
- Create a new release and upload the AAB.
- Verify that Google Play shows the expected `versionName` and `versionCode`.
- Paste the copy-ready Google Play summary from `doc/releases/release_v<version>.md`.
- Keep release notes within the Google Play limit of 500 Unicode characters per language.
- Review and roll out the release to internal testers.

## Tag and push (after successful Google Play rollout)

- Only tag after the AAB is successfully uploaded and rolled out.
- Create the tag on `master`:
  - `git tag v<version>`
- Push code and tags:
  - `git push origin develop master`
  - `git push origin v<version>`

## Update the production server

The client and server currently require exact version equality. Update the production server immediately after pushing `master` and the release tag so newly updated clients can connect.

The following commands apply to the project server installed at `/srv/xword`:

```bash
# SSH to the server that runs the words-server Docker container.
cd /srv/xword
sudo -v
sudo make update XWORD_REF=master
sudo make status
```

Confirm that `make status` reports:

- `state=running`
- `version=<version>`

The update rebuilds and recreates the Docker container. The room state is stored in memory, so active connections and the current network game are reset during the update.

For other server installation layouts, follow `server/README.md` instead of using the `/srv/xword` path.

## Return to development

After the release and server update, leave the local development checkout on the current `develop` branch:

```bash
git checkout develop
git pull --ff-only
git status --short --branch
```

Create every new feature branch from an up-to-date `develop`, not from `master`.

## If upload fails

- Do not tag the failed release.
- Return to `develop`, fix the issue, and create a new `release_v<version>` branch.
- Bump the version again and repeat the release flow.
- If the fix requires more development, create a normal feature branch from `develop` and follow the standard development flow, then restart the release process with a higher version.

## TODO: Google Play Console steps (production)

- Select the Production track.
- Create a new production release and upload the AAB.
- Paste release notes and verify store listing assets.
- Review and roll out the production release.
