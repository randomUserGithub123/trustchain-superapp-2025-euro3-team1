//package nl.tudelft.trustchain.offlineeuro.community
//
//import nl.tudelft.trustchain.offlineeuro.entity.CentralAuthority
//import nl.tudelft.trustchain.offlineeuro.entity.UnsignedTokenSignResponseEntry
//import nl.tudelft.trustchain.offlineeuro.entity.UnsignedTokenStatus
//import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
//import org.junit.Assert
//import org.junit.Test
//
//class UnsignedTokenResponsePayloadTest {
//
//    @Test
//    fun serializeAndDeserializeTest() {
//        val bankName = "BestTestBank"
//        val listOfEntries = arrayListOf<UnsignedTokenSignResponseEntry>()
//
//        for (i in 0 until 10) {
//            val id = i.toLong()
//            val a = Cryptography.generateRandomBigInteger(CentralAuthority.p)
//            val c = Cryptography.generateRandomBigInteger(CentralAuthority.p)
//            val t = "JustSomeBytes $i"
//            val status = UnsignedTokenStatus.fromInt(i % 3)
//            listOfEntries.add(UnsignedTokenSignResponseEntry(id, a, c, t, status))
//        }
//
//        val payload = UnsignedTokenResponsePayload(bankName, listOfEntries)
//        val serializedMessage = payload.serialize()
//        val (deserializedMessage, _) = UnsignedTokenResponsePayload.Deserializer.deserialize(serializedMessage, 0)
//        val deserializedSignedTokens = deserializedMessage.signedTokens
//
//        Assert.assertEquals("The bankName should be correct", bankName, deserializedMessage.bankName)
//        Assert.assertEquals("The lists should have the same size", listOfEntries.size, deserializedSignedTokens.size)
//        listOfEntries.sortedBy { x -> x.id }
//        deserializedSignedTokens.sortedBy { x -> x.id }
//        for (i in 0 until listOfEntries.size)
//        {
//            val expectedEntry = listOfEntries[i]
//            val actualEntry = deserializedSignedTokens[i]
//            Assert.assertEquals("The ids should be equal", expectedEntry.id, actualEntry.id)
//            Assert.assertEquals("The a's should be equal", expectedEntry.aPrime, actualEntry.aPrime)
//            Assert.assertEquals("The c's should be equal", expectedEntry.cPrime, actualEntry.cPrime)
//            Assert.assertEquals("The t's should be equal", expectedEntry.t, actualEntry.t)
//            Assert.assertEquals("The statuses should be equal", expectedEntry.status, actualEntry.status)
//        }
//
//    }
//}
