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
) {
    companion object {
        const val MODULE_NAME = "ai_assistant"
        private const val IMPL_CLASS = "com.androidfung.departureboard.features.ai.DynamicLmEngineImpl"
    }

    private val splitInstallManager: SplitInstallManager by lazy {
        SplitInstallManagerFactory.create(context)
    }

    val isFeatureInstalled: Boolean
        get() = splitInstallManager.installedModules.contains(MODULE_NAME)

    fun loadDynamicEngine(): DynamicLmEngine? {
        if (!isFeatureInstalled) return null
        return try {
            val clazz = Class.forName(IMPL_CLASS)
            val constructor = clazz.getConstructor(Context::class.java)
            constructor.newInstance(context) as? DynamicLmEngine
        } catch (e: Exception) {
            Log.w("DynamicAiAssistant", "Failed to instantiate DynamicLmEngine via reflection", e)
            null
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

        var currentSessionId = 0
        val listener = SplitInstallStateUpdatedListener { state ->
            if (state.sessionId() == currentSessionId) {
                when (state.status()) {
                    SplitInstallSessionStatus.DOWNLOADING -> {
                        val total = state.totalBytesToDownload()
                        val downloaded = state.bytesDownloaded()
                        val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
                        trySend(AiFeatureInstallStatus.Downloading(progress))
                    }
                    SplitInstallSessionStatus.INSTALLED -> {
                        trySend(AiFeatureInstallStatus.Installed)
                        close()
                    }
                    SplitInstallSessionStatus.FAILED -> {
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
                currentSessionId = sessionId
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
