package com.motionsounds.app

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import java.io.File

class CalibrationActivity : AppCompatActivity() {

    private lateinit var calibrator: MotionCalibrator

    // UI Components
    private lateinit var motionSelector: Spinner
    private lateinit var statisticsText: TextView
    private lateinit var thresholdSlider: Slider
    private lateinit var thresholdValue: TextView
    private lateinit var minThresholdSlider: Slider
    private lateinit var minThresholdValue: TextView
    private lateinit var maxThresholdSlider: Slider
    private lateinit var maxThresholdValue: TextView
    private lateinit var axisRadioGroup: RadioGroup
    private lateinit var overlapAnalysisText: TextView
    private lateinit var loadFromRecordingsButton: MaterialButton
    private lateinit var resetButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    private lateinit var applyButton: MaterialButton

    // Data
    private val motionTypes = listOf("SCRATCHING", "SWINGING", "THROWING", "DROPPING", "WHOOSHING")
    private val configs = mutableMapOf<String, MotionCalibrator.CalibrationConfig>()
    private var currentMotionType = "SCRATCHING"
    private var recordingsData = mapOf<String, List<MotionCalibrator.MotionParams>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        calibrator = MotionCalibrator(this)

        initViews()
        loadDefaultConfigs()
        setupListeners()
        updateUI()
    }

    private fun initViews() {
        motionSelector = findViewById(R.id.motionSelector)
        statisticsText = findViewById(R.id.statisticsText)
        thresholdSlider = findViewById(R.id.thresholdSlider)
        thresholdValue = findViewById(R.id.thresholdValue)
        minThresholdSlider = findViewById(R.id.minThresholdSlider)
        minThresholdValue = findViewById(R.id.minThresholdValue)
        maxThresholdSlider = findViewById(R.id.maxThresholdSlider)
        maxThresholdValue = findViewById(R.id.maxThresholdValue)
        axisRadioGroup = findViewById(R.id.axisRadioGroup)
        overlapAnalysisText = findViewById(R.id.overlapAnalysisText)
        loadFromRecordingsButton = findViewById(R.id.loadFromRecordingsButton)
        resetButton = findViewById(R.id.resetButton)
        cancelButton = findViewById(R.id.cancelButton)
        applyButton = findViewById(R.id.applyButton)

        // Setup motion selector
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, motionTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        motionSelector.adapter = adapter
    }

    private fun loadDefaultConfigs() {
        // Load saved configs or use defaults
        for (motionType in motionTypes) {
            configs[motionType] = calibrator.loadConfig(motionType)
                ?: calibrator.getDefaultConfig(motionType)
        }
    }

    private fun setupListeners() {
        // Motion selector
        motionSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentMotionType = motionTypes[position]
                updateUI()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Threshold slider
        thresholdSlider.addOnChangeListener { _, value, _ ->
            thresholdValue.text = String.format("%.1f", value)
            updateCurrentConfig()
            checkOverlaps()
        }

        // Min threshold slider
        minThresholdSlider.addOnChangeListener { _, value, _ ->
            minThresholdValue.text = String.format("%.1f", value)
            updateCurrentConfig()
            checkOverlaps()
        }

        // Max threshold slider
        maxThresholdSlider.addOnChangeListener { _, value, _ ->
            maxThresholdValue.text = String.format("%.1f", value)
            updateCurrentConfig()
            checkOverlaps()
        }

        // Axis radio group
        axisRadioGroup.setOnCheckedChangeListener { _, _ ->
            updateCurrentConfig()
            checkOverlaps()
        }

        // Load from recordings
        loadFromRecordingsButton.setOnClickListener {
            loadDataFromRecordings()
        }

        // Reset button
        resetButton.setOnClickListener {
            resetToDefaults()
        }

        // Cancel button
        cancelButton.setOnClickListener {
            finish()
        }

        // Apply button
        applyButton.setOnClickListener {
            saveAllConfigs()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun updateUI() {
        val config = configs[currentMotionType] ?: return

        // Update sliders
        thresholdSlider.value = config.threshold
        thresholdValue.text = String.format("%.1f", config.threshold)

        minThresholdSlider.value = config.minThreshold
        minThresholdValue.text = String.format("%.1f", config.minThreshold)

        maxThresholdSlider.value = config.maxThreshold
        maxThresholdValue.text = String.format("%.1f", config.maxThreshold)

        // Update axis radio
        when (config.axisFocus) {
            "X" -> axisRadioGroup.check(R.id.axisX)
            "Y" -> axisRadioGroup.check(R.id.axisY)
            "Z" -> axisRadioGroup.check(R.id.axisZ)
            else -> axisRadioGroup.check(R.id.axisMixed)
        }

        // Update statistics
        updateStatisticsDisplay()

        // Check overlaps
        checkOverlaps()
    }

    private fun updateCurrentConfig() {
        val selectedAxis = when (axisRadioGroup.checkedRadioButtonId) {
            R.id.axisX -> "X"
            R.id.axisY -> "Y"
            R.id.axisZ -> "Z"
            else -> "MIXED"
        }

        configs[currentMotionType] = MotionCalibrator.CalibrationConfig(
            motionType = currentMotionType,
            threshold = thresholdSlider.value,
            minThreshold = minThresholdSlider.value,
            maxThreshold = maxThresholdSlider.value,
            axisDominance = 0.7f,
            axisFocus = selectedAxis
        )
    }

    private fun updateStatisticsDisplay() {
        val params = recordingsData[currentMotionType]

        if (params.isNullOrEmpty()) {
            statisticsText.text = "Vēl nav ierakstu šai kustībai.\nIzmanto Recording funkciju lai izveidotu datus."
            statisticsText.setTextColor(ContextCompat.getColor(this, R.color.black))
        } else {
            val avgAcc = params.map { it.avgAcceleration }.average()
            val maxAcc = params.map { it.maxAcceleration }.maxOrNull() ?: 0f
            val p95Acc = params.map { it.p95Acceleration }.average()
            val samples = params.sumOf { it.sampleCount }

            val dominantAxis = params.groupingBy { it.dominantAxis }
                .eachCount()
                .maxByOrNull { it.value }?.key ?: "MIXED"

            statisticsText.text = """
                📊 Dati no ${params.size} ieraksta(iem):

                Avg Acceleration: ${"%.2f".format(avgAcc)}
                P95 Acceleration: ${"%.2f".format(p95Acc)}
                Max Acceleration: ${"%.2f".format(maxAcc)}
                Total Samples: $samples
                Dominant Axis: $dominantAxis

                💡 Ieteikums:
                Threshold: ${"%.1f".format(p95Acc * 0.7f)}
                Min: ${"%.1f".format(avgAcc * 0.5f)}
                Max: ${"%.1f".format(p95Acc * 1.2f)}
            """.trimIndent()

            statisticsText.setTextColor(ContextCompat.getColor(this, R.color.purple_700))
        }
    }

    private fun checkOverlaps() {
        val currentConfig = configs[currentMotionType] ?: return
        val results = mutableListOf<String>()

        for ((otherType, otherConfig) in configs) {
            if (otherType == currentMotionType) continue

            val overlap = calibrator.checkOverlap(currentConfig, otherConfig)

            if (overlap.hasOverlap) {
                val icon = when {
                    overlap.overlapPercentage >= 60 -> "❌"
                    overlap.overlapPercentage >= 30 -> "⚠️"
                    else -> "⚠"
                }

                results.add("$icon $otherType: ${overlap.warningMessage} " +
                        "(${String.format("%.0f", overlap.overlapPercentage)}%)")
            } else {
                results.add("✓ $otherType: ${overlap.warningMessage}")
            }
        }

        overlapAnalysisText.text = results.joinToString("\n")

        // Color code based on severity
        val maxOverlap = configs.values
            .filter { it.motionType != currentMotionType }
            .map { calibrator.checkOverlap(currentConfig, it).overlapPercentage }
            .maxOrNull() ?: 0f

        val color = when {
            maxOverlap >= 60 -> R.color.red
            maxOverlap >= 30 -> R.color.orange
            else -> R.color.green
        }

        overlapAnalysisText.setTextColor(ContextCompat.getColor(this, color))
    }

    private fun loadDataFromRecordings() {
        // Try to find JSON files in cache
        recordingsData = calibrator.analyzeAllRecordings()

        if (recordingsData.isEmpty()) {
            // Try project root directory (where recordings were saved during dev)
            val projectFiles = File("/home/user/AZ_Sound").listFiles { file ->
                file.name.startsWith("Motion Recording_") && file.name.endsWith(".json")
            }

            if (projectFiles != null && projectFiles.isNotEmpty()) {
                val tempData = mutableMapOf<String, MutableList<MotionCalibrator.MotionParams>>()
                for (file in projectFiles) {
                    val params = calibrator.analyzeRecording(file)
                    if (params != null) {
                        if (!tempData.containsKey(params.motionType)) {
                            tempData[params.motionType] = mutableListOf()
                        }
                        tempData[params.motionType]!!.add(params)
                    }
                }
                recordingsData = tempData
            }
        }

        // Generate recommended configs from data
        for ((motionType, paramsList) in recordingsData) {
            val recommended = calibrator.generateRecommendedConfig(paramsList)
            if (recommended != null) {
                configs[motionType] = recommended
            }
        }

        updateUI()
    }

    private fun resetToDefaults() {
        for (motionType in motionTypes) {
            configs[motionType] = calibrator.getDefaultConfig(motionType)
        }
        updateUI()
    }

    private fun saveAllConfigs() {
        for ((_, config) in configs) {
            calibrator.saveConfig(config)
        }
    }

    companion object {
        const val REQUEST_CODE_CALIBRATION = 1001
    }
}
