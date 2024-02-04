package nl.tudelft.trustchain.offlineeuro.db

import android.content.Context
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.entity.RegisteredUser
import org.sqlite.SQLiteException
import java.math.BigInteger

/**
 * An overlay for the *RegisteredUsers* table.
 *
 * This class can be used to interact with the database.
 *
 * @property Context the context of the application, can be null, if a driver is given.
 * @property driver the driver of the database, can be used to pass a custom driver
 */
class RegisteredUserManager (
    context: Context?,
    private val driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "registered_users.db"),
) {

    private val database: Database = Database(driver)
    private val registeredUserMapper = {
            id: Long,
            name: String,
            s: ByteArray,
            k: ByteArray,
            v: ByteArray,
            r: ByteArray,
        ->
        RegisteredUser(
            id.toInt(),
            name,
            BigInteger(s),
            BigInteger(k),
            BigInteger(v),
            BigInteger(r)
        )
    }

    /**
     * Creates the RegisteredUser table if it not yet exists.
     */
    init {
        database.dbOfflineEuroQueries.createRegisteredUserTable()
        database.dbOfflineEuroQueries.clearAllRegisteredUsers()
    }

    /**
     * Tries to add a new [RegisteredUser] to the table.
     *
     * @param user the user that should be registered. Its id will be omitted.
     * @return true iff registering the user is successful.
     */
    fun addRegisteredUser(user: RegisteredUser): Boolean {
        val (_, name, s, k, v, r) = user
        try {
            database.dbOfflineEuroQueries.addUser(
                name,
                s.toByteArray(),
                k.toByteArray(),
                v.toByteArray(),
                r.toByteArray()
            )
            return true
        }
        catch (e: SQLiteException) {
            // TODO Perhaps throw specific custom exception to indicate which property was not unique
            return false
        }
    }

    /**
     * Gets a [RegisteredUser] by its [name].
     *
     * @param name the name of the [RegisteredUser]
     * @return the [RegisteredUser] with the [name] or null if the user does not exist.
     */
    fun getRegisteredUserByName(name: String): RegisteredUser? {
        return database.dbOfflineEuroQueries.getUserByName(name, registeredUserMapper)
            .executeAsOneOrNull()
    }

    /**
     * Gets a [RegisteredUser] by its [w].
     *
     * @param w the name of the [RegisteredUser]
     * @return the [RegisteredUser] with the [w] or null if the user does not exist.
     */
    fun getRegisteredUserByW(w: BigInteger): RegisteredUser? {
        return database.dbOfflineEuroQueries.getUserByR(w.toByteArray(), registeredUserMapper)
            .executeAsOneOrNull()
    }
    /**
     * Gets the number of [RegisteredUser]s.
     *
     * @return The number of [RegisteredUser]s.
     */
    fun getUserCount(): Long {
        return  database.dbOfflineEuroQueries.getRegisteredUserCount().executeAsOne()
    }

    fun getAllRegisteredUsers(): List<RegisteredUser> {
        return database.dbOfflineEuroQueries.getAllRegisteredUsers(registeredUserMapper).executeAsList()
    }

    /**
     * Clears all the [RegisteredUser]s from the table.
     */
    fun clearAllRegisteredUsers() {
        database.dbOfflineEuroQueries.clearAllRegisteredUsers()
    }
}
