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
    val expectedElements: Int,
    val falsePositiveRate: Double = 0.01
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

    /**
     * Approximates the number of elements currently in the Bloom filter.
     * Formula: n_approx = -m / k * ln(1 - s / m)
     * where:
     * m = size of the filter (this.size)
     * k = number of hash functions (this.numHashFunctions)
     * s = number of set bits (bitSet.cardinality())
     */
    fun getApproximateElementCount(): Double {
        val s = bitSet.cardinality().toDouble()
        val m = size.toDouble()
        val k = numHashFunctions.toDouble()

        if (s == m) return expectedElements.toDouble() // Filter is full, assume max elements
        if (s == 0.0) return 0.0 // Filter is empty

        return -m / k * ln(1.0 - s / m)
    }

    /**
     * Serializes the bloom filter to a byte array for transmission
     */
    fun toBytes(): ByteArray {
        return bitSet.toByteArray()
    }
    
    /**
     * Returns a hexadecimal string representation of the underlying BitSet for visualization.
     * Each byte of the BitSet's backing array is represented by two hexadecimal characters.
     */
    fun toHexString(): String {
        return bitSet.toByteArray().joinToString("") { "%02x".format(it) }
    }

    /**
     * Applies Algorithm 2 for sharing spent monies to update this Bloom filter.
     * This method modifies the current Bloom filter's internal state.
     *
     * @param receivedBF The BloomFilter received from another participant (F_R).
     * @param myReceivedMoniesIds A list of byte arrays representing the IDs of monies received by this participant (M).
     * @return A descriptive String message about the update outcome.
     */
    fun applyAlgorithm2Update(
        receivedBF: BloomFilter,
        myReceivedMonies: List<DigitalEuro>
    ): String {
        val currentFS = this // Previously Shared BF (F_S)
        val receivedFR = receivedBF // Received BF (F_R)
        val capacity = currentFS.expectedElements // Capacity (c)

        // 1. Create FM (Bloom filter from own monies M)
        val fm = BloomFilter(capacity, currentFS.falsePositiveRate)
        for (eur in myReceivedMonies) {
            fm.add(eur)
        }

        // 2. Compute FS_union_FM (FS_union_FM = FS U FM) - This is conceptually 'currentFS' after adding own monies
        val fsUnionFm = BloomFilter(capacity, currentFS.falsePositiveRate)
        fsUnionFm.bitSet.or(currentFS.bitSet)
        fsUnionFm.bitSet.or(fm.bitSet) // Union current FS with FM (user's own monies)

        // 3. Implement Algorithm 2's conditional logic (steps 7-14)
        val nextSharedBF: BloomFilter
        var updateMessage: String = ""

        // Calculate F_S_union_F_R: Union of (FS U FM) and FR
        val fsUnionFmUnionFr = BloomFilter(capacity, currentFS.falsePositiveRate)
        fsUnionFmUnionFr.bitSet.or(fsUnionFm.bitSet)
        fsUnionFmUnionFr.bitSet.or(receivedFR.bitSet)

        // Calculate F_M_union_F_R: Union of FM and FR
        val fmUnionFr = BloomFilter(capacity, currentFS.falsePositiveRate)
        fmUnionFr.bitSet.or(fm.bitSet)
        fmUnionFr.bitSet.or(receivedFR.bitSet)

        if (fsUnionFmUnionFr.getApproximateElementCount() <= capacity) { // Corresponds to `if |(FS U FM) U FR| <= c`
            nextSharedBF = fsUnionFmUnionFr
            updateMessage = "Bloom filter updated (Merged own knowledge, previous shared, and received filter)"
        } else if (fmUnionFr.getApproximateElementCount() <= capacity) { // Corresponds to `else if |FM U FR| <= c`
            nextSharedBF = fmUnionFr
            updateMessage = "Bloom filter updated (Reset to own knowledge and received filter)"
        } else if (currentFS.getApproximateElementCount() <= capacity) { // Corresponds to `else if |FS| <= c`
            nextSharedBF = currentFS // Keep current FS
            updateMessage = "Bloom filter updated (Kept previous shared filter, no merge possible)"
        } else { // Corresponds to `else` block
            nextSharedBF = fm // Share only own received monies (FM)
            updateMessage = "Bloom filter updated (Only sharing own monies, others too large)"
        }

        // Update the current Bloom filter's bitSet with the chosen nextSharedBF
        this.bitSet.clear()
        this.bitSet.or(nextSharedBF.bitSet)

        return updateMessage
    }

    /**
     * Creates a bloom filter from a byte array
     * @param bytes The serialized bloom filter
     * @param expectedElements The expected number of elements (must match the original filter)
     * @param falsePositiveRate The false positive rate (must match the original filter)
     */
    companion object {
        fun fromBytes(
            bytes: ByteArray,
            expectedElements: Int,
            falsePositiveRate: Double = 0.01
        ): BloomFilter {
            val filter = BloomFilter(expectedElements, falsePositiveRate)
            filter.bitSet.clear()
            filter.bitSet.or(BitSet.valueOf(bytes))
            return filter
        }
    }
}
