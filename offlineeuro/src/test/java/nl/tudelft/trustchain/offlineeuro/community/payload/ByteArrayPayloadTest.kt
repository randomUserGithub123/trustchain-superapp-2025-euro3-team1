package nl.tudelft.trustchain.offlineeuro.community.payload

import org.junit.Assert
import org.junit.Test

class ByteArrayPayloadTest {

    @Test
    fun serializeAndDeserializeTest() {
        val byteArray = "NotAPublicKeyButJustSomeBytesAgain".toByteArray()

        val serializedPayload = ByteArrayPayload(byteArray).serialize()
        val deserializedPayload = ByteArrayPayload.deserialize(serializedPayload).first
        val deserializedBytes = deserializedPayload.bytes

        Assert.assertArrayEquals("The bytes should be equal", byteArray, deserializedBytes)
    }
}
