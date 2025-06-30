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
package com.xpdustry.imperium.common.content

import com.xpdustry.imperium.mindustry.events.*
import kotlin.reflect.KClass

enum class MindustryGamemode(val pvp: Boolean = false, val type: MindustryGamemodeSubtype) {
    SURVIVAL(type = MindustryGamemodeSubtype.Standard),
    ATTACK(type = MindustryGamemodeSubtype.Standard),
    PVP(pvp = true, type = MindustryGamemodeSubtype.Standard),
    SANDBOX(type = MindustryGamemodeSubtype.Standard),
    ROUTER(type = MindustryGamemodeSubtype.Standard),
    SURVIVAL_EXPERT(type = MindustryGamemodeSubtype.Standard),
    HEXED(pvp = true, type = MindustryGamemodeSubtype.Standard),
    TOWER_DEFENSE(type = MindustryGamemodeSubtype.Standard),
    HUB(type = MindustryGamemodeSubtype.Standard),
    TESTING(type = MindustryGamemodeSubtype.Standard),
    EVENTS(type = MindustryGamemodeSubtype.Events(MindustryGamemodeSubtype.EventType.NONE)),
}

// Use object for Standard, and class for Events to allow for event type
// Such a long name, shorten?
sealed class MindustryGamemodeSubtype {
    data object Standard : MindustryGamemodeSubtype()
    data class Events(val type: EventType) : MindustryGamemodeSubtype()

    enum class EventType(val clazz: KClass<*>?) {
        NONE(null),
        LIMITED_ORE(LimitedOres::class),
        CRATES(Crates::class),
        // Add more event types as needed
    }
}
