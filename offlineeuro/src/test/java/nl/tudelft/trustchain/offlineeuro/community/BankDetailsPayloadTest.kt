//package nl.tudelft.trustchain.offlineeuro.community
//
//import nl.tudelft.trustchain.offlineeuro.entity.BankDetails
//import org.junit.Assert
//import org.junit.Test
//import java.math.BigInteger
//
//class BankDetailsPayloadTest {
//
//    @Test
//    fun serializeAndDeserializeTest() {
//        val name = "TheBestBankAround"
//        val z = BigInteger("123456123412")
//        val eb = BigInteger("1235123421452138213")
//        val nb = BigInteger("32321298052132132131245213512312")
//        val publicKey = "ActuallyNotAPublicKeyButJustAByteArray".toByteArray()
//        val bankDetails = BankDetails(name, z, eb, nb, publicKey)
//
//        val payload = BankDetailsPayload(bankDetails)
//        val serializedMessage = payload.serialize()
//        val (deserializedMessage, _) = BankDetailsPayload.Deserializer.deserialize(serializedMessage, 0)
//        val deserializedDetails = deserializedMessage.bankDetails
//        Assert.assertEquals("The data should be unchanged", name, deserializedDetails.name)
//        Assert.assertEquals("The data should be unchanged", z, deserializedDetails.z)
//        Assert.assertEquals("The data should be unchanged", eb, deserializedDetails.eb)
//        Assert.assertEquals("The data should be unchanged", nb, deserializedDetails.nb)
//        Assert.assertArrayEquals("The data should be unchanged", publicKey, deserializedDetails.publicKeyBytes)
//    }
//}
