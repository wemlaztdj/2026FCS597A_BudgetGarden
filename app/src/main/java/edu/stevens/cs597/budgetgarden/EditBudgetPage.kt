
package edu.stevens.cs597.budgetgarden

import android.app.DatePickerDialog
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import edu.stevens.cs597.budgetgarden.data.Budget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class EditBudgetPage(
    private val root: View,
    private val gardenApp: GardenBudgetApp,
    private val scope: CoroutineScope,
    private val budgetId: Long
) {
    fun bind(onNavigate: (String) -> Unit) {

        // 1. Connect XML views
        val nameInput = root.findViewById<EditText>(
            R.id.edit_goal_name_input
        )
        val amountInput = root.findViewById<EditText>(
            R.id.edit_goal_amount_input
        )
        val descriptionInput = root.findViewById<EditText>(
            R.id.edit_goal_description_input
        )
        val dateButton = root.findViewById<Button>(
            R.id.edit_goal_date_button
        )
        val balanceText = root.findViewById<TextView>(
            R.id.edit_current_balance_text
        )
        val plantPreview = root.findViewById<ImageView>(
            R.id.edit_plant_preview
        )
        val plantName = root.findViewById<TextView>(
            R.id.edit_plant_name_text
        )
        val previousButton = root.findViewById<Button>(
            R.id.edit_previous_plant_button
        )
        val nextButton = root.findViewById<Button>(
            R.id.edit_next_plant_button
        )
        val saveButton = root.findViewById<Button>(
            R.id.save_changes_button
        )
        val cancelButton = root.findViewById<Button>(
            R.id.cancel_edit_button
        )
        val archiveButton = root.findViewById<Button>(
            R.id.archive_budget_button
        )

        // 2. State
        var selectedDate: LocalDate? = null
        var selectedPlantIndex = 0
        var busy = true

        val controls = listOf<View>(
            nameInput, amountInput, descriptionInput,
            dateButton, previousButton, nextButton,
            saveButton, cancelButton, archiveButton
        )

        fun setBusy(value: Boolean) {
            busy = value
            controls.forEach { it.isEnabled = !value }
        }

        fun showSelectedPlant() {
            val plant = PlantCatalog.choices[selectedPlantIndex]
            plantPreview.setImageResource(plant.drawableRes)
            plantName.setText(plant.labelRes)
        }

        setBusy(true)

        // 3. Load existing budget
        scope.launch {
            try {
                gardenApp.awaitDatabaseReady()

                val budget = gardenApp.dao.getBudget(budgetId)

                if (budget == null ||
                    budget.isArchived ||
                    budget.type == "WALLET"
                ) {
                    Toast.makeText(
                        root.context,
                        "Budget unavailable",
                        Toast.LENGTH_SHORT
                    ).show()
                    onNavigate("garden")
                    return@launch
                }

                nameInput.setText(budget.name)

                amountInput.setText(
                    BigDecimal.valueOf(
                        budget.goalAmount, 2
                    ).toPlainString()
                )

                descriptionInput.setText(budget.description)

                selectedDate = budget.neededDate?.let {
                    Instant.ofEpochMilli(it)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }

                dateButton.text = selectedDate?.toString()
                    ?: root.context.getString(
                        R.string.goal_date_prompt
                    )

                selectedPlantIndex =
                    PlantCatalog.choices.indexOf(
                        PlantCatalog.fromKey(budget.icon)
                    )

                showSelectedPlant()

                val balance = gardenApp.dao.balanceAt(
                    budgetId,
                    System.currentTimeMillis()
                )

                val formattedBalance =
                    NumberFormat.getCurrencyInstance(Locale.US)
                        .format(BigDecimal.valueOf(balance, 2))

                balanceText.text = root.context.getString(
                    R.string.edit_current_balance_format,
                    formattedBalance
                )

                setBusy(false)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(
                    root.context,
                    e.message ?: "Unable to load budget",
                    Toast.LENGTH_LONG
                ).show()
                onNavigate("garden")
            }
        }

        // 4. Change plant appearance
        previousButton.setOnClickListener {
            if (busy) return@setOnClickListener

            selectedPlantIndex =
                (selectedPlantIndex +
                        PlantCatalog.choices.size - 1) %
                        PlantCatalog.choices.size

            showSelectedPlant()
        }

        nextButton.setOnClickListener {
            if (busy) return@setOnClickListener

            selectedPlantIndex =
                (selectedPlantIndex + 1) %
                        PlantCatalog.choices.size

            showSelectedPlant()
        }

        // 5. Change target date
        dateButton.setOnClickListener {
            if (busy) return@setOnClickListener

            val initialDate = selectedDate ?: LocalDate.now()

            DatePickerDialog(
                root.context,
                { _, year, month, day ->
                    selectedDate = LocalDate.of(
                        year, month + 1, day
                    )
                    dateButton.text = selectedDate.toString()
                },
                initialDate.year,
                initialDate.monthValue - 1,
                initialDate.dayOfMonth
            ).show()
        }

        // 6. Save edited budget
        saveButton.setOnClickListener {
            if (busy) return@setOnClickListener

            val name = nameInput.text.toString().trim()
            val amount = parseCents(
                amountInput.text.toString().trim()
            )

            if (name.isBlank()) {
                nameInput.error = "Enter a budget name"
                nameInput.requestFocus()
                return@setOnClickListener
            }

            if (amount == null) {
                amountInput.error =
                    "Enter a valid non-negative amount"
                amountInput.requestFocus()
                return@setOnClickListener
            }

            val neededDate = selectedDate
                ?.atStartOfDay(ZoneId.systemDefault())
                ?.toInstant()
                ?.toEpochMilli()

            val description =
                descriptionInput.text.toString().trim()

            val plantKey =
                PlantCatalog.choices[selectedPlantIndex].key

            setBusy(true)

            scope.launch {
                try {
                    gardenApp.repository.updateBudgetDetails(
                        budgetId = budgetId,
                        name = name,
                        goalAmount = amount,
                        neededDate = neededDate,
                        description = description,
                        icon = plantKey
                    )

                    Toast.makeText(
                        root.context,
                        "Budget updated",
                        Toast.LENGTH_SHORT
                    ).show()

                    onNavigate("garden")

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    setBusy(false)
                    Toast.makeText(
                        root.context,
                        e.message ?: "Unable to save changes",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // 7. Cancel
        cancelButton.setOnClickListener {
            if (!busy) onNavigate("garden")
        }

        // 8. Archive confirmation
        fun confirmArchive(wallet: Budget) {
            AlertDialog.Builder(root.context)
                .setTitle("Archive budget?")
                .setMessage(
                    "Remaining funds will be returned to " +
                            "${wallet.name}. Transaction history " +
                            "will be preserved."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Archive") { _, _ ->
                    setBusy(true)

                    scope.launch {
                        try {
                            gardenApp.repository.archiveBudget(
                                budgetId,
                                wallet.id
                            )

                            Toast.makeText(
                                root.context,
                                "Budget archived",
                                Toast.LENGTH_SHORT
                            ).show()

                            onNavigate("garden")

                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            setBusy(false)
                            Toast.makeText(
                                root.context,
                                e.message ?: "Unable to archive",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                .show()
        }

        archiveButton.setOnClickListener {
            if (busy) return@setOnClickListener

            setBusy(true)

            scope.launch {
                try {
                    val wallets = gardenApp.dao.getBudgets()
                        .filter {
                            it.type == "WALLET" &&
                                    !it.isArchived
                        }

                    setBusy(false)

                    when {
                        wallets.isEmpty() -> {
                            Toast.makeText(
                                root.context,
                                "No active wallet found",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        wallets.size == 1 -> {
                            confirmArchive(wallets.first())
                        }

                        else -> {
                            val names = wallets.map {
                                it.name
                            }.toTypedArray()

                            AlertDialog.Builder(root.context)
                                .setTitle(
                                    "Choose a wallet for remaining funds"
                                )
                                .setItems(names) { _, which ->
                                    confirmArchive(wallets[which])
                                }
                                .setNegativeButton(
                                    "Cancel", null
                                )
                                .show()
                        }
                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    setBusy(false)
                    Toast.makeText(
                        root.context,
                        e.message ?: "Unable to load wallets",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Convert dollars to cents without floating-point errors
    private fun parseCents(raw: String): Long? {
        if (!raw.matches(
                Regex("""[0-9]+(\.[0-9]{1,2})?""")
            )
        ) return null

        return try {
            BigDecimal(raw)
                .movePointRight(2)
                .longValueExact()
                .takeIf { it >= 0L }
        } catch (_: ArithmeticException) {
            null
        }
    }
}
