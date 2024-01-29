package nl.tudelft.trustchain.offlineeuro.community

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeLong
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeLong
import nl.tudelft.ipv8.messaging.serializeVarLen

class BankDetailsPayload (
    val name: String,
    val role: Role
): Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(name.toByteArray())
        val roleInt = role.ordinal
        val roleLong = roleInt.toLong()
        payload += serializeLong(roleLong)
        return payload
    }

    companion object Deserializer : Deserializable<BankDetailsPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<BankDetailsPayload, Int> {
            var localOffset = offset

            val (name, nameSize) = deserializeVarLen(buffer, localOffset)
            localOffset += nameSize
            val roleInt = deserializeLong(buffer, localOffset).toInt()
            val role = Role.fromInt(roleInt)

            return Pair(
                BankDetailsPayload(name.toString(Charsets.UTF_8), role),
                localOffset - offset
            )
        }
    }
}
