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
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val initExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    }

    private val interpreterLock = Any()
    @Volatile
    private var interpreter: InterpreterApi? = null
    @Volatile
    private var isPlayServicesInitialized = false
    @Volatile
    private var isClosed = false

    init {
        // Initialize Play Services LiteRT runtime asynchronously and map model off the Main thread
        val opt = TfLiteInitializationOptions.builder()
            .setEnableGpuDelegateSupport(false)
            .build()

        TfLite.initialize(context.applicationContext, opt)
            .addOnSuccessListener(initExecutor) {
                if (!isClosed) {
                    isPlayServicesInitialized = true
                    Log.d(TAG, "Play Services LiteRT initialized successfully")
                    tryLoadModel()
                }
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
                val created = context.assets.openFd(MODEL_ASSET_NAME).use { assetDescriptor ->
                    java.io.FileInputStream(assetDescriptor.fileDescriptor).use { inputStream ->
                        val modelBuffer = inputStream.channel.map(
                            java.nio.channels.FileChannel.MapMode.READ_ONLY,
                            assetDescriptor.startOffset,
                            assetDescriptor.declaredLength
                        )
                        val options = InterpreterApi.Options()
                            .setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)
                        InterpreterApi.create(modelBuffer, options)
                    }
                }
                synchronized(interpreterLock) {
                    if (isClosed) {
                        created.close()
                    } else {
                        interpreter?.close()
                        interpreter = created
                    }
                }
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

        if (interpreter != null) {
            try {
                val inputBuffer = ByteBuffer.allocateDirect(32 * 4).order(ByteOrder.nativeOrder())
                val features = extractFeatures(clean)
                features.forEach { inputBuffer.putFloat(it) }
                inputBuffer.rewind()

                val outputBuffer = ByteBuffer.allocateDirect(TransitQueryIntent.entries.size * 4).order(ByteOrder.nativeOrder())
                val ranInference = synchronized(interpreterLock) {
                    val activeInterpreter = interpreter
                    if (activeInterpreter != null) {
                        activeInterpreter.run(inputBuffer, outputBuffer)
                        true
                    } else {
                        false
                    }
                }
                if (ranInference) {
                    outputBuffer.rewind()
                    val scores = FloatArray(TransitQueryIntent.entries.size)
                    outputBuffer.asFloatBuffer().get(scores)
                    val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: 0
                    val predictedIntent = TransitQueryIntent.entries.getOrElse(maxIdx) { TransitQueryIntent.GENERAL_QUERY }
                    return TransitClassificationResult(predictedIntent, scores[maxIdx])
                }
            } catch (e: Exception) {
                Log.w(TAG, "Play Services LiteRT inference error, using heuristic fallback", e)
            }
        }

        // Fast zero-latency heuristic matcher (multi-language)
        val intent = when {
            clean.contains("status") || clean.contains("good service") || clean.contains("running") ||
                clean.contains("狀態") || clean.contains("運行状況") || clean.contains("운행 현황") ->
                TransitQueryIntent.SERVICE_STATUS
            clean.contains("delay") || clean.contains("disruption") || clean.contains("problem") || clean.contains("strike") ||
                clean.contains("延誤") || clean.contains("受阻") || clean.contains("遅延") || clean.contains("見合わせ") || clean.contains("지연") ->
                TransitQueryIntent.DISRUPTION_CHECK
            clean.contains("timetable") || clean.contains("schedule") || clean.contains("first train") || clean.contains("last train") ||
                clean.contains("時刻表") || clean.contains("尾班車") || clean.contains("末班車") || clean.contains("終電") || clean.contains("막차") || clean.contains("시간표") ->
                TransitQueryIntent.TIMETABLE_LOOKUP
            clean.contains("next") || clean.contains("when") || clean.contains("train") || clean.contains("bus") || clean.contains("depart") ||
                clean.contains("下一班") || clean.contains("列車") || clean.contains("巴士") || clean.contains("次の") || clean.contains("電車") || clean.contains("バス") || clean.contains("다음") || clean.contains("열차") || clean.contains("버스") ->
                TransitQueryIntent.NEXT_DEPARTURE
            else ->
                TransitQueryIntent.GENERAL_QUERY
        }
        return TransitClassificationResult(intent, 0.95f)
    }

    private fun extractFeatures(query: String, size: Int = 32): FloatArray {
        val array = FloatArray(size)
        val tokens = query.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        val featureUnits = if (tokens.size <= 1 && query.length > 2) {
            query.windowed(size = 2, step = 1)
        } else {
            tokens
        }
        for ((idx, token) in featureUnits.withIndex()) {
            if (idx >= size) break
            array[idx] = (token.hashCode() % 1000) / 1000f
        }
        return array
    }

    override fun close() {
        isClosed = true
        synchronized(interpreterLock) {
            interpreter?.close()
            interpreter = null
        }
    }
}
