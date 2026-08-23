package com.stridepath.app

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

class PermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply {
            textSize = 18f
            setPadding(48, 48, 48, 48)
            text = """
                StridePath health-data privacy

                StridePath requests read-only access to your step count through Health Connect so it can show daily walking progress, quest completion, streaks, and achievements.

                StridePath does not sell health data, does not use it for advertising, and does not upload Health Connect data to a StridePath server. The app is designed to store its own logs locally on your device.

                Health Connect permission is optional. If you do not grant it, StridePath can fall back to the phone step-counter sensor when that sensor is available and Android activity-recognition permission is granted.

                You can revoke Health Connect access at any time in Android Health Connect settings.
            """.trimIndent()
        }
        setContentView(ScrollView(this).apply { addView(text) })
    }
}
