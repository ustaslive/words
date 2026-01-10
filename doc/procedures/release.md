# Release process (internal testing)

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
- Read current values in `app/build.gradle.kts`:
  - `versionCode` must strictly increase.
  - `versionName` must match the tag version without the `v` prefix, for example `1.2.3` with tag `v1.2.3`.
- If Gradle values and tags do not align, fix them before creating the release branch.

## Create the release branch

- Start from `develop`:
  - `git checkout develop`
  - `git pull`
- Create `release_v<version>`:
  - `git checkout -b release_v<version>`
- Only release-specific changes are allowed in this branch:
  - Update `versionCode` and `versionName` in `app/build.gradle.kts`.
  - Create `doc/releases` if it does not exist.
  - Create release notes in `doc/releases/release_v<version>.md`.
  - Commit the release changes.

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
  - `git pull`
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
docker cp words:/words/app/build/outputs/bundle/release/app-release.aab ./app-release-v<version>.aab
```

## Google Play Console steps (internal testing)

- Open Google Play Console and select the app.
- Go to the Internal testing track.
- Create a new release and upload the AAB.
- Paste the release notes from `doc/releases/release_v<version>.md`.
- Review and roll out the release to internal testers.

## Tag and push (after successful upload)

- Only tag after the AAB is successfully uploaded and rolled out.
- Create the tag on `master`:
  - `git tag v<version>`
- Push code and tags:
  - `git push origin develop master`
  - `git push origin v<version>`

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
