package com.familyquest.app

import com.familyquest.application.FamilyQuestService
import com.familyquest.domain.event.EventPayloadCodec
import com.familyquest.domain.repository.FamilyQuestRepository
import com.familyquest.sync.EventCodec
import com.familyquest.sync.SnapshotCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(
    repositoryFactory: (EventPayloadCodec) -> FamilyQuestRepository,
) {
    private val eventCodec = EventCodec()
    private val snapshotCodec = SnapshotCodec()
    private val repository = repositoryFactory(eventCodec)

    val service = FamilyQuestService(
        repository = repository,
        eventExporter = eventCodec,
        snapshotExporter = snapshotCodec,
        backupCodec = snapshotCodec,
    )

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        applicationScope.launch { service.ensureSeedData() }
    }
}

