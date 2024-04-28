package nl.tudelft.trustchain.offlineeuro.db

import app.cash.sqldelight.db.SqlDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.libraries.GrothSahaiSerializer
import nl.tudelft.trustchain.offlineeuro.libraries.SchnorrSignatureSerializer

open class OfflineEuroManager(
    private val bilinearGroup: BilinearGroup,
    driver: SqlDriver,
) {

    private val gSS = GrothSahaiSerializer
    private val sSS = SchnorrSignatureSerializer
    protected val database: Database = Database(driver)

    protected fun serialize(grothSahaiProofs: List<GrothSahaiProof>): ByteArray? {
        return gSS.serializeGrothSahaiProofs(grothSahaiProofs)
    }

    protected fun serialize(schnorrSignature: SchnorrSignature?): ByteArray? {
        return sSS.serializeSchnorrSignature(schnorrSignature)
    }


    protected fun deserializeGSP(byteArray: ByteArray?) : ArrayList<GrothSahaiProof> {
        return gSS.deserializeProofBytes(byteArray, bilinearGroup)
    }

    protected fun deserializeSchnorr(byteArray: ByteArray?): SchnorrSignature? {
        return sSS.deserializeSchnorrSignatureBytes(byteArray)
    }
}
