package nl.tudelft.trustchain.offlineeuro.libraries

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import org.junit.Assert
import org.junit.Test
import java.io.StreamCorruptedException

class GrothSahaiSerializerTest {
    private val group = BilinearGroup()

    private fun randomGElement(): Element {
        return group.generateRandomElementOfG()
    }

    private fun randomHElement(): Element {
        return group.generateRandomElementOfH()
    }

    private fun randomGTElement(): Element {
        return group.generateRandomElementOfGT()
    }

    private fun generateRandomInvalidProof(): GrothSahaiProof {
        return GrothSahaiProof(
            randomGElement(),
            randomGElement(),
            randomHElement(),
            randomHElement(),
            randomGElement(),
            randomGElement(),
            randomHElement(),
            randomHElement(),
            randomGTElement()
        )
    }

    @Test
    fun serializeAndDeserializeEmptyListTest() {
        val proofList = arrayListOf<GrothSahaiProof>()
        val proofListBytes = GrothSahaiSerializer.serializeGrothSahaiProofs(proofList)
        Assert.assertNull(proofListBytes)
    }

    @Test
    fun serializeSingleProofTest() {
        val proof = generateRandomInvalidProof()
        val bytes = GrothSahaiSerializer.serializeGrothSahaiProof(proof)
        val deserialized = GrothSahaiSerializer.deserializeProofBytes(bytes, group)
        Assert.assertEquals(proof, deserialized)
    }

    @Test
    fun serializeAndDeserializeNullListTest() {
        val proofListBytes = GrothSahaiSerializer.serializeGrothSahaiProofs(null)
        Assert.assertNull(proofListBytes)
    }

    @Test
    fun serializeWithoutCompressionInlineTest() {
        val proof = generateRandomInvalidProof()
        val bytes =
            GrothSahaiSerializer.serializeGrothSahaiProof(
                proof,
                useCompression = false
            )
        val deserialized = GrothSahaiSerializer.deserializeProofBytes(bytes, group)
        Assert.assertEquals(proof, deserialized)
    }

    @Test
    fun serializeWithoutCompressionMultilineTest() {
        val proof = generateRandomInvalidProof()
        val bytes =
            GrothSahaiSerializer.serializeGrothSahaiProof(
                proof,
                useCompression = false
            )
        val deserialized =
            GrothSahaiSerializer.deserializeProofBytes(
                bytes,
                group
            )
        Assert.assertEquals(proof, deserialized)
    }

    @Test(expected = StreamCorruptedException::class)
    fun deserializeEmptyBytesTest() {
        GrothSahaiSerializer.deserializeProofBytes(ByteArray(0), group)
    }

    @Test(expected = StreamCorruptedException::class)
    fun deserializeInvalidFlagTest() {
        val invalidBytes = byteArrayOf(99) + "InvalidData".toByteArray()
        GrothSahaiSerializer.deserializeProofBytes(invalidBytes, group)
    }

    @Test
    fun deserializeEmptyListBytesTest() {
        val result = GrothSahaiSerializer.deserializeProofListBytes(byteArrayOf(), group)
        Assert.assertTrue(result.isEmpty())
    }

    @Test
    fun verifyCompressionEffectivenessTest() {
        // Create multiple proofs to get better compression statistics
        val proofs = List(5) { generateRandomInvalidProof() }
        var totalUncompressed = 0
        var totalCompressed = 0

        proofs.forEachIndexed { index, proof ->
            val uncompressedBytes = GrothSahaiSerializer.serializeGrothSahaiProof(
                proof,
                useCompression = false
            )
            val compressedBytes = GrothSahaiSerializer.serializeGrothSahaiProof(
                proof,
                useCompression = true
            )

            totalUncompressed += uncompressedBytes.size
            totalCompressed += compressedBytes.size

            println("\nProof #${index + 1}:")
            println("Original size: ${uncompressedBytes.size} bytes")
            println("Compressed size: ${compressedBytes.size} bytes")
            println("Compression ratio: ${String.format("%.2f", (compressedBytes.size.toFloat() / uncompressedBytes.size.toFloat()) * 100)}%")

            // Verify data integrity
            val decompressedProof = GrothSahaiSerializer.deserializeProofBytes(compressedBytes, group)
            Assert.assertEquals("Decompressed data should match original", proof, decompressedProof)
        }

        println("\nOverall Statistics:")
        println("Total uncompressed: $totalUncompressed bytes")
        println("Total compressed: $totalCompressed bytes")
        println("Average compression ratio: ${String.format("%.2f", (totalCompressed.toFloat() / totalUncompressed.toFloat()) * 100)}%")

        // Test passes if compression works OR if data size stays the same
        Assert.assertTrue(
            "Compressed data should not be larger than original",
            totalCompressed <= totalUncompressed
        )
    }
}
