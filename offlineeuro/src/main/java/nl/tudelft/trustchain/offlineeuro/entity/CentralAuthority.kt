package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger
import java.security.MessageDigest

object CentralAuthority {

    val p: BigInteger = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129870127")
    val q: BigInteger = findQ(p)

    // Current assumption is that 5 is a primitive root of p
    val alpha: BigInteger = BigInteger("5").pow(2)

    private fun findQ(prime: BigInteger): BigInteger {

        // Certainty is calculated as  (1 – (1/2) ^ certainty).
        if (!prime.isProbablePrime(10)) {
            throw Exception("The given value is not a prime. Pick a different value")
        }

        val potentialQ = (prime - BigInteger.ONE)/ BigInteger("2")
        if (!potentialQ.isProbablePrime(10)) {
            throw Exception("q is not prime, pick a different p")
        }
        return potentialQ
    }

    // Three published hash functions
    fun H(x: BigInteger, y: BigInteger, z: BigInteger): BigInteger {
        val data = "$x,$y,$z".toByteArray(Charsets.UTF_8)
        return calculateHash(data).mod(q)
    }

    fun H0(a: BigInteger, b: BigInteger, c: BigInteger, d: String): BigInteger {
        val data = "$a,$b,$c,$d".toByteArray(Charsets.UTF_8)
        return calculateHash(data).mod(q)
    }

    fun H1(x: String): BigInteger {
        val data = x.toByteArray(Charsets.UTF_8)
        return calculateHash(data)
    }

    private fun calculateHash(data: ByteArray): BigInteger {
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = sha256Digest.digest(data)
        val hexString = hashBytes.joinToString("") { "%02x".format(it) }
        return BigInteger(hexString, 16)
    }
}
