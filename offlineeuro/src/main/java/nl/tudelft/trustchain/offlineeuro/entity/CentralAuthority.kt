package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element
import java.math.BigInteger
import java.security.MessageDigest

object CentralAuthority {

    val groupDescription: BilinearGroup = BilinearGroup()
    private val CRSPair = CRSGenerator.generateCRSMap(groupDescription)
    private val crsMap = CRSPair.second
    val crs = CRSPair.first

    val p: BigInteger = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129870127")
    val q: BigInteger = findQ(p)

    val registeredUsers = mutableMapOf<Element, Element>()

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

    fun registerUser(): Pair<Element, Element> {
        var secretKey = groupDescription.pairing.zr.newRandomElement().immutable
        var publicKey = groupDescription.g.duplicate().powZn(secretKey).immutable

        while (registeredUsers.containsKey(publicKey)) {
            secretKey = groupDescription.pairing.zr.newRandomElement().immutable
            publicKey = groupDescription.g.duplicate().powZn(secretKey).immutable
        }
        registeredUsers[publicKey] = secretKey
        return Pair(secretKey, publicKey)
    }

    fun getUserFromProof(grothSahaiProof: GrothSahaiProof): Element? {
        val crsExponent = crsMap[crs.u]
        val publicKey = grothSahaiProof.c1.powZn(crsExponent!!.mul(-1)).mul(grothSahaiProof.c2).immutable

        for (knownPublicKey in registeredUsers.keys) {
            if (knownPublicKey == publicKey) {
                return publicKey
            }
        }

        return if (registeredUsers.keys.contains(publicKey))
            publicKey
        else {
            null
        }
    }

    fun getUserFromProofs(proofs: Pair<GrothSahaiProof, GrothSahaiProof>) : Element? {
        val firstPK = getUserFromProof(proofs.first)
        val secondPK = getUserFromProof(proofs.second)

        return if (firstPK == secondPK)
            firstPK
        else
            null
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
