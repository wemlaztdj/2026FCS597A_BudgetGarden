package edu.stevens.cs597.budgetgarden

import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import android.app.DatePickerDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
class AddToWalletPage(
    private val root: View,
    private val gardenApp: GardenBudgetApp,
    private val scope: CoroutineScope
) {

    fun bind(onNavigate: (String) -> Unit) {

        val balanceText = root.findViewById<TextView>(
            R.id.wallet_balance_text
        )
        val walletSpinner = root.findViewById<Spinner>(
            R.id.wallet_spinner
        )
        val amountInput = root.findViewById<EditText>(
            R.id.wallet_amount_input
        )

        val memoInput = root.findViewById<EditText>(
            R.id.wallet_memo_input
        )
        val postingDateButton = root.findViewById<Button>(
            R.id.wallet_posting_date_button
        )

// Default posting date is today.
        val selectedDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dateFormat = SimpleDateFormat(
            "MMMM d, yyyy",
            Locale.US
        )

        fun updateDateButton() {
            postingDateButton.text = dateFormat.format(
                selectedDate.time
            )
        }

        updateDateButton()

        postingDateButton.setOnClickListener {

            val year = selectedDate.get(Calendar.YEAR)
            val month = selectedDate.get(Calendar.MONTH)
            val day = selectedDate.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(
                root.context,
                { _, selectedYear, selectedMonth, selectedDay ->

                    selectedDate.set(
                        selectedYear,
                        selectedMonth,
                        selectedDay
                    )

                    updateDateButton()
                },
                year,
                month,
                day
            ).show()
        }
        val saveButton = root.findViewById<Button>(
            R.id.save_wallet_button
        )

        val cancelButton = root.findViewById<Button>(
            R.id.cancel_wallet_button
        )

        // Display the current Wallet balance

        scope.launch {
            try {
                // Wait for database initialization
                gardenApp.awaitDatabaseReady()

                // Get all active wallets
                val wallets = gardenApp.dao.getBudgets().filter {
                    it.type == "WALLET" && !it.isArchived
                }

                if (wallets.isEmpty()) {
                    balanceText.text = root.context.getString(
                        R.string.wallet_no_wallets
                    )
                    saveButton.isEnabled = false
                    return@launch
                }

                // Get the wallet names
                val walletNames = wallets.map { it.name }

                // Create the Spinner adapter
                val adapter = ArrayAdapter(
                    root.context,
                    android.R.layout.simple_spinner_item,
                    walletNames
                )

                adapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
                )

                // Connect adapter to Spinner
                walletSpinner.adapter = adapter

                // Update balance when user selects a Wallet
                walletSpinner.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {

                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                        ) {
                            val selectedWallet = wallets[position]

                            scope.launch {
                                val balance = gardenApp.dao.balanceAt(
                                    selectedWallet.id,
                                    System.currentTimeMillis()
                                )

                                balanceText.text = formatMoney(balance)
                            }
                        }

                        override fun onNothingSelected(
                            parent: AdapterView<*>?
                        ) {
                            // No action needed
                        }
                    }

            } catch (e: Exception) {
                balanceText.text = root.context.getString(
                    R.string.wallet_load_failed
                )
                saveButton.isEnabled = false
            }
        }


        // Cancel and return Home
        cancelButton.setOnClickListener {
            onNavigate("home")
        }

        // Save money to Wallet
        saveButton.setOnClickListener {

            val amountCents = parseAmount(
                amountInput.text.toString()
            )

            if (amountCents == null || amountCents <= 0L) {
                amountInput.error = "Enter a valid amount (up to 2 decimals)"
                return@setOnClickListener
            }

            val memo = memoInput.text.toString()
                .trim()
                .ifBlank { "Added to Wallet" }

            saveButton.isEnabled = false

            scope.launch {
                try {
                    gardenApp.awaitDatabaseReady()

                    val wallets = gardenApp.dao.getBudgets().filter {
                        it.type == "WALLET" && !it.isArchived
                    }

                    val wallet = wallets.getOrNull(
                        walletSpinner.selectedItemPosition
                    ) ?: throw IllegalStateException(
                        root.context.getString(R.string.wallet_select_required)
                    )

                    gardenApp.repository.addTransaction(
                        fromId = null,
                        toId = wallet.id,
                        amount = amountCents,
                        postDate = selectedDate.timeInMillis,
                        memo = memo
                    )

                    Toast.makeText(
                        root.context,
                        "Money added to Wallet!",
                        Toast.LENGTH_SHORT
                    ).show()

                    onNavigate("home")

                } catch (e: Exception) {
                    Toast.makeText(
                        root.context,
                        e.message ?: "Failed to add money",
                        Toast.LENGTH_LONG
                    ).show()

                    saveButton.isEnabled = true
                }
            }
        }
    }

    // Convert dollars to cents without floating-point errors
    private fun parseAmount(input: String): Long? {
        return try {
            BigDecimal(input.trim())
                .movePointRight(2)
                .longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    private fun formatMoney(cents: Long): String {
        val dollars = BigDecimal.valueOf(cents, 2)
        return NumberFormat.getCurrencyInstance(Locale.US)
            .format(dollars)
    }
}
