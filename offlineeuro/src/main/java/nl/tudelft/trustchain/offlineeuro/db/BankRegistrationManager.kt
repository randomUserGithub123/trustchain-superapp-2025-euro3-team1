package nl.tudelft.trustchain.offlineeuro.db

import android.content.Context
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.entity.BankDetails
import nl.tudelft.trustchain.offlineeuro.entity.BankRegistration
import org.sqlite.SQLiteException
import java.math.BigInteger

class BankRegistrationManager (
    context: Context?,
    driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "bank_registrations.db"),
) {
    private val database = Database(driver)
    private val queries = database.dbOfflineEuroQueries

    init {
        queries.createBankRegistrationTable()
    }

    private val bankRegistrationMapper = {
            id: Long,
            bankName: String,
            z: ByteArray,
            eb: ByteArray,
            nb: ByteArray,
            publicKey: ByteArray,
            m: ByteArray?,
            rm: ByteArray?,
            v: ByteArray?,
            r: ByteArray?,
            ->
        BankRegistration(
            id,
            BankDetails(
                bankName,
                BigInteger(z),
                BigInteger(eb),
                BigInteger(nb),
                publicKey
            ),
            if (m != null) BigInteger(m) else null,
            if (rm != null) BigInteger(rm) else null,
            if (v != null) BigInteger(v) else null,
            if (r != null) BigInteger(r) else null,
        )
    }

    fun addNewBank(bankDetails: BankDetails): Boolean {
        try {
            database.dbOfflineEuroQueries.addNewBank(
                bankDetails.name,
                bankDetails.z.toByteArray(),
                bankDetails.eb.toByteArray(),
                bankDetails.nb.toByteArray(),
                bankDetails.publicKeyBytes
            )
        } catch (e: SQLiteException) {
            return false
        }
        return true
    }

    fun getBankRegistrationByName(name: String): BankRegistration? {
       return queries.getBankRegistrationByName(name, bankRegistrationMapper).executeAsOneOrNull()
    }

    fun getBankById(id: Long): BankRegistration? {
        return queries.getBankRegistrationById(id, bankRegistrationMapper).executeAsOneOrNull()
    }

    fun setOwnValuesForBank(bankName: String, m: BigInteger, rm: BigInteger): Boolean {
        var result = true

        database.transaction {
            queries.setOwnValuesForBank(
                m.toByteArray(),
                rm.toByteArray(),
                bankName)

            val rowsAffected = queries.getChanges().executeAsOne().toInt()

            // Only one record should be affected
            if (rowsAffected != 1) {
                result = false
                this.rollback()
            }
        }

        return result
    }

    fun setBankRegistrationValues(bankName: String, v: BigInteger, r: BigInteger): Boolean {

        var result = true
        database.transaction {
            queries.setBankRegistrationValues(
                v.toByteArray(),
                r.toByteArray(),
                bankName
            )
            val rowsAffected = queries.getChanges().executeAsOne().toInt()

            if (rowsAffected != 1) {
                result = false
                this.rollback()
            }
        }
        return result
    }

    fun clearAllRegisteredBanks() {
        queries.clearAllRegisteredBanks()
    }

    fun getBankRegistrations(): List<BankRegistration> {
        return queries.getBankRegistrations(bankRegistrationMapper).executeAsList()
    }

}
