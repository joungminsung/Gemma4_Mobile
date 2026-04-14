package com.gemma4mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Thin launcher for side button (digital assistant) integration.
 * Forwards to MainActivity with ASSIST extra, then finishes.
 */
@AndroidEntryPoint
class AssistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_ASSIST, true)
        }
        startActivity(mainIntent)
        finish()
    }

    companion object {
        const val EXTRA_FROM_ASSIST = "from_assist"
    }
}
