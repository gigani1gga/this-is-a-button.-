# This is a button

A one-screen Android app with a single button. Pressing it spins up CPU and
memory load **inside the app's own process** and then crashes the app on purpose.
It does not touch the rest of the device.

## Build

Locally (needs JDK 17, Android SDK 34, Gradle 8.9):

    gradle assembleDebug

The APK lands in `app/build/outputs/apk/debug/`. Pushing to `main` also builds it
in GitHub Actions and uploads it as the `ThisIsAButton-debug-apk` artifact.

## Unzip and organize

Upload a `.zip` to the repo root (on `main`) and the **Unzip and organize**
workflow unpacks it into the repo, deletes the zip, commits, and starts the
Android build. You can also run it by hand from the Actions tab.
