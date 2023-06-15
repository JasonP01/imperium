/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.mindustry.core.security.verif

import com.google.common.net.InetAddresses
import com.google.inject.Inject
import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.database.Database
import com.xpdustry.foundation.common.misc.switchIfEmpty
import com.xpdustry.foundation.common.misc.toValueMono
import java.time.Duration
import java.time.temporal.ChronoUnit

class PunishmentVerification @Inject constructor(
    private val database: Database,
    private val verification: VerificationService,
) : FoundationListener {

    override fun onFoundationInit() {
        verification.register("punishment") { player ->
            database.punishments
                .findAllByTargetIp(InetAddresses.forString(player.ip()))
                .filter { it.expired.not() }
                .sort { a, b ->
                    val aDuration = a.duration ?: ChronoUnit.FOREVER.duration
                    val bDuration = b.duration ?: ChronoUnit.FOREVER.duration
                    bDuration.compareTo(aDuration)
                }
                .next()
                .map<Verification.Result> {
                    Verification.Failure(
                        """
                        [red]Oh no! You are currently banned from Chaotic Neutral!
                        [accent]Reason:[white] ${it.reason}
                        [accent]Duration:[white] ${formatDuration(it.duration)}

                        [accent]Appeal in our discord server: [white]https://discord.xpdustry.com
                        """.trimIndent(),
                    )
                }
                .switchIfEmpty { Verification.Success.toValueMono() }
        }
    }

    private fun formatDuration(duration: Duration?): String = when {
        duration == null -> "Permanent"
        duration >= Duration.ofDays(1L) -> "${duration.toDays()} days"
        duration >= Duration.ofHours(1L) -> "${duration.toHours()} hours"
        duration >= Duration.ofMinutes(1L) -> "${duration.toMinutes()} minutes"
        else -> "${duration.seconds} seconds"
    }
}
