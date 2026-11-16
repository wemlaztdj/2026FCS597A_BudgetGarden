package edu.stevens.cs597.budgetgarden

import android.graphics.Color
import android.view.View
import android.widget.TextView
import edu.stevens.cs597.budgetgarden.data.TransactionRow
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TransactionRowRenderer {

    fun render(row: View, transaction: TransactionRow) {

        val title = row.findViewById<TextView>(
            R.id.upcoming_transaction_title
        )

        val details = row.findViewById<TextView>(
            R.id.upcoming_transaction_details
        )

        val amount = row.findViewById<TextView>(
            R.id.upcoming_transaction_amount
        )

        // Transaction title
        title.text = transaction.memo.ifBlank {
            when {
                transaction.fromName == null -> "Income"
                transaction.toName == null -> "Expense"
                else -> "Transfer"
            }
        }

        // Date
        val dateFormat = SimpleDateFormat(
            "MMM d, yyyy",
            Locale.getDefault()
        )

        val date = dateFormat.format(
            Date(transaction.postDate)
        )

        // Money movement
        val movement = when {
            transaction.fromName == null ->
                "Income → ${transaction.toName}"

            transaction.toName == null ->
                "${transaction.fromName} → Expense"

            else ->
                "${transaction.fromName} → ${transaction.toName}"
        }

        details.text = "$date · $movement"

        val formattedAmount =
            NumberFormat.getCurrencyInstance(Locale.US)
                .format(BigDecimal.valueOf(transaction.amount, 2))

        // Amount and color
        when {
            transaction.fromName == null -> {
                amount.text = "+$formattedAmount"
                amount.setTextColor(
                    Color.rgb(46, 125, 50)
                )
            }

            transaction.toName == null -> {
                amount.text = "-$formattedAmount"
                amount.setTextColor(
                    Color.rgb(183, 28, 28)
                )
            }

            else -> {
                amount.text = formattedAmount
                amount.setTextColor(
                    Color.rgb(36, 69, 44)
                )
            }
        }
    }
}
