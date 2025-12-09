package com.example.rangervault.utils

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.CaptureActivity
import android.graphics.Color as AndroidColor

class PortraitCaptureActivity : CaptureActivity()

object QRGenerator {
    fun generate(content: String): Bitmap {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val w = bitMatrix.width; val h = bitMatrix.height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        for (x in 0 until w) { for (y in 0 until h) { bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE) } }
        return bmp
    }
}