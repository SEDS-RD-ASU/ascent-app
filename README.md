# GOOBER Connect

The ground-station app for SEDS-RD-ASU's ASCENT flight computer. It finds ASCENT boards over Bluetooth,
shows their telemetry, runs the team's pre-flight checklist, and sends board commands.

## Install on Android

1. On your Android phone, open this link. It always gets the newest build:
   **https://github.com/SEDS-RD-ASU/ascent-app/releases/latest/download/goober-connect.apk**
2. Tap the downloaded file. The first time, Android asks you to allow installs from your browser: allow
   it, then tap **Install**.
3. Open GOOBER Connect and allow Bluetooth when it asks.

To update, open the same link again. The new build installs over the old one and keeps your checklist.

Older builds are on the [Releases page](https://github.com/SEDS-RD-ASU/ascent-app/releases). To go back to
one, uninstall the app first: Android won't install an older version over a newer one.

## Install on iPhone

There's no download for iPhones yet. That needs a paid Apple Developer account, and then TestFlight.
Until then, build it from a Mac with Xcode:

1. Open `iosBlueFalconExampleMP/iosBlueFalconExampleMP.xcodeproj` in Xcode.
2. Under **Signing & Capabilities**, pick your own team (your Apple ID), and change the bundle identifier
   to something unique, such as `yourname.goober`.
3. Plug in your iPhone, pick it as the destination, and press **Run**.

The first time, trust yourself as a developer on the phone: Settings → General → VPN & Device Management.
The app stops opening after 7 days, and then needs building again.

## Safety

Before sending any command, check the board name on the card. Pop Apogee, Pop Mains and Erase Flash can't
be undone.
