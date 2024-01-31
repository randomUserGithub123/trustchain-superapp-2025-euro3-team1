package nl.tudelft.trustchain.offlineeuro.community

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.trustchain.offlineeuro.entity.UserRegistrationMessage
import java.math.BigInteger

class UserRegistrationPayload (
    val userRegistrationMessage: UserRegistrationMessage,
): Serializable {
    override fun serialize(): ByteArray {

        var payload = ByteArray(0)
        payload += serializeVarLen(userRegistrationMessage.userName.toByteArray())
        payload += serializeVarLen(userRegistrationMessage.i.first.toByteArray())
        payload += serializeVarLen(userRegistrationMessage.i.second.toByteArray())
        payload += serializeVarLen(userRegistrationMessage.arm.toByteArray())
        return payload
    }

    companion object Deserializer : Deserializable<UserRegistrationPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<UserRegistrationPayload, Int> {
            var localOffset = offset

            val (nameBytes, nameSize) = deserializeVarLen(buffer, localOffset)
            localOffset += nameSize
            val (firstIByes, firstIBytesSize) = deserializeVarLen(buffer, localOffset)
            localOffset += firstIBytesSize
            val (secondIBytes, secondIBytesSize) = deserializeVarLen(buffer, localOffset)
            localOffset += secondIBytesSize
            val (armBytes, armSize) = deserializeVarLen(buffer, localOffset)
            localOffset += armSize

            val i = Pair(BigInteger(firstIByes), BigInteger(secondIBytes))
            val payload = UserRegistrationMessage(
                nameBytes.toString(Charsets.UTF_8),
                i,
                BigInteger(armBytes)
            )

            return Pair(
                UserRegistrationPayload(payload),
                localOffset - offset
            )
        }
    }
}
