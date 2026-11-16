package edu.stevens.cs597.budgetgarden

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class RecentTransactionsPage(
    private val root: View,
    private val gardenApp: GardenBudgetApp,
    private val scope: CoroutineScope
) {

    fun bind() {

        val container = root.findViewById<LinearLayout>(
            R.id.recent_transactions_container
        )

        val status = root.findViewById<TextView>(
            R.id.recent_status_text
        )

        scope.launch {
            try {
                // Wait for database initialization.
                gardenApp.awaitDatabaseReady()

                // Retrieve posted transactions.
                val now = System.currentTimeMillis()

                val transactions =
                    gardenApp.dao.postedTransactions(now)

                // Clear previously displayed rows.
                container.removeAllViews()

                // Check if there are any transactions.
                if (transactions.isEmpty()) {
                    status.setText(
                        R.string.recent_transactions_empty
                    )
                    status.visibility = View.VISIBLE
                    return@launch
                }

                // Hide loading message.
                status.visibility = View.GONE

                val inflater = LayoutInflater.from(root.context)

                // Display transactions.
                for (transaction in transactions) {

                    val row = inflater.inflate(
                        R.layout.item_transaction,
                        container,
                        false
                    )

                    // Reuse the shared renderer.
                    TransactionRowRenderer.render(
                        row,
                        transaction
                    )

                    container.addView(row)
                }

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                Log.e(
                    "RecentTransactionsPage",
                    "Could not load transactions",
                    e
                )

                container.removeAllViews()

                status.setText(
                    R.string.recent_transactions_error
                )
                status.visibility = View.VISIBLE
            }
        }
    }
}
