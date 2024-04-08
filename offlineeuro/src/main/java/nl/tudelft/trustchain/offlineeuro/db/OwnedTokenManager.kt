//package nl.tudelft.trustchain.offlineeuro.db
//
//import android.content.Context
//import com.squareup.sqldelight.android.AndroidSqliteDriver
//import com.squareup.sqldelight.db.SqlDriver
//import nl.tudelft.offlineeuro.sqldelight.Database
//import java.math.BigInteger
//
//class OwnedTokenManager(
//    context: Context?,
//    driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "owned_tokens.db"),
//    ) {
//
//    private val database: Database = Database(driver)
//    private val ownedTokenMapper = {
//            id: Long,
//            u: ByteArray,
//            g: ByteArray,
//            a: ByteArray,
//            r: ByteArray,
//            aPrime: ByteArray,
//            t: String,
//            w: ByteArray,
//            y: ByteArray,
//            bankId: Long
//        ->
//        TokenEntry(
//            id,
//            Token(
//                BigInteger(u),
//                BigInteger(g),
//                BigInteger(a),
//                BigInteger(r),
//                BigInteger(aPrime),
//                t
//            ),
//            BigInteger(w),
//            BigInteger(y),
//            bankId
//        )
//    }
//
//    init {
//        database.dbOfflineEuroQueries.createOwnedTokenTable()
//    }
//    fun getAllTokens(): List<TokenEntry> {
//        return database.dbOfflineEuroQueries.getAllTokens(ownedTokenMapper).executeAsList()
//    }
//
//    fun addToken(token: Token, w: BigInteger, y: BigInteger, bankId: Long) {
//        return database.dbOfflineEuroQueries.addToken(
//            token.u.toByteArray(),
//            token.g.toByteArray(),
//            token.a.toByteArray(),
//            token.r.toByteArray(),
//            token.aPrime.toByteArray(),
//            token.t,
//            w.toByteArray(),
//            y.toByteArray(),
//            bankId
//        )
//    }
//
//    fun removeTokenById(id: Long) {
//        database.dbOfflineEuroQueries.removeTokenById(id)
//    }
//
//    fun removeAllTokens() {
//        database.dbOfflineEuroQueries.removeAllTokens()
//    }
//}
