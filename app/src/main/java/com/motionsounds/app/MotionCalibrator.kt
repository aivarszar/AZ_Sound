package com.motionsounds.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/**
 * Kustību parametru kalibrācijas menedžeris
 * Analizē ierakstītos datus un ļauj pielāgot detection parametrus
 */
class MotionCalibrator(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("motion_calibration", Context.MODE_PRIVATE)

    /**
     * Kustības parametri
     */
    data class MotionParams(
        val motionType: String,
        val minAcceleration: Float,
        val maxAcceleration: Float,
        val avgAcceleration: Float,
        val p95Acceleration: Float,
        val minDeltaX: Float,
        val maxDeltaX: Float,
        val avgDeltaX: Float,
        val minDeltaY: Float,
        val maxDeltaY: Float,
        val avgDeltaY: Float,
        val minDeltaZ: Float,
        val maxDeltaZ: Float,
        val avgDeltaZ: Float,
        val dominantAxis: String, // "X", "Y", "Z", or "MIXED"
        val sampleCount: Int
    )

    /**
     * Kalibrācijas konfigurācija
     */
    data class CalibrationConfig(
        val motionType: String,
        val threshold: Float,          // Galvenais threshold activation
        val minThreshold: Float,       // Min vērtība aktivizēšanai
        val maxThreshold: Float,       // Max vērtība (pārāk augsta = cita kustība)
        val axisDominance: Float,      // Cik daudz ass dominē (0-1)
        val axisFocus: String          // Kurš ass ir galvenais
    )

    /**
     * Analizē ierakstītu JSON failu
     */
    fun analyzeRecording(jsonFile: File): MotionParams? {
        try {
            val jsonContent = jsonFile.readText()
            val json = JSONObject(jsonContent)

            val motionType = json.getString("motion_type")
            val samples = json.getJSONArray("samples")

            if (samples.length() == 0) return null

            val accelerations = mutableListOf<Float>()
            val deltaXs = mutableListOf<Float>()
            val deltaYs = mutableListOf<Float>()
            val deltaZs = mutableListOf<Float>()

            for (i in 0 until samples.length()) {
                val sample = samples.getJSONObject(i)
                accelerations.add(sample.getDouble("acceleration").toFloat())
                deltaXs.add(sample.getDouble("delta_x").toFloat())
                deltaYs.add(sample.getDouble("delta_y").toFloat())
                deltaZs.add(sample.getDouble("delta_z").toFloat())
            }

            // Aprēķina statistiku
            val avgAcc = accelerations.average().toFloat()
            val sortedAcc = accelerations.sorted()
            val p95Acc = sortedAcc[(sortedAcc.size * 0.95).toInt().coerceIn(0, sortedAcc.size - 1)]

            // Nosaka dominējošo asi
            var xDominant = 0
            var yDominant = 0
            var zDominant = 0

            for (i in deltaXs.indices) {
                val dx = abs(deltaXs[i])
                val dy = abs(deltaYs[i])
                val dz = abs(deltaZs[i])

                when {
                    dx > dy && dx > dz -> xDominant++
                    dy > dx && dy > dz -> yDominant++
                    dz > dx && dz > dy -> zDominant++
                }
            }

            val total = deltaXs.size
            val dominantAxis = when {
                xDominant.toFloat() / total > 0.5f -> "X"
                yDominant.toFloat() / total > 0.5f -> "Y"
                zDominant.toFloat() / total > 0.5f -> "Z"
                else -> "MIXED"
            }

            return MotionParams(
                motionType = motionType,
                minAcceleration = accelerations.minOrNull() ?: 0f,
                maxAcceleration = accelerations.maxOrNull() ?: 0f,
                avgAcceleration = avgAcc,
                p95Acceleration = p95Acc,
                minDeltaX = deltaXs.minOrNull() ?: 0f,
                maxDeltaX = deltaXs.maxOrNull() ?: 0f,
                avgDeltaX = deltaXs.average().toFloat(),
                minDeltaY = deltaYs.minOrNull() ?: 0f,
                maxDeltaY = deltaYs.maxOrNull() ?: 0f,
                avgDeltaY = deltaYs.average().toFloat(),
                minDeltaZ = deltaZs.minOrNull() ?: 0f,
                maxDeltaZ = deltaZs.maxOrNull() ?: 0f,
                avgDeltaZ = deltaZs.average().toFloat(),
                dominantAxis = dominantAxis,
                sampleCount = samples.length()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Analizē visus ierakstus no cache direktorijas
     */
    fun analyzeAllRecordings(): Map<String, List<MotionParams>> {
        val cacheDir = context.cacheDir
        val jsonFiles = cacheDir.listFiles { file ->
            file.name.endsWith(".json") && file.name.contains("motion_")
        } ?: emptyArray()

        val results = mutableMapOf<String, MutableList<MotionParams>>()

        for (file in jsonFiles) {
            val params = analyzeRecording(file)
            if (params != null) {
                if (!results.containsKey(params.motionType)) {
                    results[params.motionType] = mutableListOf()
                }
                results[params.motionType]!!.add(params)
            }
        }

        return results
    }

    /**
     * Ģenerē ieteikto konfigurāciju no analīzes
     */
    fun generateRecommendedConfig(params: List<MotionParams>): CalibrationConfig? {
        if (params.isEmpty()) return null

        val motionType = params.first().motionType

        // Aprēķina vidējos no visiem ierakstiem
        val avgOfAvgs = params.map { it.avgAcceleration }.average().toFloat()
        val avgOfP95s = params.map { it.p95Acceleration }.average().toFloat()
        val maxOfMaxs = params.map { it.maxAcceleration }.maxOrNull() ?: 0f

        // Ieteiktais threshold ir 70% no p95
        val recommendedThreshold = avgOfP95s * 0.7f

        // Min threshold: 50% no average
        val minThreshold = avgOfAvgs * 0.5f

        // Max threshold: 120% no p95
        val maxThreshold = avgOfP95s * 1.2f

        // Nosaka dominējošo asi
        val axisCounts = mutableMapOf("X" to 0, "Y" to 0, "Z" to 0, "MIXED" to 0)
        params.forEach { axisCounts[it.dominantAxis] = axisCounts[it.dominantAxis]!! + 1 }
        val dominantAxis = axisCounts.maxByOrNull { it.value }?.key ?: "MIXED"

        // Axis dominance strength (0-1)
        val dominanceStrength = axisCounts[dominantAxis]!!.toFloat() / params.size

        return CalibrationConfig(
            motionType = motionType,
            threshold = recommendedThreshold,
            minThreshold = minThreshold,
            maxThreshold = maxThreshold,
            axisDominance = dominanceStrength,
            axisFocus = dominantAxis
        )
    }

    /**
     * Pārbauda vai divi motion parametri pārklājas
     */
    fun checkOverlap(config1: CalibrationConfig, config2: CalibrationConfig): OverlapResult {
        // Pārbauda acceleration range pārklāšanos
        val range1 = config1.minThreshold..config1.maxThreshold
        val range2 = config2.minThreshold..config2.maxThreshold

        val hasAccelerationOverlap = !(config1.maxThreshold < config2.minThreshold ||
                                        config2.maxThreshold < config1.minThreshold)

        // Pārbauda axis dominance
        val hasDifferentAxis = config1.axisFocus != config2.axisFocus

        // Aprēķina overlap severity (0-100%)
        val overlapSeverity = if (!hasAccelerationOverlap) {
            0f
        } else {
            val overlapStart = maxOf(config1.minThreshold, config2.minThreshold)
            val overlapEnd = minOf(config1.maxThreshold, config2.maxThreshold)
            val overlapSize = overlapEnd - overlapStart

            val range1Size = config1.maxThreshold - config1.minThreshold
            val range2Size = config2.maxThreshold - config2.minThreshold

            // Percentage of smaller range that overlaps
            (overlapSize / minOf(range1Size, range2Size)) * 100f
        }

        val warning = when {
            overlapSeverity == 0f -> "✓ Nav pārklāšanās"
            overlapSeverity < 30f && hasDifferentAxis -> "✓ Maza pārklāšanās, bet atšķiras ass"
            overlapSeverity < 30f -> "⚠ Neliela pārklāšanās"
            overlapSeverity < 60f && hasDifferentAxis -> "⚠ Vidēja pārklāšanās"
            overlapSeverity < 60f -> "⚠ Būtiska pārklāšanās!"
            else -> "❌ KRITISKA PĀRKLĀŠANĀS!"
        }

        return OverlapResult(
            motion1 = config1.motionType,
            motion2 = config2.motionType,
            hasOverlap = hasAccelerationOverlap,
            overlapPercentage = overlapSeverity,
            hasDifferentAxis = hasDifferentAxis,
            warningMessage = warning
        )
    }

    data class OverlapResult(
        val motion1: String,
        val motion2: String,
        val hasOverlap: Boolean,
        val overlapPercentage: Float,
        val hasDifferentAxis: Boolean,
        val warningMessage: String
    )

    /**
     * Saglabā kalibrācijas konfigurāciju
     */
    fun saveConfig(config: CalibrationConfig) {
        prefs.edit().apply {
            putFloat("${config.motionType}_threshold", config.threshold)
            putFloat("${config.motionType}_min", config.minThreshold)
            putFloat("${config.motionType}_max", config.maxThreshold)
            putFloat("${config.motionType}_dominance", config.axisDominance)
            putString("${config.motionType}_axis", config.axisFocus)
            apply()
        }
    }

    /**
     * Ielādē kalibrācijas konfigurāciju
     */
    fun loadConfig(motionType: String): CalibrationConfig? {
        if (!prefs.contains("${motionType}_threshold")) return null

        return CalibrationConfig(
            motionType = motionType,
            threshold = prefs.getFloat("${motionType}_threshold", 0f),
            minThreshold = prefs.getFloat("${motionType}_min", 0f),
            maxThreshold = prefs.getFloat("${motionType}_max", 0f),
            axisDominance = prefs.getFloat("${motionType}_dominance", 0f),
            axisFocus = prefs.getString("${motionType}_axis", "MIXED") ?: "MIXED"
        )
    }

    /**
     * Dzēš visas kalibrācijas
     */
    fun clearAllCalibrations() {
        prefs.edit().clear().apply()
    }

    /**
     * Iegūst default konfigurāciju (no pašreizējiem threshold)
     */
    fun getDefaultConfig(motionType: String): CalibrationConfig {
        return when (motionType) {
            "SCRATCHING" -> CalibrationConfig(
                motionType = "SCRATCHING",
                threshold = 8f,
                minThreshold = 5f,
                maxThreshold = 15f,
                axisDominance = 0.7f,
                axisFocus = "Z"
            )
            "SWINGING" -> CalibrationConfig(
                motionType = "SWINGING",
                threshold = 1.5f,
                minThreshold = 0.7f,
                maxThreshold = 2.5f,
                axisDominance = 0.3f,
                axisFocus = "MIXED"
            )
            "THROWING" -> CalibrationConfig(
                motionType = "THROWING",
                threshold = 15f,
                minThreshold = 10f,
                maxThreshold = 25f,
                axisDominance = 0.6f,
                axisFocus = "Z"
            )
            "DROPPING" -> CalibrationConfig(
                motionType = "DROPPING",
                threshold = 20f,
                minThreshold = 15f,
                maxThreshold = 90f,
                axisDominance = 0.8f,
                axisFocus = "Z"
            )
            "WHOOSHING" -> CalibrationConfig(
                motionType = "WHOOSHING",
                threshold = 3f,
                minThreshold = 2f,
                maxThreshold = 10f,
                axisDominance = 0.7f,
                axisFocus = "X"
            )
            else -> CalibrationConfig(
                motionType = motionType,
                threshold = 5f,
                minThreshold = 2f,
                maxThreshold = 15f,
                axisDominance = 0.5f,
                axisFocus = "MIXED"
            )
        }
    }
}
