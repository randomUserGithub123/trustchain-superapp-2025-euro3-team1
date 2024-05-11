package nl.tudelft.trustchain.offlineeuro.communication

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class IPV8CommunicationProtocolTest {

    private val context = null
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
        Database.Schema.create(this)
    }

    private val community: OfflineEuroCommunity = Mockito.mock(OfflineEuroCommunity::class.java)
    private val ttpBilinearGroup = BilinearGroup(pairingType = PairingTypes.FromFile)
    private val ttpCRS = CRSGenerator.generateCRSMap(ttpBilinearGroup)
    private val initialBilinearGroup = BilinearGroup(pairingType = PairingTypes.FromFile)
    private val addressBookManager = AddressBookManager(context, initialBilinearGroup, driver)
    private val mockMessageList = arrayListOf<ICommunityMessage>()
    private val iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

    @Before
    fun setup() {
        `when` (community.messageList).thenReturn(mockMessageList)
        `when`(community.getGroupDescriptionAndCRS()).then {
            val message = BilinearGroupCRSMessage(ttpBilinearGroup.toGroupElementBytes(), ttpCRS.first.toCRSBytes())
            community.messageList.add(message)
            null
        }


    }

    @Test
    fun getGroupDescriptionAndCRSTest() {
        val groupDescription = iPV8CommunicationProtocol.getGroupDescriptionAndCRS()
        Assert.assertEquals(ttpBilinearGroup.pairing, groupDescription.pairing)
        Assert.assertEquals(ttpBilinearGroup.g, groupDescription.g)
        Assert.assertEquals(ttpBilinearGroup.h, groupDescription.h)
        Assert.assertEquals(ttpBilinearGroup.gt, groupDescription.gt)
    }

}
