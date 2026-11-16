package edu.stevens.cs597.budgetgarden

import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Keep the withdrawal form and its database calls here, rather than MainActivity. */
class TakeOutFundsPage(
    private val root: View,
    private val gardenApp: GardenBudgetApp,
    private val scope: CoroutineScope
) {
    private data class Source(val id: Long, val label: String, val balanceCents: Long) {
        override fun toString() = label
    }

    fun renderAvailableFunds(formattedBalance: String) {
        root.findViewById<TextView>(R.id.available_funds_text).text =
            root.context.getString(R.string.available_funds_format, formattedBalance)
    }

    fun bind(onNavigate: (String) -> Unit) {
        val context = root.context
        val spinner = root.findViewById<Spinner>(R.id.spend_source_spinner)
        val amountInput = root.findViewById<EditText>(R.id.spend_amount_input)
        val memoInput = root.findViewById<EditText>(R.id.spend_memo_input)
        val spendButton = root.findViewById<Button>(R.id.spend_button)

        spendButton.isEnabled = false

        scope.launch {
            try {
                gardenApp.awaitDatabaseReady()
                val dao = gardenApp.dao
                val now = System.currentTimeMillis()
                val budgets = dao.getBudgets().filter { !it.isArchived }

                val sources = mutableListOf<Source>()
                budgets.firstOrNull { it.type == "WALLET" }?.let { wallet ->
                    sources.add(
                        Source(
                            wallet.id,
                            context.getString(R.string.spend_leftover_option),
                            dao.balanceAt(wallet.id, now)
                        )
                    )
                }
                budgets.filter { it.type != "WALLET" }.forEach { goal ->
                    val balance = dao.balanceAt(goal.id, now)
                    if (balance > 0L) {
                        sources.add(Source(goal.id, goal.name, balance))
                    }
                }

                val adapter = ArrayAdapter(
                    context, android.R.layout.simple_spinner_item, sources
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter

                spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?, view: View?, position: Int, id: Long
                    ) {
                        renderAvailableFunds(money(sources[position].balanceCents))
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
                spendButton.isEnabled = sources.isNotEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TakeOutFundsPage", "Could not load funds", e)
                Toast.makeText(context, R.string.spend_load_failed, Toast.LENGTH_LONG).show()
            }
        }

        spendButton.setOnClickListener {
            amountInput.error = null
            val source = spinner.selectedItem as? Source ?: return@setOnClickListener
            val cents = SpendFunds.parseCents(amountInput.text.toString().trim())
            if (cents == null) {
                amountInput.error = context.getString(R.string.goal_add_funds_invalid)
                amountInput.requestFocus()
                return@setOnClickListener
            }
            val memo = memoInput.text.toString().trim()
                .ifBlank { context.getString(R.string.spend_default_memo) }

            spendButton.isEnabled = false
            scope.launch {
                when (val result = SpendFunds.spend(gardenApp, source.id, cents, memo)) {
                    is SpendResult.Success -> {
                        Toast.makeText(
                            context, R.string.spend_success, Toast.LENGTH_SHORT
                        ).show()
                        onNavigate("home")
                    }
                    is SpendResult.NotEnough -> {
                        amountInput.error = context.getString(
                            R.string.spend_not_enough, money(result.availableCents)
                        )
                        spendButton.isEnabled = true
                    }
                    is SpendResult.Failed -> {
                        Toast.makeText(
                            context, R.string.goal_add_funds_failed, Toast.LENGTH_LONG
                        ).show()
                        spendButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun money(cents: Long): String =
        NumberFormat.getCurrencyInstance(Locale.US).format(BigDecimal.valueOf(cents, 2))
}