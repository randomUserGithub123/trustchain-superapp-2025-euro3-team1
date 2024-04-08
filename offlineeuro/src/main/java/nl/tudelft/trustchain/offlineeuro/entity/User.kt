package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity

enum class CommunicationState {
    INPROGRESS,
    COMPLETE,
    FAILED
}

class User (
    var name: String,
    private val context: Context?,
)
{
    val privateKey: Element
    val publicKey: Element
    val wallet: Wallet
    val groupDescription: BilinearGroup
    val crs: CRS

    init {
        groupDescription = CentralAuthority.groupDescription
        privateKey = groupDescription.getRandomZr()
        publicKey = groupDescription.g.powZn(privateKey)
        CentralAuthority.registerUser(name, publicKey)
        crs = CentralAuthority.crs
        wallet = Wallet(privateKey, publicKey)
    }

    // TODO Cleaner solution for this
    var communicationState: CommunicationState = CommunicationState.COMPLETE

    fun registerWithBank(bankName: String, community: OfflineEuroCommunity, userName: String): Boolean {
        //TODO
        return false
    }

    fun sendTokenToRandomPeer(community: OfflineEuroCommunity, keepToken: Boolean = false): Boolean {
        //TODO
        return false
    }


//    fun getBalance(): Int {
//        return ownedTokenManager.getAllTokens().size
//    }
//
//    fun removeAllTokens() {
//        ownedTokenManager.removeAllTokens()
//    }
}
