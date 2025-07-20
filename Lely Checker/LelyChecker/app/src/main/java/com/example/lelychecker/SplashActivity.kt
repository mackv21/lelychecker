package com.example.lelychecker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("SplashActivity", "onCreate started")

        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("SplashActivity", "Starting UserDetailsActivity")
            startActivity(Intent(this, MainActivity::class.java))
            Log.d("SplashActivity", "Finished starting UserDetailsActivity")
            finish()
        }, 2000) // 2 seconds splash
    }
}