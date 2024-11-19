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
package com.xpdustry.imperium.mindustry.event

import arc.math.geom.Vec2
import mindustry.world.blocks.storage.Vault

data class Vault(
    val name: String,
    val rarity: Int,
    val positive: Boolean,
    val effects: (Int, Int) -> Unit
)

object VaultTypes {
    val commonVault =
        listOf(
            Vault("test1", 1, true) { x, y ->
                UnitTypes.oct.spawn(Vec2(x.toFloat(), y.toFloat()), Team.get(255))
            },
            Vault("test2", 1, false) { x, y -> LOGGER.debug("I dont want to do the rest") },
        )

    val uncommonVault =
        listOf(
            Vault("test1", 2, true) { x, y ->
                // Todo
            },
            Vault("test2", 2, false) { x, y ->
                // Todo
            },
        )

    val rareVault =
        listOf(
            Vault("test1", 3, true) { x, y ->
                // Todo
            },
            Vault("test2", 3, false) { x, y ->
                // Todo
            },
        )

    val epicVault =
        listOf(
            Vault("test1", 4, true) { x, y ->
                // Todo
            },
            Vault("test2", 4, false) { x, y ->
                // Todo
            },
        )

    val legendaryVault =
        listOf(
            Vault("test1", 5, true) { x, y ->
                // Todo
            },
            Vault("test2", 5, false) { x, y ->
                // Todo
            },
        )
}
