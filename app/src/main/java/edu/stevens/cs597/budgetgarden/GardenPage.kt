package edu.stevens.cs597.budgetgarden

import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import android.widget.Button

class GardenPage(
    private val root: View,
    private val gardenApp: GardenBudgetApp,
    private val scope: CoroutineScope
) {
    private var navigate: (String) -> Unit = {}
    fun bind(onNavigate: (String) -> Unit) {
        navigate = onNavigate
        root.findViewById<View>(R.id.garden_create_goal_button).setOnClickListener {
            onNavigate("create_goal")
        }
        // test amount of $1000
        scope.launch {
            gardenApp.awaitDatabaseReady()
            val walletId = gardenApp.dao.getBudgets().first { it.type == "WALLET" }.id
            if (gardenApp.dao.balanceAt(walletId, System.currentTimeMillis()) == 0L) {
                gardenApp.repository.addTransaction(
                    fromId = null,
                    toId = walletId,
                    amount = 100000L, // $1,000.00
                    postDate = System.currentTimeMillis(),
                    memo = "Test income"
                )
            }
        }

        loadGarden()
    }

    private fun loadGarden() {
        val container = root.findViewById<LinearLayout>(R.id.garden_container)
        val status = root.findViewById<TextView>(R.id.garden_status_text)

        scope.launch {
            try {
                gardenApp.awaitDatabaseReady()
                val dao = gardenApp.dao
                val now = System.currentTimeMillis()
                val plants = dao.getBudgets().filter {
                    !it.isArchived && it.type != "WALLET"
                }
                val balances = plants.map { it to dao.balanceAt(it.id, now) }
                val currency = NumberFormat.getCurrencyInstance(Locale.US)
                val inflater = LayoutInflater.from(root.context)

                container.removeAllViews()
                balances.forEach { (budget, balance) ->
                    val row = inflater.inflate(R.layout.item_plant_goal, container, false)
                    val appearance = PlantCatalog.fromKey(budget.icon)
                    row.findViewById<ImageView>(R.id.goal_plant_image).apply {
                        setImageResource(appearance.drawableRes)
                        contentDescription = root.context.getString(appearance.labelRes)
                    }
                    row.findViewById<TextView>(R.id.garden_goal_name).text = budget.name
                    row.findViewById<TextView>(R.id.garden_goal_amounts).text =
                        root.context.getString(
                            R.string.garden_goal_amounts,
                            currency.format(BigDecimal.valueOf(balance, 2)),
                            currency.format(BigDecimal.valueOf(budget.goalAmount, 2))
                        )

                    val addFundsButton = row.findViewById<Button>(R.id.add_funds_button)
                    if (balance >= budget.goalAmount) {
                        addFundsButton.isEnabled = false
                        addFundsButton.setText(R.string.goal_add_funds_reached_button)
                    } else {
                        addFundsButton.setOnClickListener {
                            showAddFundsDialog(budget.id, budget.name)
                        }
                    }

                    val takeOutButton = row.findViewById<Button>(R.id.take_out_funds_button)
                    if (balance <= 0L) {
                        takeOutButton.isEnabled = false
                        takeOutButton.setText(R.string.goal_take_out_empty_button)
                    } else {
                        takeOutButton.setOnClickListener {
                            showTakeOutDialog(budget.id, budget.name)
                        }
                    }

                    container.addView(row)
                }

                status.setText(R.string.garden_empty)
                status.visibility = if (plants.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GardenPage", "Could not load garden", e)
                container.removeAllViews()
                status.setText(R.string.garden_load_failed)
                status.visibility = View.VISIBLE
            }
        }
    }

    private fun showAddFundsDialog(goalId: Long, goalName: String) {
        val context = root.context
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = context.getString(R.string.goal_add_funds_hint)
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.goal_add_funds_title, goalName))
            .setView(input)
            .setPositiveButton(R.string.goal_add_funds_confirm) { _, _ ->
                val cents = parseCents(input.text.toString().trim())
                if (cents == null) {
                    Toast.makeText(
                        context, R.string.goal_add_funds_invalid, Toast.LENGTH_LONG
                    ).show()
                } else {
                    addFunds(goalId, cents)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addFunds(goalId: Long, cents: Long) {
        val context = root.context

        scope.launch {
            try {
                gardenApp.awaitDatabaseReady()
                val dao = gardenApp.dao
                val now = System.currentTimeMillis()

                val walletId = dao.getBudgets()
                    .firstOrNull { it.type == "WALLET" && !it.isArchived }
                    ?.id
                if (walletId == null) {
                    Toast.makeText(
                        context, R.string.goal_add_funds_failed, Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                if (dao.balanceAt(walletId, now) < cents) {
                    Toast.makeText(
                        context, R.string.goal_add_funds_insufficient, Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                gardenApp.repository.addTransaction(
                    fromId = walletId,
                    toId = goalId,
                    amount = cents,
                    postDate = now,
                    memo = "Added funds to savings goal"
                )

                Toast.makeText(
                    context, R.string.goal_add_funds_success, Toast.LENGTH_SHORT
                ).show()
                navigate("home")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GardenPage", "Could not add funds", e)
                Toast.makeText(
                    context, R.string.goal_add_funds_failed, Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showTakeOutDialog(goalId: Long, goalName: String) {
        val context = root.context
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = context.getString(R.string.goal_add_funds_hint)
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.goal_take_out_title, goalName))
            .setView(input)
            .setPositiveButton(R.string.goal_take_out_confirm) { _, _ ->
                val cents = parseCents(input.text.toString().trim())
                if (cents == null) {
                    Toast.makeText(
                        context, R.string.goal_add_funds_invalid, Toast.LENGTH_LONG
                    ).show()
                } else {
                    takeOutFunds(goalId, cents)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun takeOutFunds(goalId: Long, cents: Long) {
        val context = root.context

        scope.launch {
            try {
                gardenApp.awaitDatabaseReady()
                val dao = gardenApp.dao
                val now = System.currentTimeMillis()

                val walletId = dao.getBudgets()
                    .firstOrNull { it.type == "WALLET" && !it.isArchived }
                    ?.id
                if (walletId == null) {
                    Toast.makeText(
                        context, R.string.goal_add_funds_failed, Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val available = dao.balanceAt(goalId, now)
                if (cents > available) {
                    val formatted = NumberFormat.getCurrencyInstance(Locale.US)
                        .format(BigDecimal.valueOf(available.coerceAtLeast(0L), 2))
                    Toast.makeText(
                        context,
                        context.getString(R.string.goal_take_out_exceeds, formatted),
                        Toast.LENGTH_LONG
                    ).show()
                    loadGarden()
                    return@launch
                }

                gardenApp.repository.addTransaction(
                    fromId = goalId,
                    toId = walletId,
                    amount = cents,
                    postDate = now,
                    memo = "Took out funds from savings goal"
                )

                Toast.makeText(
                    context, R.string.goal_take_out_success, Toast.LENGTH_SHORT
                ).show()
                navigate("home")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GardenPage", "Could not take out funds", e)
                Toast.makeText(
                    context, R.string.goal_add_funds_failed, Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun parseCents(raw: String): Long? {
        if (!raw.matches(Regex("""[0-9]+(\.[0-9]{1,2})?"""))) {
            return null
        }
        return try {
            BigDecimal(raw).movePointRight(2).longValueExact()
                .takeIf { it > 0L }
        } catch (_: ArithmeticException) {
            null
        }
    }
}