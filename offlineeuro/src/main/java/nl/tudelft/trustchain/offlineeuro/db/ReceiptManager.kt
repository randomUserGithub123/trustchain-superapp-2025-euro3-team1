package nl.tudelft.trustchain.offlineeuro.db

import android.content.Context
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.entity.ReceiptEntry
import nl.tudelft.trustchain.offlineeuro.entity.Token
import java.math.BigInteger

class ReceiptManager(
    context: Context?,
    driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "receipts.db"),
) {

    private val database: Database = Database(driver)
    private val receiptsMapper = {
            u: ByteArray,
            g: ByteArray,
            a: ByteArray,
            r: ByteArray,
            aPrime: ByteArray,
            t: String,
            challenge: ByteArray,
            gamma: ByteArray,
            bankId: Long
        ->
        ReceiptEntry(
            Token(
                BigInteger(u),
                BigInteger(g),
                BigInteger(a),
                BigInteger(r),
                BigInteger(aPrime),
                t
            ),
            BigInteger(challenge),
            BigInteger(gamma),
            bankId
        )
    }

    init {
        database.dbOfflineEuroQueries.createOwnedTokenTable()
    }
    fun getAllReceipts(): List<ReceiptEntry> {
        return database.dbOfflineEuroQueries.getAllReceipts(receiptsMapper).executeAsList()
    }

    fun getAllReceiptsByBankId(bankId: Long): List<ReceiptEntry> {
        return database.dbOfflineEuroQueries.getReceiptsByBankId(bankId, receiptsMapper).executeAsList()
    }


    fun addReceipt(receipt: ReceiptEntry) {
        val token = receipt.token
        return database.dbOfflineEuroQueries.addReceipt(
            token.u.toByteArray(),
            token.g.toByteArray(),
            token.a.toByteArray(),
            token.r.toByteArray(),
            token.aPrime.toByteArray(),
            token.t,
            receipt.challenge.toByteArray(),
            receipt.gamma.toByteArray(),
            receipt.bankId
        )
    }
}
