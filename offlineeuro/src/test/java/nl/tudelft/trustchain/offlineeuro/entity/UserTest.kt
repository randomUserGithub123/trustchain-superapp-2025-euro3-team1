package nl.tudelft.trustchain.offlineeuro.entity

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahai
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import kotlin.system.measureTimeMillis

class UserTest {

    private val context = null
    private val community: OfflineEuroCommunity = Mockito.mock(OfflineEuroCommunity::class.java)
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
        Database.Schema.create(this)
    }
    private val group: BilinearGroup = CentralAuthority.groupDescription

    private val bank: Bank

    init {
        CentralAuthority.initializeRegisteredUserManager(context, driver)

        bank = Bank("BestTestBank", context, DepositedEuroManager(context, group, driver))
    }

    private fun createTestUser(userName: String): User {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
            Database.Schema.create(this)
        }
        val walletManager = WalletManager(context, group, driver)

        return User (
            userName,
            context,
            walletManager,
            IPV8CommunicationProtocol(AddressBookManager(context, group, driver), community)
        )
    }

    private fun withdrawEuro(user: User): DigitalEuro {
        return user.withdrawDigitalEuro(bank.name)
    }

    private fun getRandomizationElements(): Pair<Element, RandomizationElements> {
        val randomT = group.pairing.zr.newRandomElement().immutable
        return Pair(randomT, GrothSahai.tToRandomizationElements(randomT, group, CentralAuthority.crs))
    }
    @Test
    fun singleTransactionTest() {
        val user = createTestUser("User1")
        withdrawEuro(user)
        val userWallet = user.wallet
        val randomT = group.pairing.zr.newRandomElement().immutable
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT, group, CentralAuthority.crs)
        val transactionDetails = userWallet.spendEuro(randomizationElements)

        val timeInMillis = measureTimeMillis {
        val isValid = Transaction.validate(transactionDetails!!, bank.publicKey)
        Assert.assertTrue(isValid)
    }

    println("It took $timeInMillis ms")
    }

    @Test
    fun twoTransactionsTest() {
        val user1 = createTestUser("The Withdrawer")
        val user2 = createTestUser("The Receiver")
        val user3 = createTestUser("The depositor")
        val timeInMillis = measureTimeMillis {
            withdrawEuro(user1)
            Assert.assertTrue(requestNormalSpend(user1, user2))
            Assert.assertTrue(requestNormalSpend(user2, user3))
            val bankResponse = user3.sendDigitalEuroTo(bank.name)
            Assert.assertEquals(bankResponse, "Deposit was successful!")
        }

        println("It took $timeInMillis ms")
    }

    @Test
    fun twentyTransactionsTest() {
        val user = createTestUser("User1")
        val initialEuro = withdrawEuro(user)
        println("Initial size = ${initialEuro.sizeInBytes()}")
        val userWallet = user.wallet
        val amountOfTransactions = 20

        for (i in 0 until amountOfTransactions) {
            val randomT = group.pairing.zr.newRandomElement().immutable
            val randomizationElements = GrothSahai.tToRandomizationElements(randomT, group, CentralAuthority.crs)
            val transactionDetails = userWallet.spendEuro(randomizationElements)
            val isValid = Transaction.validate(transactionDetails!!, bank.publicKey)
            Assert.assertTrue(isValid)

            userWallet.addToWallet(transactionDetails, randomT)
        }

        // The used Euro should have 20 transaction proofs now
        val resultingEuroProofs = userWallet.getWalletEntryToSpend()?.digitalEuro?.proofs
        Assert.assertEquals("The used Euro should have $amountOfTransactions proofs now",amountOfTransactions, resultingEuroProofs?.size)
    }

    @Test
    fun revokeAnonymityTest() {
        val user1 = createTestUser("User1")
        withdrawEuro(user1)

        val randomT = group.pairing.zr.newRandomElement().immutable
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT, group, CentralAuthority.crs)
        val transactionDetails = user1.wallet.spendEuro(randomizationElements)
        val foundPK = CentralAuthority.getUserFromProof(transactionDetails!!.currentTransactionProof.grothSahaiProof)

        Assert.assertNotNull(foundPK)
        Assert.assertEquals(user1.publicKey, foundPK!!.publicKey)
        Assert.assertEquals(user1.name, foundPK.name)
    }

    @Test
    fun doubleSpendingDetectionSimple() {
        val user1 = createTestUser("User1")
        val user2 = createTestUser("User2")
        val user3 = createTestUser("User3")
        withdrawEuro(user1)
        requestNormalSpend(user1, user2)
        requestDoubleSpend(user1, user3)

        // Deposit the two Euros
        val firstDeposit = user2.sendDigitalEuroTo(bank.name)
        Assert.assertEquals(firstDeposit, "Deposit was successful!")
        val secondDeposit = user3.sendDigitalEuroTo(bank.name)

        Assert.assertTrue(secondDeposit.contains("Double spending"))
        Assert.assertTrue(secondDeposit.contains("${user1.publicKey}"))
    }

    @Test
    fun sizeAfter50Transactions() {
        val user = createTestUser("User1")

        val initialEuro = withdrawEuro(user)
        val userWallet = user.wallet
        val amountOfTransactions = 10


        for (i in 0 until amountOfTransactions) {
            val randomT = group.pairing.zr.newRandomElement().immutable
            val randomizationElements = GrothSahai.tToRandomizationElements(randomT, group, CentralAuthority.crs)
            val transactionDetails = userWallet.spendEuro(randomizationElements)
            val isValid = Transaction.validate(transactionDetails!!, bank.publicKey)
            Assert.assertTrue(isValid)
            userWallet.addToWallet(transactionDetails, randomT)
        }

        // The used Euro should have 51 transaction proofs now
        val resultingEuroProofs = userWallet.getWalletEntryToSpend()?.digitalEuro?.proofs
        val timeInMillis = measureTimeMillis {
            user.sendDigitalEuroTo(bank.name)
        }
        println("Verifying the deposit took $timeInMillis ms")
        Assert.assertEquals("The used Euro should have $amountOfTransactions proofs now",amountOfTransactions, resultingEuroProofs?.size)
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

        Assert.assertTrue("The transaction should be valid", requestNormalSpend(user1, user5))
        Assert.assertTrue("The transaction should be valid", requestNormalSpend(user2, user3))
        Assert.assertTrue("The transaction should be valid", requestNormalSpend(user3, user4))
        Assert.assertTrue("The transaction should be valid", requestNormalSpend(user4, user1))
        Assert.assertTrue("The transaction should be valid", requestNormalSpend(user5, user2))
        Assert.assertTrue("The transaction should be valid", requestDoubleSpend(user3, user4))
        Assert.assertTrue("The transaction should be valid", requestNormalSpend(user1, user2))
        Assert.assertTrue("The transaction should be valid", requestNormalSpend(user4, user2))
        Assert.assertTrue("The transaction should be valid", requestNormalSpend(user3, user1))

        val responses = depositAllEuros(userList)

        Assert.assertEquals(4, responses.size) // 3 euros 1 double spend

        val successFilter = responses.filter { it == "Deposit was successful!" }
        Assert.assertEquals(3, successFilter.size)

        val doubleSpendFilter = responses.filter { it.contains("Double spending detected.") }
        Assert.assertEquals(1, doubleSpendFilter.size)
        Assert.assertTrue(doubleSpendFilter.first().contains(user3.publicKey.toString()))
        Assert.assertTrue(doubleSpendFilter.first().contains(user3.name))
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
            val eurosToDeposit = user.wallet.getAllWalletEntriesToSpend().count()
            for (i in 0 until eurosToDeposit) {
                val response = user.sendDigitalEuroTo(bank.name)
                bankResponses.add(response)
            }
        }
        return bankResponses

    }

}
