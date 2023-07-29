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
package com.xpdustry.imperium.discord

import com.xpdustry.imperium.common.application.SimpleImperiumApplication
import com.xpdustry.imperium.common.misc.ExitStatus
import com.xpdustry.imperium.common.misc.logger
import java.util.Scanner
import kotlin.system.exitProcess

class ImperiumDiscord : SimpleImperiumApplication(discordModule()) {
    override fun exit(status: ExitStatus) {
        super.exit(status)
        exitProcess(status.ordinal)
    }
}

fun main() {
    val application = ImperiumDiscord()

    application.instances.createSingletons()
    application.init()

    val scanner = Scanner(System.`in`)
    while (scanner.hasNextLine()) {
        val line = scanner.nextLine()
        if (line == "exit") {
            break
        }
        logger<ImperiumDiscord>().info("Type 'exit' to exit.")
    }

    application.exit(ExitStatus.EXIT)
}