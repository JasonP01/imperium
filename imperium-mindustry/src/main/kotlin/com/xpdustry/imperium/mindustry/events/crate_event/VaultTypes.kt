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
package com.xpdustry.imperium.mindustry.events.crate_event

import com.xpdustry.imperium.mindustry.misc.toWorldFloat
import mindustry.content.Fx
import mindustry.content.UnitTypes
import mindustry.game.Team
import mindustry.gen.*
import mindustry.world.Tile

data class Vault(
    val name: String,
    val rarity: Int,
    val positive: Boolean,
    val effect: (Int, Int, Team) -> Unit
)

data class CrateData(
    val rarity: Int,
    val x: Int,
    val y: Int,
    val was: MutableList<Tile>,
    val type: String?
)

fun getVaultByRarity(rarity: Int): List<Vault> {
    return when (rarity) {
        1 -> VaultTypes.commonVault
        2 -> VaultTypes.uncommonVault
        3 -> VaultTypes.rareVault
        4 -> VaultTypes.epicVault
        5 -> VaultTypes.legendaryVault
        6 -> VaultTypes.mythicVault
        else -> emptyList()
    }
}

fun getVaultByType(vault: String): Vault? {
    return VaultTypes.getByName(vault)
}

object VaultTypes {
    val commonVault =
        listOf(
            // Quasar dagger - Chronus?
            Vault("Electric Dagger", 1, true) { x, y, team ->
                repeat(1) {
                    val unit = UnitTypes.quasar.spawn(team,x.toWorldFloat(), y.toWorldFloat(), 0f)
                    unit.type = UnitTypes.dagger
                    Call.effect(Fx.spawn, x.toFloat(), y.toFloat(), 0f, team.color)
                }
            },
            Vault("test2", 1, false) { x, y, team -> println("i dont want to finish this") },
        )

    val uncommonVault =
        listOf(
            // Quad crawler
            Vault("Crawler Bomb", 2, true) { x, y, team ->
                val unit = UnitTypes.quad.spawn(team, x.toWorldFloat(), y.toWorldFloat(), 0f)
                unit.type = UnitTypes.crawler
                Call.effect(Fx.spawn, x.toWorldFloat(), y.toWorldFloat(), 0f, team.color)
            },
            Vault("test2", 2, false) { x, y, team ->
                // Todo
            },
        )

    val rareVault =
        listOf(
            Vault("test1", 3, true) { x, y, team ->
                // Todo
            },
            Vault("test2", 3, false) { x, y, team ->
                // Todo
            },
        )

    val epicVault =
        listOf(
            Vault("test1", 4, true) { x, y, team ->
                // Todo
            },
            Vault("test2", 4, false) { x, y, team ->
                // Todo
            },
        )

    val legendaryVault =
        listOf(
            Vault("test1", 5, true) { x, y, team ->
                // Todo
            },
            Vault("test2", 5, false) { x, y, team ->
                // Todo
            },
        )

    val mythicVault =
        listOf(
            Vault("test1", 6, true) { x, y, team ->
                // Todo
            },
            Vault("test2", 6, true) { x, y, team ->
                // Todo
            },
        )

    fun getByName(name: String): Vault? {
        return sequenceOf(
            commonVault,
            uncommonVault,
            rareVault,
            epicVault,
            legendaryVault,
            mythicVault
        ).flatMap { it.asSequence() }
            .find { it.name.equals(name, ignoreCase = true) }
    }
}