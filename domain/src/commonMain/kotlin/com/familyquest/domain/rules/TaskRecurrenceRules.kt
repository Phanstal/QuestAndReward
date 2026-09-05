package com.familyquest.domain.rules

import com.familyquest.domain.model.TaskRecurrence
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

object TaskRecurrenceRules {
    private val boundary = LocalTime(4, 0)
    private const val MONTHLY_COMPLETION_SEPARATOR = '#'

    fun businessDate(instant: Instant, timeZone: TimeZone): LocalDate {
        val local = instant.toLocalDateTime(timeZone)
        return if (local.time < boundary) local.date.minus(1, DateTimeUnit.DAY) else local.date
    }

    fun occurrenceKey(recurrence: TaskRecurrence, instant: Instant, timeZone: TimeZone): String {
        val date = businessDate(instant, timeZone)
        return when (recurrence) {
            TaskRecurrence.ONCE -> "once"
            TaskRecurrence.DAILY -> "daily:$date"
            TaskRecurrence.WEEKLY -> {
                val monday = date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)
                "weekly:$monday"
            }
            TaskRecurrence.MONTHLY -> "monthly:${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
            TaskRecurrence.YEARLY -> "yearly:${date.year}"
        }
    }

    fun completionOccurrenceKey(
        recurrence: TaskRecurrence,
        instant: Instant,
        timeZone: TimeZone,
        occurrenceIndex: Int,
    ): String {
        require(
            occurrenceIndex in
                TaskScheduleRules.MIN_MONTHLY_TARGET_COUNT..TaskScheduleRules.MAX_MONTHLY_TARGET_COUNT,
        )
        val periodKey = occurrenceKey(recurrence, instant, timeZone)
        return if (recurrence == TaskRecurrence.MONTHLY && occurrenceIndex > 1) {
            "$periodKey$MONTHLY_COMPLETION_SEPARATOR$occurrenceIndex"
        } else {
            require(occurrenceIndex == 1)
            periodKey
        }
    }

    fun belongsToOccurrence(completionKey: String, occurrenceKey: String): Boolean {
        if (completionKey == occurrenceKey) return true
        if (!occurrenceKey.startsWith("monthly:")) return false
        val prefix = "$occurrenceKey$MONTHLY_COMPLETION_SEPARATOR"
        val index = completionKey.takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.toIntOrNull()
        return index != null && index in 2..TaskScheduleRules.MAX_MONTHLY_TARGET_COUNT
    }

    fun isCompletedToday(completedAt: Long, now: Instant, timeZone: TimeZone): Boolean {
        return businessDate(Instant.fromEpochMilliseconds(completedAt), timeZone) == businessDate(now, timeZone)
    }
}
