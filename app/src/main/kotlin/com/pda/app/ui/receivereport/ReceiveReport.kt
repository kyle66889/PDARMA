package com.pda.app.ui.receivereport

import com.pda.app.data.api.model.ReceivedBatch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Receive Report 中的一天分组：标题 + 该天的批次（按收货时间倒序）。 */
data class ReceiveReportDay(
    val label: String,
    val date: LocalDate,
    val batches: List<ReceivedBatch>
)

private val DAY_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/**
 * 纯函数（可单测）：把已收货批次过滤到最近三天（today、today-1、today-2），
 * 按天倒序分组，每天内按收货时间倒序。
 */
fun buildReceiveReport(batches: List<ReceivedBatch>, today: LocalDate): List<ReceiveReportDay> {
    val windowStart = today.minusDays(2)
    return batches
        .filter { val d = it.receivedAt.toLocalDate(); !d.isBefore(windowStart) && !d.isAfter(today) }
        .groupBy { it.receivedAt.toLocalDate() }
        .toSortedMap(compareByDescending { it })
        .map { (date, list) ->
            ReceiveReportDay(
                label = dayLabel(date, today),
                date = date,
                batches = list.sortedByDescending { it.receivedAt }
            )
        }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DAY_FORMAT)
}
