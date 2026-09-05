package com.familyquest.application

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

interface BusinessTimeProvider {
    fun now(): Instant
    fun timeZone(): TimeZone
    fun ticks(): Flow<Instant>
}

object SystemBusinessTimeProvider : BusinessTimeProvider {
    override fun now(): Instant = Clock.System.now()
    override fun timeZone(): TimeZone = TimeZone.currentSystemDefault()

    override fun ticks(): Flow<Instant> = flow {
        while (true) {
            val current = now()
            emit(current)
            val zone = timeZone()
            val local = current.toLocalDateTime(zone)
            val todayBoundary = LocalDateTime(local.date, LocalTime(4, 0))
            val nextBoundary = if (local < todayBoundary) {
                todayBoundary
            } else {
                LocalDateTime(local.date.plus(1, DateTimeUnit.DAY), LocalTime(4, 0))
            }
            val waitMillis = (nextBoundary.toInstant(zone) - current).inWholeMilliseconds.coerceAtLeast(1_000)
            delay(waitMillis)
        }
    }
}
