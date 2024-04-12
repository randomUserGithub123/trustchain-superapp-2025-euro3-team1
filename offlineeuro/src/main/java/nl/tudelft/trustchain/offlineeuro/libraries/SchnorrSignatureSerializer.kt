package nl.tudelft.trustchain.offlineeuro.libraries

import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.math.BigInteger

private data class SchnorrSignatureBytes(
    val signature: ByteArray,
    val encryption: ByteArray,
    val message: ByteArray
)  : Serializable {

}


object SchnorrSignatureSerializer {
    fun serializeSchnorrSignature(signature: SchnorrSignature) : ByteArray {
        val signatureAsBytes = schnorrSignatureToBytes(signature)
        return serializeSchnorrBytes(signatureAsBytes)
    }

    fun deserializeProofBytes(bytes: ByteArray): SchnorrSignature {
        val signatureBytes = deserializeSignatureBytes(bytes)
        return bytesToSchnorrSignature(signatureBytes)
    }

    private fun serializeSchnorrBytes(signatureBytes: SchnorrSignatureBytes): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
        objectOutputStream.writeObject(signatureBytes)
        objectOutputStream.close()
        return byteArrayOutputStream.toByteArray()
    }

    private fun deserializeSignatureBytes(bytes: ByteArray):  SchnorrSignatureBytes {
        val byteArrayInputStream = ByteArrayInputStream(bytes)
        val objectInputStream = ObjectInputStream(byteArrayInputStream)
        val signatureBytes = objectInputStream.readObject() as  SchnorrSignatureBytes
        objectInputStream.close()
        return signatureBytes
    }

    private fun schnorrSignatureToBytes(schnorrSignature: SchnorrSignature) : SchnorrSignatureBytes {
        val (signature, encryption, message) = schnorrSignature
        return SchnorrSignatureBytes(
            signature.toByteArray(),
            encryption.toByteArray(),
            message
        )
    }

    private fun bytesToSchnorrSignature(signatureBytes: SchnorrSignatureBytes) : SchnorrSignature {
        val (signature, encryption, message) = signatureBytes
        return SchnorrSignature(
            BigInteger(signature),
            BigInteger(encryption),
            message
        )
    }
}
