# Android download link

- **Date:** 2026-11-01
- **Status:** Design approved; not implemented yet
- **Platforms:** Android. iPhones come later, through TestFlight, once the team has a paid Apple Developer account.
- **Branch:** `android-download`, from `main`

## Why

Teammates need to install GOOBER Connect without the person who builds it, for example before a test
launch. The team has a mix of iPhones and Android phones.

- **iPhones:** Apple only allows installing an app on someone else's iPhone through TestFlight or the App
  Store, or on a list of registered devices. All of these need a paid Apple Developer account
  ($99/year), and the team doesn't have one. With a free account, every install has to come from a Mac
  with Xcode, and the app stops opening after 7 days.
- **Android:** any phone can install an app file (an APK) from a download link.

This change:
- has GitHub build the Android app and publish it at one link that always gets the newest build;
- adds a README telling teammates how to install it;
- gives the app a proper package ID before anyone installs it;
- makes the app ask for Bluetooth permission on older Android versions too.

It's also the Android version's first build ever. Nobody has compiled it until now, including the Android
pieces of the scan screen and the checklist.

## What teammates see

### Installing

1. On their Android phone, they open
   `https://github.com/SEDS-RD-ASU/ascent-app/releases/latest/download/goober-connect.apk`.
2. They tap the downloaded file. The first time, Android asks them to allow installs from their browser;
   they allow it and tap **Install**.
3. They open GOOBER Connect and allow Bluetooth when asked.

### Updating

They open the same link again. The new build installs over the old one and keeps their checklist.

### Older builds

Every build stays on the repo's Releases page, as "Build 12", "Build 13" and so on. If a new build
misbehaves, they can install an older one, after uninstalling the newer one (Android won't install an
older version over a newer one).

### The README

A new `README.md` at the top of the repo, which has none yet, with three parts:
- **Install on Android:** the link and the steps above.
- **Install on iPhone:** TestFlight once the team has a paid Apple Developer account. Until then, build
  it from a Mac with Xcode: open `iosBlueFalconExampleMP`, pick your own team and a unique bundle ID,
  plug in the phone and press Run. It stops opening after 7 days and needs building again.
- **Safety:** before sending any command, check the board name on the card.

## How it works

No new libraries.

### App changes (`androidBlueFalconExampleMP`)

- **Package ID:**
  - `applicationId` changes from `com.example.bluefalconcomposemultiplatform.android` to
    `io.github.sedsrdasu.gooberconnect`, named after the GitHub org.
  - It changes now because phones use it to recognise the app; changing it after people install it
    would make their phones treat the new version as a different app.
  - The code's `namespace` stays as it is.
- **Version:**
  - `versionCode` and `versionName` come from the Gradle properties `versionCode` and `versionName`
    when they're given. Otherwise they stay 1 and "1.0", as for builds on a Mac.
  - The workflow passes GitHub's run counter N, giving version code N and version name `1.0.N`. Each
    build therefore counts as an update.
- **Signing:**
  - The `release` build signs with the key named by three environment variables:
    `GOOBER_KEYSTORE_FILE`, `GOOBER_KEYSTORE_PASSWORD` and `GOOBER_KEY_ALIAS`. The key password is the
    keystore password.
  - Without them, `assembleRelease` makes an unsigned file, as now. Debug builds are unchanged.
- **Permissions:** `MainActivity` asks for what each Android version needs, matching what the scan screen
  checks (`AndroidPlatformBluetooth`):

  | Android version | What the app asks for |
  |---|---|
  | 7–11 | Precise location, which Android then required for Bluetooth scanning. Today the app asks for nothing on these versions, so the scan screen says permission is needed and points to Settings. |
  | 12 and newer | Nearby devices (Bluetooth scan and connect). Location is no longer asked for: the app doesn't need it there, and refusing it showed a misleading "Cannot scan without permission". |

### The workflow (`.github/workflows/android.yml`)

It runs on GitHub's free machines (free because the repo is public), using only GitHub's own tools:
checkout, set up Java 17, Gradle, and the `gh` command.

| When | What it does |
|---|---|
| A pull request is opened or updated | Builds the debug app as a check. No key needed, nothing published. |
| A push to `main` (a merged pull request) | Builds the signed release app and publishes it (below) |
| "Run workflow" pressed on GitHub | Same as a push to `main` |

Publishing a build N:
1. The signed app is renamed `goober-connect.apk`.
2. It goes up as a new release, tagged `build-N` on the commit it was built from, titled "Build N", and
   marked as the latest release. That's what keeps the link above pointing at it.
3. The release notes give:
   - the commit it was built from, with its first line;
   - the install link;
   - "Before sending any command, check the board name on the card."

If the signing secrets are missing, the run stops with "signing key not set up" before building, rather
than publishing an app that can't update the installed one.

### The signing key

Android only installs an update over an existing app if both were signed with the same key. GitHub would
otherwise make a new key on every build, so teammates would have to uninstall first each time and lose
their checklist.

- **Making it:** made once, with Java's `keytool` (a PKCS12 key file with one password).
- **Storing it:** saved as three repository secrets, under Settings → Secrets and variables → Actions:

  | Secret | Value |
  |---|---|
  | `ANDROID_KEYSTORE_BASE64` | The key file, as base64 |
  | `ANDROID_KEYSTORE_PASSWORD` | Its password |
  | `ANDROID_KEY_ALIAS` | The key's name |

  Adding them needs admin access to the repo. Without it, the org owner makes and adds the key.
- **Never in the repo:** the repo is public, and anyone holding the key could make an app that installs as
  an "update" of GOOBER Connect, an app that can fire pyros.
- **Backup:** keep the key file and password somewhere safe, such as the team's shared drive or a password
  manager. If they're lost, the next build gets a new key, and everyone has to uninstall and reinstall
  once.

## Testing

1. **On the Mac:**
   - Install Google's Android command-line tools with Homebrew (about 1–2 GB: the Android 14 SDK and build
     tools). The SDK location goes in `local.properties`, which is git-ignored.
   - Build the debug app, and the release app signed with a throwaway test key.
   - Inspect the release file: package `io.github.sedsrdasu.gooberconnect`, label "GOOBER Connect", the
     version given, and a valid signature (`apksigner verify`).
   - The iOS unit tests still pass.
2. **On GitHub:** the pull request's check passes.
3. **After merging:** a "Build N" release appears, and the link downloads `goober-connect.apk`.
4. **With a teammate's Android phone:**
   - They install it from the link, get the permission prompt and see the scan screen.
   - They install the next build over it, and their checklist is still there.
   - Neither of us has an Android phone, so this needs a teammate.

## Not included / open questions

- **iPhones:** TestFlight once there's a paid Apple Developer account; worth asking SEDS or ASU. A free
  route (sideloading with AltStore or Sideloadly, renewed every 7 days) is possible later.
- **An app icon:** Android shows a generic icon until there is one.
- **The Play Store:** not planned.
- **Old releases** pile up, one per merge. That's fine for now; they can be tidied if the list gets long.
- **The board commands** have still never touched a real board. Someone should run those checks before a
  teammate relies on the app at a launch.
