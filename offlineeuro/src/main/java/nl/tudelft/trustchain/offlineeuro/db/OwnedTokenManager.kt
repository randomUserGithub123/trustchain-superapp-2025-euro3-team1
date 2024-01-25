package nl.tudelft.trustchain.offlineeuro.db

import android.content.Context
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.entity.Token
import nl.tudelft.trustchain.offlineeuro.entity.TokenEntry
import java.math.BigInteger

class OwnedTokenManager(
    context: Context?,
    private val driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "owned_tokens.db"),
    ) {

    private val database: Database = Database(driver)
    private val ownedTokenMapper = {
            id: Long,
            u: ByteArray,
            g: ByteArray,
            a: ByteArray,
            r: ByteArray,
            aPrime: ByteArray,
            t: String,
            w: ByteArray,
            y: ByteArray
        ->
        TokenEntry(
            id.toInt(),
            Token(
                BigInteger(u),
                BigInteger(g),
                BigInteger(a),
                BigInteger(r),
                BigInteger(aPrime),
                t
            ),
            BigInteger(w),
            BigInteger(y)
        )
    }

    fun getAllTokens(): List<TokenEntry> {
        return database.dbOfflineEuroQueries.getAllTokens(ownedTokenMapper).executeAsList()
    }
}
