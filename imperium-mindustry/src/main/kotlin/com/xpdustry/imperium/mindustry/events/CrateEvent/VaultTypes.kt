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

import arc.graphics.Color
import arc.math.geom.Vec2
import arc.util.Tmp
import com.xpdustry.imperium.mindustry.misc.toWorldFloat
import mindustry.Vars
import mindustry.content.Fx
import mindustry.content.UnitTypes
import mindustry.entities.*
import mindustry.entities.abilities.*
import mindustry.entities.bullet.*
import mindustry.entities.effect.*
import mindustry.entities.part.*
import mindustry.entities.pattern.*
import mindustry.game.Team
import mindustry.gen.*
import mindustry.graphics.Pal
import mindustry.type.*
import mindustry.type.ammo.*
import mindustry.type.unit.*
import mindustry.type.weapons.*

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
    val was: MutableList<Tile>
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

object VaultTypes {
    val commonVault =
        listOf(
            Vault("Electric Dagger", 1, true) { x, y, team ->
                repeat(1) {
                    val unit = UnitTypes.dagger.create(team)
                    unit.weapons.clear() // remove old weapon
                    unit.weapons.addAll(UnitTypes.quasar.weapons)
                    unit.rotation(0f)
                    Tmp.v1.rnd(Vars.tilesize * 2)
                    unit.set(x + Tmp.v1.x, x + Tmp.v1.y)
                    unit.add()
                    Call.effect(Fx.spawn, x.toFloat(), y.toFloat(), 0f, team.color)
                }
            },
            Vault("test2", 1, false) { x, y, team -> println("i dont want to finish this") },
        )

    val uncommonVault =
        listOf(
            Vault("Crawler Bomb", 2, true) { x, y, team ->
                val unit = UnitTypes.crawler.create(team)
                Tmp.v1.rnd(Vars.tilesize * 2)
                unit.weapons.clear()
                unit.weapons.add(UnitTypes.quad.weapons.first())
                unit.set(x + Tmp.v1.x, x + Tmp.v1.y)
                unit.add()
                Call.effect(Fx.spawn, x, y, 0f, team.color)
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
}