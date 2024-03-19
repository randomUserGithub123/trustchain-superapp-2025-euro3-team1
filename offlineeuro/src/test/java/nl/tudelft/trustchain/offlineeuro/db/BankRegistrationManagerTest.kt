package nl.tudelft.trustchain.offlineeuro.db

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.entity.BankDetails
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

class BankRegistrationManagerTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
        Database.Schema.create(this)
    }

    private val bankRegistrationManager = BankRegistrationManager(null, driver)

    @Before
    fun before() {
        bankRegistrationManager.clearAllRegisteredBanks()
    }

    /**
     * Test case for update queries while the bank name cannot be found.
     */
    @Test
    fun updateWithUnknownBankTest() {
        val bigInt = BigInteger("12345667878532412")
        val nonExistingBank = "Bank404"

        val setOwnValues = bankRegistrationManager.setOwnValuesForBank(nonExistingBank, bigInt, bigInt, "User")
        val setBankValues = bankRegistrationManager.setBankRegistrationValues(nonExistingBank, bigInt, bigInt)

        Assert.assertFalse("There should be no record affected", setOwnValues)
        Assert.assertFalse("There should be no record affected", setBankValues)
    }

    @Test
    fun insertingNewEntriesTest() {
        val name = "TheBestBankAround"
        val z = BigInteger("123456123412")
        val eb = BigInteger("1235123421452138213")
        val nb = BigInteger("32321298052132132131245213512312")
        val publicKey = "ActuallyNotAPublicKeyButJustAByteArray".toByteArray()
        val bankDetails = BankDetails(name, z, eb, nb, publicKey)

        val insert = bankRegistrationManager.addNewBank(bankDetails)
        val duplicate = bankRegistrationManager.addNewBank(bankDetails)
        Assert.assertTrue("The bank should be added", insert)
        Assert.assertFalse("A duplicate name should not be allowed", duplicate)

        val retrievedDetails = bankRegistrationManager.getBankRegistrationByName(name)
        Assert.assertNotNull("The bank should be found", retrievedDetails)
        val retrievedBankDetails = retrievedDetails!!.bankDetails

        Assert.assertEquals("The bank should be added correctly", name, retrievedBankDetails.name)
        Assert.assertEquals("The bank should be added correctly", z, retrievedBankDetails.z)
        Assert.assertEquals("The bank should be added correctly", eb, retrievedBankDetails.eb)
        Assert.assertEquals("The bank should be added correctly", nb, retrievedBankDetails.nb)
        Assert.assertArrayEquals("The bank should be added correctly", publicKey, retrievedBankDetails.publicKeyBytes)
        Assert.assertNull("There should be no value for m yet", retrievedDetails.m)
        Assert.assertNull("There should be no value for rm yet", retrievedDetails.rm)
        Assert.assertNull("There should be no value for v yet", retrievedDetails.v)
        Assert.assertNull("There should be no value for r yet", retrievedDetails.r)

        val m = BigInteger("2321421897529103201")
        val rm = BigInteger("321421576967455213")

        val setOwnValues = bankRegistrationManager.setOwnValuesForBank(name, m, rm, "UserName")
        Assert.assertTrue("The registration should be updated", setOwnValues)

        val updatedRegistration = bankRegistrationManager.getBankRegistrationByName(name)
        Assert.assertNotNull("The bank should be found", updatedRegistration)

        val updatedBankDetails = updatedRegistration!!.bankDetails
        Assert.assertEquals("The first values should not be changed", name, updatedBankDetails.name)
        Assert.assertEquals("The first values should not be changed", z, updatedBankDetails.z)
        Assert.assertEquals("The first values should not be changed", eb, updatedBankDetails.eb)
        Assert.assertEquals("The first values should not be changed", nb, updatedBankDetails.nb)
        Assert.assertArrayEquals("TThe first values should not be changed", publicKey, updatedBankDetails.publicKeyBytes)
        Assert.assertEquals("m should be updated to have the correct value", m, updatedRegistration.m)
        Assert.assertEquals("rm should be updated to have the correct value", rm, updatedRegistration.rm)
        Assert.assertNull("There should be no value for v yet", updatedRegistration.v)
        Assert.assertNull("There should be no value for r yet", updatedRegistration.r)

        val v = BigInteger("1234852121456123421")
        val r = BigInteger("25324897239432132")

        val setBankValues = bankRegistrationManager.setBankRegistrationValues(name, v, r)
        Assert.assertTrue("The registration should be updated", setBankValues)

        val completedRegistration = bankRegistrationManager.getBankRegistrationByName(name)
        Assert.assertNotNull("The bank should be found", completedRegistration)

        val completedBankDetails = completedRegistration!!.bankDetails
        Assert.assertEquals("The first values should not be changed", name, completedBankDetails.name)
        Assert.assertEquals("The first values should not be changed", z, completedBankDetails.z)
        Assert.assertEquals("The first values should not be changed", eb, completedBankDetails.eb)
        Assert.assertEquals("The first values should not be changed", nb, completedBankDetails.nb)
        Assert.assertArrayEquals("The first values should not be changed", publicKey, completedBankDetails.publicKeyBytes)
        Assert.assertEquals("The second values should not be changed", m, completedRegistration.m)
        Assert.assertEquals("The second values should not be changed", rm, completedRegistration.rm)
        Assert.assertEquals("v should be updated to have the correct value", v, completedRegistration.v)
        Assert.assertEquals("r should be updated to have the correct value", r, completedRegistration.r)
    }
}
