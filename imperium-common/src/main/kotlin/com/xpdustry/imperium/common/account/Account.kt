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
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.config.MindustryConfig
import java.time.Instant
import kotlin.time.Duration

data class Account(
    val id: Int,
    val username: String,
    val discord: Long?,
    val games: Int,
    val playtime: Duration,
    val creation: Instant,
    val legacy: Boolean,
    val rank: Rank,
    val lastJoin: Instant? = null,
    val lastName: String, // last used ingame name
    val chatMessages: String, // stores 200 chat messages as JSON
    val tileHistory: String // 2000 history logs as JSON
)
