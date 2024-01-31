package nl.tudelft.trustchain.offlineeuro.entity

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.db.BankRegistrationManager
import nl.tudelft.trustchain.offlineeuro.db.OwnedTokenManager
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager
import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
import org.junit.Assert
import org.junit.Test
import java.math.BigInteger

class BankTest {
    // Set the bank to use an in memory database
    private val context = null
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
        Database.Schema.create(this)
    }
    private val bank = Bank(context, RegisteredUserManager(context, driver))
    private val rsaParameters = bank.getPublicRSAValues()
    private val bankDetails = BankDetails(
        "BestTestBank",
        bank.z,
        rsaParameters.first,
        rsaParameters.second,
        "NotAPubKeyJustSomeBytes".toByteArray()
    )

    private val p = CentralAuthority.p
    private val alpha = CentralAuthority.alpha

    // Define a user to get access to some of the user computation methods
    private val user = User(context,
        OwnedTokenManager(context, driver),
        BankRegistrationManager(context, driver)
    )
    @Test
    fun registerUserTest() {

        val userM = Cryptography.generateRandomBigInteger(CentralAuthority.p)
        val userRm = Cryptography.generateRandomBigInteger(CentralAuthority.p)
        val userI = user.computeI(bankDetails, userM, userRm)
        val arm = alpha.modPow(userRm, p)
        val userName = "IAmTheRichestUser"
        val userRegistrationMessage = UserRegistrationMessage(userName, userI, arm)

        val bankResponseMessage = bank.registerUser(userRegistrationMessage)

        Assert.assertEquals("The registration should be valid", MessageResult.SuccessFul, bankResponseMessage.result)
        Assert.assertNotEquals("The v should be set", BigInteger.ZERO, bankResponseMessage.v)
        Assert.assertNotEquals("The r should be set", BigInteger.ZERO, bankResponseMessage.r)
        Assert.assertTrue("The should be no error message", bankResponseMessage.errorMessage.isEmpty())
    }
}
