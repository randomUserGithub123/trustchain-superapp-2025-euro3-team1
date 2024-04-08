//package nl.tudelft.trustchain.offlineeuro.community
//
//import nl.tudelft.trustchain.offlineeuro.entity.CentralAuthority
//import nl.tudelft.trustchain.offlineeuro.entity.Receipt
//import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
//import org.junit.Assert
//import org.junit.Test
//
//class DepositPayloadTest {
//    @Test
//    fun serializeAndDeserializeTest() {
//        val userName = "TestUser"
//        val listOfReceipts = arrayListOf<Receipt>()
//
//        // Generate 10 random receipts
//        for (i in 0 until 10) {
//            val token = Token(
//                Cryptography.generateRandomBigInteger(CentralAuthority.p),
//                Cryptography.generateRandomBigInteger(CentralAuthority.p),
//                Cryptography.generateRandomBigInteger(CentralAuthority.p),
//                Cryptography.generateRandomBigInteger(CentralAuthority.p),
//                Cryptography.generateRandomBigInteger(CentralAuthority.p),
//                "Not really a random string number $i",
//            )
//            val gamma = Cryptography.generateRandomBigInteger(CentralAuthority.p)
//            val challenge = Cryptography.generateRandomBigInteger(CentralAuthority.p)
//            val receipt = Receipt(token, gamma, challenge)
//            listOfReceipts.add(receipt)
//        }
//
//        val payload = DepositPayload(userName, listOfReceipts)
//        val serializedMessage = payload.serialize()
//        val (deserializedMessage, _) = DepositPayload.Deserializer.deserialize(serializedMessage, 0)
//        val deserializedReceipts = deserializedMessage.receipts
//
//        Assert.assertEquals("The username should be correct", userName, deserializedMessage.userName)
//        Assert.assertEquals("The lists should have the same size", listOfReceipts.size, deserializedReceipts.size)
//        listOfReceipts.sortedBy { x -> x.token.t }
//        deserializedReceipts.sortedBy { x -> x.token.t }
//
//        for (i in 0 until listOfReceipts.size)
//        {
//            val expectedEntry = listOfReceipts[i]
//            val actualEntry = deserializedReceipts[i]
//            Assert.assertEquals("The tokens should be equal", expectedEntry.token, actualEntry.token)
//            Assert.assertEquals("The gamma's should be equal", expectedEntry.gamma, actualEntry.gamma)
//            Assert.assertEquals("The challenges should be equal", expectedEntry.challenge, actualEntry.challenge)
//        }
//
//    }
//}
