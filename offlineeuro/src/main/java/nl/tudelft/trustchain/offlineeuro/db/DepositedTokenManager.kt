package nl.tudelft.trustchain.offlineeuro.db

import android.content.Context
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.entity.Receipt
import nl.tudelft.trustchain.offlineeuro.entity.Token
import java.math.BigInteger

class DepositedTokenManager(
    context: Context?,
    private val driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "deposited_tokens.db"),
) {

    private val database: Database = Database(driver)

    private val depositedTokenMapper = {
            u: ByteArray,
            g: ByteArray,
            a: ByteArray,
            r: ByteArray,
            aPrime: ByteArray,
            t: String,
            gamma: ByteArray,
            challenge: ByteArray
        ->
        Receipt(
            Token(
                BigInteger(u),
                BigInteger(g),
                BigInteger(a),
                BigInteger(r),
                BigInteger(aPrime),
                t
            ),
            BigInteger(gamma),
            BigInteger(challenge)
        )
    }

    init {
        database.dbOfflineEuroQueries.createDepositedTokensTable()
    }
    fun depositToken(receipt: Receipt) {
        val (u, g, a, r, aPrime, t) = receipt.token
        database.dbOfflineEuroQueries.addDepositedToken(
            u.toByteArray(),
            g.toByteArray(),
            a.toByteArray(),
            r.toByteArray(),
            aPrime.toByteArray(),
            t,
            receipt.gamma.toByteArray(),
            receipt.challenge.toByteArray())
    }

    fun getAllReceipts(): List<Receipt> {
        return database.dbOfflineEuroQueries.getAllDepositedTokens(depositedTokenMapper).executeAsList()
    }
}
