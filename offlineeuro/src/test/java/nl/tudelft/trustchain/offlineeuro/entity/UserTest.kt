package nl.tudelft.trustchain.offlineeuro.entity

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.db.BankRegistrationManager
import nl.tudelft.trustchain.offlineeuro.db.DepositedTokenManager
import nl.tudelft.trustchain.offlineeuro.db.OwnedTokenManager
import nl.tudelft.trustchain.offlineeuro.db.ReceiptManager
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager
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

    val usedSignatures = arrayListOf<BigInteger>()
    // Create a bank and a user with an in memory database
    val bank = Bank(
        "BestTestBank",
        context,
        RegisteredUserManager(context, driver),
        DepositedTokenManager(context, driver)
    )

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


    private fun createTestUser(userName: String): User {
        return User (
            userName,
            context,
            OwnedTokenManager(context, driver),
            BankRegistrationManager(context, driver),
            UnsignedTokenManager(context, driver),
            ReceiptManager(context, driver)
        )
    }

    private fun generateNewDigitalEuro(): DigitalEuro {
        var signature = Cryptography.generateRandomBigInteger(BigInteger("9999999999"))

        while (usedSignatures.contains(signature))
            signature = Cryptography.generateRandomBigInteger(BigInteger("9999999999"))

        return DigitalEuro(arrayListOf<GrothSahaiProof>(), signature)
    }

    private fun withdrawEuro(user: User) {
        user.wallet.addToWallet(generateNewDigitalEuro(), null)
    }

    private fun getRandomizationElements(): Pair<Element, RandomizationElements> {
        val randomT = group.pairing.zr.newRandomElement().immutable
        return GrothSahai.tToRandomizationElements(randomT)
    }
    @Test
    fun singleTransactionTest() {
        val bankSignature = BigInteger("12451241252134612")
        val digitalEuro = DigitalEuro(arrayListOf<GrothSahaiProof>(), bankSignature)
        val userWallet = user.wallet
        userWallet.addToWallet(digitalEuro, null)

        val randomT = group.pairing.zr.newRandomElement().immutable
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT)
        val transactionDetails = userWallet.spendEuro(randomizationElements.second)
        val isValid = Transaction.validate(transactionDetails!!)

        Assert.assertTrue(isValid)
    }

    @Test
    fun twoTransactionsTest() {
        val bankSignature = BigInteger("12451241252134612")
        val digitalEuro = DigitalEuro(arrayListOf<GrothSahaiProof>(), bankSignature)
        val userWallet = user.wallet
        userWallet.addToWallet(digitalEuro, null)

        val randomT = group.pairing.zr.newRandomElement().immutable
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT)
        val transactionDetails = userWallet.spendEuro(randomizationElements.second)
        val isValid = Transaction.validate(transactionDetails!!)
        Assert.assertTrue(isValid)

        userWallet.addToWallet(transactionDetails, randomT)
        val nextT = group.pairing.zr.newRandomElement().immutable
        val nextRandomizationElements = GrothSahai.tToRandomizationElements(nextT)
        val transactionDetails2 = userWallet.spendEuro(nextRandomizationElements.second)
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
            val transactionDetails = userWallet.spendEuro(randomizationElements.second)
            val isValid = Transaction.validate(transactionDetails!!)
            Assert.assertTrue(isValid)
            userWallet.addToWallet(transactionDetails, randomT)
        }

        // The used Euro should have 50 transaction proofs now
        val resultingEuroProofs = userWallet.euros.first().digitalEuro.proofs
        Assert.assertEquals("The used Euro should have $amountOfTransactions proofs now",amountOfTransactions, resultingEuroProofs.size)
    }

    @Test
    fun revokeAnonymityTest() {
        val bankSignature = BigInteger("12451241252134612")
        val digitalEuro = DigitalEuro(arrayListOf<GrothSahaiProof>(), bankSignature)
        val userWallet = Wallet(user.privateKey, user.publicKey)
        userWallet.addToWallet(digitalEuro, null)

        val randomT = group.pairing.zr.newRandomElement().immutable
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT)
        val transactionDetails = userWallet.spendEuro(randomizationElements.second)
        val foundPK = CentralAuthority.getUserFromProof(transactionDetails!!.currentProofs.first.grothSahaiProof)

        Assert.assertNotNull(foundPK)
        Assert.assertEquals(user.publicKey, foundPK)
    }

    @Test
    fun doubleSpendingDetectionSimple() {
        val bankSignature = BigInteger("12451241252134612")
        val digitalEuro = DigitalEuro(arrayListOf<GrothSahaiProof>(), bankSignature)
        val userWallet = Wallet(user.privateKey, user.publicKey)
        userWallet.addToWallet(digitalEuro, null)

        val randomT = group.pairing.zr.newRandomElement().immutable
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT)
        val transactionDetails = userWallet.spendEuro(randomizationElements.second)
        val isValid = Transaction.validate(transactionDetails!!)
        Assert.assertTrue(isValid)
        userWallet.addToWallet(transactionDetails, randomT)

        val nextT = group.pairing.zr.newRandomElement().immutable
        val nextRandomizationElements = GrothSahai.tToRandomizationElements(nextT)
        val transactionDetails2 = userWallet.doubleSpendEuro(nextRandomizationElements.second)
        val isValid2 = Transaction.validate(transactionDetails2!!)
        Assert.assertTrue(isValid2)
        userWallet.addToWallet(transactionDetails2, randomT)
        // Deposit the two Euros
        val firstDeposit = userWallet.depositEuro(bank)

        Assert.assertEquals(firstDeposit, "Deposit was successful!")
        val secondDeposit = userWallet.depositEuro(bank)

        Assert.assertTrue(secondDeposit.contains("Double spending"))
        Assert.assertTrue(secondDeposit.contains("${user.publicKey}"))
    }

    @Test
    fun doubleSpendingMultipleUsers() {
        val user1 = createTestUser("User1")
        val user2 = createTestUser("User2")
        val user3 = createTestUser("User3")
        val user4 = createTestUser("User4")
        val user5 = createTestUser("User5")

        val userList = arrayListOf(user1, user2, user3, user4, user5)

        withdrawEuro(user1)
        withdrawEuro(user2)
        withdrawEuro(user3)

        requestNormalSpend(user1, user5)
        requestNormalSpend(user2, user3)
        requestNormalSpend(user3, user4)
        requestNormalSpend(user4, user1)
        requestNormalSpend(user5, user2)
        requestDoubleSpend(user3, user4)
        requestNormalSpend(user5, user2)
        requestNormalSpend(user4, user2)
        requestNormalSpend(user3, user1)

        val responses = depositAllEuros(userList)

        Assert.assertEquals(4, responses.size) // 3 euros 1 double spend

        val successFilter = responses.filter { it == "Deposit was successful!" }
        Assert.assertEquals(3, successFilter.size)

        val doubleSpendFilter = responses.filter { it.contains("Double spending detected.") }
        Assert.assertEquals(1, doubleSpendFilter.size)
        Assert.assertTrue(doubleSpendFilter.first().contains(user3.publicKey.toString()))
    }

    private fun requestNormalSpend(userFrom: User, userTo: User): Boolean {
        val randomizationElements = getRandomizationElements()
        val transactionDetails = userFrom.wallet.spendEuro(randomizationElements.second) ?: return false

        userTo.wallet.addToWallet(transactionDetails, randomizationElements.first)
        return true
    }

    private fun requestDoubleSpend(userFrom: User, userTo: User): Boolean {
        val randomizationElements = getRandomizationElements()
        val transactionDetails = userFrom.wallet.doubleSpendEuro(randomizationElements.second) ?: return false

        userTo.wallet.addToWallet(transactionDetails, randomizationElements.first)
        return true
    }

    private fun depositAllEuros(userList: List<User>): ArrayList<String> {
        val bankResponses = arrayListOf<String>()

        for (user in userList) {
            while (user.wallet.euros.isNotEmpty()) {
                val euroToDeposit = user.wallet.euros.removeAt(0)

                val response = bank.depositEuro(euroToDeposit.digitalEuro)
                bankResponses.add(response)
            }
        }
        return bankResponses

    }

}
