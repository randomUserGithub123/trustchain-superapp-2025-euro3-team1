package nl.tudelft.trustchain.offlineeuro.libraries

import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.io.IOException


object SchnorrSignatureSerializer {

    private const val INT_SIZE = 4

    fun serializeSchnorrSignature(schnorrSignature: SchnorrSignature?): ByteArray {
        if (schnorrSignature == null) return ByteArray(0)

        val (signature, encryption, message) = schnorrSignature

        val signatureBytes = signature.toByteArray()
        val encryptionBytes = encryption.toByteArray()

        val outputStream = ByteArrayOutputStream()

        // This is length-prefixing for signature and encryption bytes
        outputStream.write(intToBytes(signatureBytes.size))
        outputStream.write(intToBytes(encryptionBytes.size))

        // Write the actual byte arrays.
        outputStream.write(signatureBytes, 0, signatureBytes.size)
        outputStream.write(encryptionBytes, 0, encryptionBytes.size)
        outputStream.write(message, 0, message.size)

        return outputStream.toByteArray()
    }

    fun deserializeSchnorrSignatureBytes(bytes: ByteArray?): SchnorrSignature? {
        if (bytes == null || bytes.isEmpty()) return null

        val inputStream = ByteArrayInputStream(bytes)

        // Read the lengths first
        val signatureLengthBytes = ByteArray(INT_SIZE)
        inputStream.read(signatureLengthBytes)
        val signatureLength = bytesToInt(signatureLengthBytes)

        val encryptionLengthBytes = ByteArray(INT_SIZE)
        inputStream.read(encryptionLengthBytes)
        val encryptionLength = bytesToInt(encryptionLengthBytes)

        // then read the actual bytes
        val signatureBytes = ByteArray(signatureLength)
        inputStream.read(signatureBytes)

        val encryptionBytes = ByteArray(encryptionLength)
        inputStream.read(encryptionBytes)

        // The rest is the message content
        val messageBytes = inputStream.readBytes()

        return SchnorrSignature(
            BigInteger(signatureBytes),
            BigInteger(encryptionBytes),
            messageBytes
        )
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    }

    private fun bytesToInt(bytes: ByteArray): Int {
        if (bytes.size < INT_SIZE) throw IOException("Invalid byte array length for integer conversion.")
        return (bytes[0].toInt() shl 24) or
            (bytes[1].toInt() and 0xFF shl 16) or
            (bytes[2].toInt() and 0xFF shl 8) or
            (bytes[3].toInt() and 0xFF)
    }
}
