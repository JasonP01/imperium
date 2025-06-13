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
import mindustry.world.blocks.environment.OreBlock
import mindustry.world.blocks.environment.StaticWall
import mindustry.world.blocks.production.BeamDrill
import mindustry.world.blocks.production.BeamDrill.BeamDrillBuild
import mindustry.world.blocks.production.Drill
import mindustry.world.blocks.production.Drill.DrillBuild
import mindustry.world.meta.Attribute
import mindustry.world.Tile


class LimitedOres : LifecycleListener {
    // Regular ores (eg copper, lead)
    private val ores = mutableMapOf<Tile, Pair<Item, Double>>()
    // Block ores (graphite wall)
    private val blockOres = mutableMapOf<Tile, Pair<Item, Double>>()
    // Floor ores (sand)
    private val floorOres = mutableMapOf<Tile, Pair<Item, Double>>()
    // How much the ore decays every second by chance
    private var config = 0.02
    private var chance = 30

    private val oresToRemove = mutableListOf<Tile>()
    private val floorsToRemove = mutableListOf<Tile>()
    private val blocksToRemove = mutableListOf<Tile>()

    @EventHandler
    fun onMapStart(event: MenuToPlayEvent) {
        for (tile in Vars.world.tiles) {
            // We will replace graphiticWall with carbonWall
            if (tile.floor().itemDrop != null) floorOres[tile] = Pair(tile.floor().itemDrop, 1.0)
            if (tile.overlay() != null && tile.overlay() is OreBlock) ores[tile] = Pair(tile.overlay().itemDrop, 1.0)
            if (tile.block() != null) {
                if (tile.block().itemDrop != null) blockOres[tile] = Pair(tile.block().itemDrop, 1.0)
                // This is ineffiecent, alot of blocks have this attribute, make it look for air nearby?
                else if (tile.block().attributes.get(Attribute.sand) > 0F) blockOres[tile] = Pair(Items.sand, 1.0)
            }
        }
    }

    @TaskHandler(interval = 1, unit = MindustryTimeUnit.SECONDS)
    fun onOreDecay() {
        for ((tile, pair) in ores) {
            val (item, value) = pair
            val build = tile.build
            if (build is Drill.DrillBuild && build.dominantItem == item) {
                val random = Random.nextInt(1, 101)
                if (random < chance) {
                    ores[tile] = Pair(item, value - config)
                }
            }
        }
        for ((tile, pair) in floorOres) {
            val (item, value) = pair
            val build = tile.build
            if (build is Drill.DrillBuild && build.dominantItem == item) {
                val random = Random.nextInt(1, 101)
                if (random < chance) {
                    floorOres[tile] = Pair(item, value - config)
                }
            }
        }
        Groups.build.each({ b -> 
            if (b is BeamDrill.BeamDrillBuild) {
                for (t in b.facing) {
                    val (item, value) = blockOres[t]!!
                    val random = Random.nextInt(1, 101)
                    if (random < chance) {
                        blockOres[t] = Pair(item, value - config)
                    }
                }
            }
        })
        onOreDecayRemoval()
    }

    @ImperiumCommand(["eventconfig"], Rank.ADMIN)
    @ClientSide
    @ServerSide
    fun onConfigCommand(sender: CommandSender, value: Double?, chancevalue: Int?) {
        value?.let { config = it }
        chancevalue?.let { chance = it }
        sender.reply("Set decay rate to $config, $chance")
    }

    fun onOreDecayRemoval() {
        for ((tile, pair) in ores) {
            val (item, value) = pair
            if (value <= 0.0) {
                println("Removing ore pair ($tile, $item)")
                if (tile.overlay().itemDrop == item) {
                    oresToRemove.add(tile)
                }
            }
        }
        for ((tile, pair) in floorOres) {
            val (item, value) = pair
            if (value <= 0.0) {
                println("Removing floor ore pair ($tile, $item)")
                if (tile.floor().itemDrop == item) {
                    tile.setFloorNet(Blocks.charr.asFloor())
                    floorsToRemove.add(tile)
                }
            }
        }
        for ((tile, pair) in blockOres) {
            val (item, value) = pair
            if (value <= 0.0) {
                println("Removing block ore pair ($tile, $item)")
                if (tile.block() is StaticWall && tile.block().attributes.get(Attribute.sand) != 0F) {
                    tile.setNet(Blocks.air)
                    blocksToRemove.add(tile)
                }
            }
        }
        oresToRemove.forEach { ores.remove(it) }
        floorsToRemove.forEach { floorOres.remove(it) }
        blocksToRemove.forEach { blockOres.remove(it) }
    }
}