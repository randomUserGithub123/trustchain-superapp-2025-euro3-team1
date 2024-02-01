package nl.tudelft.trustchain.offlineeuro.db

import android.content.Context
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.entity.UnsignedToken
import nl.tudelft.trustchain.offlineeuro.entity.UnsignedTokenAdd
import nl.tudelft.trustchain.offlineeuro.entity.UnsignedTokenStatus
import java.math.BigInteger

class UnsignedTokenManager(
    context: Context?,
    driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "unsigned_tokens.db"),
) {

    private val database: Database = Database(driver)
    private val unsignedTokenMapper = {
            id: Long,
            a: ByteArray,
            c: ByteArray,
            bigA: ByteArray,
            beta1: ByteArray,
            beta2: ByteArray,
            l: ByteArray,
            u: ByteArray,
            g: ByteArray,
            y: ByteArray,
            w: ByteArray,
            bankId: Long,
            status: Long,
        ->
        UnsignedToken(
            id,
            BigInteger(a),
            BigInteger(c),
            BigInteger(bigA),
            BigInteger(beta1),
            BigInteger(beta2),
            BigInteger(l),
            BigInteger(u),
            BigInteger(g),
            BigInteger(y),
            BigInteger(w),
            bankId,
            UnsignedTokenStatus.fromInt(status.toInt())
        )
    }

    init {
        database.dbOfflineEuroQueries.createUnsignedTokenTable()
    }

    fun addUnsignedToken(unsignedTokenAdd: UnsignedTokenAdd): Long {
        val (a, c, bigA, beta1, beta2, l, u, g, y, w, bankId, status) = unsignedTokenAdd

        database.dbOfflineEuroQueries.addUnsignedToken(
            a.toByteArray(),
            c.toByteArray(),
            bigA.toByteArray(),
            beta1.toByteArray(),
            beta2.toByteArray(),
            l.toByteArray(),
            u.toByteArray(),
            g.toByteArray(),
            y.toByteArray(),
            w.toByteArray(),
            bankId,
            status.ordinal.toLong(),
        )
        return database.dbOfflineEuroQueries.getLastInsertedId().executeAsOne()
    }

    fun updateUnsignedTokenStatusById(newStatus: UnsignedTokenStatus, tokenId: Long) {
        database.dbOfflineEuroQueries.updateStatusById(newStatus.ordinal.toLong(), tokenId)
    }

    fun getUnsignedTokenByIds(ids: List<Long>): List<UnsignedToken> {
        return database.dbOfflineEuroQueries.getUnsignedTokensByIds(ids, unsignedTokenMapper).executeAsList()
    }

    fun getUnsignedTokenById(id: Long): UnsignedToken {
        return database.dbOfflineEuroQueries.getUnsignedTokensById(id, unsignedTokenMapper).executeAsOne()
    }
}
