//package com.example.rangervault // <--- Check your package name!
//
//import android.util.Base64
//import java.security.KeyFactory
//import java.security.Signature
//import java.security.spec.X509EncodedKeySpec
//
//object OfflineVerifier {
//
//    // PASTE YOUR PUBLIC.PEM CONTENT HERE (Keep the newlines if possible, or make it one long string)
//    // NOTE: In production, you wouldn't hardcode this, but for a Hackathon, this is perfect.
//    private const val PUBLIC_KEY_PEM = """
//    -----BEGIN PUBLIC KEY-----
//    MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApQsck+B8/SLr/oTNaMME
//    czslBI4LFDqE49OoC+QRo0qoGh+6Q8M/WgzufDAfplGcdeDFUCb6eO59te4s+Omx
//    aYm4R96qmdp7vFZAu7qWWoRHYHTbSXAfyx7W6dtGsAJjVRjDyfVDmnncSCvLzhWL
//    PHpfzp2bMYLmB5x5Khlp6t2ZcII9VERl0JpLAFk9+iyKFfeTwdLzGcpRVobUgUPC
//    EeZVBQmJ7e4MfbCpKxUeY1UjMLvder1Gk7aKELgsSZFPwHGe7KsHYAHJGraLlvgd
//    X9yK7wgBmUNUxxn6OV6ov9S4efVe4zG1IyEAnEgr5moWdAaxjpl3U0F+tyEa4Ny2
//    5QIDAQAB
//    -----END PUBLIC KEY-----
//
//    """
//
//    fun verify(fullQrData: String): Boolean {
//        try {
//            // 1. Split the QR Data (Payload ## Signature)
//            val parts = fullQrData.split("##")
//            if (parts.size != 2) return false
//
//            val payload = parts[0]
//            val signatureStr = parts[1]
//
//            // 2. Clean the Public Key string
//            val realKey = PUBLIC_KEY_PEM
//                .replace("-----BEGIN PUBLIC KEY-----", "")
//                .replace("-----END PUBLIC KEY-----", "")
//                .replace("\\s".toRegex(), "") // Remove spaces/newlines
//
//            // 3. Convert String to PublicKey Object
//            val keyBytes = Base64.decode(realKey, Base64.DEFAULT)
//            val keySpec = X509EncodedKeySpec(keyBytes)
//            val keyFactory = KeyFactory.getInstance("RSA")
//            val publicKey = keyFactory.generatePublic(keySpec)
//
//            // 4. Verify the Signature
//            val signatureBytes = Base64.decode(signatureStr, Base64.DEFAULT)
//            val signature = Signature.getInstance("SHA256withRSA")
//            signature.initVerify(publicKey)
//            signature.update(payload.toByteArray())
//
//            val isSigValid = signature.verify(signatureBytes)
//
//            // 5. Check Time (Optional but recommended for "Dev or Die")
//            // Payload format: "userId|role|timestamp"
//            val payloadParts = payload.split("|")
//            val tokenTime = payloadParts[2].toLong()
//            val currentTime = System.currentTimeMillis() / 30000
//
//            // Allow 1 minute window (current or previous 30s block)
//            val isTimeValid = (currentTime - tokenTime) <= 2 && (currentTime - tokenTime) >= 0
//
//            return isSigValid && isTimeValid
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//            return false
//        }
//    }
//}
//
//updated4
package com.example.rangervault

import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object OfflineVerifier {

    // --- PASTE YOUR PUBLIC KEY HERE ---
    // Keep the "BEGIN" and "END" lines.
    private const val PUBLIC_KEY_PEM = """
    -----BEGIN PUBLIC KEY-----
    MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApQsck+B8/SLr/oTNaMME
    czslBI4LFDqE49OoC+QRo0qoGh+6Q8M/WgzufDAfplGcdeDFUCb6eO59te4s+Omx
    aYm4R96qmdp7vFZAu7qWWoRHYHTbSXAfyx7W6dtGsAJjVRjDyfVDmnncSCvLzhWL
    PHpfzp2bMYLmB5x5Khlp6t2ZcII9VERl0JpLAFk9+iyKFfeTwdLzGcpRVobUgUPC
    EeZVBQmJ7e4MfbCpKxUeY1UjMLvder1Gk7aKELgsSZFPwHGe7KsHYAHJGraLlvgd
    X9yK7wgBmUNUxxn6OV6ov9S4efVe4zG1IyEAnEgr5moWdAaxjpl3U0F+tyEa4Ny2
    5QIDAQAB
    -----END PUBLIC KEY-----
    """

    fun verify(fullQrData: String): Boolean {
        try {
            // 1. Split Data
            val parts = fullQrData.split("##")
            if (parts.size != 2) return false

            val payload = parts[0]
            val signatureStr = parts[1]

            // 2. Format Key
            val realKey = PUBLIC_KEY_PEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "") // Remove newlines/spaces

            // 3. Create Public Key Object
            val keyBytes = Base64.decode(realKey, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)

            // 4. Verify Signature
            val signatureBytes = Base64.decode(signatureStr, Base64.DEFAULT)
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(publicKey)
            signature.update(payload.toByteArray())

            val isSigValid = signature.verify(signatureBytes)

            // 5. Verify Time (30 Second Window)
            // Payload format: "userId|role|timestamp"
            val payloadParts = payload.split("|")
            if(payloadParts.size < 3) return false

            val tokenTime = payloadParts[2].toLong()
            val currentTime = System.currentTimeMillis() / 30000 // 30s blocks

            // Valid if current block OR previous block (allow 1 min drift)
            val isTimeValid = (currentTime - tokenTime) <= 1 && (currentTime - tokenTime) >= 0

            return isSigValid && isTimeValid

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}