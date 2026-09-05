package com.familyquest.application

import com.familyquest.domain.event.DomainEvent

fun interface EventExporter {
    fun export(events: List<DomainEvent>): String
}
