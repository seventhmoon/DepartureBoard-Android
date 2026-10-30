package com.androidfung.departureboard.ai

import android.content.Context
import android.util.Log
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.android.gms.tflite.java.TfLite
import org.tensorflow.lite.InterpreterApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Commuter query classification result predicted by on-device LiteRT model.
 */
enum class TransitQueryIntent {
    NEXT_DEPARTURE,
    SERVICE_STATUS,
    DISRUPTION_CHECK,
    TIMETABLE_LOOKUP,
    GENERAL_QUERY
}

data class TransitClassificationResult(
    val intent: TransitQueryIntent,
    val confidence: Float
)

/**
 * High-performance on-device LiteRT classifier backed by Google Play Services (play-services-litert).
 * Adds 0 KB native C++ (.so) overhead to the base APK by leveraging system-level Play Services runtime.
 */
class LiteRtTransitClassifier(
    private val context: Context
) : AutoCloseable {

    companion object {
        private const val TAG = "LiteRtClassifier"
        private const val MODEL_ASSET_NAME = "transit_classifier.tflite"
    }

    private var interpreter: InterpreterApi? = null
    private var isPlayServicesInitialized = false

    init {
        // Initialize Play Services LiteRT runtime asynchronously
        val opt = TfLiteInitializationOptions.builder()
            .setEnableGpuDelegateSupport(false)
            .build()

        TfLite.initialize(context.applicationContext, opt)
            .addOnSuccessListener {
                isPlayServicesInitialized = true
                Log.d(TAG, "Play Services LiteRT initialized successfully")
                tryLoadModel()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Play Services LiteRT unavailable, using heuristic fallback", e)
            }
    }

    private fun tryLoadModel() {
        try {
            val hasAsset = runCatching {
                context.assets.open(MODEL_ASSET_NAME).use { true }
            }.getOrDefault(false)

            if (hasAsset) {
                val assetDescriptor = context.assets.openFd(MODEL_ASSET_NAME)
                val inputStream = java.io.FileInputStream(assetDescriptor.fileDescriptor)
                val fileChannel = inputStream.channel
                val modelBuffer = fileChannel.map(
                    java.nio.channels.FileChannel.MapMode.READ_ONLY,
                    assetDescriptor.startOffset,
                    assetDescriptor.declaredLength
                )
                val options = InterpreterApi.Options()
                    .setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)
                interpreter = InterpreterApi.create(modelBuffer, options)
                Log.d(TAG, "Interpreter loaded via Google Play Services runtime.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Play Services LiteRT interpreter: ${e.message}")
        }
    }

    val isReady: Boolean
        get() = interpreter != null

    /**
     * Classifies commuter intent. Falls back cleanly to deterministic heuristic if model is not yet bundled.
     */
    fun classifyIntent(query: String): TransitClassificationResult {
        val clean = query.trim().lowercase()

        val activeInterpreter = interpreter
        if (activeInterpreter != null) {
            try {
                val inputBuffer = ByteBuffer.allocateDirect(32 * 4).order(ByteOrder.nativeOrder())
                val features = extractFeatures(clean)
                features.forEach { inputBuffer.putFloat(it) }
                inputBuffer.rewind()

                val outputBuffer = ByteBuffer.allocateDirect(TransitQueryIntent.entries.size * 4).order(ByteOrder.nativeOrder())
                activeInterpreter.run(inputBuffer, outputBuffer)
                outputBuffer.rewind()

                val scores = FloatArray(TransitQueryIntent.entries.size)
                outputBuffer.asFloatBuffer().get(scores)
                val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: 0
                val predictedIntent = TransitQueryIntent.entries.getOrElse(maxIdx) { TransitQueryIntent.GENERAL_QUERY }
                return TransitClassificationResult(predictedIntent, scores[maxIdx])
            } catch (e: Exception) {
                Log.w(TAG, "Play Services LiteRT inference error, using heuristic fallback", e)
            }
        }

        // Fast zero-latency heuristic matcher
        val intent = when {
            clean.contains("status") || clean.contains("good service") || clean.contains("running") ->
                TransitQueryIntent.SERVICE_STATUS
            clean.contains("delay") || clean.contains("disruption") || clean.contains("problem") || clean.contains("strike") ->
                TransitQueryIntent.DISRUPTION_CHECK
            clean.contains("timetable") || clean.contains("schedule") || clean.contains("first train") || clean.contains("last train") ->
                TransitQueryIntent.TIMETABLE_LOOKUP
            clean.contains("next") || clean.contains("when") || clean.contains("train") || clean.contains("bus") || clean.contains("depart") ->
                TransitQueryIntent.NEXT_DEPARTURE
            else ->
                TransitQueryIntent.GENERAL_QUERY
        }
        return TransitClassificationResult(intent, 0.95f)
    }

    private fun extractFeatures(query: String, size: Int = 32): FloatArray {
        val array = FloatArray(size)
        val tokens = query.split("\\s+".toRegex())
        for ((idx, token) in tokens.withIndex()) {
            if (idx >= size) break
            array[idx] = (token.hashCode() % 1000) / 1000f
        }
        return array
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }
}
