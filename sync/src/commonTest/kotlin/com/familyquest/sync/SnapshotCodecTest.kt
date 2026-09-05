package com.familyquest.sync

import com.familyquest.application.BackupInventoryItem
import com.familyquest.application.BackupSnapshot
import com.familyquest.application.BackupTask
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotCodecTest {
    @Test
    fun `exports the exact structured backup schema`() {
        val snapshot = BackupSnapshot(
            tasks = listOf(
                BackupTask(
                    id = "task-1",
                    title = "Read \"book\"\nnow",
                    type = "daily",
                    coins = 15,
                    completed = true,
                    emoji = "\uD83D\uDCD6",
                    deadlineMinutes = 22 * 60,
                    weekDays = listOf(1, 3, 5),
                    monthDay = null,
                    completions = 7,
                ),
            ),
            coins = 80,
            totalEarned = 245,
            completedToday = 3,
            inventory = listOf(
                BackupInventoryItem(
                    id = "inventory-1",
                    title = "Movie night",
                    emoji = "\uD83C\uDFAC",
                    cost = 50,
                ),
            ),
        )

        val root = Json.parseToJsonElement(SnapshotCodec().export(snapshot)).jsonObject

        assertEquals(
            setOf("tasks", "coins", "totalEarned", "completedToday", "inventory"),
            root.keys,
        )
        assertEquals(JsonPrimitive(80), root.getValue("coins"))
        assertEquals(JsonPrimitive(245), root.getValue("totalEarned"))
        assertEquals(JsonPrimitive(3), root.getValue("completedToday"))

        val task = root.getValue("tasks").jsonArray.single().jsonObject
        assertEquals(
            setOf(
                "id",
                "title",
                "type",
                "coins",
                "completed",
                "emoji",
                "deadlineMinutes",
                "weekDays",
                "monthDay",
                "completions",
            ),
            task.keys,
        )
        assertEquals("task-1", task.getValue("id").jsonPrimitive.content)
        assertEquals("Read \"book\"\nnow", task.getValue("title").jsonPrimitive.content)
        assertEquals(JsonPrimitive("daily"), task.getValue("type"))
        assertEquals(JsonPrimitive(15), task.getValue("coins"))
        assertEquals(JsonPrimitive(true), task.getValue("completed"))
        assertEquals(JsonPrimitive("\uD83D\uDCD6"), task.getValue("emoji"))
        assertEquals(JsonPrimitive(22 * 60), task.getValue("deadlineMinutes"))
        assertEquals(listOf(1, 3, 5), task.getValue("weekDays").jsonArray.map { it.jsonPrimitive.int })
        assertEquals(JsonNull, task.getValue("monthDay"))
        assertEquals(JsonPrimitive(7), task.getValue("completions"))

        val inventoryItem = root.getValue("inventory").jsonArray.single().jsonObject
        assertEquals(setOf("id", "title", "emoji", "cost"), inventoryItem.keys)
        assertEquals(JsonPrimitive("inventory-1"), inventoryItem.getValue("id"))
        assertEquals(JsonPrimitive("Movie night"), inventoryItem.getValue("title"))
        assertEquals(JsonPrimitive("\uD83C\uDFAC"), inventoryItem.getValue("emoji"))
        assertEquals(JsonPrimitive(50), inventoryItem.getValue("cost"))
    }
}
