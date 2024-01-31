package nl.tudelft.trustchain.offlineeuro.db

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.entity.RegisteredUser
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
class RegisteredUserManagerTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
        Database.Schema.create(this)
    }

    private val registeredUserManager = RegisteredUserManager(null, driver)
    @Before
    fun before() {
        registeredUserManager.clearAllRegisteredUsers()
    }

    @Test
    fun registerAndGetByNameTest() {
        val name = "Tester"
        val s = BigInteger("1234")
        val k = BigInteger("567")
        val v = BigInteger("890")
        val r = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129870127")
        val userToRegister = RegisteredUser(-1, name, s, k, v, r)
        registeredUserManager.addRegisteredUser(userToRegister)

        val registeredUser = registeredUserManager.getRegisteredUserByName(name)!!
        Assert.assertNotEquals("The user should have a valid ID", -1, registeredUser.id)
        Assert.assertEquals("The name should be unchanged", name, registeredUser.name)
        Assert.assertEquals("The value of s should be unchanged", s, registeredUser.s)
        Assert.assertEquals("The value of k should be unchanged", k, registeredUser.k)
        Assert.assertEquals("The value of v should be unchanged", v, registeredUser.v)
        Assert.assertEquals("The value of r should be unchanged", r, registeredUser.r)

        val unknownUserName = "This name is not in the database"
        val notFoundUser = registeredUserManager.getRegisteredUserByName(unknownUserName)
        Assert.assertNull("An unknown user should return null", notFoundUser)
    }

    @Test
    fun uniqueRegisterValuesTest() {
        val name = "Tester"
        val s = BigInteger("1234")
        val k = BigInteger("567")
        val v = BigInteger("890")
        val r = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129870127")
        val userToRegister = RegisteredUser(-1, name, s, k, v, r)
        val firstRegisterResult = registeredUserManager.addRegisteredUser(userToRegister)

        Assert.assertTrue("The first registration should be allowed", firstRegisterResult)

        val differentName = "Jester"
        val differentUserSameR = RegisteredUser(-1, differentName, s, k, v, r)
        val sameRResult = registeredUserManager.addRegisteredUser(differentUserSameR)
        Assert.assertFalse("Two users should never have the same r", sameRResult)

        val differentR = BigInteger("11579237316195423570985008687907853269984665640564039457584007913129870127")
        val differentUserSameName = RegisteredUser(-1, name, s, k, v, differentR)
        val sameNameResult = registeredUserManager.addRegisteredUser(differentUserSameName)
        Assert.assertFalse("Two users should never have the same r", sameNameResult)

        val differentUserDifferentNameAndR = RegisteredUser(-1, differentName, s, k, v, differentR)
        val differentResult = registeredUserManager.addRegisteredUser(differentUserDifferentNameAndR)
        Assert.assertTrue("The different user should be added", differentResult)

        val numberOfRegisteredUsers = registeredUserManager.getUserCount()
        Assert.assertEquals("There should only be two registered users", 2, numberOfRegisteredUsers)

    }
}
