# S Pen Playback MVP

Android handwriting prototype for the Samsung Galaxy Z Fold6 with S Pen input and time-based handwriting playback.

## Current version

MVP 0.2 — multi-page handwriting.

## Features

- S Pen / Android stylus input
- Finger input ignored on the writing canvas for basic palm rejection
- Pressure-sensitive stroke width
- Historical `MotionEvent` sample capture for smoother paths
- Undo, clear, and whole-stroke eraser
- Timestamped stroke recording
- Play / pause handwriting reconstruction
- Seekable playback timeline
- Playback speeds: 0.5x, 1x, 2x, 4x
- Multiple persistent pages
- Previous / next page navigation
- Add and delete pages
- Automatic local saving
- Independent playback resume position per page

## Project structure

- `app/src/main/java/com/ravivariar/spenplayback/MainActivity.java` — UI, page management, ink capture/rendering, playback, and persistence
- `app/src/main/AndroidManifest.xml` — app manifest
- `app/src/main/res/values/styles.xml` — theme
- `app/build.gradle` — Android application configuration
- `build.gradle` / `settings.gradle` — root Gradle configuration

## Build

Open the `spen-v02` directory as a project in Android Studio and build the `app` module.

Current configuration:

- compileSdk: 36
- targetSdk: 36
- minSdk: 23
- applicationId: `com.ravivariar.spenplayback.mvp`
- version: `0.2.0`

The MVP intentionally uses Android framework `View`, `Canvas`, and `MotionEvent` APIs so the ink/playback model stays small and easy to iterate. A later version can move rendering to Jetpack Ink while preserving the timestamped stroke model.

## Data model

A page contains strokes. Each stroke contains normalized `(x, y)` coordinates, pressure, and a relative timestamp for every sampled point. Playback reconstructs the handwriting against that recorded timeline.

## Recommended next milestones

1. Page thumbnails and page reordering
2. Pen color and width controls
3. Lasso selection
4. Better low-latency rendering with Jetpack Ink
5. Notebook management
6. PDF backgrounds/import
7. Audio synchronized with handwriting
