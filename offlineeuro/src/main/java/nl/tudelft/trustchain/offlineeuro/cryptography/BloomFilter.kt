package nl.tudelft.trustchain.offlineeuro.cryptography

import net.openhft.hashing.LongHashFunction
import java.util.BitSet
import kotlin.math.ln
import kotlin.math.pow
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuro

/**
 * A secure bloom filter implementation for double-spending detection in the offline euro system.
 *
 * @property expectedElements The expected number of elements to be stored in the filter
 * @property falsePositiveRate The desired false positive rate (between 0 and 1). Default is 0.01 (1%)
 * @throws IllegalArgumentException if falsePositiveRate is not between 0 and 1
 */
class BloomFilter(
    private val expectedElements: Int,
    private val falsePositiveRate: Double = 0.01
) {
    private val bitSet: BitSet
    private val numHashFunctions: Int
    private val size: Int

    init {
        require(expectedElements > 0) { "Expected elements must be greater than 0" }
        require(falsePositiveRate > 0 && falsePositiveRate < 1) { "False positive rate must be between 0 and 1" }

        // Optimal size and number of hash functions calculation
        size = calculateOptimalSize(expectedElements, falsePositiveRate)
        numHashFunctions = calculateOptimalHashFunctions(size, expectedElements)
        bitSet = BitSet(size)
    }

    /**
     * Returns the size of the bit array in bytes
     */
    fun getBitArraySize(): Int {
        return bitSet.size() / 8
    }

    /**
     * Adds a digital euro to the bloom filter using its serial number and cryptographic properties
     */
    fun add(euro: DigitalEuro) {
        val hashValues = getHashValues(euro)
        for (hash in hashValues) {
            bitSet.set(hash)
        }
    }

    /**
     * Checks if a digital euro might be in the bloom filter
     * Returns true if the euro MIGHT be in the filter (potential double-spend)
     * Returns false if the euro is definitely NOT in the filter
     */
    fun mightContain(euro: DigitalEuro): Boolean {
        val hashValues = getHashValues(euro)
        return hashValues.all { bitSet.get(it) }
    }

    /**
     * Generates hash values for a digital euro using its serial number and cryptographic properties
     */
    private fun getHashValues(euro: DigitalEuro): List<Int> {
        val hashValues = mutableListOf<Int>()
        val serialNumber = euro.serialNumber
        val firstTheta = euro.firstTheta1.toBytes()
        val signature = euro.signature.toBytes()

        val data = serialNumber.toByteArray() + firstTheta + signature

        for (i in 0 until numHashFunctions) {
            val xxHash = LongHashFunction.xx(i.toLong())
            val hash = xxHash.hashBytes(data)

            val hashValue = Math.abs(hash.toInt()) % size
            hashValues.add(hashValue)
        }

        return hashValues
    }

    /**
     * Calculates the optimal size of the bloom filter based on expected elements and false positive rate.
     * The formula used is: m = -n * ln(p) / (ln(2)^2)
     * where:
     * m = size of the filter
     * n = number of expected elements
     * p = desired false positive rate
     */
    private fun calculateOptimalSize(
        n: Int,
        p: Double
    ): Int {
        return (-n * ln(p) / (ln(2.0).pow(2))).toInt()
    }

    /**
     * Calculates the optimal number of hash functions based on size and expected elements.
     * The formula used is: k = (m/n) * ln(2)
     * where:
     * k = number of hash functions
     * m = size of the filter
     * n = number of expected elements
     */
    private fun calculateOptimalHashFunctions(
        m: Int,
        n: Int
    ): Int {
        return ((m.toDouble() / n) * ln(2.0)).toInt()
    }

    /**
     * Clears the bloom filter
     */
    fun clear() {
        bitSet.clear()
    }

    /**
     * Returns the current false positive rate of the filter.
     * This is an approximation based on the number of set bits in the filter.
     * The formula used is: (1 - e^(-kn/m))^k
     * where:
     * k = number of hash functions
     * n = number of elements added
     * m = size of the filter
     */
    fun getCurrentFalsePositiveRate(): Double {
        val setBits = bitSet.cardinality()
        val n = setBits.toDouble() / numHashFunctions // Approximate number of elements
        val k = numHashFunctions.toDouble()
        val m = size.toDouble()
        return (1 - Math.exp(-k * n / m)).pow(k)
    }
}
