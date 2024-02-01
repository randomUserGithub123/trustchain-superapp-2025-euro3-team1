package nl.tudelft.trustchain.offlineeuro.community

import nl.tudelft.trustchain.offlineeuro.entity.CentralAuthority
import nl.tudelft.trustchain.offlineeuro.entity.UnsignedTokenSignRequestEntry
import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
import org.junit.Assert
import org.junit.Test

class UnsignedTokenPayloadTest {

    @Test
    fun serializeAndDeserializeTest() {
        val listOfEntries = arrayListOf<UnsignedTokenSignRequestEntry>()

        for (i in 0 until 10) {
            val id = i.toLong()
            val a = Cryptography.generateRandomBigInteger(CentralAuthority.p)
            val c = Cryptography.generateRandomBigInteger(CentralAuthority.p)
            listOfEntries.add(UnsignedTokenSignRequestEntry(id, a, c))
        }

        val payload = UnsignedTokenPayload(listOfEntries)
        val serializedMessage = payload.serialize()
        val (deserializedMessage, _) = UnsignedTokenPayload.Deserializer.deserialize(serializedMessage, 0)
        val deserializedTokensToSign = deserializedMessage.tokensToSign

        Assert.assertEquals("The lists should have the same size", listOfEntries.size, deserializedTokensToSign.size)
        listOfEntries.sortedBy { x -> x.id }
        deserializedTokensToSign.sortedBy { x -> x.id }
        for (i in 0 until listOfEntries.size)
        {
            val expectedEntry = listOfEntries[i]
            val actualEntry = deserializedTokensToSign[i]
            Assert.assertEquals("The ids should be equal", expectedEntry.id, actualEntry.id)
            Assert.assertEquals("The a's should be equal", expectedEntry.a, actualEntry.a)
            Assert.assertEquals("The c's should be equal", expectedEntry.c, actualEntry.c)
        }

    }

}
