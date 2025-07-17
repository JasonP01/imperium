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
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.image.ImageFormat
import com.xpdustry.imperium.common.image.inputStream
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.common.webhook.WebhookChannel
import com.xpdustry.imperium.common.webhook.WebhookMessage
import com.xpdustry.imperium.common.webhook.WebhookMessageSender
import com.xpdustry.nohorny.image.NoHornyResult
import com.xpdustry.nohorny.image.analyzer.ImageAnalyzerEvent
import java.awt.image.BufferedImage
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.requests.RestAction
import java.time.OffsetDateTime
import okhttp3.MediaType.Companion.toMediaType

class NoHornyListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val users = instances.get<UserManager>()
    private val punishments = instances.get<PunishmentManager>()
    private val config = instances.get<ImperiumConfig>()
    private val webhook = instances.get<WebhookMessageSender>()
    private val codec = instances.get<IdentifierCodec>()
    private val discordService = instances.get<DiscordService>() // Assume this is injected

    @EventHandler
    fun onImageLogicAnalyzer(event: ImageAnalyzerEvent) =
        ImperiumScope.MAIN.launch {
            when (event.result.rating) {
                NoHornyResult.Rating.SAFE -> Unit
                NoHornyResult.Rating.WARNING -> {
                    logger.debug(
                        "Cluster in rect ({}, {}, {}, {}) is possibly unsafe.",
                        event.group.x,
                        event.group.y,
                        event.group.w,
                        event.group.h,
                    )

                    val expiry = System.currentTimeMillis() + 30.days.inWholeMilliseconds
                    val id = event.author?.uuid?.let { users.findByUuid(it) }?.id?.let(codec::encode)

                    val buttons = mutableListOf(
                        WebhookMessage.Button(
                            label = "Expires in 1 month",
                            customId = expiry.toString(),
                            style = WebhookMessage.Button.Style.SECONDARY,
                            disabled = true
                        )
                    )
                    if (id != null) {
                        buttons.add(
                            WebhookMessage.Button(
                                label = "Ban",
                                customId = id,
                                style = WebhookMessage.Button.Style.DANGER,
                                disabled = false
                            )
                        )
                    }

                    webhook.send(
                        WebhookChannel.NOHORNY,
                        WebhookMessage(
                            content =
                                buildString {
                                    appendLine("**Possible NSFW image detected**")
                                    append("Located at ${event.group.x}, ${event.group.y}")
                                    if (id != null) {
                                        append(" by user `$id`")
                                    }
                                    appendLine()
                                    for ((entry, percent) in event.result.details) {
                                        appendLine("- ${entry.name}: ${"%.1f %%".format(percent * 100)}")
                                    }
                                },
                            attachments = listOf(event.image.toUnsafeAttachment()),
                            components = buttons,
                        ),
                    )
                }
                NoHornyResult.Rating.UNSAFE -> {
                    logger.info(
                        "Cluster in rect ({}, {}, {}, {}) is unsafe. Destroying blocks.",
                        event.group.x,
                        event.group.y,
                        event.group.w,
                        event.group.h,
                    )

                    val user = event.author?.uuid?.let { users.findByUuid(it) } ?: return@launch
                    val punishment =
                        punishments.punish(
                            config.server.identity,
                            user.id,
                            "Placing NSFW image",
                            Punishment.Type.BAN,
                            30.days,
                        )

                    val buttons = mutableListOf<WebhookMessage.Button>()
                    val punishmentId = codec.encode(punishment)
                    buttons.add(
                        WebhookMessage.Button(
                            label = "Pardon",
                            customId = "pardon:$punishmentId",
                            style = WebhookMessage.Button.Style.SUCCESS,
                            disabled = false
                        )
                    )

                    webhook.send(
                        WebhookChannel.NOHORNY,
                        WebhookMessage(
                            content =
                                buildString {
                                    appendLine("**NSFW image detected**")
                                    appendLine("Related to punishment `$punishmentId`")
                                    for ((entry, percent) in event.result.details) {
                                        appendLine("- ${entry.name}: ${"%.1f %%".format(percent * 100)}")
                                    }
                                },
                            attachments = listOf(event.image.toUnsafeAttachment()),
                            components = buttons,
                        ),
                    )
                }
            }
        }

    private fun BufferedImage.toUnsafeAttachment() =
        WebhookMessage.Attachment("SPOILER_image.jpg", "NSFW image", "image/jpeg".toMediaType()) {
            inputStream(ImageFormat.JPG)
        }

    fun handleDiscordMessage(message: Message, channel: MessageChannel) {
        // Only delete if not pinned and does not contain a button
        val hasButton = message.actionRows.any { it.components.isNotEmpty() }
        val isPinned = message.isPinned

        if (!hasButton && !isPinned) {
            channel.deleteMessageById(message.id).queue()
        }
    }

    /**
     * Fetches all messages in the given channel, filters out pinned and messages with buttons,
     * bulk deletes those eligible, and manually deletes those older than 14 days.
     * Additionally, deletes messages with expiry buttons that have expired and logs to CONSOLE.
     */
    suspend fun cleanChannel(channel: MessageChannel) = withContext(Dispatchers.IO) {
        val allMessages = mutableListOf<Message>()
        var lastMessageId: String? = null
        var fetchMore = true

        // Fetch messages in batches of 100 until none left
        while (fetchMore) {
            val action: RestAction<List<Message>> =
                if (lastMessageId == null) channel.history.retrievePast(100)
                else channel.history.retrievePast(100).limit(100).before(lastMessageId)
            val batch = action.submit().get()
            if (batch.isEmpty()) break
            allMessages.addAll(batch)
            lastMessageId = batch.last().id
            fetchMore = batch.size == 100
        }

        val nowMillis = System.currentTimeMillis()
        val now = OffsetDateTime.now()
        val twoWeeksAgo = now.minusDays(14)

        val toBulkDelete = mutableListOf<Message>()
        val toManualDelete = mutableListOf<Message>()

        for (msg in allMessages) {
            val isPinned = msg.isPinned
            val hasButton = msg.actionRows.any { it.components.isNotEmpty() }
            var expiredNoHorny = false

            // Check for expiry button
            msg.actionRows.forEach { row ->
                row.components.forEach { comp ->
                    val customId = comp.id
                    if (customId != null) {
                        customId.toLongOrNull()?.let { expiryMillis =>
                            if (nowMillis >= expiryMillis) {
                                expiredNoHorny = true
                            }
                        }
                    }
                }
            }

            if (expiredNoHorny) {
                msg.delete().queue()
                webhook.send(
                    WebhookChannel.CONSOLE,
                    WebhookMessage(content = "Deleted NoHorny message ${msg.id} due to expiry.")
                )
                continue
            }

            if (!hasButton && !isPinned) {
                if (msg.timeCreated.isAfter(twoWeeksAgo)) {
                    toBulkDelete.add(msg)
                } else {
                    toManualDelete.add(msg)
                }
            }
        }

        // Bulk delete (max 100 per call, only messages younger than 14 days)
        toBulkDelete.chunked(100).forEach { chunk ->
            channel.deleteMessages(chunk).queue()
        }

        // Manually delete older messages
        toManualDelete.forEach { msg ->
            channel.deleteMessageById(msg.id).queue()
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}

// --- Button Handlers ---

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.interactions.modals.Modal
import net.dv8tion.jda.api.interactions.modals.ModalMapping
import net.dv8tion.jda.api.interactions.modals.ModalTextInput
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.user.User

// Handler for Ban button, requires Rank.MODERATOR
fun handleBanButton(
    event: ButtonInteractionEvent,
    punishments: PunishmentManager,
    users: UserManager,
    getUserRank: (String) -> Rank // Function to get rank by Discord user id
) {
    val discordUserId = event.user.id
    if (getUserRank(discordUserId) < Rank.MODERATOR) {
        event.reply("You do not have permission to use this button.").setEphemeral(true).queue()
        return
    }
    val userId = event.button.customId
    val modal =
        Modal.create("ban-nsfw-modal", "Ban User for NSFW Image")
            .addActionRow(
                ModalTextInput.create("ban-user-id", "User Id - Do not edit", ModalTextInput.Style.SHORT)
                    .setRequired(true)
                    .setValue(userId)
                    .setPlaceholder("User Id")
                    .build()
            )
            .addActionRow(
                ModalTextInput.create("ban-reason", "Ban Reason", ModalTextInput.Style.SHORT)
                    .setRequired(true)
                    .setValue("Nsfw image")
                    .setPlaceholder("Reason for ban")
                    .build()
            )
            .build()
    event.replyModal(modal).queue()
}

// Handler for Ban modal submission, requires Rank.MODERATOR
fun handleBanModal(
    event: ModalInteractionEvent,
    punishments: PunishmentManager,
    users: UserManager,
    getUserRank: (String) -> Rank
) {
    val discordUserId = event.user.id
    if (getUserRank(discordUserId) < Rank.MODERATOR) {
        event.reply("You do not have permission to use this action.").setEphemeral(true).queue()
        return
    }
    val userId = event.getValue("ban-user-id")?.asString ?: return
    val reason = event.getValue("ban-reason")?.asString ?: "Nsfw image"
    val user = users.findById(userId)
    if (user == null) {
        event.reply("User not found.").setEphemeral(true).queue()
        return
    }
    punishments.punish(
        discordUserId, // Discord user who submitted the form
        user.id,
        reason,
        Punishment.Type.BAN,
        30.days
    )
    event.reply("User $userId has been banned for 30 days for reason: $reason").setEphemeral(true).queue()
}

// Handler for Pardon button, requires Rank.MODERATOR
fun handlePardonButton(
    event: ButtonInteractionEvent,
    punishments: PunishmentManager,
    getUserRank: (String) -> Rank
) {
    val discordUserId = event.user.id
    if (getUserRank(discordUserId) < Rank.MODERATOR) {
        event.reply("You do not have permission to use this button.").setEphemeral(true).queue()
        return
    }
    val customId = event.button.customId
    if (!customId.startsWith("pardon:")) return
    val punishmentId = customId.removePrefix("pardon:")
    val entry = punishments.findById(punishmentId)
    if (entry == null) {
        event.reply("Punishment not found.").setEphemeral(true).queue()
        return
    }
    if (entry.pardon != null) {
        event.reply("Punishment already pardoned.").setEphemeral(true).queue()
        return
    }
    punishments.pardon(discordUserId, punishmentId, "Pardoned via button")
    event.reply("Punishment has been pardoned.").setEphemeral(true).queue()
}
