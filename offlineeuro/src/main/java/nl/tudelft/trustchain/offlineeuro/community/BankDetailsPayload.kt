package nl.tudelft.trustchain.offlineeuro.community

class BankDetailsPayload ()
//): Serializable {
//    override fun serialize(): ByteArray {
//
//        var payload = ByteArray(0)
//        payload += serializeVarLen(bankDetails.name.toByteArray())
//        payload += serializeVarLen(bankDetails.z.toByteArray())
//        payload += serializeVarLen(bankDetails.eb.toByteArray())
//        payload += serializeVarLen(bankDetails.nb.toByteArray())
//        payload += serializeVarLen(bankDetails.publicKeyBytes)
//        return payload
//    }
//
//    companion object Deserializer : Deserializable<BankDetailsPayload> {
//        override fun deserialize(
//            buffer: ByteArray,
//            offset: Int
//        ): Pair<BankDetailsPayload, Int> {
//            var localOffset = offset
//
//            val (nameBytes, nameSize) = deserializeVarLen(buffer, localOffset)
//            localOffset += nameSize
//            val (zBytes, zSize) = deserializeVarLen(buffer, localOffset)
//            localOffset += zSize
//            val (ebBytes, ebSize) = deserializeVarLen(buffer, localOffset)
//            localOffset += ebSize
//            val (nbBytes, nbSize) = deserializeVarLen(buffer, localOffset)
//            localOffset += nbSize
//
//            val (publicKeyBytes, publicKeySize) = deserializeVarLen(buffer, localOffset)
//            localOffset += publicKeySize
//
//            val payload = BankDetails(
//                nameBytes.toString(Charsets.UTF_8),
//                BigInteger(zBytes),
//                BigInteger(ebBytes),
//                BigInteger(nbBytes),
//                publicKeyBytes
//            )
//
//
//            return Pair(
//                BankDetailsPayload(payload),
//                localOffset - offset
//            )
//        }
//    }
//}
