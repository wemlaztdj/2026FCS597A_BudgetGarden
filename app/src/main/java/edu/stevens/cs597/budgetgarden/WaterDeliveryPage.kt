
package edu.stevens.cs597.budgetgarden

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import edu.stevens.cs597.budgetgarden.data.TransactionRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WaterDeliveryPage(
    private val root: View,
    private val gardenApp: GardenBudgetApp,
    private val scope: CoroutineScope
) {

    fun bind() {
        val container = root.findViewById<LinearLayout>(
            R.id.upcoming_transactions_container
        )

        val status = root.findViewById<TextView>(
            R.id.upcoming_status_text
        )

        scope.launch {
            try {
                // Wait until database initialization finishes.
                gardenApp.awaitDatabaseReady()

                // Retrieve future transactions.
                val now = System.currentTimeMillis()
                val transactions =
                    gardenApp.dao.upcomingTransactions(now)

                container.removeAllViews()

                // Display an empty message if nothing is scheduled.
                if (transactions.isEmpty()) {
                    status.setText(
                        R.string.upcoming_transactions_empty
                    )
                    status.visibility = View.VISIBLE
                    return@launch
                }

                // Hide loading message.
                status.visibility = View.GONE

                // Create one UI row per transaction.
                val inflater = LayoutInflater.from(root.context)

                for (transaction in transactions) {
                    val row = inflater.inflate(
                        R.layout.item_transaction,
                        container,
                        false
                    )

                    TransactionRowRenderer.render(row, transaction)

                    container.addView(row)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(
                    "WaterDeliveryPage",
                    "Could not load upcoming transactions",
                    e
                )

                container.removeAllViews()
                status.setText(
                    R.string.upcoming_transactions_error
                )
                status.visibility = View.VISIBLE
            }
        }
    }
}
