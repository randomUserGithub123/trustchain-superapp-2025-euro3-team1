package nl.tudelft.trustchain.offlineeuro.community

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.trustchain.offlineeuro.entity.ChallengeResponse
import nl.tudelft.trustchain.offlineeuro.entity.Token
import java.math.BigInteger

class ChallengeResponsePayload(
    val bankName: String,
    val challenges: List<ChallengeResponse>
): Serializable {
    override fun serialize(): ByteArray {

        var payload = ByteArray(0)
        payload += serializeVarLen(bankName.toByteArray())

        for (challenge in challenges) {
            payload += challenge.token.serialize()
            payload += serializeVarLen(challenge.challenge.toByteArray())
            payload += serializeVarLen(challenge.gamma.toByteArray())

        }
        return payload
    }

    companion object Deserializer : Deserializable<ChallengeResponsePayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<ChallengeResponsePayload, Int> {
            var localOffset = offset

            val (bankNameBytes, bankNameSize) = deserializeVarLen(buffer, localOffset)
            localOffset += bankNameSize

            val challenges = arrayListOf<ChallengeResponse>()

            while (localOffset < buffer.size) {

                val (token, tokenSize) = Token.deserialize(buffer, localOffset)
                localOffset += tokenSize

                val (challengeBytes, challengeSize) = deserializeVarLen(buffer, localOffset)
                localOffset += challengeSize

                val (gammaBytes, gammaSize) = deserializeVarLen(buffer, localOffset)
                localOffset += gammaSize

                val challenge = ChallengeResponse(token, BigInteger(challengeBytes), BigInteger(gammaBytes))
                challenges.add(challenge)
            }

            return Pair(
                ChallengeResponsePayload(bankNameBytes.toString(Charsets.UTF_8), challenges),
                localOffset - offset
            )
        }
    }
}
