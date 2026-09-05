package com.familyquest.domain.rules

import com.familyquest.domain.model.AchievementId
import com.familyquest.domain.model.ProgressStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressionRulesTest {
    @Test
    fun `level progress uses task experience without reward spending`() {
        val progress = DefaultProgressionPolicy.project(
            ProgressStats(experiencePoints = 245, completedTaskCount = 4, redemptionCount = 2),
        )

        assertEquals(2, progress.player.level)
        assertEquals(45, progress.player.currentLevelExperience)
        assertEquals(245, progress.player.totalExperience)
        assertEquals(200, progress.player.experienceForNextLevel)
    }

    @Test
    fun `achievements are deterministic projections of persisted stats`() {
        val progress = DefaultProgressionPolicy.project(
            ProgressStats(experiencePoints = 20, completedTaskCount = 1, redemptionCount = 0),
        )

        assertTrue(progress.achievements.single { it.id == AchievementId.FIRST_QUEST }.unlocked)
        assertFalse(progress.achievements.single { it.id == AchievementId.QUEST_TRIO }.unlocked)
        assertFalse(progress.achievements.single { it.id == AchievementId.FIRST_REWARD }.unlocked)
    }

    @Test
    fun `negative net experience never produces an invalid level`() {
        val player = DefaultProgressionPolicy.project(ProgressStats(experiencePoints = -10)).player

        assertEquals(1, player.level)
        assertEquals(0, player.currentLevelExperience)
    }

    @Test
    fun `level is capped at twelve`() {
        val atCap = DefaultProgressionPolicy.project(ProgressStats(experiencePoints = 2_200)).player
        val aboveCap = DefaultProgressionPolicy.project(ProgressStats(experiencePoints = 20_000)).player

        assertEquals(12, atCap.level)
        assertEquals(12, aboveCap.level)
        assertEquals(12, DefaultProgressionPolicy.MAX_LEVEL)
    }
}
