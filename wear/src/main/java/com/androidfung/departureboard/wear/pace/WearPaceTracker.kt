package com.androidfung.departureboard.wear.pace

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.PassiveListenerCallback
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

enum class CommuterPaceMode {
    STATIONARY,
    WALKING,
    BRISK,
    SPRINTING
}

enum class SprintCatchability {
    MAKEABLE,
    SPRINT_NEEDED,
    UNLIKELY
}

data class PaceSprintAssessment(
    val speedMetersPerSec: Double,
    val paceMode: CommuterPaceMode,
    val estimatedWalkSeconds: Int,
    val marginSeconds: Int,
    val catchability: SprintCatchability,
    val isLiveTelemetry: Boolean
) {
    fun formattedSpeed(): String = String.format(Locale.US, "%.1f m/s", speedMetersPerSec)
}

/**
 * Pure evaluator translating Wear OS Health Services walking/running speed (or step cadence)
 * into a live concourse catchability assessment for [WearSprintHudScreen].
 */
object PaceSprintEvaluator {
    const val DEFAULT_CONCOURSE_METERS = 160.0
    const val DEFAULT_WALK_SPEED_MPS = 1.35
    const val GATE_BUFFER_SECONDS = 25

    fun speedFromStepsPerMinute(stepsPerMinute: Double): Double {
        if (stepsPerMinute <= 0.0) return 0.0
        val strideMeters = when {
            stepsPerMinute >= 150.0 -> 1.15
            stepsPerMinute >= 120.0 -> 0.85
            else -> 0.72
        }
        return ((stepsPerMinute / 60.0) * strideMeters).coerceIn(0.4, 4.5)
    }

    fun evaluate(
        remainingSeconds: Int,
        observedSpeedMps: Double?,
        concourseDistanceMeters: Double = DEFAULT_CONCOURSE_METERS
    ): PaceSprintAssessment {
        val hasTelemetry = observedSpeedMps != null && observedSpeedMps >= 0.25
        val effectiveSpeed = if (hasTelemetry) {
            observedSpeedMps.coerceIn(0.6, 5.0)
        } else {
            DEFAULT_WALK_SPEED_MPS
        }
        val mode = when {
            observedSpeedMps != null && observedSpeedMps < 0.25 -> CommuterPaceMode.STATIONARY
            effectiveSpeed >= 2.35 -> CommuterPaceMode.SPRINTING
            effectiveSpeed >= 1.65 -> CommuterPaceMode.BRISK
            else -> CommuterPaceMode.WALKING
        }
        val walkSeconds = (concourseDistanceMeters / effectiveSpeed).roundToInt() + GATE_BUFFER_SECONDS
        val marginSeconds = remainingSeconds - walkSeconds
        val catchability = when {
            marginSeconds >= 30 -> SprintCatchability.MAKEABLE
            marginSeconds >= -20 -> SprintCatchability.SPRINT_NEEDED
            else -> SprintCatchability.UNLIKELY
        }
        return PaceSprintAssessment(
            speedMetersPerSec = effectiveSpeed,
            paceMode = mode,
            estimatedWalkSeconds = walkSeconds,
            marginSeconds = marginSeconds,
            catchability = catchability,
            isLiveTelemetry = hasTelemetry
        )
    }
}

/**
 * Streams passive walking/running speed from Wear OS Health Services (`PassiveMonitoringClient`)
 * with an automatic hardware `TYPE_STEP_DETECTOR` cadence fallback when `SPEED` telemetry is unavailable.
 */
class WearHealthPaceClient(private val context: Context) {

    private val _observedSpeedMps = MutableStateFlow<Double?>(null)
    val observedSpeedMps: StateFlow<Double?> = _observedSpeedMps.asStateFlow()

    private val passiveClient: PassiveMonitoringClient? by lazy {
        runCatching { HealthServices.getClient(context).passiveMonitoringClient }.getOrNull()
    }
    private var sensorListener: SensorEventListener? = null
    private val stepTimestampsMs = ArrayDeque<Long>()

    private val passiveCallback = object : PassiveListenerCallback {
        override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
            val speedSample = dataPoints.getData(DataType.SPEED).lastOrNull()?.value
            if (speedSample != null && speedSample >= 0.0) {
                _observedSpeedMps.value = speedSample
                return
            }
            val spmSample = dataPoints.getData(DataType.STEPS_PER_MINUTE).lastOrNull()?.value
            if (spmSample != null && spmSample > 0.0) {
                _observedSpeedMps.value = PaceSprintEvaluator.speedFromStepsPerMinute(spmSample.toDouble())
            }
        }
    }

    suspend fun start() {
        var registeredHealthServices = false
        val client = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) passiveClient else null
        if (client != null) {
            try {
                val capabilities = client.getCapabilitiesAsync().awaitFuture(context)
                val supported = mutableSetOf<DataType<*, *>>()
                if (DataType.SPEED in capabilities.supportedDataTypesPassiveMonitoring) {
                    supported += DataType.SPEED
                }
                if (DataType.STEPS_PER_MINUTE in capabilities.supportedDataTypesPassiveMonitoring) {
                    supported += DataType.STEPS_PER_MINUTE
                }
                if (supported.isNotEmpty()) {
                    val config = PassiveListenerConfig.builder()
                        .setDataTypes(supported)
                        .build()
                    client.setPassiveListenerCallback(config, passiveCallback)
                    registeredHealthServices = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                registeredHealthServices = false
            }
        }
        if (!registeredHealthServices) {
            startStepCadenceFallback()
        }
    }

    fun stop() {
        runCatching { passiveClient?.clearPassiveListenerCallbackAsync() }
        sensorListener?.let { listener ->
            runCatching { context.getSystemService<SensorManager>()?.unregisterListener(listener) }
        }
        sensorListener = null
        stepTimestampsMs.clear()
    }

    private fun startStepCadenceFallback() {
        val sensorManager = context.getSystemService<SensorManager>() ?: return
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = System.currentTimeMillis()
                stepTimestampsMs.addLast(now)
                while (stepTimestampsMs.isNotEmpty() && now - stepTimestampsMs.first() > 10_000L) {
                    stepTimestampsMs.removeFirst()
                }
                if (stepTimestampsMs.size >= 3) {
                    val spanSec = ((now - stepTimestampsMs.first()).coerceAtLeast(1000L)) / 1000.0
                    val spm = ((stepTimestampsMs.size - 1) / spanSec) * 60.0
                    _observedSpeedMps.value = PaceSprintEvaluator.speedFromStepsPerMinute(spm)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            sensorListener = listener
        }
    }
}

private suspend fun <T> ListenableFuture<T>.awaitFuture(context: Context): T =
    suspendCancellableCoroutine { cont ->
        Futures.addCallback(
            this,
            object : FutureCallback<T> {
                override fun onSuccess(result: T) {
                    if (cont.isActive) cont.resume(result)
                }

                override fun onFailure(t: Throwable) {
                    if (cont.isActive) cont.resumeWithException(t)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        cont.invokeOnCancellation { cancel(false) }
    }
