//package nl.tudelft.trustchain.offlineeuro.community
//
//import nl.tudelft.ipv8.messaging.Deserializable
//import nl.tudelft.ipv8.messaging.Serializable
//import nl.tudelft.ipv8.messaging.deserializeLong
//import nl.tudelft.ipv8.messaging.deserializeVarLen
//import nl.tudelft.ipv8.messaging.serializeLong
//import nl.tudelft.ipv8.messaging.serializeVarLen
//import nl.tudelft.trustchain.offlineeuro.entity.MessageResult
//import nl.tudelft.trustchain.offlineeuro.entity.UserRegistrationResponseMessage
//import java.math.BigInteger
//
//class UserRegistrationResponsePayload (
//    val userRegistrationResponseMessage: UserRegistrationResponseMessage,
//): Serializable {
//    override fun serialize(): ByteArray {
//
//        var payload = ByteArray(0)
//        payload += serializeVarLen(userRegistrationResponseMessage.bankName.toByteArray())
//        payload += serializeVarLen(userRegistrationResponseMessage.v.toByteArray())
//        payload += serializeVarLen(userRegistrationResponseMessage.r.toByteArray())
//        payload += serializeVarLen(userRegistrationResponseMessage.errorMessage.toByteArray())
//        payload += serializeLong(userRegistrationResponseMessage.result.ordinal.toLong())
//        return payload
//    }
//
//    companion object Deserializer : Deserializable<UserRegistrationResponsePayload> {
//        override fun deserialize(
//            buffer: ByteArray,
//            offset: Int
//        ): Pair<UserRegistrationResponsePayload, Int> {
//            var localOffset = offset
//
//            val (nameBytes, nameSize) = deserializeVarLen(buffer, localOffset)
//            localOffset += nameSize
//            val (vBytes, vBytesSize) = deserializeVarLen(buffer, localOffset)
//            localOffset += vBytesSize
//            val (rBytes, rBytesSize) = deserializeVarLen(buffer, localOffset)
//            localOffset += rBytesSize
//            val (errorMessageBytes, errorMessageSize) = deserializeVarLen(buffer, localOffset)
//            localOffset += errorMessageSize
//
//            val resultInt = deserializeLong(buffer, localOffset).toInt()
//            val result = MessageResult.fromInt(resultInt)
//
//            val payload = UserRegistrationResponseMessage(
//                result,
//                nameBytes.toString(Charsets.UTF_8),
//                BigInteger(vBytes),
//                BigInteger(rBytes),
//                errorMessageBytes.toString(Charsets.UTF_8)
//            )
//
//            return Pair(
//                UserRegistrationResponsePayload(payload),
//                localOffset - offset
//            )
//        }
//    }
//}
