/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.random.nextInt
import org.javacord.api.entity.Attachment
import org.javacord.api.entity.message.embed.EmbedBuilder

class SchematicCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val content = instances.get<MindustryContentHandler>()

    @Command(["schematic", "text"])
    @NonEphemeral
    suspend fun onSchematicCommand(actor: InteractionSender, schematic: String) {
        val result = content.getSchematic(schematic)
        if (result.isFailure) {
            actor.respond("Failed to parse the schematic.")
            return
        }

        val parsed = result.getOrThrow()
        val bytes = ByteArrayOutputStream()
        content.writeSchematic(parsed, bytes).getOrThrow()

        val name = "${parsed.name().stripMindustryColors()}_${Random.nextInt(1000..9999)}.msch"
        actor.respond {
            addAttachment(ByteArrayInputStream(bytes.toByteArray()), name)
            addEmbed(
                EmbedBuilder()
                    .setAuthor(actor.user)
                    .setTitle(parsed.name())
                    .setImage(content.getSchematicPreview(parsed).getOrThrow())
                    .setTimestampToNow(),
            )
        }
    }

    @Command(["schematic", "file"])
    @NonEphemeral
    suspend fun onSchematicCommand(actor: InteractionSender, file: Attachment) {
        if (!file.fileName.endsWith(".msch")) {
            actor.respond("Invalid schematic file!")
            return
        }

        if (file.size > MAX_FILE_SIZE) {
            actor.respond("Schematic file is too large!")
            return
        }

        val result = content.getSchematic(file.asInputStream())
        if (result.isFailure) {
            actor.respond("Failed to parse the schematic.")
            return
        }

        val parsed = result.getOrThrow()
        val name = "${parsed.name().stripMindustryColors()}_${Random.nextInt(1000..9999)}.msch"
        actor.respond {
            addAttachment(file.asInputStream(), name)
            addEmbed(
                EmbedBuilder()
                    .setAuthor(actor.user)
                    .setTitle(parsed.name())
                    .setImage(content.getSchematicPreview(parsed).getOrThrow())
                    .setTimestampToNow(),
            )
        }
    }

    companion object {
        // 2MB
        private const val MAX_FILE_SIZE = 2 * 1024 * 1024
    }
}