package nl.tudelft.trustchain.offlineeuro.cryptography

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuro
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.io.File
import java.math.BigInteger

class BloomFilterPerformanceTest {
    private lateinit var group: BilinearGroup
    private lateinit var mockElement: Element
    private lateinit var signature: SchnorrSignature
    private lateinit var depositedEuroManager: DepositedEuroManager
    private val resultsDir = File("test-results")
    private val testSizes = listOf(1000, 5000, 10000, 25000, 50000, 100000)
    private val numTrials = 100 // Number of trials for each test size

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        group = BilinearGroup()
        mockElement = mock(Element::class.java)
        // placeholder values for signature
        signature =
            SchnorrSignature(
                BigInteger("11111111111111111111111111111111"),
                BigInteger("22222222222222222222222222222222"),
                "SchnorrSignatureTest".toByteArray()
            )
        // depositedEuroManager = mock(DepositedEuroManager::class.java)
        resultsDir.mkdirs()

        // Setup mock behavior
        `when`(mockElement.toBytes()).thenReturn(ByteArray(32))
        // `when`(mockSignature.toBytes()).thenReturn(ByteArray(64))
        // `when`(mockSignature.signedMessage).thenReturn(ByteArray(32))
        // `when`(SchnorrSignatureSerializer.serializeSchnorrSignature(any())).thenReturn(ByteArray(64))
        // `when`(GrothSahaiSerializer.serializeGrothSahaiProofs(any())).thenReturn(ByteArray(32))
        // `when`(depositedEuroManager.getDigitalEurosByDescriptor(any(DigitalEuro::class.java))).thenReturn(emptyList())
    }

    @Test
    fun testMemoryUsage() {
        val results = mutableListOf<String>()
        results.add("Size,OldMethodMemory(bytes),BloomFilterMemory(bytes)")

        for (size in testSizes) {
            // Calculate memory for old method using actual DigitalEuro size
            val sampleEuro = createMockDigitalEuro()
            val oldMethodMemory = sampleEuro.sizeInBytes() * size

            // Calculate memory for Bloom filter
            val bloomFilter = BloomFilter(size)
            val bloomFilterMemory = bloomFilter.getBitArraySize()

            results.add("$size,$oldMethodMemory,$bloomFilterMemory")
        }

        writeResults("memory_usage.csv", results)
    }

    // @Test
    // fun testTransactionProcessingTime() {
    //     val results = mutableListOf<String>()
    //     results.add("Size,OldMethodTime(ms),BloomFilterTime(ms)")

    //     for (size in testSizes) {
    //         var oldMethodTotalTime = 0L
    //         var bloomFilterTotalTime = 0L

    //         repeat(numTrials) {
    //             // Test old method
    //             val oldMethodTime =
    //                 measureTimeMillis {
    //                     val euro = createMockDigitalEuro()
    //                     val duplicateEuros = depositedEuroManager.getDigitalEurosByDescriptor(euro)
    //                     if (duplicateEuros.isEmpty()) {
    //                         depositedEuroManager.insertDigitalEuro(euro)
    //                     }
    //                 }

    //             // Test Bloom filter method
    //             val bloomFilter = BloomFilter(size)
    //             val bloomFilterTime =
    //                 measureTimeMillis {
    //                     val euro = createMockDigitalEuro()
    //                     if (!bloomFilter.mightContain(euro)) {
    //                         bloomFilter.add(euro)
    //                         depositedEuroManager.insertDigitalEuro(euro)
    //                     }
    //                 }

    //             oldMethodTotalTime += oldMethodTime
    //             bloomFilterTotalTime += bloomFilterTime
    //         }

    //         val avgOldMethodTime = oldMethodTotalTime / numTrials
    //         val avgBloomFilterTime = bloomFilterTotalTime / numTrials
    //         results.add("$size,$avgOldMethodTime,$avgBloomFilterTime")
    //     }

    //     writeResults("transaction_processing_time.csv", results)
    // }

    // @Test
    // fun testDoubleSpendingDetectionTime() {
    //     val results = mutableListOf<String>()
    //     results.add("Size,OldMethodTime(ms),BloomFilterTime(ms)")

    //     for (size in testSizes) {
    //         var oldMethodTotalTime = 0L
    //         var bloomFilterTotalTime = 0L

    //         repeat(numTrials) {
    //             val euro = createMockDigitalEuro()

    //             // Test old method
    //             val oldMethodTime =
    //                 measureTimeMillis {
    //                     val duplicateEuros = depositedEuroManager.getDigitalEurosByDescriptor(euro)
    //                     if (duplicateEuros.isNotEmpty()) {
    //                         // Double spending detected
    //                     }
    //                 }

    //             // Test Bloom filter method
    //             val bloomFilter = BloomFilter(size)
    //             bloomFilter.add(euro) // Add the euro first to simulate double spending
    //             val bloomFilterTime =
    //                 measureTimeMillis {
    //                     if (bloomFilter.mightContain(euro)) {
    //                         // Potential double spending detected
    //                     }
    //                 }

    //             oldMethodTotalTime += oldMethodTime
    //             bloomFilterTotalTime += bloomFilterTime
    //         }

    //         val avgOldMethodTime = oldMethodTotalTime / numTrials
    //         val avgBloomFilterTime = bloomFilterTotalTime / numTrials
    //         results.add("$size,$avgOldMethodTime,$avgBloomFilterTime")
    //     }

    //     writeResults("double_spending_detection_time.csv", results)
    // }

    // @Test
    // fun testFalsePositiveRate() {
    //     val results = mutableListOf<String>()
    //     results.add("Size,FalsePositiveRate")

    //     for (size in testSizes) {
    //         val bloomFilter = BloomFilter(size)
    //         var falsePositives = 0

    //         // Add some elements to the filter
    //         repeat(size / 2) {
    //             bloomFilter.add(createMockDigitalEuro())
    //         }

    //         // Test for false positives
    //         repeat(numTrials) {
    //             val testEuro = createMockDigitalEuro()
    //             if (bloomFilter.mightContain(testEuro)) {
    //                 falsePositives++
    //             }
    //         }

    //         val falsePositiveRate = falsePositives.toDouble() / numTrials
    //         results.add("$size,$falsePositiveRate")
    //     }

    //     writeResults("false_positive_rate.csv", results)
    // }

    // @Test
    // fun testLookupAvoidanceRate() {
    //     val results = mutableListOf<String>()
    //     results.add("Size,LookupAvoidanceRate")

    //     for (size in testSizes) {
    //         val bloomFilter = BloomFilter(size)
    //         var avoidedLookups = 0

    //         // Add some elements to the filter
    //         repeat(size / 2) {
    //             bloomFilter.add(createMockDigitalEuro())
    //         }

    //         // Test lookup avoidance
    //         repeat(numTrials) {
    //             val testEuro = createMockDigitalEuro()
    //             if (!bloomFilter.mightContain(testEuro)) {
    //                 avoidedLookups++
    //             }
    //         }

    //         val avoidanceRate = avoidedLookups.toDouble() / numTrials
    //         results.add("$size,$avoidanceRate")
    //     }

    //     writeResults("lookup_avoidance_rate.csv", results)
    // }

    private fun createMockDigitalEuro(): DigitalEuro {
        val serialNumber = "test-${System.currentTimeMillis()}"
        return DigitalEuro(serialNumber, mockElement, signature, ArrayList())
    }

    private fun writeResults(
        filename: String,
        results: List<String>
    ) {
        File(resultsDir, filename).writeText(results.joinToString("\n"))
    }
}
