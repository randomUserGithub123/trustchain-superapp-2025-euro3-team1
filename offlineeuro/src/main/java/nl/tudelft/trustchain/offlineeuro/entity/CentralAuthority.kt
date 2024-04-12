package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager

object CentralAuthority {

    val groupDescription: BilinearGroup = BilinearGroup()
    private val CRSPair = CRSGenerator.generateCRSMap(groupDescription)
    private val crsMap = CRSPair.second
    private var registeredUserManager: RegisteredUserManager? = null
    val crs = CRSPair.first

    fun initializeRegisteredUserManager(context: Context? = null, driver: JdbcSqliteDriver? = null) {

        // Check if the manager is already initialized
        if (registeredUserManager != null)
            return

        registeredUserManager = if (context != null)
            RegisteredUserManager(context, groupDescription)
        else if (driver != null)
            RegisteredUserManager(null, groupDescription, driver)
        else
            throw Exception("Pass either a Context or a JdbcSqliteDriver")
    }

    private fun checkManagerInitialized() {
        if (registeredUserManager == null) throw Exception("Initialize the manager first")
    }

    fun registerUser(name: String, publicKey: Element): Boolean {
        checkManagerInitialized()
        return registeredUserManager!!.addRegisteredUser(name, publicKey)
    }

    fun getUserFromProof(grothSahaiProof: GrothSahaiProof): Element? {
        val crsExponent = crsMap[crs.u]
        val publicKey = grothSahaiProof.c1.powZn(crsExponent!!.mul(-1)).mul(grothSahaiProof.c2).immutable
        checkManagerInitialized()
        val registeredUser = registeredUserManager!!.getRegisteredUserByPublicKey(publicKey)

        if (registeredUser == null) {
            val users = registeredUserManager!!.getAllRegisteredUsers()
            val count = users.count()
        }
        return registeredUser?.publicKey
    }

    fun getUserFromProofs(proofs: Pair<GrothSahaiProof, GrothSahaiProof>) : Element? {
        val firstPK = getUserFromProof(proofs.first)
        val secondPK = getUserFromProof(proofs.second)

        return if (firstPK == secondPK)
            firstPK
        else
            null
    }
}
