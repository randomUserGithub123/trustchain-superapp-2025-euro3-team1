package nl.tudelft.trustchain.offlineeuro.entity

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.db.BankRegistrationManager
import nl.tudelft.trustchain.offlineeuro.db.OwnedTokenManager
import nl.tudelft.trustchain.offlineeuro.db.ReceiptManager
import nl.tudelft.trustchain.offlineeuro.db.UnsignedTokenManager
import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
import org.junit.Assert
import org.junit.Test
import java.math.BigInteger

class UserTest {

    private val context = null
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
        Database.Schema.create(this)
    }

    val group = CentralAuthority.groupDescription
    val crs = CentralAuthority.crs

    private val user = User (
        "IAmTheRichestUser",
        context,
        OwnedTokenManager(context, driver),
        BankRegistrationManager(context, driver),
        UnsignedTokenManager(context, driver),
        ReceiptManager(context, driver)
    )

    @Test
    fun singleTransactionTest() {
        val bankSignature = BigInteger("12451241252134612")
        val digitalEuro = DigitalEuro(arrayListOf<GrothSahaiProof>(), bankSignature)
        val userWallet = Wallet(user.privateKey, user.publicKey)
        userWallet.addToWallet(digitalEuro, null)

        val randomT = group.pairing.zr.newRandomElement().immutable
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT)
        val transactionDetails = userWallet.spendEuro(randomizationElements)
        val isValid = Transaction.validate(transactionDetails!!)

        Assert.assertTrue(isValid)
    }

    @Test
    fun twoTransactionsTest() {
        val bankSignature = BigInteger("12451241252134612")
        val digitalEuro = DigitalEuro(arrayListOf<GrothSahaiProof>(), bankSignature)
        val userWallet = Wallet(user.privateKey, user.publicKey)
        userWallet.addToWallet(digitalEuro, null)

        val randomT = group.pairing.zr.newRandomElement().immutable
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT)
        val transactionDetails = userWallet.spendEuro(randomizationElements)
        val isValid = Transaction.validate(transactionDetails!!)
        Assert.assertTrue(isValid)

        userWallet.addToWallet(transactionDetails, randomT)
        val nextT = group.pairing.zr.newRandomElement().immutable
        val nextRandomizationElements = GrothSahai.tToRandomizationElements(nextT)
        val transactionDetails2 = userWallet.spendEuro(nextRandomizationElements)
        val isValid2 = Transaction.validate(transactionDetails2!!)
        Assert.assertTrue(isValid2)
    }

    @Test
    fun twentyTransactionsTest() {
        val bankSignature = Cryptography.generateRandomBigInteger(BigInteger("999999999999999999999999999"))
        val digitalEuro = DigitalEuro(arrayListOf<GrothSahaiProof>(), bankSignature)
        val userWallet = Wallet(user.privateKey, user.publicKey)
        userWallet.addToWallet(digitalEuro, null)
        val amountOfTransactions = 20

        for (i in 0 until amountOfTransactions) {
            val randomT = group.pairing.zr.newRandomElement().immutable
            val randomizationElements = GrothSahai.tToRandomizationElements(randomT)
            val transactionDetails = userWallet.spendEuro(randomizationElements)
            val isValid = Transaction.validate(transactionDetails!!)
            Assert.assertTrue(isValid)
            userWallet.addToWallet(transactionDetails, randomT)
        }

        // The used Euro should have 50 transaction proofs now
        val resultingEuroProofs = userWallet.euros.first().digitalEuro.proofs
        Assert.assertEquals("The used Euro should have $amountOfTransactions proofs now",amountOfTransactions, resultingEuroProofs.size)
    }


}
