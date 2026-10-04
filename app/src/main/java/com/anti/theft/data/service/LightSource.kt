package com.anti.theft.data.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.getSystemService
import com.anti.theft.domain.models.LightSampleModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

class LightSource(context: Context) {

    private val sensorManager = context.getSystemService<SensorManager>()

    fun samples(): Flow<LightSampleModel> {
        val manager = sensorManager ?: return emptyFlow()
        val sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT, true)
            ?: manager.getDefaultSensor(Sensor.TYPE_LIGHT)
            ?: return emptyFlow()
        return callbackFlow {
            val thread = HandlerThread("light-sensor").apply { start() }
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    trySend(LightSampleModel(event.values[0], event.timestamp))
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, Handler(thread.looper))
            awaitClose {
                manager.unregisterListener(listener)
                thread.quitSafely()
            }
        }.buffer(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }
}
