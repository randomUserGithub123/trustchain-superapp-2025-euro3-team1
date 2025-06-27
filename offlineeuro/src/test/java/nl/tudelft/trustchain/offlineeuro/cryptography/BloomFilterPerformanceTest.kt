package nl.tudelft.trustchain.offlineeuro.cryptography

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuro
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
    private val testSizes = listOf(100, 500, 1000, 5000, 10000, 25000, 50000, 100000)
    private val numTrials = 1000 // Increased from 20 to 1000 for more accurate measurements

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
        resultsDir.mkdirs()

        // Setup mock behavior
        `when`(mockElement.toBytes()).thenReturn(ByteArray(32))
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

    @Test
    fun testDoubleSpendingDetectionPerformance() {
        val results = mutableListOf<String>()
        results.add("Size,LinearSearchTime(micros),BloomFilterTime(micros),SpeedupFactor")

        for (size in testSizes) {
            println("Testing double spending detection performance for size: $size")

            val linearSearchTimes = mutableListOf<Long>()
            val bloomFilterTimes = mutableListOf<Long>()

            // Create test data ONCE outside the timing loop
            val existingEuros = createMockEuros(size)
            val bloomFilter = BloomFilter(size)
            existingEuros.forEach { bloomFilter.add(it) }

            // Warmup phase to avoid JVM warmup effects
            repeat(10) {
                val warmupEuro = createMockDigitalEuro("warmup-$it")
                linearSearchForDoubleSpend(warmupEuro, existingEuros)
                bloomFilter.mightContain(warmupEuro)
            }

            // Run multiple trials
            repeat(numTrials) {
                // Create a new test euro for each trial
                val testEuro = createMockDigitalEuro("test-euro-${System.currentTimeMillis()}-$it")

                // Test 1: Linear search method
                val linearStartTime = System.nanoTime()
                linearSearchForDoubleSpend(testEuro, existingEuros)
                val linearEndTime = System.nanoTime()
                linearSearchTimes.add((linearEndTime - linearStartTime) / 1000) // Convert to microseconds

                // Test 2: BloomFilter method
                val bloomStartTime = System.nanoTime()
                bloomFilter.mightContain(testEuro)
                val bloomEndTime = System.nanoTime()
                bloomFilterTimes.add((bloomEndTime - bloomStartTime) / 1000) // Convert to microseconds
            }

            // Calculate average times
            val avgLinearTime = linearSearchTimes.average()
            val avgBloomTime = bloomFilterTimes.average()
            val speedupFactor = if (avgBloomTime > 0) avgLinearTime / avgBloomTime else Double.POSITIVE_INFINITY

            val resultLine =
                "$size,${String.format("%.3f", avgLinearTime)},${String.format("%.3f", avgBloomTime)},${String.format("%.2f", speedupFactor)}"
            results.add(resultLine)

            println(
                "Size: $size, " +
                    "Linear: ${String.format("%.3f", avgLinearTime)}μs, " +
                    "Bloom: ${String.format("%.3f", avgBloomTime)}μs, " +
                    "Speedup: ${String.format("%.2f", speedupFactor)}x"
            )
        }

        writeResults("double_spending_detection_performance.csv", results)
    }

    @Test
    fun testDoubleSpendingDetectionWithExistingToken() {
        val results = mutableListOf<String>()
        results.add("Size,LinearSearchTime(micros),BloomFilterTime(micros),SpeedupFactor")

        for (size in testSizes) {
            println("Testing double spending detection with existing token for size: $size")

            val linearSearchTimes = mutableListOf<Long>()
            val bloomFilterTimes = mutableListOf<Long>()

            // Create test data ONCE outside the timing loop
            val existingEuros = createMockEuros(size)
            val bloomFilter = BloomFilter(size)
            existingEuros.forEach { bloomFilter.add(it) }

            // Run multiple trials
            repeat(numTrials) {
                // Use different existing tokens for each trial to avoid caching effects
                val testEuro = existingEuros[it % existingEuros.size]

                // Test 1: Linear search method
                val linearStartTime = System.nanoTime()
                linearSearchForDoubleSpend(testEuro, existingEuros)
                val linearEndTime = System.nanoTime()
                linearSearchTimes.add((linearEndTime - linearStartTime) / 1000)

                // Test 2: BloomFilter method
                val bloomStartTime = System.nanoTime()
                bloomFilter.mightContain(testEuro)
                val bloomEndTime = System.nanoTime()
                bloomFilterTimes.add((bloomEndTime - bloomStartTime) / 1000)
            }

            // Calculate average times
            val avgLinearTime = linearSearchTimes.average()
            val avgBloomTime = bloomFilterTimes.average()
            val speedupFactor = if (avgBloomTime > 0) avgLinearTime / avgBloomTime else Double.POSITIVE_INFINITY

            val resultLine =
                "$size,${String.format("%.3f", avgLinearTime)},${String.format("%.3f", avgBloomTime)},${String.format("%.2f", speedupFactor)}"
            results.add(resultLine)

            println(
                "Size: $size, " +
                    "Linear: ${String.format("%.3f", avgLinearTime)}μs, " +
                    "Bloom: ${String.format("%.3f", avgBloomTime)}μs, " +
                    "Speedup: ${String.format("%.2f", speedupFactor)}x"
            )
        }

        writeResults("double_spending_detection_existing_token.csv", results)
    }

    @Test
    fun testHashCreationPerformance() {
        val results = mutableListOf<String>()
        results.add("Size,HashCreationTime(micros),LinearSearchTime(micros),HashVsLinearRatio")

        for (size in testSizes) {
            println("Testing hash creation performance for size: $size")

            val hashCreationTimes = mutableListOf<Long>()
            val linearSearchTimes = mutableListOf<Long>()

            // Create test data ONCE outside the timing loop
            val existingEuros = createMockEuros(size)

            // Run multiple trials
            repeat(numTrials) {
                // Create new test data for each trial
                val testEuro = createMockDigitalEuro("test-euro-${System.currentTimeMillis()}-$it")
                val bloomFilter = BloomFilter(size)

                // Test 1: Hash creation time (BloomFilter add operation)
                val hashStartTime = System.nanoTime()
                bloomFilter.add(testEuro)
                val hashEndTime = System.nanoTime()
                hashCreationTimes.add((hashEndTime - hashStartTime) / 1000)

                // Test 2: Linear search time
                val linearStartTime = System.nanoTime()
                linearSearchForDoubleSpend(testEuro, existingEuros)
                val linearEndTime = System.nanoTime()
                linearSearchTimes.add((linearEndTime - linearStartTime) / 1000)
            }

            // Calculate average times
            val avgHashTime = hashCreationTimes.average()
            val avgLinearTime = linearSearchTimes.average()
            val ratio = if (avgLinearTime > 0) avgHashTime / avgLinearTime else Double.POSITIVE_INFINITY

            val resultLine =
                "$size,${String.format("%.3f", avgHashTime)},${String.format("%.3f", avgLinearTime)},${String.format("%.4f", ratio)}"
            results.add(resultLine)

            println(
                "Size: $size, " +
                    "Hash: ${String.format("%.3f", avgHashTime)}μs, " +
                    "Linear: ${String.format("%.3f", avgLinearTime)}μs, " +
                    "Ratio: ${String.format("%.4f", ratio)}"
            )
        }

        writeResults("hash_creation_performance.csv", results)
    }

    private fun linearSearchForDoubleSpend(testEuro: DigitalEuro, existingEuros: List<DigitalEuro>): Boolean {
        return existingEuros.any { it.descriptorEquals(testEuro) }
    }

    private fun createMockEuros(count: Int): List<DigitalEuro> {
        return (0 until count).map {
            createMockDigitalEuro("euro-$it-${System.currentTimeMillis()}")
        }
    }

    private fun createMockDigitalEuro(): DigitalEuro {
        val serialNumber = "test-${System.currentTimeMillis()}"
        return DigitalEuro(serialNumber, mockElement, signature, ArrayList())
    }

    private fun createMockDigitalEuro(serialNumber: String): DigitalEuro {
        return DigitalEuro(serialNumber, mockElement, signature, ArrayList())
    }

    private fun writeResults(filename: String, results: List<String>) {
        File(resultsDir, filename).writeText(results.joinToString("\n"))
    }
}
