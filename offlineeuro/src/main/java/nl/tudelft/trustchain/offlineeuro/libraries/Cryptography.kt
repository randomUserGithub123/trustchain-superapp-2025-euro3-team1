package nl.tudelft.trustchain.offlineeuro.libraries

import nl.tudelft.trustchain.offlineeuro.entity.RSAParameters
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import java.math.BigInteger
import java.security.SecureRandom


object Cryptography {

    fun generateRSAParameters(bitLength: Int): RSAParameters {
        val random = SecureRandom()

        val keyGenParam = RSAKeyGenerationParameters(
            BigInteger.valueOf(65537), // Public exponent e
            random,
            bitLength,
            80 // Certainty level
        )

        val keyGen = RSAKeyPairGenerator()
        keyGen.init(keyGenParam)

        val keyPair: AsymmetricCipherKeyPair = keyGen.generateKeyPair()

        val publicKey = keyPair.public as org.bouncycastle.crypto.params.RSAKeyParameters
        val privateKey = keyPair.private as org.bouncycastle.crypto.params.RSAKeyParameters

        val n = publicKey.modulus
        val e = publicKey.exponent
        val d = privateKey.exponent
        return RSAParameters(n, e, d)
    }
}
