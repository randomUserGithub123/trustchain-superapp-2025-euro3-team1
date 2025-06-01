package nl.tudelft.trustchain.offlineeuro.cryptography

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuro
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class BloomFilterTest {
    private lateinit var bloomFilter: BloomFilter
    private lateinit var group: BilinearGroup
    private lateinit var mockElement: Element
    private lateinit var mockSignature: SchnorrSignature

    @Before
    fun setup() {
        bloomFilter = BloomFilter(1000)
        group = BilinearGroup()
        mockElement = mock(Element::class.java)
        mockSignature = mock(SchnorrSignature::class.java)
        `when`(mockElement.toBytes()).thenReturn(ByteArray(32) { 1 })
        `when`(mockSignature.toBytes()).thenReturn(ByteArray(32) { 2 })
    }

    @Test
    fun testAddAndContains() {
        val euro =
            DigitalEuro(
                "test123",
                mockElement,
                mockSignature
            )

        Assert.assertFalse(bloomFilter.mightContain(euro))
        bloomFilter.add(euro)

        Assert.assertTrue(bloomFilter.mightContain(euro))
    }

    @Test
    fun testFalsePositives() {
        val euro1 =
            DigitalEuro(
                "test123",
                mockElement,
                mockSignature
            )

        val euro2 =
            DigitalEuro(
                "test456",
                mockElement,
                mockSignature
            )

        bloomFilter.add(euro1)
        Assert.assertFalse(bloomFilter.mightContain(euro2))
    }

    @Test
    fun testClear() {
        val euro =
            DigitalEuro(
                "test123",
                mockElement,
                mockSignature
            )

        bloomFilter.add(euro)
        Assert.assertTrue(bloomFilter.mightContain(euro))

        bloomFilter.clear()
        Assert.assertFalse(bloomFilter.mightContain(euro))
    }

    @Test
    fun testDifferentSizes() {
        val smallFilter = BloomFilter(100)
        val largeFilter = BloomFilter(10000)

        val euro =
            DigitalEuro(
                "test123",
                mockElement,
                mockSignature
            )

        smallFilter.add(euro)
        largeFilter.add(euro)

        Assert.assertTrue(smallFilter.mightContain(euro))
        Assert.assertTrue(largeFilter.mightContain(euro))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidFalsePositiveRate() {
        BloomFilter(1000, 1.5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeFalsePositiveRate() {
        BloomFilter(1000, -0.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testZeroExpectedElements() {
        BloomFilter(0)
    }

    @Test
    fun testFalsePositiveRateCalculation() {
        val filter = BloomFilter(1000, 0.01) // 1% false positive rate
        val euro =
            DigitalEuro(
                "test123",
                mockElement,
                mockSignature
            )

        val initialRate = filter.getCurrentFalsePositiveRate()
        println("Initial false positive rate: $initialRate")
        Assert.assertTrue("Initial false positive rate should be very low", initialRate < 0.01)

        filter.add(euro)

        val rateAfterAdd = filter.getCurrentFalsePositiveRate()
        println("False positive rate after adding one element: $rateAfterAdd")
        Assert.assertTrue("False positive rate should still be low with one element", rateAfterAdd < 0.01)
    }
}
