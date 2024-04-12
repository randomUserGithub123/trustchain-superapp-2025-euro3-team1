package nl.tudelft.trustchain.offlineeuro.db

import android.content.Context
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.offlineeuro.sqldelight.WalletQueries
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuro
import nl.tudelft.trustchain.offlineeuro.entity.WalletEntry
import nl.tudelft.trustchain.offlineeuro.libraries.GrothSahaiSerializer
import nl.tudelft.trustchain.offlineeuro.libraries.SchnorrSignatureSerializer

class WalletManager (
    context: Context?,
    private val bilinearGroup: BilinearGroup,
    driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "wallet.db"),
) {

    private val database: Database = Database(driver)
    private val queries: WalletQueries = database.walletQueries

    private val walletEntryMapper = {
            serialNumber: String,
            firstTheta: ByteArray,
            signature: ByteArray,
            previousProofs: ByteArray,
            secretT: ByteArray,
            transactionSignature: ByteArray
        ->
        WalletEntry(
            DigitalEuro(
                serialNumber,
                bilinearGroup.gElementFromBytes(firstTheta),
                SchnorrSignatureSerializer.deserializeProofBytes(signature),
                GrothSahaiSerializer.deserializeProofBytes(previousProofs, bilinearGroup)
            ),
            bilinearGroup.zrElementFromBytes(secretT),
            SchnorrSignatureSerializer.deserializeProofBytes(transactionSignature)
        )
    }
}
