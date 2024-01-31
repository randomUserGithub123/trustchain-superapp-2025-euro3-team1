package nl.tudelft.trustchain.offlineeuro.community

import nl.tudelft.trustchain.offlineeuro.entity.MessageResult
import nl.tudelft.trustchain.offlineeuro.entity.UserRegistrationResponseMessage
import org.junit.Assert
import org.junit.Test
import java.math.BigInteger

class UserRegistrationResponsePayloadTest {

    @Test
    fun serializeAndDeserializeTest() {
        val result = MessageResult.SuccessFul
        val name = "TheBestBankAround"
        val v = BigInteger("123456123412")
        val r = BigInteger("1235123421452138213")
        val errorMessage = "ActuallyNotAnErrorMessage"
        val reply = UserRegistrationResponseMessage(result, name, v, r, errorMessage)

        val payload = UserRegistrationResponsePayload(reply)
        val serializedMessage = payload.serialize()
        val (deserializedMessage, _) = UserRegistrationResponsePayload.Deserializer.deserialize(serializedMessage, 0)
        val response = deserializedMessage.userRegistrationResponseMessage
        Assert.assertEquals("The data should be unchanged", result, response.result)
        Assert.assertEquals("The data should be unchanged", name, response.bankName)
        Assert.assertEquals("The data should be unchanged", v, response.v)
        Assert.assertEquals("The data should be unchanged", r, response.r)
        Assert.assertEquals("The data should be unchanged", errorMessage, response.errorMessage)
    }
}
