# ClipNotepad

A lightweight Android clipboard note-taking app with a floating circular button overlay that can appear above other apps.

## Features
- Save current clipboard text to local storage
- Store large notes efficiently in files while keeping the index lightweight
- View saved notes from a local list UI
- Floating circular button overlay to save clipboard text while using other apps
- Works without a server or account

## Setup
1. Open the project in Android Studio.
2. Let Gradle sync.
3. Connect an Android device or emulator.
4. Run the app.
5. Tap `Enable floating button` and allow the overlay permission.

## Notes
- `SYSTEM_ALERT_WINDOW` is required for the overlay button to display above other apps.
- The app stores note text in internal app storage (`files/texts/...`).
