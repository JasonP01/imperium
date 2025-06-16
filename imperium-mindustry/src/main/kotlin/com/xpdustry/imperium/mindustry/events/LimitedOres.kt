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

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.lifecycle.LifecycleListener
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.game.MenuToPlayEvent
import kotlin.random.Random
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Items
import mindustry.gen.Groups
import mindustry.type.Item
import mindustry.world.Tile
import mindustry.world.blocks.environment.OreBlock
import mindustry.world.blocks.environment.StaticWall
import mindustry.world.blocks.production.BeamDrill
import mindustry.world.blocks.production.BeamDrill.BeamDrillBuild
import mindustry.world.blocks.production.Drill
import mindustry.world.blocks.production.Drill.DrillBuild
import mindustry.world.blocks.production.WallCrafter
import mindustry.world.blocks.production.WallCrafter.WallCrafterBuild
import mindustry.world.meta.Attribute

class LimitedOres : LifecycleListener {
    private val ores = mutableMapOf<Pair<Int, Int>, Pair<Item, Double>>()
    private val blockOres = mutableMapOf<Pair<Int, Int>, Pair<Item, Double>>()
    private val floorOres = mutableMapOf<Pair<Int, Int>, Pair<Item, Double>>()

    private var config = 0.005
    private var chance = 15
    private var mapIndexed = false

    private val oresToRemove = mutableListOf<Pair<Int, Int>>()
    private val floorsToRemove = mutableListOf<Pair<Int, Int>>()
    private val blocksToRemove = mutableListOf<Pair<Int, Int>>()

    @EventHandler
    fun onMapStart(event: MenuToPlayEvent) {
        for (tile in Vars.world.tiles) {
            val coords = tile.x.toInt() to tile.y.toInt()
            if (tile.floor().itemDrop != null) floorOres[coords] = tile.floor().itemDrop to 1.0
            if (tile.overlay() != null && tile.overlay() is OreBlock) ores[coords] = tile.overlay().itemDrop to 1.0
            if (tile.block() != null) {
                if (tile.block().itemDrop != null) blockOres[coords] = tile.block().itemDrop to 1.0
                else if (tile.block().attributes.get(Attribute.sand) > 0F) blockOres[coords] = Items.sand to 1.0
            }
        }
        mapIndexed = true
    }

    @TaskHandler(interval = 3, unit = MindustryTimeUnit.SECONDS)
    fun onOreDecay() {
        if (Vars.state.isPaused) return
        if (!mapIndexed) return
        for ((coords, pair) in ores) {
            val (item, value) = pair
            val tile = Vars.world.tile(coords.first, coords.second) ?: continue
            val build = tile.build
            if (build is Drill.DrillBuild && build.dominantItem == item && build.efficiency > 0) {
                val random = Random.nextInt(1, 101)
                if (random <= chance) {
                    ores[coords] = item to value - config
                }
            }
        }
        for ((coords, pair) in floorOres) {
            val (item, value) = pair
            val tile = Vars.world.tile(coords.first, coords.second) ?: continue
            val build = tile.build
            if (build is Drill.DrillBuild && build.dominantItem == item && build.efficiency > 0) {
                val random = Random.nextInt(1, 101)
                if (random <= chance) {
                    floorOres[coords] = item to value - config
                }
            }
        }
        Groups.build.each { b ->
            if (
                (b is BeamDrill.BeamDrillBuild && b.efficiency > 0) ||
                    (b is WallCrafter.WallCrafterBuild && b.efficiency > 0)
            ) {

                val facingTiles =
                    when (b) {
                        is BeamDrill.BeamDrillBuild -> b.facing.toList()
                        is WallCrafter.WallCrafterBuild -> getFacingTiles(b)
                        else -> emptyList()
                    }

                for (t in facingTiles) {
                    if (t == null) continue
                    val coords = t.x.toInt() to t.y.toInt()
                    if (b is BeamDrill.BeamDrillBuild && t.overlay()?.itemDrop != null) {
                        val (oitem, ovalue) = ores[coords] ?: continue
                        val orandom = Random.nextInt(1, 101)
                        if (orandom <= chance) {
                            ores[coords] = oitem to ovalue - config
                        }
                    }
                    // Don't decay blocks from plasma bores
                    if (t.block() != Blocks.graphiticWall && b is BeamDrill.BeamDrillBuild) continue
                    val (item, value) = blockOres[coords] ?: continue
                    val random = Random.nextInt(1, 101)
                    if (random <= chance) {
                        blockOres[coords] = item to value - config
                    }
                }
            }
        }
        onOreDecayRemoval()
    }

    @ImperiumCommand(["setconfig"], Rank.ADMIN)
    @ClientSide
    @ServerSide
    fun onConfigCommand(sender: CommandSender, value: Double?, chancevalue: Int?) {
        value?.let { config = it }
        chancevalue?.let { chance = it }
        sender.reply("Set decay rate to $config, $chance")
    }

    @ImperiumCommand(["viewconfig"], Rank.MODERATOR)
    @ClientSide
    @ServerSide
    fun onConfigViewCommand(sender: CommandSender) {
        sender.reply("Current decay rate: $config, $chance")
    }

    @ImperiumCommand(["entry"], Rank.ADMIN)
    @ClientSide
    @ServerSide
    private fun getEntry(sender: CommandSender, x: Int, y: Int, type: String) {
        when (type) {
            "block" -> {
                sender.reply("${blockOres[Pair(x, y)]}")
            }
            "floor" -> {
                sender.reply("${floorOres[Pair(x, y)]}")
            }
            "overlay" -> {
                sender.reply("${ores[Pair(x, y)]}")
            }
            else -> sender.reply("Invalid lookup type")
        }
    }

    fun onOreDecayRemoval() {
        oresToRemove.clear()
        floorsToRemove.clear()
        blocksToRemove.clear()
        for ((coords, pair) in ores) {
            val (item, value) = pair
            val tile = Vars.world.tile(coords.first, coords.second) ?: continue
            if (value <= 1e-6 && tile.overlay().itemDrop == item) {
                tile.setOverlayNet(Blocks.air.asFloor())
                oresToRemove.add(coords)
            }
        }
        for ((coords, pair) in floorOres) {
            val (item, value) = pair
            val tile = Vars.world.tile(coords.first, coords.second) ?: continue
            if (value <= 1e-6 && tile.floor().itemDrop == item) {
                tile.setFloorNet(Blocks.charr.asFloor())
                floorsToRemove.add(coords)
            }
        }
        for ((coords, pair) in blockOres) {
            val (item, value) = pair
            val tile = Vars.world.tile(coords.first, coords.second) ?: continue
            if (value <= 1e-6) {
                if (
                    tile.block() is StaticWall && tile.block().attributes.get(Attribute.sand) != 0F ||
                        tile.block().itemDrop != null
                ) {
                    tile.setNet(Blocks.dirtWall)
                    blocksToRemove.add(coords)
                }
            }
        }
        oresToRemove.forEach { ores.remove(it) }
        floorsToRemove.forEach { floorOres.remove(it) }
        blocksToRemove.forEach { blockOres.remove(it) }
    }

    fun getFacingTiles(build: WallCrafter.WallCrafterBuild): List<Tile> {
        val tiles = mutableListOf<Tile>()

        val tx = build.tileX()
        val ty = build.tileY()
        val size = build.block.size
        val cornerX = tx - (size - 1) / 2
        val cornerY = ty - (size - 1) / 2

        for (i in 0 until size) {
            val (rx, ry) =
                when (build.rotation) {
                    0 -> cornerX + size to cornerY + i
                    1 -> cornerX + i to cornerY + size
                    2 -> cornerX - 1 to cornerY + i
                    3 -> cornerX + i to cornerY - 1
                    else -> continue
                }

            val tile = Vars.world.tile(rx, ry)
            if (tile != null) tiles.add(tile)
        }

        return tiles
    }
}
