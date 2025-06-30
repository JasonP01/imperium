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
package com.xpdustry.imperium.mindustry.events

import arc.Events
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Flag
import com.xpdustry.imperium.mindustry.game.MenuToPlayEvent
import com.xpdustry.imperium.mindustry.misc.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.game.EventType.BlockBuildBeginEvent
import mindustry.game.EventType.GameOverEvent
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.world.Tile
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.ConstructBlock.ConstructBuild

class EventListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val freeTiles = mutableListOf<Pair<Int, Int>>()
    // Tiles that have StaticWalls in thier area
    // Used when we have no valid freeTiles
    private val otherTiles = mutableListOf<Pair<Int, Int>>()
    private val crateLabels = mutableListOf<CrateData>()
    private val crates = mutableMapOf<Pair(Int, Int), CrateData>()
    private var delayJob: Job? = null

    @EventHandler
    fun onDelayStart(event: MenuToPlayEvent) {
        delayJob =
            ImperiumScope.MAIN.launch {
                // delay between 3 minutes and 13 minutes
                delay(Random.nextLong(3 * 60, 13 * 60).seconds)
                while (isActive) {
                    onCrateGenerate()
                    delay(Random.nextLong(3 * 60, 13 * 60).seconds)
                }
            }
    }

    @EventHandler
    fun onDelayRemove(event: GameOverEvent) {
        delayJob?.cancel()
        delayJob = null
    }

    @TaskHandler(interval = 10, unit = MindustryTimeUnit.SECONDS)
    fun registerfreeTiles() {
        freeTiles.clear()
        for (x in 0..Vars.world.width()) {
            for (y in 0..Vars.world.height()) {
                if (checkValid(x, y, false)) {
                    freeTiles.add(x to y)
                }
            }
        }
    }

    @TaskHandler(interval = 10L, unit = MindustryTimeUnit.SECONDS)
    fun registerOtherTiles() {
        otherTiles.clear()
        for (x in 0..Vars.world.width()) {
            for (y in 0..Vars.world.height()) {
                if (checkValid(x, y, true)) {
                    otherTiles.add(x to y)
                }
            }
        }
    }

    @TaskHandler(interval = 1L, unit = MindustryTimeUnit.SECONDS)
    fun crateRarityLabel() {
        for (cdata in crateLabels) {
            val rarityText =
                when (cdata.rarity) {
                    1 -> "[grey]Common"
                    2 -> "[green]Uncommon"
                    3 -> "[royal]Rare"
                    4 -> "[purple]Epic"
                    5 -> "[gold]Legendary"
                    6 -> "[orange]M[gold]y[orange]t[gold]h[orange]i[gold]c"
                    else -> "Unknown, this shouldnt happen"
                }
            Call.label(
                "Event Vault\nRarity: $rarityText", 1f, cdata.x.toWorldFloat(), cdata.y.toWorldFloat())
        }
    }

    // TODO: should this stay during the event?
    @ImperiumCommand(["crate"], Rank.ADMIN)
    @ClientSide
    fun onManualGenerateCommand(
        sender: CommandSender,
        x: Int = 0,
        y: Int = 0,
        @Flag rarity: Int = 0
    ) {
        generateCrate(x, y, rarity, true)
        sender.player.sendMessage("Spawned crate at $x, $y with rarity $rarity")
    }

    fun onCrateGenerate() {
        val localfreeTiles: MutableList<Pair<Int, Int>>
        val dirty: Boolean

        if (freeTiles.isEmpty()) {
            if (otherTiles.isEmpty()) {
                LOGGER.error("How is the entire map full??")
                Call.showInfoMessage(
                    """
                    [scarlet]The map has no valid tiles left to spawn crates!
                    [white] Free up space for crates to spawn!
                    """.trimIndent())
                return
            }
            localfreeTiles = otherTiles.toMutableList()
            dirty = true
        } else {
            localfreeTiles = freeTiles.toMutableList()
            dirty = false
        }

        while (localfreeTiles.isNotEmpty()) {
            val randomTile = localfreeTiles.random()
            val (x, y) = randomTile
            if (checkValid(x, y, dirty)) {
                generateCrate(x, y, 0, dirty)
                return
            } else {
                localfreeTiles.remove(randomTile)
            }
        }
        LOGGER.error(
            "Failed to generate crate: No valid tiles left.") // tmp log, remove map walls instead
        registerfreeTiles()
        registerOtherTiles()
    }

    fun generateCrate(x: Int, y: Int, rarity: Int, wasDirty: Boolean) {
        val newRarity = rarity.takeIf { it != 0 } ?: generateRarity()
        val tile = Vars.world.tile(x, y)
        val crateData = CrateData(newRarity, x, y, if (wasDirty) {
            (x - 1..x + 1).flatMap { x1 ->
                (y - 1..y + 1).mapNotNull { y1 ->
                    Vars.world.tile(x1, y1)
                }
            }
        } else emptyList())
        crates[Pair(x, y)] = crateData

        val rarityText =
            when (newRarity) {
                1 -> "[grey]Common"
                2 -> "[green]Uncommon"
                3 -> "[royal]Rare"
                4 -> "[purple]Epic"
                5 -> "[gold]Legendary"
                6 -> "[orange]M[gold]y[orange]t[gold]h[orange]i[gold]c"
                else -> "Unknown Rarity"
            }

        tile.setNet(Blocks.vault)
        Call.sendMessage(
            "A $rarityText [white]crate has spawned at (${tile.x}, ${tile.y})")
        crateLabels.add(crateData)
    }

    @EventHandler
    fun onCrateDeletion(event: BlockBuildBeginEvent) {
        if (!event.breaking) return
        val building = event.tile.build
        if (building !is ConstructBlock.ConstructBuild || building.current != Blocks.vault) return
        val x = event.tile.x
        val y = event.tile.y
        val crate = crates[Pair(x, y)] ?: return
        handleCrateRemoval(crate, event.tile, event.team)
    }

    fun handleCrateRemoval(vault: CrateData, tile: Tile, team: Team) {
        val crate = getVaultByRarity(vault.rarity).random()
        crate.effect(vault.x, vault.y, team)
        crates.remove(Pair(vault.x, vault.y))
        // Restore previous blocks if any were replaced
        if (vault.was.isNotEmpty()) {
            for (prevTile in vault.was) {
                // Only restore if the tile is valid and not null
                val originalTile = Vars.world.tile(prevTile.x, prevTile.y)
                if (originalTile != null) {
                    originalTile.setNet(prevTile.block())
                }
            }
        } else {
            tile.setNet(Blocks.air)
        }
        crateLabels.removeIf { it.x == vault.x && it.y == vault.y && it.rarity == vault.rarity }
    }

    fun generateRarity(): Int {
        val randomValue = Random.nextDouble(0.0, 100.0)
        return when {
            randomValue < 0.5 -> 6
            randomValue < 2 -> 5
            randomValue < 10 -> 4
            randomValue < 25 -> 3
            randomValue < 50 -> 2
            else -> 1
        }
    }

    fun checkValid(x: Int, y: Int, replace: Boolean): Boolean {
        if (!replace) {
            return (x - 1..x + 1).all { x1 ->
                (y - 1..y + 1).all { y1 ->
                    val tile = Vars.world.tile(x1, y1)
                    tile != null && tile.block() == Blocks.air
                }
            }
        } else {
            return (x - 1..x + 1).all { x1 ->
                (y - 1..y + 1).all { y1 ->
                    val tile = Vars.world.tile(x1, y1)
                    tile != null &&
                        (tile.block() == Blocks.air || tile.block() is StaticWall)
                }
            }
        }
    }

    companion object {
        private val LOGGER by LoggerDelegate()
    }
}