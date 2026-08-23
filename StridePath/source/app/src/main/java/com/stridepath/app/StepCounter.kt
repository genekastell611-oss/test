package com.stridepath.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.time.LocalDate
import kotlin.math.roundToInt

class StepCounterManager(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val prefs = appContext.getSharedPreferences("stridepath_steps", Context.MODE_PRIVATE)
    private var callback: ((Int) -> Unit)? = null

    val hasSensor: Boolean get() = sensor != null

    fun start(onSteps: (Int) -> Unit) {
        callback = onSteps
        sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        manager.unregisterListener(this)
        callback = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val raw = event?.values?.firstOrNull() ?: return
        val today = LocalDate.now().toString()
        val storedDay = prefs.getString("day", null)
        val lastRaw = prefs.getFloat("last_raw", -1f)
        var accumulated = if (storedDay == today) prefs.getInt("accumulated", 0) else 0
        if (lastRaw >= 0f && raw >= lastRaw) accumulated += (raw - lastRaw).roundToInt().coerceAtLeast(0)
        prefs.edit()
            .putString("day", today)
            .putFloat("last_raw", raw)
            .putInt("accumulated", accumulated)
            .apply()
        callback?.invoke(accumulated)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
