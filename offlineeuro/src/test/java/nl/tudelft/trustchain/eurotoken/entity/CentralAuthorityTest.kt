package nl.tudelft.trustchain.eurotoken.entity

import nl.tudelft.trustchain.offlineeuro.entity.CentralAuthority
import org.junit.Assert
import org.junit.Test

class CentralAuthorityTest {
    @Test
    fun pIsPrimeTest() {
        val p = CentralAuthority.p
        Assert.assertTrue("p should be a prime", p.isProbablePrime(10))
    }

    @Test
    fun qIsPrimeTest() {
        val q = CentralAuthority.q
        Assert.assertTrue("q should be a prime", q.isProbablePrime(10))
    }
}
