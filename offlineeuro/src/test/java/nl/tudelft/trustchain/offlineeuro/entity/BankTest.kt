//package nl.tudelft.trustchain.offlineeuro.entity
//
//import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
//import nl.tudelft.offlineeuro.sqldelight.Database
//import nl.tudelft.trustchain.offlineeuro.db.OwnedTokenManager
//import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager
//import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
//import org.junit.Assert
//import org.junit.Test
//import java.math.BigInteger
//
//class BankTest {
//    // Set the bank to use an in memory database
//    private val context = null
//    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
//        Database.Schema.create(this)
//    }
//    private val bank = Bank("BestTestBank", context, RegisteredUserManager(context, driver))
//    private val rsaParameters = bank.getPublicRSAValues()
//    private val bankDetails = BankDetails(
//        "BestTestBank",
//        bank.z,
//        rsaParameters.first,
//        rsaParameters.second,
//        "NotAPubKeyJustSomeBytes".toByteArray()
//    )
//
//    private val p = CentralAuthority.p
//    private val alpha = CentralAuthority.alpha
//
//    // Define a user to get access to some of the user computation methods
//    private val user = User (
//        "IAmTheRichestUser",
//        context,
//        OwnedTokenManager(context, driver),
//        BankRegistrationManager(context, driver),
//        UnsignedTokenManager(context, driver)
//    )
//
//    @Test
//    fun registerUserTest() {
//
//        val userM = Cryptography.generateRandomBigInteger(CentralAuthority.p)
//        val userRm = Cryptography.generateRandomBigInteger(CentralAuthority.p)
//        val userI = user.computeI(bankDetails, userM, userRm)
//        val arm = alpha.modPow(userRm, p)
//        val userRegistrationMessage = UserRegistrationMessage(user.name, userI, arm)
//
//        val bankResponseMessage = bank.handleUserRegistration(userRegistrationMessage)
//
//        Assert.assertEquals("The registration should be valid", MessageResult.SuccessFul, bankResponseMessage.result)
//        Assert.assertNotEquals("The v should be set", BigInteger.ZERO, bankResponseMessage.v)
//        Assert.assertNotEquals("The r should be set", BigInteger.ZERO, bankResponseMessage.r)
//        Assert.assertTrue("The should be no error message", bankResponseMessage.errorMessage.isEmpty())
//
//        val registeredUsers = bank.getRegisteredUsers()
//        Assert.assertEquals("There should be one registered user", 1, registeredUsers.count())
//        Assert.assertEquals("The registered user is correct", user.name, registeredUsers[0].name)
//
//        // Test duplicate username
//        val secondM = Cryptography.generateRandomBigInteger(CentralAuthority.p)
//        val secondRm = Cryptography.generateRandomBigInteger(CentralAuthority.p)
//        val secondI = user.computeI(bankDetails, secondM, secondRm)
//        val secondArm = alpha.modPow(secondRm, p)
//
//        val secondRegistrationMessage = UserRegistrationMessage(user.name, secondI, secondArm)
//        val secondResponseMessage = bank.handleUserRegistration(secondRegistrationMessage)
//
//        Assert.assertEquals("The registration should be invalid", MessageResult.Failed, secondResponseMessage.result)
//        Assert.assertEquals("The v should not be set", BigInteger.ZERO, secondResponseMessage.v)
//        Assert.assertEquals("The r should be not set", BigInteger.ZERO, secondResponseMessage.r)
//        Assert.assertTrue("The should be am error message",
//            secondResponseMessage.errorMessage.isNotEmpty()
//        )
//
//        val newName = "JustABitPoorer"
//        val thirdRegistrationMessage = UserRegistrationMessage(newName, secondI, secondArm)
//        val thirdResponse = bank.handleUserRegistration(thirdRegistrationMessage)
//
//        Assert.assertEquals("The registration should be valid", MessageResult.SuccessFul, thirdResponse.result)
//        Assert.assertNotEquals("The v should be set", BigInteger.ZERO, thirdResponse.v)
//        Assert.assertNotEquals("The r should be set", BigInteger.ZERO, thirdResponse.r)
//        Assert.assertTrue("The should be no error message", thirdResponse.errorMessage.isEmpty())
//
//        val secondRegisteredUsers = bank.getRegisteredUsers()
//        Assert.assertEquals("There should be one registered user", 2, secondRegisteredUsers.count())
//        Assert.assertEquals("The registered user is correct", user.name, secondRegisteredUsers[0].name)
//        Assert.assertEquals("The registered user is correct", newName, secondRegisteredUsers[1].name)
//    }
//}
