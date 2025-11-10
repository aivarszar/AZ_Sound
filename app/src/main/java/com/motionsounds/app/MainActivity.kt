package com.motionsounds.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var soundPool: SoundPool

    // UI elementi
    private lateinit var motionStatusText: TextView
    private lateinit var accelerometerDataText: TextView
    private lateinit var sensitivityGroup: RadioGroup

    // Recording UI elementi
    private lateinit var motionTypeSpinner: Spinner
    private lateinit var recordButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var recordingStatusText: TextView
    private lateinit var exportButtonsLayout: LinearLayout
    private lateinit var exportJsonButton: MaterialButton
    private lateinit var exportCsvButton: MaterialButton

    // Motion Recorder
    private lateinit var motionRecorder: MotionRecorder
    private var lastRecordedSession: RecordingSession? = null

    // Skaņu ID (tiks ielādēti no raw foldera)
    private var scratchingSoundId: Int = 0
    private var swingingSoundId: Int = 0
    private var ohoSoundId: Int = 0
    private var thudSoundId: Int = 0
    private var whooshSoundId: Int = 0

    // Kustību detekcijas mainīgie
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastUpdate: Long = 0

    // Kustību stāvokļi
    private enum class MotionState {
        IDLE, SCRATCHING, SWINGING, THROWING, DROPPING, WHOOSHING
    }

    private var currentState = MotionState.IDLE
    private var lastSoundTime: Long = 0
    private val soundCooldown = 500L // Minimālais laiks starp skaņām (ms)

    // Jutīguma līmeņi
    private var shakingThreshold = 15f
    private var swingThreshold = 8f
    private var throwThreshold = 20f
    private var dropThreshold = 5f

    // Kustību history detekcijai
    private val accelerationHistory = mutableListOf<Float>()
    private val maxHistorySize = 20

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializē UI elementus
        motionStatusText = findViewById(R.id.motionStatus)
        accelerometerDataText = findViewById(R.id.accelerometerData)
        sensitivityGroup = findViewById(R.id.sensitivityGroup)

        // Inicializē Recording UI elementus
        motionTypeSpinner = findViewById(R.id.motionTypeSpinner)
        recordButton = findViewById(R.id.recordButton)
        stopButton = findViewById(R.id.stopButton)
        recordingStatusText = findViewById(R.id.recordingStatus)
        exportButtonsLayout = findViewById(R.id.exportButtonsLayout)
        exportJsonButton = findViewById(R.id.exportJsonButton)
        exportCsvButton = findViewById(R.id.exportCsvButton)

        // Inicializē MotionRecorder
        motionRecorder = MotionRecorder(this)

        // Inicializē sensoru menedžeri
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Inicializē SoundPool
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        // Ielādē skaņas (pagaidām placeholder - pievienosim vēlāk)
        loadSounds()

        // Jutīguma kontroles
        sensitivityGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.lowSensitivity -> setSensitivity(0.7f)
                R.id.mediumSensitivity -> setSensitivity(1.0f)
                R.id.highSensitivity -> setSensitivity(1.5f)
            }
        }

        // Setup Recording UI
        setupRecordingUI()
    }

    private fun loadSounds() {
        // Šeit ielādēsim skaņas no res/raw foldera
        // Pagaidām komentēts, jo vēl nav skaņu failu
        /*
        scratchingSoundId = soundPool.load(this, R.raw.scratching, 1)
        swingingSoundId = soundPool.load(this, R.raw.swinging, 1)
        ohoSoundId = soundPool.load(this, R.raw.oho, 1)
        thudSoundId = soundPool.load(this, R.raw.thud, 1)
        whooshSoundId = soundPool.load(this, R.raw.whoosh, 1)
        */
    }

    private fun setupRecordingUI() {
        // Setup Motion Type Spinner
        val motionTypes = arrayOf(
            "SCRATCHING - Ādas kasīšana",
            "SWINGING - Šūpoles",
            "THROWING - OHO mešana",
            "DROPPING - Būkšķis",
            "WHOOSHING - Švīkstoņa"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, motionTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        motionTypeSpinner.adapter = adapter

        // Record Button
        recordButton.setOnClickListener {
            startRecording()
        }

        // Stop Button
        stopButton.setOnClickListener {
            stopRecording()
        }

        // Export JSON Button
        exportJsonButton.setOnClickListener {
            lastRecordedSession?.let { session ->
                motionRecorder.shareRecording(session, MotionRecorder.ExportFormat.JSON)
            }
        }

        // Export CSV Button
        exportCsvButton.setOnClickListener {
            lastRecordedSession?.let { session ->
                motionRecorder.shareRecording(session, MotionRecorder.ExportFormat.CSV)
            }
        }
    }

    private fun startRecording() {
        val selectedMotionType = when (motionTypeSpinner.selectedItemPosition) {
            0 -> "SCRATCHING"
            1 -> "SWINGING"
            2 -> "THROWING"
            3 -> "DROPPING"
            4 -> "WHOOSHING"
            else -> "UNKNOWN"
        }

        val sensitivity = when (sensitivityGroup.checkedRadioButtonId) {
            R.id.lowSensitivity -> "LOW"
            R.id.highSensitivity -> "HIGH"
            else -> "MEDIUM"
        }

        motionRecorder.startRecording(selectedMotionType, sensitivity)

        // Update UI
        recordButton.isEnabled = false
        stopButton.isEnabled = true
        exportButtonsLayout.visibility = View.GONE
        recordingStatusText.text = "🔴 Ieraksta... Izpildi kustību 10 reizes!"
        recordingStatusText.setTextColor(ContextCompat.getColor(this, R.color.red))
    }

    private fun stopRecording() {
        val session = motionRecorder.stopRecording()
        lastRecordedSession = session

        // Update UI
        recordButton.isEnabled = true
        stopButton.isEnabled = false

        session?.let {
            exportButtonsLayout.visibility = View.VISIBLE
            recordingStatusText.text = "✅ Ieraksts pabeigts!\n" +
                    "Samples: ${it.getSampleCount()} | " +
                    "Ilgums: ${it.getDuration()}ms | " +
                    "Avg: ${"%.2f".format(it.getAverageAcceleration())}"
            recordingStatusText.setTextColor(ContextCompat.getColor(this, R.color.green))
        }
    }

    private fun setSensitivity(factor: Float) {
        shakingThreshold = 15f * factor
        swingThreshold = 8f * factor
        throwThreshold = 20f * factor
        dropThreshold = 5f * factor
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { acc ->
            sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Atjauno UI ar sensoru datiem
            updateSensorDisplay(x, y, z)

            val currentTime = System.currentTimeMillis()

            if (lastUpdate != 0L) {
                val timeDiff = currentTime - lastUpdate

                if (timeDiff > 100) { // Apstrādā reizi 100ms
                    val deltaX = abs(x - lastX)
                    val deltaY = abs(y - lastY)
                    val deltaZ = abs(z - lastZ)

                    val acceleration = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()).toFloat()

                    // Pievieno vēsturei
                    accelerationHistory.add(acceleration)
                    if (accelerationHistory.size > maxHistorySize) {
                        accelerationHistory.removeAt(0)
                    }

                    // Ieraksta datus, ja recording režīmā
                    if (motionRecorder.isRecording()) {
                        motionRecorder.addSample(x, y, z, deltaX, deltaY, deltaZ, acceleration)

                        // Atjaunina recording status ar sample count
                        val session = motionRecorder.getCurrentSession()
                        session?.let {
                            runOnUiThread {
                                recordingStatusText.text = "🔴 Ieraksta... Samples: ${it.getSampleCount()}"
                            }
                        }
                    }

                    // Detektē kustības
                    detectMotion(x, y, z, deltaX, deltaY, deltaZ, acceleration, currentTime)

                    lastUpdate = currentTime
                }
            } else {
                lastUpdate = currentTime
            }

            lastX = x
            lastY = y
            lastZ = z
        }
    }

    private fun detectMotion(x: Float, y: Float, z: Float,
                            deltaX: Float, deltaY: Float, deltaZ: Float,
                            acceleration: Float, currentTime: Long) {

        // 1. MEŠANA UZ AUGŠU (throw up) - strauja kustība uz augšu, tad brīvais kritiens
        if (z > throwThreshold && abs(x) < 5 && abs(y) < 5) {
            if (currentTime - lastSoundTime > soundCooldown) {
                setMotionState(MotionState.THROWING, "Met uz augšu!")
                playSound(ohoSoundId)
                lastSoundTime = currentTime
                return
            }
        }

        // 2. NOMEŠANA (drop) - strauja kustība uz leju
        if (z < -dropThreshold && acceleration > 10) {
            if (currentTime - lastSoundTime > soundCooldown) {
                setMotionState(MotionState.DROPPING, "Nokrīt!")
                playSound(thudSoundId)
                lastSoundTime = currentTime
                return
            }
        }

        // 3. ĀDAS KASĪŠANA (scratching) - ātras vertikālas kustības
        if (deltaY > shakingThreshold && deltaX < 8 && deltaZ < 8) {
            if (currentTime - lastSoundTime > soundCooldown) {
                setMotionState(MotionState.SCRATCHING, "Kasa ādu")
                playSound(scratchingSoundId)
                lastSoundTime = currentTime
                return
            }
        }

        // 4. ŠŪPOLES (swinging) - ritmiskas horizontālas kustības ar vertikālu komponentu
        if (isSwingingMotion(deltaX, deltaY, deltaZ)) {
            if (currentTime - lastSoundTime > soundCooldown) {
                setMotionState(MotionState.SWINGING, "Šūpojas")
                playSound(swingingSoundId)
                lastSoundTime = currentTime
                return
            }
        }

        // 5. ŠVĪKSTOŅA (whoosh) - šūpināšana ar īso malu
        if (isWhooshingMotion(x, y, z, deltaX, deltaY, deltaZ)) {
            if (currentTime - lastSoundTime > soundCooldown) {
                setMotionState(MotionState.WHOOSHING, "Švīkst")
                playSound(whooshSoundId)
                lastSoundTime = currentTime
                return
            }
        }

        // Ja nav aktīvas kustības, atgriežas idle stāvoklī
        if (acceleration < 2 && currentTime - lastSoundTime > 1000) {
            setMotionState(MotionState.IDLE, "Gaida kustību…")
        }
    }

    private fun isSwingingMotion(deltaX: Float, deltaY: Float, deltaZ: Float): Boolean {
        // Šūpoles: kombinācija no horizontālas un vertikālas kustības
        val horizontalMotion = deltaX > swingThreshold
        val verticalMotion = deltaY > swingThreshold / 2
        return horizontalMotion && verticalMotion && deltaZ < swingThreshold
    }

    private fun isWhooshingMotion(x: Float, y: Float, z: Float,
                                   deltaX: Float, deltaY: Float, deltaZ: Float): Boolean {
        // Švīkstoņa: ierīce ir vertikālā pozīcijā (x vai y dominē) un ātri šūpojas
        val isVertical = abs(x) > 8 || abs(y) > 8
        val isFastMotion = deltaX > 12 || deltaY > 12
        return isVertical && isFastMotion
    }

    private fun setMotionState(state: MotionState, statusText: String) {
        currentState = state
        runOnUiThread {
            motionStatusText.text = statusText

            // Maina teksta krāsu atkarībā no stāvokļa
            val colorRes = when (state) {
                MotionState.IDLE -> R.color.green
                MotionState.SCRATCHING -> R.color.orange
                MotionState.SWINGING -> R.color.purple_500
                MotionState.THROWING -> R.color.teal_700
                MotionState.DROPPING -> R.color.red
                MotionState.WHOOSHING -> R.color.teal_200
            }
            motionStatusText.setTextColor(ContextCompat.getColor(this, colorRes))
        }
    }

    private fun playSound(soundId: Int) {
        if (soundId != 0) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }

    private fun updateSensorDisplay(x: Float, y: Float, z: Float) {
        runOnUiThread {
            accelerometerDataText.text = String.format(
                "X: %.2f  Y: %.2f  Z: %.2f",
                x, y, z
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Nav nepieciešams šai aplikācijai
    }
}
