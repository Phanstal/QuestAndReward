package com.familyquest.domain.rules

import com.familyquest.domain.model.TaskRecurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskScheduleRulesTest {
    @Test
    fun `daily keeps only a valid optional deadline`() {
        assertEquals(
            NormalizedTaskSchedule(8 * 60, emptySet(), null),
            TaskScheduleRules.normalize(TaskRecurrence.DAILY, 8 * 60, setOf(1, 2), 15),
        )
        assertNull(TaskScheduleRules.normalize(TaskRecurrence.DAILY, 24 * 60, emptySet(), null))
    }

    @Test
    fun `weekly sorts days and supplies the prototype default`() {
        assertEquals(
            NormalizedTaskSchedule(22 * 60, sortedSetOf(0, 6), null),
            TaskScheduleRules.normalize(TaskRecurrence.WEEKLY, 22 * 60, setOf(6, 0), 12),
        )
        assertEquals(
            TaskScheduleRules.DEFAULT_WEEK_DAYS,
            TaskScheduleRules.normalize(TaskRecurrence.WEEKLY, null, emptySet(), null)?.weekDays,
        )
        assertNull(TaskScheduleRules.normalize(TaskRecurrence.WEEKLY, null, setOf(7), null))
    }

    @Test
    fun `monthly keeps an optional legacy day and validates a one to eight completion target`() {
        assertEquals(
            NormalizedTaskSchedule(null, emptySet(), null),
            TaskScheduleRules.normalize(TaskRecurrence.MONTHLY, 500, setOf(1), null),
        )
        assertEquals(31, TaskScheduleRules.normalize(TaskRecurrence.MONTHLY, null, emptySet(), 31)?.monthDay)
        assertEquals(
            8,
            TaskScheduleRules.normalize(TaskRecurrence.MONTHLY, null, emptySet(), 31, 8)
                ?.monthlyTargetCount,
        )
        assertNull(TaskScheduleRules.normalize(TaskRecurrence.MONTHLY, null, emptySet(), 0))
        assertNull(TaskScheduleRules.normalize(TaskRecurrence.MONTHLY, null, emptySet(), 32))
        assertNull(TaskScheduleRules.normalize(TaskRecurrence.MONTHLY, null, emptySet(), 1, 0))
        assertNull(TaskScheduleRules.normalize(TaskRecurrence.MONTHLY, null, emptySet(), 1, 9))
    }

    @Test
    fun `custom and legacy yearly discard schedule-only fields`() {
        val empty = NormalizedTaskSchedule(null, emptySet(), null)
        assertEquals(empty, TaskScheduleRules.normalize(TaskRecurrence.ONCE, 60, setOf(1), 1, 8))
        assertEquals(empty, TaskScheduleRules.normalize(TaskRecurrence.YEARLY, 60, setOf(1), 1, 8))
    }
}
