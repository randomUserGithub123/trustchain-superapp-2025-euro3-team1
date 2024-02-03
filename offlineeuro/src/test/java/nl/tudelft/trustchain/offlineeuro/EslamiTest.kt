package nl.tudelft.trustchain.eurotoken

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.db.BankRegistrationManager
import nl.tudelft.trustchain.offlineeuro.db.DepositedTokenManager
import nl.tudelft.trustchain.offlineeuro.db.OwnedTokenManager
import nl.tudelft.trustchain.offlineeuro.db.ReceiptManager
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager
import nl.tudelft.trustchain.offlineeuro.db.UnsignedTokenManager
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.BankDetails
import nl.tudelft.trustchain.offlineeuro.entity.MessageResult
import nl.tudelft.trustchain.offlineeuro.entity.Token
import nl.tudelft.trustchain.offlineeuro.entity.UnsignedTokenSignRequestEntry
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.entity.UserRegistrationMessage
import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.times
import java.math.BigInteger

class EslamiTest {

    // Set up for in memory databases
    private val context = null
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
        Database.Schema.create(this)
    }

    // Separate drivers for the merchant users
    private val driver2 = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
        Database.Schema.create(this)
    }

    private val driver3 = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
        Database.Schema.create(this)
    }
    // Set up a Mock for the Community
    private val community: OfflineEuroCommunity = Mockito.mock(OfflineEuroCommunity::class.java)
    private val unsignedTokenMessageCaptor = argumentCaptor<List<UnsignedTokenSignRequestEntry>>()
    private val registrationMessageCaptor = argumentCaptor<UserRegistrationMessage>()

    @Before
    fun setup() {
        `when`(community.sendUserRegistrationMessage(any(), any())).doReturn(true)

        `when`(community.sendUnsignedTokens(any(), any())).then {}

    }

    @Test
    fun eslamiProtocolTest() {

        // Create a bank and a user with an in memory database
        val bank = Bank(
            "BestTestBank",
            context,
            RegisteredUserManager(context, driver),
            DepositedTokenManager(context, driver)
        )
        val rsaParameters = bank.getPublicRSAValues()
        val bankDetails = BankDetails(
            "BestTestBank",
            bank.z,
            rsaParameters.first,
            rsaParameters.second,
            "NotAPubKeyJustSomeBytes".toByteArray()
        )

        val user = User (
            "TheRichestUser",
            context,
            OwnedTokenManager(context, driver),
            BankRegistrationManager(context, driver),
            UnsignedTokenManager(context, driver),
            ReceiptManager(context, driver)
        )

        // The user should start with no tokens
        Assert.assertEquals("The user should have no tokens", 0, user.getBalance())
        // Register the user
        user.handleBankDetailsReply(bankDetails)
        user.registerWithBank(bankDetails.name, community)

        // Assert that the registration request is sent
        verify(community, times(1)).sendUserRegistrationMessage(registrationMessageCaptor.capture(), any())

        val registerMessage = registrationMessageCaptor.allValues[0]
        Assert.assertNotNull("The registration request should be sent now", registerMessage)

        // Handle the registration request as the bank
        val registrationResponse = bank.handleUserRegistration(registerMessage)

        // Registration should be successful
        Assert.assertEquals("The registration should be successful", MessageResult.SuccessFul, registrationResponse.result)

        // Complete the bank registration for the user
        user.handleRegistrationResponse(registrationResponse)

        // Withdraw a token
        user.withdrawToken(bankDetails.name, community)

        // Assert that the UnsignedTokenRequest is sent
        verify(community, Mockito.times(1)).sendUnsignedTokens(unsignedTokenMessageCaptor.capture(), any())
        val unsignedTokenMessage = unsignedTokenMessageCaptor.allValues[0]
        Assert.assertNotNull("The sign request should be sent now", unsignedTokenMessage)

        // Sign the tokens with the bank
        val signedTokenResponse = bank.handleSignUnsignedTokenRequest(user.name, unsignedTokenMessage)

        // Handle the response with the user
        user.handleUnsignedTokenSignResponse(bankDetails.name, signedTokenResponse)

        // The user should now have one valid token
        Assert.assertEquals("The user should now have a token", 1, user.getBalance())
        val token = user.getTokens()[0].token
        Assert.assertTrue("The token should be valid", user.checkReceivedToken(token, bank.name))
        val randomToken = Token(token.u, token.g, token.r, token.a, token.aPrime, token.t)
        Assert.assertFalse("The token should be invalid", user.checkReceivedToken(randomToken, bank.name))

        val merchantOne = User(
            "MerchantOne",
            context,
            OwnedTokenManager(context, driver2),
            BankRegistrationManager(context, driver2),
            UnsignedTokenManager(context, driver2),
            ReceiptManager(context, driver2)
        )

        Assert.assertNull("The merchant does not know of any bank", merchantOne.getBankRegistrationByName(bankDetails.name))
        Assert.assertNotNull("The merchant does not know of any bank", user.getBankRegistrationByName(bankDetails.name))

        val merchantTwo = User(
            "MerchantTwo",
            context,
            OwnedTokenManager(context, driver3),
            BankRegistrationManager(context, driver3),
            UnsignedTokenManager(context, driver3),
            ReceiptManager(context, driver3)
        )

//        val merchant1ID = "101212121"
//        val firstChallenge = user.onTokensReceived(token, bank.name, BigInteger(merchant1ID))
//        Assert.assertNotNull("The merchant should respond with a challenge", firstChallenge)
//        val firstGamma = user.onChallengeReceived(firstChallenge!!, token)
//        Assert.assertNotEquals("The gamma should be valid", BigInteger.ZERO, firstGamma)
//        Assert.assertTrue("The challenge should return true", user.verifyChallengeResponse(token, firstGamma, firstChallenge))
//        val receipt = user.onChallengeResponseReceived(token, firstGamma, firstChallenge)
//        Assert.assertNotNull("The challenge response should be valid", receipt!!)
//
//        Assert.assertEquals("Deposit was successful!", bank.depositToken(receipt))
//
//        val merchant2ID = "32132131321"
//        val secondChallenge = user.onTokenReceived(token, bank.name, BigInteger(merchant2ID))
//
//        Assert.assertNotNull("The merchant should respond with a challenge", secondChallenge)
//        val secondGamma = user.onChallengeReceived(secondChallenge!!, token)
//        Assert.assertNotEquals("The gamma should be valid", BigInteger.ZERO, secondGamma)
//        Assert.assertTrue("The challenge should return true", user.verifyChallengeResponse(token, secondGamma, secondChallenge))
//        val checkTwo = user.onChallengeResponseReceived(token, secondGamma, secondChallenge)
//        Assert.assertNotNull("The challenge response should be valid", checkTwo!!)
//        val result = bank.depositToken(checkTwo)
//        Assert.assertTrue(result.contains("Double Spending detected! This is done by"))

    }

    @Test
    fun solvingTest(){
        val u = BigInteger("101883854348309890603168273231009025754705253624354976652063900669174453337185")
        val y = BigInteger("69133590048615250080135987274793558714860351197423196008484529514901662011187")
        val d = BigInteger("20788845790586771363780782591895646703810887356727784638856780228916467431088")
        val d_prime = BigInteger("54442900470843834126092784710849417245498975184929599235687267415564474009171")
        val p = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129870127")
        val w = BigInteger("49732341216323764246207857283659407037075415685418637815515690870020050531776")

        val firstGamma = Cryptography.solve_for_gamma(w, u, y, d, p)
        val secondGamma = Cryptography.solve_for_gamma(w, u, y, d_prime, p)
        val foundY = Cryptography.solve_for_y(firstGamma, secondGamma, d, d_prime, p)
        Assert.assertEquals(foundY, y)
        val foundW = Cryptography.solve_for_w(u, foundY, firstGamma, d, p).mod(p-BigInteger.ONE)
        Assert.assertEquals(foundW, w)
    }

    @Test
    fun challengesTest() {
        // TODO rewrite for IPV8 changes
//        val user = User()
//        val u = BigInteger("101883854348309890603168273231009025754705253624354976652063900669174453337185")
//        val y = BigInteger("69133590048615250080135987274793558714860351197423196008484529514901662011187")
//        val p = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129870127")
//        val w = BigInteger("49732341216323764246207857283659407037075415685418637815515690870020050531776")
//        val g = CentralAuthority.alpha.modPow(w, CentralAuthority.p)
//        var correctRuns = 0
//        val amountOfRuns = 100
//        // Only u and g are used to create a challenge
//        val token = Token(u, g, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, "")
//        for (i in 1..amountOfRuns) {
//            val merchantID = BigInteger("101212121")
//            val d = user.computeChallenge(token, merchantID)
//            val gamma = Cryptography.solve_for_gamma(w, u, y, d, p)
//            val merchantID2 = BigInteger("121321312")
//            val d_prime = user.computeChallenge(token, merchantID2)
//            val gamma_prime = Cryptography.solve_for_gamma(w, u, y, d_prime, p)
//            var foundY: BigInteger
//            if ((gamma-gamma_prime).gcd(p - BigInteger.ONE) != BigInteger.ONE) {
//                throw Exception()
//            }
//
//            try {
//                foundY = Cryptography.solve_for_y(gamma, gamma_prime, d, d_prime, p)
//            } catch (e: Exception) {
//                continue
//            }
//            // Assert that the found y is correct, if there is one found
//            Assert.assertEquals(foundY, y)
//            // Assert that the found w is correct, if there is one found
//            val foundW = Cryptography.solve_for_w(u, foundY, gamma, d, p).mod(p-BigInteger.ONE)
//            Assert.assertEquals(foundW, w)
//
//            correctRuns++
//        }
//        Assert.assertEquals(amountOfRuns, correctRuns)

    }
}
