package nl.tudelft.trustchain.offlineeuro.community

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.trustchain.offlineeuro.entity.Receipt
import nl.tudelft.trustchain.offlineeuro.entity.Token
import java.math.BigInteger

class DepositPayload (
    val userName: String,
    val receipts: List<Receipt>
): Serializable {
    override fun serialize(): ByteArray {

        var payload = ByteArray(0)
        payload += serializeVarLen(userName.toByteArray())
        for (receipt in receipts) {
            payload += receipt.token.serialize()
            payload += serializeVarLen(receipt.gamma.toByteArray())
            payload += serializeVarLen(receipt.challenge.toByteArray())
        }

        return payload
    }

    companion object Deserializer : Deserializable<DepositPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<DepositPayload, Int> {

            var localOffset = offset
            val receipts = arrayListOf<Receipt>()

            val (userNameBytes, userNameSize) = deserializeVarLen(buffer, localOffset)
            localOffset += userNameSize

            while (localOffset < buffer.size) {
                val (token, tokenBytes) = Token.deserialize(buffer, localOffset)
                localOffset += tokenBytes

                val (gammaBytes, gammaSize) = deserializeVarLen(buffer, localOffset)
                localOffset += gammaSize

                val (challengeBytes, challengeSize) = deserializeVarLen(buffer, localOffset)
                localOffset += challengeSize

                val receipt = Receipt(token, BigInteger(gammaBytes), BigInteger(challengeBytes))
                receipts.add(receipt)
            }


            return Pair(
                DepositPayload(userNameBytes.toString(Charsets.UTF_8), receipts),
                localOffset - offset
            )
        }
    }
}
