package nl.tudelft.trustchain.offlineeuro.community

import org.junit.Assert
import org.junit.Test

class FindBankPayloadTest {

    @Test
    fun serializeAndDeserializeTest() {
        val name = "SomeUser"
        val role = Role.User

        val payload = FindBankPayload(name, role)
        val serializedMessage = payload.serialize()
        val (deserializedMessage, _) = FindBankPayload.Deserializer.deserialize(serializedMessage, 0)
        Assert.assertEquals("The data should be unchanged", name, deserializedMessage.name)
        Assert.assertEquals("The data should be unchanged", Role.User, deserializedMessage.role)
    }
}
