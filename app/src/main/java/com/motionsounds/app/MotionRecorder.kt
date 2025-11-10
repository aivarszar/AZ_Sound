package com.motionsounds.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Datu klase sensoru nolasījumu ierakstīšanai
 */
data class SensorSample(
    val timeMs: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val deltaX: Float,
    val deltaY: Float,
    val deltaZ: Float,
    val acceleration: Float
)

/**
 * Kustību ierakstīšanas sesija
 */
data class RecordingSession(
    val motionType: String,
    val timestamp: String,
    val sensitivity: String,
    val samples: MutableList<SensorSample> = mutableListOf()
) {
    val startTime: Long = System.currentTimeMillis()

    fun addSample(sample: SensorSample) {
        samples.add(sample)
    }

    fun getDuration(): Long {
        return if (samples.isNotEmpty()) {
            samples.last().timeMs - samples.first().timeMs
        } else 0
    }

    fun getSampleCount(): Int = samples.size

    fun getAverageAcceleration(): Float {
        return if (samples.isNotEmpty()) {
            samples.map { it.acceleration }.average().toFloat()
        } else 0f
    }

    fun getMaxAcceleration(): Float {
        return samples.maxOfOrNull { it.acceleration } ?: 0f
    }
}

/**
 * Kustību ierakstīšanas menedžeris
 */
class MotionRecorder(private val context: Context) {

    private var currentSession: RecordingSession? = null
    private var isRecording = false

    /**
     * Sāk jaunu ierakstīšanas sesiju
     */
    fun startRecording(motionType: String, sensitivity: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            .format(Date())

        currentSession = RecordingSession(motionType, timestamp, sensitivity)
        isRecording = true
    }

    /**
     * Aptur ierakstīšanu
     */
    fun stopRecording(): RecordingSession? {
        isRecording = false
        return currentSession
    }

    /**
     * Pievieno sensoru nolasījumu
     */
    fun addSample(x: Float, y: Float, z: Float,
                  deltaX: Float, deltaY: Float, deltaZ: Float,
                  acceleration: Float) {
        if (!isRecording || currentSession == null) return

        val timeMs = System.currentTimeMillis() - currentSession!!.startTime
        val sample = SensorSample(timeMs, x, y, z, deltaX, deltaY, deltaZ, acceleration)
        currentSession!!.addSample(sample)
    }

    fun isRecording(): Boolean = isRecording

    fun getCurrentSession(): RecordingSession? = currentSession

    /**
     * Eksportē sesiju uz JSON formātu
     */
    fun exportToJson(session: RecordingSession): String {
        val json = JSONObject()
        json.put("motion_type", session.motionType)
        json.put("timestamp", session.timestamp)
        json.put("sensitivity", session.sensitivity)
        json.put("duration_ms", session.getDuration())
        json.put("sample_count", session.getSampleCount())

        // Statistika
        val stats = JSONObject()
        stats.put("avg_acceleration", session.getAverageAcceleration())
        stats.put("max_acceleration", session.getMaxAcceleration())
        json.put("statistics", stats)

        // Samples
        val samplesArray = JSONArray()
        for (sample in session.samples) {
            val sampleJson = JSONObject()
            sampleJson.put("time_ms", sample.timeMs)
            sampleJson.put("x", sample.x)
            sampleJson.put("y", sample.y)
            sampleJson.put("z", sample.z)
            sampleJson.put("delta_x", sample.deltaX)
            sampleJson.put("delta_y", sample.deltaY)
            sampleJson.put("delta_z", sample.deltaZ)
            sampleJson.put("acceleration", sample.acceleration)
            samplesArray.put(sampleJson)
        }
        json.put("samples", samplesArray)

        return json.toString(2) // Pretty print with indent
    }

    /**
     * Eksportē sesiju uz CSV formātu
     */
    fun exportToCsv(session: RecordingSession): String {
        val csv = StringBuilder()

        // Header info
        csv.append("# Motion Recording Data\n")
        csv.append("# Motion Type: ${session.motionType}\n")
        csv.append("# Timestamp: ${session.timestamp}\n")
        csv.append("# Sensitivity: ${session.sensitivity}\n")
        csv.append("# Duration: ${session.getDuration()} ms\n")
        csv.append("# Samples: ${session.getSampleCount()}\n")
        csv.append("# Avg Acceleration: ${session.getAverageAcceleration()}\n")
        csv.append("# Max Acceleration: ${session.getMaxAcceleration()}\n")
        csv.append("\n")

        // Column headers
        csv.append("time_ms,x,y,z,delta_x,delta_y,delta_z,acceleration\n")

        // Data rows
        for (sample in session.samples) {
            csv.append("${sample.timeMs},")
            csv.append("${sample.x},")
            csv.append("${sample.y},")
            csv.append("${sample.z},")
            csv.append("${sample.deltaX},")
            csv.append("${sample.deltaY},")
            csv.append("${sample.deltaZ},")
            csv.append("${sample.acceleration}\n")
        }

        return csv.toString()
    }

    /**
     * Saglabā failu un atver Share dialog
     */
    fun shareRecording(session: RecordingSession, format: ExportFormat) {
        val (content, extension, mimeType) = when (format) {
            ExportFormat.JSON -> Triple(
                exportToJson(session),
                "json",
                "application/json"
            )
            ExportFormat.CSV -> Triple(
                exportToCsv(session),
                "csv",
                "text/csv"
            )
        }

        // Izveido faila nosaukumu
        val fileName = "motion_${session.motionType}_${session.timestamp.replace(":", "-")}.$extension"

        // Saglabā failā
        val file = File(context.cacheDir, fileName)
        file.writeText(content)

        // Izveido Intent priekš Share
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Motion Recording: ${session.motionType}")
            putExtra(Intent.EXTRA_TEXT,
                "Kustības ieraksts:\n" +
                "Tips: ${session.motionType}\n" +
                "Samples: ${session.getSampleCount()}\n" +
                "Ilgums: ${session.getDuration()}ms\n" +
                "Vidējā paātrinājums: ${"%.2f".format(session.getAverageAcceleration())}"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Dalīties ar ierakstu"))
    }

    enum class ExportFormat {
        JSON, CSV
    }
}
