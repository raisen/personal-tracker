# Personal Tracker

A personal tracker app. Data is stored in GitHub Gists.

## Setup

You need a GitHub personal access token with the `gist` scope. Create one at **Settings → Developer settings → Personal access tokens** on GitHub.

## Running

```bash
npm install
npm run dev      # start dev server on port 5173
npm run build    # production build → dist/
```

## Android APK

The latest debug APK is built automatically on every push to `main` and published as a GitHub release.

**[Download latest APK](../../releases/download/debug-latest/app-debug.apk)** — open this link on your phone to install directly.

To build locally:

```bash
cd android
./gradlew assembleDebug    # APK output: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug     # build + install to connected device via adb
```
