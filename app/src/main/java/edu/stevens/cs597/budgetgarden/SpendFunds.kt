package edu.stevens.cs597.budgetgarden

import android.util.Log
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal

sealed class SpendResult {
    object Success : SpendResult()
    data class NotEnough(val availableCents: Long) : SpendResult()
    object Failed : SpendResult()
}

object SpendFunds {

    fun parseCents(raw: String): Long? {
        if (!raw.matches(Regex("""[0-9]+(\.[0-9]{1,2})?"""))) return null
        return try {
            BigDecimal(raw).movePointRight(2).longValueExact().takeIf { it > 0L }
        } catch (_: ArithmeticException) {
            null
        }
    }

    suspend fun spend(
        gardenApp: GardenBudgetApp,
        sourceId: Long,
        cents: Long,
        memo: String
    ): SpendResult {
        return try {
            gardenApp.awaitDatabaseReady()
            val now = System.currentTimeMillis()

            val available = gardenApp.dao.balanceAt(sourceId, now)
            if (cents > available) {
                return SpendResult.NotEnough(available.coerceAtLeast(0L))
            }

            gardenApp.repository.addTransaction(
                fromId = sourceId,
                toId = null,
                amount = cents,
                postDate = now,
                memo = memo
            )
            SpendResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SpendFunds", "Could not spend funds", e)
            SpendResult.Failed
        }
    }
}