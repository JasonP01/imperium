package com.xpdustry.imperium.mindustry.security

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object AccountDataUtil {
    /**
     * Appends a chat message to the JSON string of chatMessages, keeping a max size.
     */
    fun appendChatMessage(chatMessagesJson: String, message: String, maxSize: Int = 200): String {
        val messages = try {
            Json.decodeFromString<MutableList<String>>(chatMessagesJson)
        } catch (e: Exception) {
            mutableListOf<String>()
        }
        messages.add(message)
        if (messages.size > maxSize) messages.removeAt(0)
        return Json.encodeToString(messages)
    }

    /**
     * Appends a tile history entry to the JSON string of tileHistory, keeping a max size.
     */
    fun appendTileHistory(tileHistoryJson: String, entry: String, maxSize: Int = 2000): String {
        val history = try {
            Json.decodeFromString<MutableList<String>>(tileHistoryJson)
        } catch (e: Exception) {
            mutableListOf<String>()
        }
        history.add(entry)
        if (history.size > maxSize) history.removeAt(0)
        return Json.encodeToString(history)
    }

    /**
     * Utility to get chat messages as a list.
     */
    fun getChatMessages(chatMessagesJson: String): List<String> =
        try { Json.decodeFromString(chatMessagesJson) } catch (e: Exception) { emptyList() }

    /**
     * Utility to get tile history as a list.
     */
    fun getTileHistory(tileHistoryJson: String): List<String> =
        try { Json.decodeFromString(tileHistoryJson) } catch (e: Exception) { emptyList() }
}

class Evidence(instances: InstanceManager) : ImperiumApplication.Listener {
    private val accounts = instances.get<AccountManager>()
}