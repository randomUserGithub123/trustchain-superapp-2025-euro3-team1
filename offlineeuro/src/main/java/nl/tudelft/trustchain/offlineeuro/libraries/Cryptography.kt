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

    fun generateRandomBigInteger(rangeLow: BigInteger, rangeHigh: BigInteger): BigInteger {
        val random = SecureRandom()
        var randomNumber: BigInteger
        do {
            randomNumber = BigInteger(rangeHigh.bitLength(), random)
        } while (randomNumber >= rangeHigh || randomNumber < rangeLow)

        return randomNumber
    }

    fun generateRandomBigInteger(rangeHigh: BigInteger): BigInteger {
        return generateRandomBigInteger(BigInteger.ONE, rangeHigh)
    }
    fun solve_for_gamma(w: BigInteger, u: BigInteger, y:BigInteger, d:BigInteger, p: BigInteger): BigInteger {
        // Calculate the modular inverse of y modulo(p - 1)
        val y_inverse = y.modInverse(p - BigInteger.ONE)
        // Calculate gamma
        return (y_inverse * (d - w * u))
    }

    fun solve_for_y(gamma: BigInteger, gamma_prime: BigInteger, d: BigInteger, d_prime: BigInteger, p : BigInteger): BigInteger {

        var gg_prime_inverse = BigInteger.ZERO
        try {
            gg_prime_inverse = (gamma - gamma_prime).modInverse(p - BigInteger.ONE)
            return ((d - d_prime) * gg_prime_inverse).mod(p - BigInteger.ONE)
        } catch (e: Exception) {
            try {
                gg_prime_inverse = (gamma_prime - gamma).modInverse(p - BigInteger.ONE)
                return ((d_prime - d) * gg_prime_inverse).mod(p - BigInteger.ONE)
            } catch (e: Exception) {
                gamma_prime.modInverse(p - BigInteger.ONE)
                gamma.modInverse(p - BigInteger.ONE)
                throw Exception("Was separately invertable tho")
            }
            }
    }
    fun solve_for_w(u: BigInteger, y: BigInteger, gamma: BigInteger, d: BigInteger, p: BigInteger): BigInteger{
        // Calculate the modular inverse of y modulo(p - 1)
        val uInv = u.modInverse(p - BigInteger.ONE)

        // Calculate b
        val w = (uInv * (d - gamma * y)).mod(p - BigInteger.ONE)
        return w
    }
}
