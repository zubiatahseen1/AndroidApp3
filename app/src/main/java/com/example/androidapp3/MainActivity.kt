package com.example.androidapp3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.androidapp3.data.SessionRepository
import com.example.androidapp3.databinding.ActivityMainBinding
import com.example.androidapp3.model.StepSession
import com.example.androidapp3.sensor.StepTracker
import com.example.androidapp3.util.StepMath

/**
 * StepQuest - main screen.
 *
 * Responsibilities, in the order they happen:
 *   1. Ask for the ACTIVITY_RECOGNITION runtime permission (Android 10+).
 *   2. Start/stop a [StepTracker] in step with the Activity lifecycle.
 *   3. Render live sensor values, and the numbers derived from them.
 *   4. Save a finished walk into [SessionRepository] for the history screen.
 *
 * The Activity deliberately owns none of the sensor logic and none of the maths -
 * it wires the two together and draws the result.
 */
class MainActivity : AppCompatActivity(), StepTracker.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tracker: StepTracker

    /** True between "Start walk" and "Stop & save". Survives onPause/onResume. */
    private var isWalkActive = false

    /** When the current walk began, used for duration and cadence. */
    private var walkStartedAt = 0L

    // A Handler tied to the main thread, used to redraw the timer once a second.
    // Sensor callbacks are event-driven, but elapsed time is not - something has
    // to tick. Every posted Runnable is removed again in onPause so it cannot
    // keep firing (and leaking this Activity) after the screen goes away.
    private val uiHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateDerivedReadouts()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    /**
     * The modern replacement for onRequestPermissionsResult.
     * It must be registered while the Activity is being created - registering it
     * as a property does exactly that - and the lambda runs once the user answers.
     */
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                beginWalk()
            } else {
                // Denied is a normal outcome, not a crash. Tell the user plainly
                // what stopped working and how to turn it back on.
                binding.textStatus.text = getString(R.string.permission_denied)
                binding.buttonStart.isEnabled = true
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tracker = StepTracker(this, this)

        showSensorSource()
        wireButtons()
        renderSteps(0)
        binding.textStatus.text = getString(R.string.status_idle)
        binding.textGoalLabel.text = getString(R.string.goal_label, StepMath.STEP_GOAL)
    }

    /**
     * Sensors are subscribed here, not in onCreate, so we only listen while the
     * app is actually on screen. Coming back from the history screen therefore
     * resumes a walk that was in progress.
     */
    override fun onResume() {
        super.onResume()
        if (isWalkActive) {
            tracker.start()
            uiHandler.post(tickRunnable)
            binding.textStatus.text = getString(R.string.status_tracking)
        }
    }

    /**
     * The other half of the pair. Releasing the sensors here is what keeps the app
     * from draining the battery in the background - the single most important
     * lifecycle rule for any sensor or location app.
     */
    override fun onPause() {
        super.onPause()
        tracker.stop()
        uiHandler.removeCallbacks(tickRunnable)
        if (isWalkActive) {
            binding.textStatus.text = getString(R.string.status_paused)
        }
    }

    // ------------------------------------------------------------------- setup

    private fun wireButtons() {
        binding.buttonStart.setOnClickListener { onStartPressed() }
        binding.buttonStop.setOnClickListener { onStopPressed() }
        binding.buttonReset.setOnClickListener {
            tracker.resetWalk()
            walkStartedAt = System.currentTimeMillis()
            updateDerivedReadouts()
        }
        binding.buttonHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.buttonStop.isEnabled = false
    }

    /** Tell the user which sensor their numbers are coming from - or that there is none. */
    private fun showSensorSource() {
        binding.textSource.text = when (tracker.source) {
            StepTracker.Source.HARDWARE_COUNTER -> getString(R.string.source_hardware)
            StepTracker.Source.ACCELEROMETER_FALLBACK -> getString(R.string.source_fallback)
            StepTracker.Source.NONE -> getString(R.string.source_none)
        }
        // No sensor at all means nothing to start.
        binding.buttonStart.isEnabled = tracker.hasUsableSensor()
    }

    // -------------------------------------------------------------- permission

    /**
     * ACTIVITY_RECOGNITION only became a runtime permission in Android 10 (API 29).
     * On anything older it is granted at install time, so asking would be wrong -
     * hence the explicit version check rather than a blanket request.
     */
    private fun hasActivityPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun onStartPressed() {
        if (hasActivityPermission()) {
            beginWalk()
        } else {
            // Explain first, then ask. A dialog out of nowhere is the fastest way
            // to get a permission denied forever.
            binding.textStatus.text = getString(R.string.permission_rationale)
            binding.buttonStart.isEnabled = false
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    // ------------------------------------------------------------------ a walk

    private fun beginWalk() {
        isWalkActive = true
        walkStartedAt = System.currentTimeMillis()
        tracker.resetWalk()
        tracker.start()
        uiHandler.post(tickRunnable)

        binding.buttonStart.isEnabled = false
        binding.buttonStop.isEnabled = true
        binding.textStatus.text = getString(R.string.status_tracking)
    }

    private fun onStopPressed() {
        val steps = tracker.stepsThisWalk
        val elapsed = System.currentTimeMillis() - walkStartedAt

        SessionRepository.save(
            StepSession(
                steps = steps,
                distanceKm = StepMath.distanceKm(steps),
                calories = StepMath.calories(steps),
                durationMs = elapsed,
                startedAt = walkStartedAt,
                source = tracker.source.name
            )
        )

        tracker.stop()
        uiHandler.removeCallbacks(tickRunnable)
        isWalkActive = false

        binding.buttonStart.isEnabled = tracker.hasUsableSensor()
        binding.buttonStop.isEnabled = false
        binding.textStatus.text = getString(R.string.status_idle)
        Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------- sensor callbacks

    /** Called by [StepTracker] every time the step total moves. */
    override fun onStepsChanged(stepsThisWalk: Int) {
        renderSteps(stepsThisWalk)
    }

    /** Called on every accelerometer sample - the raw, unprocessed sensor view. */
    override fun onMotionChanged(x: Float, y: Float, z: Float, magnitude: Float) {
        binding.textMotion.text = getString(R.string.motion_readout, x, y, z)
        binding.textMotionMagnitude.text = getString(R.string.motion_magnitude, magnitude)
    }

    // ---------------------------------------------------------------- drawing

    private fun renderSteps(steps: Int) {
        binding.textSteps.text = steps.toString()

        val percent = StepMath.goalPercent(steps)
        binding.progressGoal.progress = percent
        binding.textGoalProgress.text = if (steps >= StepMath.STEP_GOAL) {
            getString(R.string.goal_reached)
        } else {
            getString(R.string.goal_progress, percent)
        }

        updateDerivedReadouts()
    }

    /**
     * Recomputes everything that is *derived* from the step count rather than
     * measured directly. Split out because the once-a-second tick needs it too.
     */
    private fun updateDerivedReadouts() {
        val steps = tracker.stepsThisWalk
        val elapsed = if (isWalkActive) System.currentTimeMillis() - walkStartedAt else 0L

        binding.textDistance.text =
            getString(R.string.distance_readout, StepMath.formatKm(StepMath.distanceKm(steps)))
        binding.textCalories.text =
            getString(R.string.calories_readout, StepMath.calories(steps))
        binding.textCadence.text = getString(
            R.string.pace_readout,
            StepMath.formatCadence(StepMath.cadence(steps, elapsed))
        )
    }
}
