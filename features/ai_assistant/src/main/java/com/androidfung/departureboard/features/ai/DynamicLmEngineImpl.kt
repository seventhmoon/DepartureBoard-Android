package com.androidfung.departureboard.features.ai

import android.content.Context
import android.util.Log
import com.androidfung.departureboard.ai.DynamicLmEngine
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of DynamicLmEngine bundled inside :features:ai_assistant.
 * Contains LiteRT-LM libraries and handles on-device model inference.
 */
class DynamicLmEngineImpl(
    private val context: Context
) : DynamicLmEngine, AutoCloseable {

    companion object {
        private const val TAG = "DynamicLmEngineImpl"
        private const val DEFAULT_MODEL_FILENAME = "qwen_0_6b.litertlm"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var isBusy = false

    override val isReady: Boolean
        get() = engine != null && conversation != null

    private fun getModelFile(): File? {
        val candidates = listOf(
            File(context.filesDir, DEFAULT_MODEL_FILENAME),
            File(context.filesDir, "model.litertlm"),
            File(context.getExternalFilesDir(null), DEFAULT_MODEL_FILENAME),
            File(context.getExternalFilesDir(null), "model.litertlm")
        )
        return candidates.firstOrNull { it.exists() && it.length() > 0 }
    }

    override suspend fun loadModel(): Boolean = withContext(dispatcher) {
        val modelFile = getModelFile() ?: return@withContext false
        mutex.withLock {
            if (isReady) return@withLock true

            releaseInternal()
            try {
                Log.d(TAG, "Initializing LiteRT-LM Engine from: ${modelFile.absolutePath}")
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    cacheDir = context.cacheDir.path
                )
                val newEngine = Engine(config).also { it.initialize() }
                engine = newEngine

                conversation = newEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(
                            "You are EkiBoard Transit AI, a helpful London and HK public transit assistant. " +
                            "Provide concise, timely, and polite answers about train departures, tube lines, buses, and live disruptions."
                        ),
                        samplerConfig = SamplerConfig(
                            topK = 20,
                            topP = 0.9,
                            temperature = 0.7
                        )
                    )
                )
                Log.d(TAG, "LiteRT-LM Engine successfully initialized.")
                true
            } catch (e: Exception) {
                Log.w(TAG, "Failed initializing with GPU, falling back to CPU: ${e.message}")
                releaseInternal()
                runCatching {
                    val cpuConfig = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.path
                    )
                    val cpuEngine = Engine(cpuConfig).also { it.initialize() }
                    engine = cpuEngine
                    conversation = cpuEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of("You are EkiBoard Transit AI assistant.")
                        )
                    )
                    true
                }.getOrDefault(false)
            }
        }
    }

    override fun streamReply(prompt: String): Flow<String> = callbackFlow {
        val job = scope.launch {
            mutex.withLock {
                if (conversation == null) {
                    trySend("Local LLM model is not ready.")
                    close()
                    return@withLock
                }
                isBusy = true
            }

            try {
                val replies = checkNotNull(conversation).sendMessageAsync(prompt)
                replies.collect { message ->
                    trySend(message.toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during LiteRT-LM reply streaming", e)
                trySend("\n[Inference error: ${e.localizedMessage}]")
            } finally {
                mutex.withLock { isBusy = false }
                close()
            }
        }

        awaitClose {
            job.cancel()
        }
    }

    private suspend fun releaseInternal() {
        while (isBusy) {
            runCatching { conversation?.cancelProcess() }
            delay(50L.milliseconds)
        }
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
    }

    override fun close() {
        scope.launch {
            mutex.withLock { releaseInternal() }
            executor.shutdown()
        }.invokeOnCompletion {
            scope.cancel()
        }
    }
}
