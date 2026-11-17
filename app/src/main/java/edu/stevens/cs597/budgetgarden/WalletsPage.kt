package edu.stevens.cs597.budgetgarden

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

class WalletsPage(
    private val root: View,
    private val gardenApp: GardenBudgetApp,
    private val scope: CoroutineScope
) {

    fun bind(onNavigate: (String) -> Unit) {

        val totalText = root.findViewById<TextView>(
            R.id.total_wallet_balance_text
        )

        val walletContainer = root.findViewById<LinearLayout>(
            R.id.wallet_list_container
        )

        val emptyText = root.findViewById<TextView>(
            R.id.wallets_empty_text
        )

        root.findViewById<View>(
            R.id.add_funds_button
        ).setOnClickListener {
            onNavigate("add_wallet")
        }

        totalText.text = "Loading..."

        scope.launch {
            try {
                gardenApp.awaitDatabaseReady()

                val dao = gardenApp.dao
                val now = System.currentTimeMillis()

                // Get all active wallets
                val wallets = dao.getBudgets().filter {
                    it.type == "WALLET" && !it.isArchived
                }

                walletContainer.removeAllViews()

                emptyText.visibility =
                    if (wallets.isEmpty()) View.VISIBLE
                    else View.GONE

                var totalBalance = 0L

                for (wallet in wallets) {

                    // Get this wallet's current balance
                    val balance = dao.balanceAt(
                        wallet.id,
                        now
                    )

                    totalBalance += balance

                    // Create a row for the wallet
                    val row = LayoutInflater.from(root.context)
                        .inflate(
                            R.layout.item_wallet,
                            walletContainer,
                            false
                        )

                    row.findViewById<TextView>(
                        R.id.wallet_name_text
                    ).text = wallet.name

                    row.findViewById<TextView>(
                        R.id.wallet_amount_text
                    ).text = money(balance)

                    walletContainer.addView(row)
                }

                // Display sum of all wallet balances
                totalText.text = money(totalBalance)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("WalletsPage", "Failed to load wallets", e)
                totalText.text = "Unavailable"
                emptyText.text = "Could not load wallets."
                emptyText.visibility = View.VISIBLE
            }
        }
    }

    private fun money(cents: Long): String =
        NumberFormat.getCurrencyInstance(Locale.US)
            .format(BigDecimal.valueOf(cents, 2))
}
