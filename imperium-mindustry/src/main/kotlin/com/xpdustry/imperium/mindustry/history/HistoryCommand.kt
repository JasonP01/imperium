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
package com.xpdustry.imperium.mindustry.history

import arc.util.Strings
import cloud.commandframework.arguments.standard.IntegerArgument
import cloud.commandframework.meta.CommandMeta
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.ImperiumPluginCommandManager
import fr.xpdustry.distributor.api.command.argument.PlayerInfoArgument
import fr.xpdustry.distributor.api.command.sender.CommandSender
import mindustry.Vars
import mindustry.net.Administration.PlayerInfo
import java.awt.Color
import java.util.function.Consumer

// TODO
//  - Add interactive mode like the "/inspector" command ?
//  - Move the rendering logic to a separate class ?
class HistoryCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val history: BlockHistory = instances.get()
    private val clientCommandManager: ImperiumPluginCommandManager = instances.get("client")
    private val serverCommandManager: ImperiumPluginCommandManager = instances.get("server")

    override fun onImperiumInit() {
        withCommandManagers { manager ->
            manager.command(
                manager.commandBuilder("history")
                    .literal("player")
                    .meta(CommandMeta.DESCRIPTION, "Show the history of a player.")
                    .argument(PlayerInfoArgument.of("player"))
                    .argument(
                        IntegerArgument.builder<CommandSender>("limit")
                            .withMin(1)
                            .withMax(50)
                            .asOptionalWithDefault(10),
                    )
                    .handler { ctx ->
                        val info = ctx.get<PlayerInfo>("player")
                        val entries = normalize(history.getHistory(info.id), ctx.get("limit"))
                        if (entries.isEmpty()) {
                            ctx.sender.sendWarning("No history found.")
                            return@handler
                        }
                        val builder = StringBuilder("[accent]History of player [white]").append(info.plainLastName())
                        if (canSeeUuid(ctx.sender)) {
                            builder.append(" [accent](").append(info.id).append(")")
                        }
                        builder.append(":")
                        for (entry in entries) {
                            builder.append("\n[accent] > ").append(renderEntry(entry, false, false, true, 3))
                        }

                        // TODO I really need this Component API
                        ctx.sender
                            .sendMessage(
                                if (ctx.sender.isConsole) Strings.stripColors(builder.toString()) else builder.toString(),
                            )
                    },
            )

            manager.command(
                manager.commandBuilder("history")
                    .literal("tile")
                    .meta(CommandMeta.DESCRIPTION, "Show the history of a tile.")
                    .argument(
                        IntegerArgument.builder<CommandSender>("x").withMin(0).withMax(
                            Short.MAX_VALUE.toInt(),
                        ),
                    )
                    .argument(
                        IntegerArgument.builder<CommandSender>("y").withMin(0).withMax(
                            Short.MAX_VALUE.toInt(),
                        ),
                    )
                    .argument(
                        IntegerArgument.builder<CommandSender>("limit")
                            .withMin(1)
                            .withMax(50)
                            .asOptionalWithDefault(10),
                    )
                    .handler { ctx ->
                        val x = ctx.get<Int>("x")
                        val y = ctx.get<Int>("y")
                        val entries = normalize(history.getHistory(x, y), ctx.get("limit"))
                        if (entries.isEmpty()) {
                            ctx.sender.sendWarning("No history found.")
                            return@handler
                        }
                        val builder = StringBuilder("[accent]History of tile [white]")
                            .append("(")
                            .append(x)
                            .append(", ")
                            .append(y)
                            .append(")[]:")
                        for (entry in entries) {
                            builder.append("\n[accent] > ")
                                .append(renderEntry(entry, true, canSeeUuid(ctx.sender), false, 3))
                        }

                        // TODO I really need this Component API
                        ctx.sender
                            .sendMessage(
                                if (ctx.sender.isConsole) Strings.stripColors(builder.toString()) else builder.toString(),
                            )
                    },
            )
        }
    }

    private fun renderEntry(entry: HistoryEntry, name: Boolean, uuid: Boolean, position: Boolean, indent: Int): String {
        val builder = StringBuilder("[white]")
        if (name) {
            builder.append(getName(entry.author))
            if (uuid && entry.author.uuid != null) {
                builder.append(" [gray](")
                    .append(entry.author.uuid)
                    .append(")")
            }
            builder.append("[white]: ")
        }
        when (entry.type) {
            HistoryEntry.Type.PLACING -> builder.append("Began construction of [accent]").append(entry.block.name)
            HistoryEntry.Type.PLACE -> builder.append("Constructed [accent]").append(entry.block.name)
            HistoryEntry.Type.BREAKING -> builder.append("Began deconstruction of [accent]").append(entry.block.name)
            HistoryEntry.Type.BREAK -> builder.append("Deconstructed [accent]").append(entry.block.name)
            HistoryEntry.Type.ROTATE -> builder.append("Set direction of [accent]").append(entry.block.name)
                .append(" [white]to [accent]")
                .append(getOrientation(entry.rotation))
            HistoryEntry.Type.CONFIGURE -> renderConfiguration(
                builder,
                entry,
                entry.configuration!!,
                indent,
            )
        }
        if (entry.type !== HistoryEntry.Type.CONFIGURE && entry.configuration != null) {
            renderConfiguration(
                builder.append(" ".repeat(indent)).append("\n[accent] > [white]"),
                entry,
                entry.configuration,
                indent + 3,
            )
        }
        builder.append("[white]")
        if (position) {
            builder.append(" at [accent](")
                .append(entry.x)
                .append(", ")
                .append(entry.y)
                .append(")")
        }
        return builder.toString()
    }

    private fun renderConfiguration(
        builder: StringBuilder,
        entry: HistoryEntry,
        config: HistoryConfig,
        ident: Int,
    ) {
        when (config) {
            is HistoryConfig.Composite -> {
                builder.append("Configured [accent]").append(entry.block.name).append("[white]:")
                for (component in config.configurations) {
                    renderConfiguration(
                        builder.append("\n").append(" ".repeat(ident)).append("[accent] - [white]"),
                        entry,
                        component,
                        ident + 3,
                    )
                }
            }
            is HistoryConfig.Text -> {
                builder.append("Changed the [accent]")
                    .append(config.type.name.lowercase())
                    .append("[white] of [accent]")
                    .append(entry.block.name)
                if (config.type === HistoryConfig.Text.Type.MESSAGE) {
                    builder.append("[white] to [gray]").append(config.text)
                }
            }
            is HistoryConfig.Link -> {
                if (config.type === HistoryConfig.Link.Type.RESET) {
                    builder.append("Reset the links of [accent]").append(entry.block.name)
                    return
                }
                builder.append(if (config.type === HistoryConfig.Link.Type.CONNECT) "Connected" else "Disconnected")
                    .append(" [accent]")
                    .append(entry.block.name)
                    .append("[white] ")
                    .append(if (config.type === HistoryConfig.Link.Type.CONNECT) "to" else "from")
                    .append(" [accent]")
                    .append(
                        config.positions.joinToString(", ") { point ->
                            "(${(point.x + entry.buildX)}, ${(point.y + entry.buildY)})"
                        },
                    )
            }
            is HistoryConfig.Canvas -> {
                builder.append("Changed the content of [accent]").append(entry.block.name)
            }
            is HistoryConfig.Content -> {
                if (config.value == null) {
                    builder.append("Reset the content of [accent]").append(entry.block.name)
                    return
                }
                builder.append("Configured [accent]")
                    .append(entry.block.name)
                    .append("[white] to [accent]")
                    .append(config.value.name)
            }
            is HistoryConfig.Enable -> {
                builder.append(if (config.value) "Enabled" else "Disabled")
                    .append(" [accent]")
                    .append(entry.block.name)
            }
            is HistoryConfig.Light -> {
                builder.append("Configured [accent]")
                    .append(entry.block.name)
                    .append("[white] to [accent]")
                    .append(toHex(config.color))
            }
            is HistoryConfig.Simple -> {
                builder.append("Configured [accent]")
                    .append(entry.block.name)
                    .append("[white] to [accent]")
                    .append(config.value?.toString() ?: "null")
            }
        }
    }

    private fun getName(author: HistoryAuthor): String {
        return if (author.uuid != null) Vars.netServer.admins.getInfo(author.uuid).lastName else author.team.name.lowercase() + " " + author.unit.name
    }

    private fun withCommandManagers(consumer: Consumer<ImperiumPluginCommandManager>) {
        consumer.accept(clientCommandManager)
        consumer.accept(serverCommandManager)
    }

    private fun canSeeUuid(sender: CommandSender): Boolean {
        return sender.isConsole || sender.player.admin()
    }

    private fun normalize(entries: List<HistoryEntry>, limit: Int): List<HistoryEntry> {
        // First we sort by timestamp from latest to earliest, then we take the first N elements,
        // then we reverse the list so the latest entries are at the end
        return entries.stream()
            .sorted(Comparator.comparing(HistoryEntry::timestamp).reversed())
            .limit(limit.toLong())
            .sorted(Comparator.comparing(HistoryEntry::timestamp))
            .toList()
    }

    private fun toHex(color: Color): String {
        // https://stackoverflow.com/a/3607942
        return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
    }

    private fun getOrientation(rotation: Int): String = when (rotation % 4) {
        0 -> "right"
        1 -> "top"
        2 -> "left"
        3 -> "bottom"
        else -> error("This should never happen")
    }
}