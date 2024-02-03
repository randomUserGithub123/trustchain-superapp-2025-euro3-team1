package nl.tudelft.trustchain.offlineeuro.community

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.trustchain.offlineeuro.entity.Challenge
import nl.tudelft.trustchain.offlineeuro.entity.Token
import java.math.BigInteger

class ChallengePayload (
    val bankName: String,
    val challenges: List<Challenge>
): Serializable {
    override fun serialize(): ByteArray {

        var payload = ByteArray(0)
        payload += serializeVarLen(bankName.toByteArray())

        for (challenge in challenges) {
            payload += challenge.token.serialize()
            payload += serializeVarLen(challenge.challenge.toByteArray())

        }
        return payload
    }

    companion object Deserializer : Deserializable<ChallengePayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<ChallengePayload, Int> {
            var localOffset = offset

            val (bankNameBytes, bankNameSize) = deserializeVarLen(buffer, localOffset)
            localOffset += bankNameSize

            val challenges = arrayListOf<Challenge>()

            while (localOffset < buffer.size) {

                val (token, tokenSize) = Token.deserialize(buffer, localOffset)
                localOffset += tokenSize

                val (challengeBytes, challengeSize) = deserializeVarLen(buffer, localOffset)
                localOffset += challengeSize

                val challenge = Challenge(token, BigInteger(challengeBytes))
                challenges.add(challenge)
            }

            return Pair(
                ChallengePayload(bankNameBytes.toString(Charsets.UTF_8), challenges),
                localOffset - offset
            )
        }
    }
}
