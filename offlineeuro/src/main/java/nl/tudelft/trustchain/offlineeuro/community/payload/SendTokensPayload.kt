package nl.tudelft.trustchain.offlineeuro.community.payload//package nl.tudelft.trustchain.offlineeuro.community
//
//import nl.tudelft.ipv8.messaging.Deserializable
//import nl.tudelft.ipv8.messaging.Serializable
//import nl.tudelft.ipv8.messaging.deserializeVarLen
//import nl.tudelft.ipv8.messaging.serializeVarLen
//
//class SendTokensPayload (
//    val bankName: String,
//    //val tokens: List<Token>
//): Serializable {
//    override fun serialize(): ByteArray {
//
//        var payload = ByteArray(0)
//        payload += serializeVarLen(bankName.toByteArray())
//        for (token in tokens) {
//            payload += token.serialize()
//        }
//
//        return payload
//    }
//
//    companion object Deserializer : Deserializable<SendTokensPayload> {
//        override fun deserialize(
//            buffer: ByteArray,
//            offset: Int
//        ): Pair<SendTokensPayload, Int> {
//
//            var localOffset = offset
//            //val tokens = arrayListOf<Token>()
//
//            val (bankNameBytes, bankNameSize) = deserializeVarLen(buffer, localOffset)
//            localOffset += bankNameSize
//            while (localOffset < buffer.size) {
//                val (token, tokenBytes) = Token.deserialize(buffer, localOffset)
//                localOffset += tokenBytes
//                tokens.add(token)
//            }
//
//
//            return Pair(
//                SendTokensPayload(bankNameBytes.toString(Charsets.UTF_8), tokens),
//                localOffset - offset
//            )
//        }
//    }
//}
