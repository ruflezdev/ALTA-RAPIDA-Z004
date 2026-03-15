package com.ruflezdev.altarapida

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.CompoundBarcodeView

class CustomCaptureActivity : CaptureActivity() {

    private lateinit var barcodeScannerView: CompoundBarcodeView
    private lateinit var btnFlash: ImageButton
    private var isFlashOn = false

    override fun initializeContent(): CompoundBarcodeView {
        setContentView(R.layout.custom_scanner_layout)
        barcodeScannerView = findViewById(R.id.zxing_barcode_scanner)
        return barcodeScannerView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        btnFlash = findViewById(R.id.btnFlash)

        if (!hasFlash()) {
            btnFlash.visibility = View.GONE
        }

        btnFlash.setOnClickListener {
            if (isFlashOn) {
                barcodeScannerView.setTorchOff()
                isFlashOn = false
                btnFlash.setColorFilter(Color.parseColor("#808080")) // Gris (Apagado)
            } else {
                barcodeScannerView.setTorchOn()
                isFlashOn = true
                btnFlash.setColorFilter(Color.WHITE) // Blanco (Encendido)
            }
        }
    }

    private fun hasFlash(): Boolean {
        return applicationContext.packageManager
            .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }
}
