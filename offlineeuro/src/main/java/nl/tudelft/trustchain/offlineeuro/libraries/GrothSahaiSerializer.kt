package nl.tudelft.trustchain.offlineeuro.libraries

import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException // Import EOFException
import java.io.InputStream


private data class GrothSahaiProofBytes(
    val c1: ByteArray,
    val c2: ByteArray,
    val d1: ByteArray,
    val d2: ByteArray,
    val theta1: ByteArray,
    val theta2: ByteArray,
    val pi1: ByteArray,
    val pi2: ByteArray,
    val target: ByteArray
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GrothSahaiProofBytes

        if (!c1.contentEquals(other.c1)) return false
        if (!c2.contentEquals(other.c2)) return false
        if (!d1.contentEquals(other.d1)) return false
        if (!d2.contentEquals(other.d2)) return false
        if (!theta1.contentEquals(other.theta1)) return false
        if (!theta2.contentEquals(other.theta2)) return false
        if (!pi1.contentEquals(other.pi1)) return false
        if (!pi2.contentEquals(other.pi2)) return false
        return target.contentEquals(other.target)
    }

    companion object {
        const val ELEMENT_SIZE_BYTES = 128
        const val NUM_ELEMENTS = 9
        const val TOTAL_SERIALIZED_SIZE = ELEMENT_SIZE_BYTES * NUM_ELEMENTS
    }
}

object GrothSahaiSerializer {
    fun serializeGrothSahaiProofs(proofs: List<GrothSahaiProof>?): ByteArray? {
        if (proofs.isNullOrEmpty()) {
            return null
        }

        val proofsAsBytes = proofs.map { x -> grothSahaiProofToBytes(x) }
        return serializeProofBytesList(proofsAsBytes)
    }

    fun serializeGrothSahaiProof(proof: GrothSahaiProof): ByteArray {
        return serializeProofBytes(grothSahaiProofToBytes(proof))
    }

    fun deserializeProofBytes(
        bytes: ByteArray,
        group: BilinearGroup
    ): GrothSahaiProof {
        val proofBytes = deserializeProofBytes(bytes)
        return bytesToGrothSahai(proofBytes, group)
    }

    fun deserializeProofListBytes(
        bytes: ByteArray?,
        group: BilinearGroup
    ): ArrayList<GrothSahaiProof> {
        if (bytes == null) return arrayListOf()
        if (bytes.isEmpty()) return arrayListOf()

        val proofBytesList = deserializeProofBytesList(bytes)
        return ArrayList(proofBytesList.map { x -> bytesToGrothSahai(x, group) })
    }

    private fun serializeProofBytes(proofBytes: GrothSahaiProofBytes): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream(GrothSahaiProofBytes.TOTAL_SERIALIZED_SIZE)
        byteArrayOutputStream.write(proofBytes.c1)
        byteArrayOutputStream.write(proofBytes.c2)
        byteArrayOutputStream.write(proofBytes.d1)
        byteArrayOutputStream.write(proofBytes.d2)
        byteArrayOutputStream.write(proofBytes.theta1)
        byteArrayOutputStream.write(proofBytes.theta2)
        byteArrayOutputStream.write(proofBytes.pi1)
        byteArrayOutputStream.write(proofBytes.pi2)
        byteArrayOutputStream.write(proofBytes.target)
        return byteArrayOutputStream.toByteArray()
    }

    private fun serializeProofBytesList(proofList: List<GrothSahaiProofBytes>): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val dataOutputStream = DataOutputStream(byteArrayOutputStream)

        // First write the number of proofs in the list
        dataOutputStream.writeInt(proofList.size)

        proofList.forEach { proofBytes ->
            // Serialize each proof using the compact method and write its bytes
            val serializedProof = serializeProofBytes(proofBytes)
            dataOutputStream.write(serializedProof)
        }
        dataOutputStream.close()
        return byteArrayOutputStream.toByteArray()
    }

    private fun deserializeProofBytesList(bytes: ByteArray): List<GrothSahaiProofBytes> {
        if (bytes.isEmpty()) return emptyList()

        val byteArrayInputStream = ByteArrayInputStream(bytes)
        val dataInputStream = DataInputStream(byteArrayInputStream)
        val list = mutableListOf<GrothSahaiProofBytes>()

        val count = dataInputStream.readInt()

        for (i in 0 until count) {
            val proofData = ByteArray(GrothSahaiProofBytes.TOTAL_SERIALIZED_SIZE)
            val bytesRead = dataInputStream.read(proofData)
            if (bytesRead < GrothSahaiProofBytes.TOTAL_SERIALIZED_SIZE) {
                throw IllegalStateException("Could not read enough bytes for a GrothSahaiProofBytes from the list.")
            }
            list.add(deserializeProofBytes(proofData))
        }
        dataInputStream.close()
        return list
    }

    @Throws(EOFException::class, java.io.IOException::class)
    private fun readExactlyNBytes(stream: InputStream, n: Int): ByteArray {
        val buffer = ByteArray(n)
        var totalBytesRead = 0
        while (totalBytesRead < n) {
            val bytesRead = stream.read(buffer, totalBytesRead, n - totalBytesRead)
            if (bytesRead == -1) { // End Of Stream
                throw EOFException("Premature end of stream. Expected $n bytes, but only got $totalBytesRead before EOF.")
            }
            totalBytesRead += bytesRead
        }
        return buffer
    }

    private fun deserializeProofBytes(bytes: ByteArray): GrothSahaiProofBytes {
        if (bytes.size != GrothSahaiProofBytes.TOTAL_SERIALIZED_SIZE) {
            throw IllegalArgumentException("Invalid byte array size for GrothSahaiProofBytes. Expected ${GrothSahaiProofBytes.TOTAL_SERIALIZED_SIZE}, got ${bytes.size}")
        }
        val inputStream = ByteArrayInputStream(bytes)
        return GrothSahaiProofBytes(
            c1 = readExactlyNBytes(inputStream, GrothSahaiProofBytes.ELEMENT_SIZE_BYTES),
            c2 = readExactlyNBytes(inputStream, GrothSahaiProofBytes.ELEMENT_SIZE_BYTES),
            d1 = readExactlyNBytes(inputStream, GrothSahaiProofBytes.ELEMENT_SIZE_BYTES),
            d2 = readExactlyNBytes(inputStream, GrothSahaiProofBytes.ELEMENT_SIZE_BYTES),
            theta1 = readExactlyNBytes(inputStream, GrothSahaiProofBytes.ELEMENT_SIZE_BYTES),
            theta2 = readExactlyNBytes(inputStream, GrothSahaiProofBytes.ELEMENT_SIZE_BYTES),
            pi1 = readExactlyNBytes(inputStream, GrothSahaiProofBytes.ELEMENT_SIZE_BYTES),
            pi2 = readExactlyNBytes(inputStream, GrothSahaiProofBytes.ELEMENT_SIZE_BYTES),
            target = readExactlyNBytes(inputStream, GrothSahaiProofBytes.ELEMENT_SIZE_BYTES)
        )
    }

    private fun grothSahaiProofToBytes(grothSahaiProof: GrothSahaiProof): GrothSahaiProofBytes {
        val (c1, c2, d1, d2, theta1, theta2, pi1, pi2, target) = grothSahaiProof
        return GrothSahaiProofBytes(
            c1.toBytes(),
            c2.toBytes(),
            d1.toBytes(),
            d2.toBytes(),
            theta1.toBytes(),
            theta2.toBytes(),
            pi1.toBytes(),
            pi2.toBytes(),
            target.toBytes()
        )
    }

    private fun bytesToGrothSahai(
        grothSahaiProofBytes: GrothSahaiProofBytes,
        group: BilinearGroup
    ): GrothSahaiProof {
        val (c1, c2, d1, d2, theta1, theta2, pi1, pi2, target) = grothSahaiProofBytes
        return GrothSahaiProof(
            group.gElementFromBytes(c1),
            group.gElementFromBytes(c2),
            group.hElementFromBytes(d1),
            group.hElementFromBytes(d2),
            group.gElementFromBytes(theta1),
            group.gElementFromBytes(theta2),
            group.hElementFromBytes(pi1),
            group.hElementFromBytes(pi2),
            group.gtElementFromBytes(target)
        )
    }
}
