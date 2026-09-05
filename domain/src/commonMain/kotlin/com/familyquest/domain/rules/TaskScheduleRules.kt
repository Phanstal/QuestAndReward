package com.familyquest.domain.rules

import com.familyquest.domain.model.TaskRecurrence

data class NormalizedTaskSchedule(
    val deadlineMinutes: Int?,
    val weekDays: Set<Int>,
    val monthDay: Int?,
    val monthlyTargetCount: Int = TaskScheduleRules.DEFAULT_MONTHLY_TARGET_COUNT,
)

object TaskScheduleRules {
    val DEFAULT_WEEK_DAYS: Set<Int> = setOf(1, 2, 3, 4, 5)
    const val DEFAULT_MONTHLY_TARGET_COUNT = 1
    const val MIN_MONTHLY_TARGET_COUNT = 1
    const val MAX_MONTHLY_TARGET_COUNT = 8

    fun normalize(
        recurrence: TaskRecurrence,
        deadlineMinutes: Int?,
        weekDays: Set<Int>,
        monthDay: Int?,
        monthlyTargetCount: Int = DEFAULT_MONTHLY_TARGET_COUNT,
    ): NormalizedTaskSchedule? {
        return when (recurrence) {
            TaskRecurrence.ONCE,
            TaskRecurrence.YEARLY,
            -> NormalizedTaskSchedule(null, emptySet(), null)

            TaskRecurrence.DAILY -> {
                if (deadlineMinutes != null && deadlineMinutes !in MINUTES_PER_DAY) return null
                NormalizedTaskSchedule(deadlineMinutes, emptySet(), null)
            }

            TaskRecurrence.WEEKLY -> {
                if (deadlineMinutes != null && deadlineMinutes !in MINUTES_PER_DAY) return null
                if (weekDays.any { it !in FIRST_WEEK_DAY..LAST_WEEK_DAY }) return null
                NormalizedTaskSchedule(
                    deadlineMinutes = deadlineMinutes,
                    weekDays = (weekDays.ifEmpty { DEFAULT_WEEK_DAYS }).toSortedSet(),
                    monthDay = null,
                )
            }

            TaskRecurrence.MONTHLY -> {
                if (monthDay != null && monthDay !in FIRST_MONTH_DAY..LAST_MONTH_DAY) return null
                if (monthlyTargetCount !in MIN_MONTHLY_TARGET_COUNT..MAX_MONTHLY_TARGET_COUNT) return null
                NormalizedTaskSchedule(
                    deadlineMinutes = null,
                    weekDays = emptySet(),
                    monthDay = monthDay,
                    monthlyTargetCount = monthlyTargetCount,
                )
            }
        }
    }

    private val MINUTES_PER_DAY = 0 until 24 * 60
    private const val FIRST_WEEK_DAY = 0
    private const val LAST_WEEK_DAY = 6
    private const val FIRST_MONTH_DAY = 1
    private const val LAST_MONTH_DAY = 31
}
