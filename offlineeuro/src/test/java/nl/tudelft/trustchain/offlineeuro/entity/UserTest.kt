package nl.tudelft.trustchain.offlineeuro.entity

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahai
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import org.junit.Assert
import org.junit.Test

class UserTest {

    private val context = null
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
        Database.Schema.create(this)
    }
    private val group: BilinearGroup = CentralAuthority.groupDescription

    private val user: User
    private val bank: Bank

    init {
        CentralAuthority.initializeRegisteredUserManager(context, driver)
        user = User("IAmTheRichestUser", context)
        bank = Bank("BestTestBank", context)
    }

    private fun createTestUser(userName: String): User {
        return User (
            userName,
            context,
        )
    }

    private fun withdrawEuro(user: User) {
        user.withdrawDigitalEuro(bank)
    }

    private fun getRandomizationElements(): Pair<Element, RandomizationElements> {
        val randomT = group.pairing.zr.newRandomElement().immutable
        return GrothSahai.tToRandomizationElements(randomT)
    }
    @Test
    fun singleTransactionTest() {
        withdrawEuro(user)
        val userWallet = user.wallet
        val randomT = group.pairing.zr.newRandomElement().immutable
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT)
        val transactionDetails = userWallet.spendEuro(randomizationElements.second)
        val isValid = Transaction.validate(transactionDetails!!, bank.publicKey)
        Assert.assertTrue(isValid)
    }

    @Test
    fun twoTransactionsTest() {
        withdrawEuro(user)
        val userWallet = user.wallet

        val randomT = group.pairing.zr.newRandomElement().immutable
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT)
        val transactionDetails = userWallet.spendEuro(randomizationElements.second)
        val isValid = Transaction.validate(transactionDetails!!, bank.publicKey)
        Assert.assertTrue(isValid)

        userWallet.addToWallet(transactionDetails, randomT)
        val nextT = group.pairing.zr.newRandomElement().immutable
        val nextRandomizationElements = GrothSahai.tToRandomizationElements(nextT)
        val transactionDetails2 = userWallet.spendEuro(nextRandomizationElements.second)
        val isValid2 = Transaction.validate(transactionDetails2!!, bank.publicKey)
        Assert.assertTrue(isValid2)
    }

    @Test
    fun twentyTransactionsTest() {
        withdrawEuro(user)
        val userWallet = user.wallet
        val amountOfTransactions = 20

        for (i in 0 until amountOfTransactions) {
            val randomT = group.pairing.zr.newRandomElement().immutable
            val randomizationElements = GrothSahai.tToRandomizationElements(randomT)
            val transactionDetails = userWallet.spendEuro(randomizationElements.second)
            val isValid = Transaction.validate(transactionDetails!!, bank.publicKey)
            Assert.assertTrue(isValid)
            userWallet.addToWallet(transactionDetails, randomT)
        }

        // The used Euro should have 50 transaction proofs now
        val resultingEuroProofs = userWallet.euros.first().digitalEuro.proofs
        Assert.assertEquals("The used Euro should have $amountOfTransactions proofs now",amountOfTransactions, resultingEuroProofs.size)
    }

    @Test
    fun revokeAnonymityTest() {
        withdrawEuro(user)
        val userWallet = user.wallet

        val randomT = group.pairing.zr.newRandomElement().immutable
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT)
        val transactionDetails = userWallet.spendEuro(randomizationElements.second)
        val foundPK = CentralAuthority.getUserFromProof(transactionDetails!!.currentTransactionProof.grothSahaiProof)

        Assert.assertNotNull(foundPK)
        Assert.assertEquals(user.publicKey, foundPK)
    }

    @Test
    fun doubleSpendingDetectionSimple() {
        withdrawEuro(user)
        val userWallet = user.wallet

        val randomT = group.pairing.zr.newRandomElement().immutable
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT)
        val transactionDetails = userWallet.spendEuro(randomizationElements.second)
        val isValid = Transaction.validate(transactionDetails!!, bank.publicKey)
        Assert.assertTrue(isValid)
        userWallet.addToWallet(transactionDetails, randomT)

        val nextT = group.pairing.zr.newRandomElement().immutable
        val nextRandomizationElements = GrothSahai.tToRandomizationElements(nextT)
        val transactionDetails2 = userWallet.doubleSpendEuro(nextRandomizationElements.second)
        val isValid2 = Transaction.validate(transactionDetails2!!, bank.publicKey)
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
