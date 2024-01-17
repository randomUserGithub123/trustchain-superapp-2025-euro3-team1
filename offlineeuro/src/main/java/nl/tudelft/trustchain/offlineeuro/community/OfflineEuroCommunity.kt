package nl.tudelft.trustchain.offlineeuro.community

import android.content.Context
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.trustchain.common.eurotoken.TransactionRepository


class OfflineEuroCommunity(
    context: Context,
    settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : Community() {
    override val serviceId = "ffffd716494b474ea9f614a16a4da0aed6899aec"

    private lateinit var transactionRepository: TransactionRepository

    /**
     * The context used to access the shared preferences.
     */
    private var myContext : Context


    init {
        myContext = context
    }

    class Factory(
        private val context: Context,
        private val settings: TrustChainSettings,
        private val database: TrustChainStore,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<OfflineEuroCommunity>(OfflineEuroCommunity::class.java) {
        override fun create(): OfflineEuroCommunity {
            return OfflineEuroCommunity(context, settings, database, crawler)
        }
    }
}
