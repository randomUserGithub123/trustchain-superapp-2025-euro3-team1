package nl.tudelft.trustchain.offlineeuro.libraries

import android.content.Context
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.RegisteredUser

object TableHelpers {
    fun removeAllButFirstRow(table: TableLayout) {
        val rowCount = table.childCount
        val rowsToBeRemoved = rowCount - 1

        for (i in rowsToBeRemoved downTo 1) {
            val row = table.getChildAt(i)
            table.removeView(row)
        }
    }

    fun addRegisteredUsersToTable(
        table: TableLayout,
        users: List<RegisteredUser>
    ) {
        val context = table.context
        for (user in users) {
            table.addView(registeredUserToTableRow(user, context))
        }
    }

    fun addAddressesToTable(
        table: TableLayout,
        addresses: List<Address>
    ) {
        val context = table.context
        for (address in addresses) {
            table.addView(addressToTableRow(address, context))
        }
    }

    private fun registeredUserToTableRow(
        user: RegisteredUser,
        context: Context
    ): TableRow {
        val tableRow = TableRow(context)

        val idField = TextView(context)
        idField.text = user.id.toString()

        val nameField = TextView(context)
        nameField.text = user.name

        val publicKeyField = TextView(context)
        publicKeyField.text = user.publicKey.toString()

        tableRow.addView(idField)
        tableRow.addView(nameField)
        tableRow.addView(publicKeyField)
        return tableRow
    }

    private fun addressToTableRow(
        address: Address,
        context: Context
    ): TableRow {
        val tableRow = TableRow(context)

        val nameField = TextView(context)
        nameField.text = address.name

        val roleField = TextView(context)
        roleField.text = address.type.toString()

        val publicKeyField = TextView(context)
        publicKeyField.text = address.publicKey.toString()

        tableRow.addView(nameField)
        tableRow.addView(roleField)
        tableRow.addView(publicKeyField)
        return tableRow
    }
}
