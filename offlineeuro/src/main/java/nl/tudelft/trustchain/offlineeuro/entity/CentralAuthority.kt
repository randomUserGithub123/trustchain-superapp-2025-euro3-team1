package nl.tudelft.trustchain.offlineeuro.entity

import java.math.BigInteger
import java.security.MessageDigest

object CentralAuthority {
    val p: BigInteger = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129870127")
    var q: BigInteger
    // Current assumption is that 5 is a primitive root of p, squared makes 25
    val alpha = BigInteger("25")
    init {
        q = findQ(p)
    }

    private fun findQ(prime: BigInteger): BigInteger {
        // Certainty is calculated as  (1 – (1/2) ^ certainty).
        if (!prime.isProbablePrime(10)) {
            return BigInteger("-1")
        }

        q = (prime - BigInteger.ONE)/ BigInteger("2")
        if (q.isProbablePrime(10)) {
            return q
        }
        return BigInteger("-1")
    }

    // Three published hash functions
    fun H(x: BigInteger, y: BigInteger, z: BigInteger, q: BigInteger): BigInteger {
        val data = "$x,$y,$z".toByteArray(Charsets.UTF_8)
        return calculateHash(data) % q
    }

    fun H0(a: BigInteger, b: BigInteger, c: BigInteger, d: String, q: BigInteger): BigInteger {
        val data = "$a,$b,$c,$d".toByteArray(Charsets.UTF_8)
        return calculateHash(data) % q
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
