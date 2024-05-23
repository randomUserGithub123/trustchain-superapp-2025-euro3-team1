package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.SERIALIZED_LONG_SIZE
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeLong
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeLong
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.trustchain.offlineeuro.enums.Role

class AddressPayload(
    val userName: String,
    val publicKey: ByteArray,
    val role: Role
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(userName.toByteArray())
        payload += serializeVarLen(publicKey)
        payload += serializeLong(role.ordinal.toLong())
        return payload
    }

    companion object Deserializer : Deserializable<AddressPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<AddressPayload, Int> {
            var localOffset = offset

            val (nameBytes, nameSize) = deserializeVarLen(buffer, localOffset)
            localOffset += nameSize
            val (publicKeyBytes, publicKeyBytesSize) = deserializeVarLen(buffer, localOffset)
            localOffset += publicKeyBytesSize

            val role = deserializeLong(buffer, localOffset)
            localOffset += SERIALIZED_LONG_SIZE

            return Pair(
                AddressPayload(
                    nameBytes.toString(Charsets.UTF_8),
                    publicKeyBytes,
                    Role.fromLong(role)
                ),
                localOffset - offset
            )
        }
    }
}
