//package nl.tudelft.trustchain.offlineeuro.community
//
//import nl.tudelft.trustchain.offlineeuro.entity.UserRegistrationMessage
//import org.junit.Assert
//import org.junit.Test
//import java.math.BigInteger
//
//class UserRegistrationPayloadTest {
//
//    @Test
//    fun serializeAndDeserializeTest() {
//        val name = "ThisIsAUserName"
//        val firstI = BigInteger("123456123412")
//        val secondI = BigInteger("1235123421452138213")
//        val i = Pair(firstI, secondI)
//        val arm = BigInteger("7452304732312356124321")
//        val userRegistrationMessage = UserRegistrationMessage(name, i, arm)
//
//        val payload = UserRegistrationPayload(userRegistrationMessage)
//        val serializedMessage = payload.serialize()
//        val (deserializedMessage, _) = UserRegistrationPayload.Deserializer.deserialize(serializedMessage, 0)
//        val deserializedUserMessage = deserializedMessage.userRegistrationMessage
//
//        Assert.assertEquals("The data should be unchanged", name, deserializedUserMessage.userName)
//        Assert.assertEquals("The data should be unchanged", firstI, deserializedUserMessage.i.first)
//        Assert.assertEquals("The data should be unchanged", secondI, deserializedUserMessage.i.second)
//        Assert.assertEquals("The data should be unchanged", arm, deserializedUserMessage.arm)
//    }
//}
