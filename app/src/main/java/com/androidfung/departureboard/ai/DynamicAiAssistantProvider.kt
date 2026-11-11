package com.androidfung.departureboard.ai

import android.content.Context
import android.util.Log
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed interface AiFeatureInstallStatus {
    data object Installed : AiFeatureInstallStatus
    data object NotInstalled : AiFeatureInstallStatus
    data class Downloading(val progress: Float) : AiFeatureInstallStatus
    data class Failed(val errorCode: Int) : AiFeatureInstallStatus
}

/**
 * Interface that the dynamic feature module (:features:ai_assistant) implements.
 * Instantiated via reflection when the dynamic module is installed.
 */
interface DynamicLmEngine {
    val isReady: Boolean
    suspend fun loadModel(): Boolean
    fun streamReply(prompt: String): Flow<String>
}

/**
 * Manages checking and on-demand installation of the :features:ai_assistant dynamic feature module.
 */
class DynamicAiAssistantProvider(
    private val context: Context
) : AutoCloseable {
    companion object {
        const val MODULE_NAME = "ai_assistant"
        private const val IMPL_CLASS = "com.androidfung.departureboard.features.ai.DynamicLmEngineImpl"
    }

    private val splitInstallManager: SplitInstallManager by lazy {
        SplitInstallManagerFactory.create(context)
    }

    @Volatile
    private var cachedEngine: DynamicLmEngine? = null

    val isFeatureInstalled: Boolean
        get() = splitInstallManager.installedModules.contains(MODULE_NAME)

    fun loadDynamicEngine(): DynamicLmEngine? {
        if (!isFeatureInstalled) return null
        cachedEngine?.let { return it }
        return synchronized(this) {
            cachedEngine ?: try {
                val clazz = Class.forName(IMPL_CLASS)
                val constructor = clazz.getConstructor(Context::class.java)
                (constructor.newInstance(context) as? DynamicLmEngine)?.also {
                    cachedEngine = it
                }
            } catch (e: Exception) {
                Log.w("DynamicAiAssistant", "Failed to instantiate DynamicLmEngine via reflection", e)
                null
            }
        }
    }

    override fun close() {
        synchronized(this) {
            (cachedEngine as? AutoCloseable)?.close()
            cachedEngine = null
        }
    }

    fun requestInstall(): Flow<AiFeatureInstallStatus> = callbackFlow {
        if (isFeatureInstalled) {
            trySend(AiFeatureInstallStatus.Installed)
            close()
            return@callbackFlow
        }

        val request = SplitInstallRequest.newBuilder()
            .addModule(MODULE_NAME)
            .build()

        val currentSessionId = java.util.concurrent.atomic.AtomicInteger(0)
        val listener = SplitInstallStateUpdatedListener { state ->
            val sessionId = currentSessionId.get()
            val matchesSession = (sessionId != 0 && state.sessionId() == sessionId) ||
                (sessionId == 0 && state.moduleNames().contains(MODULE_NAME))
            if (matchesSession) {
                when (state.status()) {
                    SplitInstallSessionStatus.DOWNLOADING,
                    SplitInstallSessionStatus.INSTALLING,
                    SplitInstallSessionStatus.PENDING -> {
                        val total = state.totalBytesToDownload()
                        val downloaded = state.bytesDownloaded()
                        val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
                        trySend(AiFeatureInstallStatus.Downloading(progress))
                    }
                    SplitInstallSessionStatus.INSTALLED -> {
                        trySend(AiFeatureInstallStatus.Installed)
                        close()
                    }
                    SplitInstallSessionStatus.FAILED,
                    SplitInstallSessionStatus.CANCELED,
                    SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                        trySend(AiFeatureInstallStatus.Failed(state.errorCode()))
                        close()
                    }
                    else -> {}
                }
            }
        }

        splitInstallManager.registerListener(listener)
        splitInstallManager.startInstall(request)
            .addOnSuccessListener { sessionId ->
                currentSessionId.set(sessionId)
            }
            .addOnFailureListener { e ->
                trySend(AiFeatureInstallStatus.Failed(-1))
                close()
            }

        awaitClose {
            splitInstallManager.unregisterListener(listener)
        }
    }
}
