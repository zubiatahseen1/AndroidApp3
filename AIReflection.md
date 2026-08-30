# AI Usage and Reflection — AndroidApp3 (StepQuest)

**Zubia Tahseen** · Mobile and Web Developer (using AI), Trios College

---

## 1. How did I use AI in this assignment?

I used AI heavily on this one, but as a **pair programmer I directed rather than a
vending machine**. I decided the app concept (a step tracker built on the device's
motion sensors), decided how it should map back to the LocationFinder tutorial, and
decided the file structure I wanted — the same `model` / `data` / `util` / `adapter`
split I used in AndroidApp2, because that's the structure my class has been teaching.
AI wrote most of the Kotlin and XML inside that structure, and I reviewed, corrected
and re-directed it as it went.

Roughly: the boilerplate (layouts, adapter, data class, string resources) I kept
close to as-generated; the sensor code I iterated on several times; and a couple of
suggestions I threw out entirely.

**Example — a prompt that changed the design.** My first framing was just "count
steps with `TYPE_STEP_COUNTER`." When I asked *"what happens if I run this on the
emulator?"* the answer was that `getDefaultSensor(TYPE_STEP_COUNTER)` returns `null`
on most emulators and the screen would just sit at zero with no explanation. That
turned into the most interesting part of the app: `StepTracker` now picks a
`Source` at construction time — hardware counter, accelerometer fallback, or none —
and tells the user on screen which one it landed on. That's a direct parallel to
LocationFinder falling back from GPS to the network provider, and I would not have
thought of it if I hadn't asked the "what breaks?" question.

**Example — something I chose not to use.** An early suggestion was to run the step
counting in a foreground `Service` with a persistent notification so it kept counting
with the screen off. It's the "correct" answer for a real fitness app, but it's well
past what we've covered, it adds `FOREGROUND_SERVICE_HEALTH` permissions and a
notification channel, and it would have buried the one thing this assignment is
actually about — registering and unregistering a listener in `onResume` / `onPause`.
I kept the simple lifecycle version and left a note in the README that persistence
and background tracking are the next step.

**Concepts I hadn't seen in class.** Three of them:

- **`SensorEventListener` and `SensorManager`** — we'd covered `LocationManager` in
  the LocationFinder tutorial but not sensors. I read the Android developer guide on
  [Sensors Overview](https://developer.android.com/guide/topics/sensors/sensors_overview)
  and had AI walk me through how `registerListener`/`unregisterListener` line up
  one-to-one with `requestLocationUpdates`/`removeUpdates`. Seeing it as the *same
  pattern with different nouns* is what made it click.
- **`ACTIVITY_RECOGNITION` being a runtime permission only from API 29** — I found
  this in the permissions reference after my first version asked for it
  unconditionally. That's why `hasActivityPermission()` has an explicit
  `Build.VERSION.SDK_INT < Q` check instead of just always asking.
- **Low-pass filtering / peak detection**, for the accelerometer fallback. I had AI
  explain the three stages (magnitude → remove gravity and smooth → rising-edge
  detection with a debounce) and I wrote them into the comments in
  `AccelerometerStepDetector.kt` in my own words, because if I couldn't explain the
  debounce I had no business shipping it.

---

## 2. How did I understand, verify and adapt the code?

**Verification.** Three things:

1. **Reading it line by line before running it.** Anywhere I couldn't say out loud
   what a line did, I asked until I could. The comments in the repo are the result —
   they're my understanding written down, not decoration.
2. **Building and running it in Android Studio**, on the emulator (which exercises
   the accelerometer fallback path) and by walking with the phone (which exercises
   the hardware counter path). I used `Log.d` in `StepTracker` and watched Logcat to
   confirm which `Source` was chosen and that step events were actually arriving.
3. **A static pass over the resources** — checking that every `R.string` and every
   `binding.<id>` in the Kotlin actually exists in the XML, which caught a couple of
   names I'd drifted on.

**Key change #1 — the step-counter baseline.** The first version showed a step count
in the tens of thousands the moment I pressed Start. Logging the raw
`event.values[0]` explained it: `TYPE_STEP_COUNTER` reports **steps since the device
last rebooted**, not since the app opened. The fix is the `baselineSteps` field in
`StepTracker` — capture the first reading of a walk and report the difference. I also
added `.coerceAtLeast(0)`, because if the phone reboots mid-walk the hardware counter
resets below the baseline and the app would otherwise display a negative step count.
This is my favourite bug of the assignment: nothing crashed, the number was just
*wrong*, and only reading the raw value told me why.

**Key change #2 — pulling the maths out of the Activity.** AI's first draft did the
steps → km → calories conversion inline in `MainActivity`, mixed in with the view
updates. I moved all of it into `util/StepMath.kt`. Two reasons: `MainActivity` is
already responsible for permissions and lifecycle and didn't need a third job, and
`StepMath` is now plain Kotlin with no Android imports, so it's the one part of this
app I could unit test without an emulator. I also fixed a division-by-zero in
`cadence()` — the original divided by elapsed minutes, which is zero in the first
instant of a walk.

**Smaller adaptations:** I switched the history row from a hard-coded `"%d steps"` to
a `<plurals>` resource so a one-step walk doesn't read "1 steps"; and I removed a
`title = ...` line that would have silently done nothing, because the app theme is
`NoActionBar` — there was no action bar for the title to appear in. I also hit a
genuinely obscure one: XML comments can't contain a double hyphen, so my
`<!-- ---- section ---- -->` separator lines made the layout fail to parse until I
changed them.

---

## 3. What did I learn or get better at?

**The thing I levelled up on: recognising a pattern instead of memorising an API.**

Going in, I thought "sensors" was a whole new topic I'd have to learn from scratch.
What actually happened is that I saw LocationFinder's shape again in different
clothes:

| LocationFinder | StepQuest |
|---|---|
| `getSystemService(LOCATION_SERVICE)` | `getSystemService(SENSOR_SERVICE)` |
| `LocationListener` | `SensorEventListener` |
| `requestLocationUpdates()` in `onResume` | `registerListener()` in `onResume` |
| `removeUpdates()` in `onPause` | `unregisterListener()` in `onPause` |
| `ACCESS_FINE_LOCATION` runtime request | `ACTIVITY_RECOGNITION` runtime request |
| GPS → network provider fallback | step counter → accelerometer fallback |
| Geocoder: coordinates → an address | `StepMath`: steps → km, kcal, cadence |

Once I saw that table, the API names stopped being things to memorise. **Ask the OS
for a service, subscribe while you're visible, unsubscribe when you're not, translate
the raw value into something a person cares about, handle the case where the hardware
isn't there.** That's the pattern, and I expect the next sensor I touch to follow it.

I also got noticeably better at **asking AI adversarial questions instead of "does
this work?"** — "what happens on a device without this sensor", "what units is this
actually in", "what does this return the very first time it's called". Every real bug
in this assignment came out of a question shaped like that, and none came out of
asking for more code.

**What went well:** the architecture. Because all the hardware talk lives behind
`StepTracker`, adding the accelerometer fallback didn't change a single line of
`MainActivity`. That's the first time a separation-of-concerns decision has visibly
paid me back mid-project rather than just being something I was told to do.

**What didn't:** I moved too fast at the start and had AI generate the whole
`MainActivity` before I'd thought about the lifecycle, which is why I had to unpick
the maths from the view code afterwards. Next time I'll decide what each file is
responsible for *before* asking for code, not after. I also spent longer than I'd
like chasing that XML double-hyphen parse error, because the error message pointed at
a line number and not at the actual rule I'd broken — a good reminder that AI is
fastest when I can describe the symptom precisely.

**Still to do (next assignment):** persist the history with Room or DataStore instead
of an in-memory object, let the user set their own stride length and step goal, and
add unit tests over `StepMath` and `AccelerometerStepDetector`.
