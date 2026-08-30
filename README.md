# AndroidApp3 — StepQuest

A small Android app that reads the device's motion sensors and turns them into a
walk tracker. Built for the Android Device Sensors assignment; it re-uses the
ideas from the LocationFinder tutorial (manifest + runtime permission, a system
service, a listener registered and unregistered with the Activity lifecycle,
raw readings converted into something readable, a second screen) but applies
them to `SensorManager` instead of location.

## What it does

- Counts steps with the **hardware step counter** (`Sensor.TYPE_STEP_COUNTER`).
- Falls back to a **peak-detection algorithm over the accelerometer** when the
  device has no pedometer — which is every emulator I tested on.
- Shows the **raw accelerometer x/y/z stream** live, plus a smoothed
  "net movement" value.
- Derives **distance, calories and cadence** from the step count, and fills a
  progress bar toward a step goal.
- Saves finished walks to an in-memory repository and lists them on a
  **history screen** backed by a `RecyclerView`.

## Structure

| File | Job |
|---|---|
| `MainActivity.kt` | Permission flow, sensor lifecycle, drawing the live screen |
| `sensor/StepTracker.kt` | The only class that talks to `SensorManager` |
| `sensor/AccelerometerStepDetector.kt` | Step detection from raw acceleration |
| `util/StepMath.kt` | Steps → km / kcal / cadence, and all formatting |
| `data/SessionRepository.kt` | In-memory store shared by both screens |
| `model/StepSession.kt` | One saved walk |
| `adapter/SessionAdapter.kt` | Rows for the history list |
| `HistoryActivity.kt` | Second screen |

## Notes

- `minSdk` 24, `targetSdk` 36, Kotlin + XML views, view binding.
- `ACTIVITY_RECOGNITION` is only a *runtime* permission from Android 10 (API 29),
  so the app version-checks before asking.
- History is session-only — no database yet. That's the next assignment.

See `AIReflection.md` for the AI usage write-up.
