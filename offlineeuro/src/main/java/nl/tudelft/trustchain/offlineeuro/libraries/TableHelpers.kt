package nl.tudelft.trustchain.offlineeuro.libraries

import android.content.Context
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
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

    fun addRegisteredUsersToTable(table: TableLayout, users: List<RegisteredUser>) {
        val context = table.context
        for (user in users) {
            table.addView(registeredUserToTableRow(user, context))
        }
    }

    private fun registeredUserToTableRow(user: RegisteredUser, context: Context): TableRow {
        val tableRow = TableRow(context)

        val idField = TextView(context)
        idField.text = user.id.toString()

        val nameField = TextView(context)
        nameField.text = user.name

        tableRow.addView(idField)
        tableRow.addView(nameField)
        return tableRow
    }
}
