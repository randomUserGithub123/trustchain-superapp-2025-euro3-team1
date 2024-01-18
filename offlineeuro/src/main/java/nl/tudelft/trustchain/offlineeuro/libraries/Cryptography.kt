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

    fun solve_for_gamma(w: BigInteger, u: BigInteger, y:BigInteger, d:BigInteger, p: BigInteger): BigInteger {

        val gcd_u_p1 = u.gcd(p - BigInteger.ONE)
        // Ensure that gcd(u, p - 1) divides (d - wu)
        if ((d - w * u) % gcd_u_p1 != BigInteger.ZERO) {
            return BigInteger.ZERO  // No solution for b
        }

        // Calculate the modular inverse of y modulo(p - 1)
        val y_inverse = y.modInverse(p - BigInteger.ONE)

        // Calculate b
        val gamma = (y_inverse * (d - w * u)) % (p - BigInteger.ONE)
        return gamma
    }

    fun solve_for_y(gamma: BigInteger, gamma_prime: BigInteger, d: BigInteger, d_prime: BigInteger, p : BigInteger): BigInteger {
        // Calculate the modular inverse of (b - b') modulo (p - 1)
        val gg_prime_inverse = (gamma - gamma_prime).abs().modInverse(p - BigInteger.ONE)

        //Calculate y
        val y = ((d - d_prime) * gg_prime_inverse) % (p - BigInteger.ONE)
        return y
    }
}
