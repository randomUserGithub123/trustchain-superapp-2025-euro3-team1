package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager
import java.util.BitSet

class TTP(
    name: String = "TTP",
    group: BilinearGroup,
    communicationProtocol: ICommunicationProtocol,
    context: Context?,
    private val registeredUserManager: RegisteredUserManager = RegisteredUserManager(context, group),
    onDataChangeCallback: ((String?) -> Unit)? = null
) : Participant(communicationProtocol, name, onDataChangeCallback) {
    val crsMap: Map<Element, Element>
    private val bloomFilter: BloomFilter = BloomFilter(10000) // TTPs handle more transactions

    init {
        communicationProtocol.participant = this
        this.group = group
        val generatedCRS = CRSGenerator.generateCRSMap(group)
        this.crs = generatedCRS.first
        this.crsMap = generatedCRS.second
        generateKeyPair()
    }

    fun registerUser(
        name: String,
        publicKey: Element
    ): Boolean {
        val result = registeredUserManager.addRegisteredUser(name, publicKey)
        onDataChangeCallback?.invoke("Registered $name")
        return result
    }

    fun getRegisteredUsers(): List<RegisteredUser> {
        return registeredUserManager.getAllRegisteredUsers()
    }

    override fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String {
        TODO("Not yet implemented")
    }

    fun getUserFromProof(grothSahaiProof: GrothSahaiProof): RegisteredUser? {
        val crsExponent = crsMap[crs.u]
        val test = group.g.powZn(crsExponent)
        val publicKey =
            grothSahaiProof.c1.powZn(crsExponent!!.mul(-1)).mul(grothSahaiProof.c2).immutable

        return registeredUserManager.getRegisteredUserByPublicKey(publicKey)
    }

    fun getUserFromProofs(
        firstProof: GrothSahaiProof,
        secondProof: GrothSahaiProof
    ): String {
        val firstPK = getUserFromProof(firstProof)
        val secondPK = getUserFromProof(secondProof)

        return if (firstPK != null && firstPK == secondPK) {
            onDataChangeCallback?.invoke("Found proof that  ${firstPK.name} committed fraud!")
            "Double spending detected. Double spender is ${firstPK.name} with PK: ${firstPK.publicKey}"
        } else {
            onDataChangeCallback?.invoke("Invalid fraud request received!")
            "No double spending detected"
        }
    }

    override fun reset() {
        registeredUserManager.clearAllRegisteredUsers()
    }

    fun getBloomFilter(): BloomFilter {
        return bloomFilter
    }

    fun updateBloomFilter(newFilter: BloomFilter) {
        // Merge the new filter with our existing one
        val ourBytes = bloomFilter.toBytes()
        val theirBytes = newFilter.toBytes()
        
        // Create a new filter with the same parameters
        val mergedFilter = BloomFilter(bloomFilter.expectedElements, bloomFilter.falsePositiveRate)
        mergedFilter.bitSet.clear()
        
        // OR the two bit sets together
        mergedFilter.bitSet.or(BitSet.valueOf(ourBytes))
        mergedFilter.bitSet.or(BitSet.valueOf(theirBytes))
        
        // Update our filter
        bloomFilter.bitSet.clear()
        bloomFilter.bitSet.or(mergedFilter.bitSet)
    }
}
