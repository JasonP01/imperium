/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.xpdustry.imperium.discord.bridge

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.bridge.BridgeChatMessage
import com.xpdustry.imperium.common.bridge.MindustryPlayerMessage
import com.xpdustry.imperium.common.bridge.MindustryServerMessage
import com.xpdustry.imperium.common.config.DiscordConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.discord.misc.addSuspendingEventListener
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.misc.identity
import com.xpdustry.imperium.discord.service.DiscordService
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

class MindustryBridgeListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discord = instances.get<DiscordService>()
    private val messenger = instances.get<Messenger>()
    private val config = instances.get<DiscordConfig>()

    override fun onImperiumInit() {
        discord.jda.addSuspendingEventListener<MessageReceivedEvent> { event ->
            if (event.isWebhookMessage ||
                event.message.author.isBot ||
                event.message.author.isSystem) {
                return@addSuspendingEventListener
            }
            val channel = (event.channel as? TextChannel) ?: return@addSuspendingEventListener
            if (channel.parentCategoryIdLong != 0L &&
                channel.parentCategoryIdLong == config.categories.liveChat) {
                ImperiumScope.MAIN.launch {
                    messenger.publish(
                        BridgeChatMessage(
                            channel.name,
                            event.message.member!!.identity,
                            event.message.contentStripped))
                }
            }
        }

        messenger.consumer<MindustryPlayerMessage> { message ->
            val channel = getLiveChatChannel(message.server) ?: return@consumer
            val text =
                when (val action = message.action) {
                    is MindustryPlayerMessage.Action.Join ->
                        ":green_square: **${message.player.name}** has joined the server."
                    is MindustryPlayerMessage.Action.Quit ->
                        ":red_square: **${message.player.name}** has left the server."
                    is MindustryPlayerMessage.Action.Chat ->
                        ":blue_square: **${message.player.name}**: ${action.message}"
                }
            channel.sendMessage(text).setAllowedMentions(emptySet()).await()
        }

        messenger.consumer<MindustryServerMessage> { message ->
            val channel = getLiveChatChannel(message.server) ?: return@consumer
            val text = buildString {
                append(":purple_square: ")
                if (message.chat) append("**${message.server.name}**: ")
                append(message.message)
            }
            channel.sendMessage(text).setAllowedMentions(emptySet()).await()
        }
    }

    private suspend fun getLiveChatChannel(server: Identity.Server): TextChannel? {
        val category = discord.getMainServer().getCategoryById(config.categories.liveChat)
        if (category == null) {
            LOGGER.error("Live chat category is not defined.")
            return null
        }
        val channel =
            category.channels.find { it.name == server.name }
                ?: discord.getMainServer().createTextChannel(server.name, category).await()
        if (channel !is TextChannel) {
            LOGGER.error("Channel ${channel.name} (${channel.id}) is not a text channel")
            return null
        }
        return channel
    }

    companion object {
        private val LOGGER = logger<MindustryBridgeListener>()
    }
}
