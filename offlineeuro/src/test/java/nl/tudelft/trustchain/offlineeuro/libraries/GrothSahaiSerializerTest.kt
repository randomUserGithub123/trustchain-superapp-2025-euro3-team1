package nl.tudelft.trustchain.offlineeuro.libraries

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.entity.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.entity.GrothSahaiProof
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
        val deserializedBytes = GrothSahaiSerializer.deserializeProofBytes(proofListBytes, group)
        Assert.assertEquals(proofList, deserializedBytes)
    }

    @Test
    fun serializeAndDeserializeSingleProofListTest() {
        val proofList = arrayListOf(generateRandomInvalidProof())
        val proofListBytes = GrothSahaiSerializer.serializeGrothSahaiProofs(proofList)
        val deserializedBytes = GrothSahaiSerializer.deserializeProofBytes(proofListBytes, group)
        Assert.assertEquals(proofList, deserializedBytes)
    }


    @Test
    fun serializeAndDeserializeTwoProofsTest() {
        val proofList = arrayListOf(generateRandomInvalidProof(),  generateRandomInvalidProof())
        val proofListBytes = GrothSahaiSerializer.serializeGrothSahaiProofs(proofList)
        val deserializedBytes = GrothSahaiSerializer.deserializeProofBytes(proofListBytes, group)
        Assert.assertEquals(proofList, deserializedBytes)
    }

    @Test
    fun serializeAndDeserializeManyProofsTest() {
        val proofList = arrayListOf<GrothSahaiProof>()

        for (i in 0 until 9) {
            proofList.add(generateRandomInvalidProof())
        }
        val proofListBytes = GrothSahaiSerializer.serializeGrothSahaiProofs(proofList)
        val deserializedBytes = GrothSahaiSerializer.deserializeProofBytes(proofListBytes, group)
        Assert.assertEquals(proofList, deserializedBytes)
    }

    @Test(expected = StreamCorruptedException::class)
    fun deserializeInvalidTest() {
        val invalidBytes = "TheseAreInvalidBytesToDeserialize".toByteArray()
        val deserializedBytes = GrothSahaiSerializer.deserializeProofBytes(invalidBytes, group)
    }
}
