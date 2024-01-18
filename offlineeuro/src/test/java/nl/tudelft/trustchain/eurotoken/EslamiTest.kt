package nl.tudelft.trustchain.eurotoken

import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.Token
import nl.tudelft.trustchain.offlineeuro.entity.User
import org.junit.Assert
import org.junit.Test
import java.math.BigInteger

class EslamiTest {

    @Test
    fun eslamiProtocolTest() {
        val bank = Bank()
        val user = User()
        // The user should start with no tokens
        Assert.assertEquals("The user should have no tokens", 0, user.getBalance())
        // register the user
        user.registerWithBank(bank)
        // Withdraw a token
        user.withdrawToken(bank)

        Assert.assertEquals("The user should now have a token", 1, user.getBalance())

        val token = user.getTokens()[0].first
        // The token should be valid
        Assert.assertTrue("The token should be valid", user.checkReceivedToken(token, bank))
        val randomToken = Token(token.u, token.g, token.r, token.A, token.ADoublePrime, token.t)
        Assert.assertFalse("The token should be invalid", user.checkReceivedToken(randomToken, bank))

        val merchant1ID = "101212121"
        val firstChallenge = user.onTokenReceived(token, bank, BigInteger(merchant1ID))
        Assert.assertNotNull("The merchant should respond with a challenge", firstChallenge)
        val firstGamma = user.onChallengeReceived(firstChallenge!!, token)
        Assert.assertNotEquals("The gamma should be valid", BigInteger.ZERO, firstGamma)
        Assert.assertTrue("The challenge should return true", user.verifyChallengeResponse(token, firstGamma, firstChallenge))
        val check = user.onChallengeResponseReceived(token, firstGamma, firstChallenge)
        Assert.assertEquals("The challenge response should be valid", Triple(token, firstChallenge, firstGamma), check!!)

        Assert.assertEquals("Deposit was successful!", bank.depositToken(check))

        val merchant2ID = "32132131321"
        val secondChallenge = user.onTokenReceived(token, bank, BigInteger(merchant2ID))

        Assert.assertNotNull("The merchant should respond with a challenge", secondChallenge)
        val secondGamma = user.onChallengeReceived(secondChallenge!!, token)
        Assert.assertNotEquals("The gamma should be valid", BigInteger.ZERO, secondGamma)
        Assert.assertTrue("The challenge should return true", user.verifyChallengeResponse(token, secondGamma, secondChallenge))
        val checkTwo = user.onChallengeResponseReceived(token, secondGamma, secondChallenge)
        Assert.assertEquals("The challenge response should be valid", Triple(token, secondChallenge, secondGamma), checkTwo!!)

        Assert.assertTrue(bank.depositToken(checkTwo).contains("Double Spending detected! This is done by"))

    }
}
