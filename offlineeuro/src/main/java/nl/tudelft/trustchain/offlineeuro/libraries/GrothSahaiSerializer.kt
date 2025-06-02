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
import java.io.EOFException
import java.io.InputStream
import java.io.StreamCorruptedException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Compress a byte array using ZLIB.
 */
private fun ByteArray.zlibCompress(): ByteArray {
    // Compress the bytes
    val output = ByteArray(this.size * 2) // Initial buffer size
    val compressor = Deflater().apply {
        setInput(this@zlibCompress)
        finish()
    }
    val compressedDataLength: Int = compressor.deflate(output)
    compressor.end()
    return output.copyOfRange(0, compressedDataLength)
}

/**
 * Decompress a byte array using ZLIB.
 */
private fun ByteArray.zlibDecompress(): ByteArray {
    val inflater = Inflater()
    val outputStream = ByteArrayOutputStream()
    return outputStream.use {
        val buffer = ByteArray(1024)
        inflater.setInput(this@zlibDecompress)
        var count = -1
        while (count != 0) {
            count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        inflater.end()
        outputStream.toByteArray()
    }
}

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
        const val NUM_ELEMENTS = 9
    }
}

object GrothSahaiSerializer {

    const val MAX_ALLOWED_PROOFS = 1000
    const val MAX_ALLOWED_PROOF_BYTES = 10000
    
    // Compression flags
    private const val COMPRESSION_FLAG_NONE: Byte = 0
    private const val COMPRESSION_FLAG_ZLIB: Byte = 1

    fun serializeGrothSahaiProofs(proofs: List<GrothSahaiProof>?, useCompression: Boolean = true): ByteArray? {
        if (proofs.isNullOrEmpty()) {
            return null
        }

        val proofsAsBytes = proofs.map { x -> grothSahaiProofToBytes(x) }
        val uncompressedData = serializeProofBytesList(proofsAsBytes)
        
        return if (useCompression) {
            val compressed = uncompressedData.zlibCompress()
            // Only use compression if it actually reduces size
            if (compressed.size < uncompressedData.size) {
                prependCompressionFlag(compressed, COMPRESSION_FLAG_ZLIB)
            } else {
                prependCompressionFlag(uncompressedData, COMPRESSION_FLAG_NONE)
            }
        } else {
            prependCompressionFlag(uncompressedData, COMPRESSION_FLAG_NONE)
        }
    }

    fun serializeGrothSahaiProof(proof: GrothSahaiProof, useCompression: Boolean = true): ByteArray {
        val uncompressedData = serializeProofBytes(grothSahaiProofToBytes(proof))
        
        return if (useCompression) {
            val compressed = uncompressedData.zlibCompress()
            // Only use compression if it actually reduces size
            if (compressed.size < uncompressedData.size) {
                prependCompressionFlag(compressed, COMPRESSION_FLAG_ZLIB)
            } else {
                prependCompressionFlag(uncompressedData, COMPRESSION_FLAG_NONE)
            }
        } else {
            prependCompressionFlag(uncompressedData, COMPRESSION_FLAG_NONE)
        }
    }

    fun deserializeProofBytes(
        bytes: ByteArray,
        group: BilinearGroup
    ): GrothSahaiProof {
        val (decompressed, _) = extractAndDecompress(bytes)
        val proofBytes = deserializeProofBytes(decompressed)
        return bytesToGrothSahai(proofBytes, group)
    }

    fun deserializeProofListBytes(
        bytes: ByteArray?,
        group: BilinearGroup
    ): ArrayList<GrothSahaiProof> {
        if (bytes == null) return arrayListOf()
        if (bytes.isEmpty()) return arrayListOf()

        val (decompressed, _) = extractAndDecompress(bytes)
        val proofBytesList = deserializeProofBytesList(decompressed)
        return ArrayList(proofBytesList.map { x -> bytesToGrothSahai(x, group) })
    }

    private fun prependCompressionFlag(data: ByteArray, flag: Byte): ByteArray {
        val result = ByteArray(data.size + 1)
        result[0] = flag
        System.arraycopy(data, 0, result, 1, data.size)
        return result
    }

    private fun extractAndDecompress(bytes: ByteArray): Pair<ByteArray, Byte> {
        if (bytes.isEmpty()) {
            throw StreamCorruptedException("Empty byte array provided for decompression")
        }
        
        val compressionFlag = bytes[0]
        val dataBytes = bytes.copyOfRange(1, bytes.size)
        
        val decompressedData = when (compressionFlag) {
            COMPRESSION_FLAG_NONE -> dataBytes
            COMPRESSION_FLAG_ZLIB -> dataBytes.zlibDecompress()
            else -> throw StreamCorruptedException("Unknown compression flag: $compressionFlag")
        }
        
        return Pair(decompressedData, compressionFlag)
    }

    private fun serializeProofBytesList(proofList: List<GrothSahaiProofBytes>): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val dataOutputStream = DataOutputStream(byteArrayOutputStream)

        dataOutputStream.writeInt(proofList.size)

        proofList.forEach { proofBytes ->
            val serializedProof = serializeProofBytes(proofBytes)
            // Prefixing with length
            dataOutputStream.writeInt(serializedProof.size)
            dataOutputStream.write(serializedProof)
        }
        dataOutputStream.close()
        return byteArrayOutputStream.toByteArray()
    }

    private fun deserializeProofBytesList(bytes: ByteArray): List<GrothSahaiProofBytes> {
        if (bytes.size < 4) {
            return emptyList()
        }

        val byteArrayInputStream = ByteArrayInputStream(bytes)
        val dataInputStream = DataInputStream(byteArrayInputStream)
        val list = mutableListOf<GrothSahaiProofBytes>()

        val count = dataInputStream.readInt()
        if (count < 0) {
            throw StreamCorruptedException("Invalid negative count for proof list size: $count")
        }
        if (count > MAX_ALLOWED_PROOFS) {
            throw StreamCorruptedException("Exceeded maximum allowed proofs. Count: $count, Max: $MAX_ALLOWED_PROOFS")
        }

        // Deserialize individual proof
        for (i in 0 until count) {
            // Read the length of the next serialized proof
            val proofLength = dataInputStream.readInt()
            if (proofLength < 0) {
                throw StreamCorruptedException("Invalid negative length for proof item ${i + 1}: $proofLength")
            }
            if (proofLength > MAX_ALLOWED_PROOF_BYTES) {
                throw StreamCorruptedException("Exceeded maximum allowed proof bytes for item ${i + 1}. Length: $proofLength, Max: $MAX_ALLOWED_PROOF_BYTES")
            }
            val proofData = ByteArray(proofLength)
            dataInputStream.readFully(proofData)
            list.add(deserializeProofBytes(proofData))
        }
        dataInputStream.close()
        return list
    }

    private fun serializeProofBytes(proofBytes: GrothSahaiProofBytes): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val dataOutputStream = DataOutputStream(byteArrayOutputStream)

        val components = listOf(
            proofBytes.c1, proofBytes.c2, proofBytes.d1, proofBytes.d2,
            proofBytes.theta1, proofBytes.theta2, proofBytes.pi1, proofBytes.pi2,
            proofBytes.target
        )

        components.forEach { byteArrayComponent ->
            dataOutputStream.writeInt(byteArrayComponent.size)
            dataOutputStream.write(byteArrayComponent)
        }

        dataOutputStream.close()
        return byteArrayOutputStream.toByteArray()
    }

    private fun deserializeProofBytes(bytes: ByteArray): GrothSahaiProofBytes {
        val byteArrayInputStream = ByteArrayInputStream(bytes)
        val dataInputStream = DataInputStream(byteArrayInputStream)

        val componentsList = mutableListOf<ByteArray>()
        for (i in 0 until GrothSahaiProofBytes.NUM_ELEMENTS) {
            // Read length of the next element
            val length = dataInputStream.readInt()
            if (length < 0) throw IllegalStateException("Invalid negative length for byte array component.")
            val componentBytes = ByteArray(length)
            dataInputStream.readFully(componentBytes)
            componentsList.add(componentBytes)
        }
        dataInputStream.close()

        // Ensure we've read all expected components and the stream isn't prematurely short/long
        if (componentsList.size != GrothSahaiProofBytes.NUM_ELEMENTS) {
            throw IllegalStateException("Deserialization error: Expected ${GrothSahaiProofBytes.NUM_ELEMENTS} components, but read ${componentsList.size}")
        }
        return GrothSahaiProofBytes(
            c1 = componentsList[0],
            c2 = componentsList[1],
            d1 = componentsList[2],
            d2 = componentsList[3],
            theta1 = componentsList[4],
            theta2 = componentsList[5],
            pi1 = componentsList[6],
            pi2 = componentsList[7],
            target = componentsList[8]
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