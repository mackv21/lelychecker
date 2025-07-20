package com.example.lelychecker

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Bitmap

class QRCodeDisplayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code_display)

        val imageView = findViewById<ImageView>(R.id.imageViewQRCode)
        val printButton = findViewById<Button>(R.id.buttonPrint)

        val bitmap = intent.getParcelableExtra<Bitmap>("QR_BITMAP")
        imageView.setImageBitmap(bitmap)

        printButton.setOnClickListener {
            // Implement print or share functionality
        }
    }
}