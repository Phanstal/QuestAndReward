package com.familyquest.domain.rules

import com.familyquest.domain.model.Achievement
import com.familyquest.domain.model.AchievementId
import com.familyquest.domain.model.GameProgress
import com.familyquest.domain.model.PlayerProgress
import com.familyquest.domain.model.ProgressStats

fun interface ProgressionPolicy {
    fun project(stats: ProgressStats): GameProgress
}

object DefaultProgressionPolicy : ProgressionPolicy {
    const val EXPERIENCE_PER_LEVEL = 200
    const val MAX_LEVEL = 12

    override fun project(stats: ProgressStats): GameProgress {
        val experience = stats.experiencePoints.coerceAtLeast(0)
        val completedTasks = stats.completedTaskCount.coerceAtLeast(0)
        val redemptions = stats.redemptionCount.coerceAtLeast(0)
        return GameProgress(
            player = PlayerProgress(
                level = (experience / EXPERIENCE_PER_LEVEL + 1).coerceAtMost(MAX_LEVEL),
                totalExperience = experience,
                currentLevelExperience = experience % EXPERIENCE_PER_LEVEL,
                experienceForNextLevel = EXPERIENCE_PER_LEVEL,
            ),
            achievements = listOf(
                achievement(
                    AchievementId.FIRST_QUEST,
                    completedTasks,
                    1,
                ),
                achievement(
                    AchievementId.QUEST_TRIO,
                    completedTasks,
                    3,
                ),
                achievement(
                    AchievementId.TREASURE_HUNTER,
                    experience,
                    50,
                ),
                achievement(
                    AchievementId.FIRST_REWARD,
                    redemptions,
                    1,
                ),
            ),
        )
    }

    private fun achievement(
        id: AchievementId,
        current: Int,
        target: Int,
    ) = Achievement(
        id = id,
        current = current.coerceAtMost(target),
        target = target,
        unlocked = current >= target,
    )
}
