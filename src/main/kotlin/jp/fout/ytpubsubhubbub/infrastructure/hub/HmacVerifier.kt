package jp.fout.ytpubsubhubbub.infrastructure.hub

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class HmacVerifier {
    fun verify(body: ByteArray, secret: String, signatureHeader: String?): Boolean {
        if (signatureHeader.isNullOrBlank()) return false
        val parts = signatureHeader.split("=", limit = 2)
        if (parts.size != 2) return false
        val (algoLabel, hexSig) = parts
        val javaAlgo = when (algoLabel.lowercase()) {
            "sha1" -> "HmacSHA1"
            "sha256" -> "HmacSHA256"
            "sha384" -> "HmacSHA384"
            "sha512" -> "HmacSHA512"
            else -> return false
        }

        val expected = try {
            HexFormat.of().parseHex(hexSig.trim())
        } catch (_: IllegalArgumentException) {
            return false
        }

        val mac = Mac.getInstance(javaAlgo)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), javaAlgo))
        val actual = mac.doFinal(body)

        return MessageDigest.isEqual(expected, actual)
    }

    fun sign(body: ByteArray, secret: String, algoLabel: String = "sha1"): String {
        val javaAlgo = when (algoLabel.lowercase()) {
            "sha1" -> "HmacSHA1"
            "sha256" -> "HmacSHA256"
            else -> throw IllegalArgumentException("unsupported algo: $algoLabel")
        }
        val mac = Mac.getInstance(javaAlgo)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), javaAlgo))
        return "$algoLabel=${HexFormat.of().formatHex(mac.doFinal(body))}"
    }
}
