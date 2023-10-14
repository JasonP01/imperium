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
package com.xpdustry.imperium.common.bridge

import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.security.Identity
import kotlinx.serialization.Serializable

@Serializable
class MindustryPlayerMessage(
    val serverName: String,
    val player: Identity.Mindustry,
    val action: Action
) : Message {
    @Serializable
    sealed interface Action {
        @Serializable data object Join : Action

        @Serializable data object Quit : Action

        @Serializable data class Chat(val message: String) : Action
    }
}
