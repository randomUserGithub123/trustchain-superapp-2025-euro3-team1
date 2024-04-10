package nl.tudelft.trustchain.offlineeuro.db

import android.content.Context
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.offlineeuro.sqldelight.WalletQueries
import nl.tudelft.trustchain.offlineeuro.entity.BilinearGroup

class WalletManager (
    context: Context?,
    private val bilinearGroup: BilinearGroup,
    driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "wallet.db"),
) {

    private val database: Database = Database(driver)
    private val queries: WalletQueries = database.walletQueries

//    private val walletEntryMapper = {
//            serialNumber: ByteArray,
//            firstTheta: ByteArray,
//            signature: ByteArray,
//            previousProofs: ByteArray,
//            secretT: ByteArray,
//        ->
//        WalletEntry(
//            DigitalEuro(
//                BigInteger(serialNumber),
//                firstTheta,
//                BigInteger(signature),
//                previousProofs
//            ),
//            secretT
//        )
//    }
}
