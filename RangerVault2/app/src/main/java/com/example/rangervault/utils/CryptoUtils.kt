package com.example.rangervault.utils

import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object OfflineVerifier {
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
            val parts = fullQrData.split("##")
            if (parts.size != 2) return false
            val payload = parts[0]; val signatureStr = parts[1]

            val realKey = PUBLIC_KEY_PEM.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replace("\\s".toRegex(), "")
            val keyBytes = Base64.decode(realKey, Base64.DEFAULT)
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))

            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(publicKey)
            signature.update(payload.toByteArray())
            val isSigValid = signature.verify(Base64.decode(signatureStr, Base64.DEFAULT))

            val payloadParts = payload.split("|")
            if(payloadParts.size < 3) return false
            val tokenTime = payloadParts[2].toLong()
            val currentTime = System.currentTimeMillis() / 30000
            val isTimeValid = (currentTime - tokenTime) <= 1 && (currentTime - tokenTime) >= 0

            return isSigValid && isTimeValid
        } catch (e: Exception) { return false }
    }
}