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
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.key.Key;
import com.xpdustry.distributor.api.player.MUUID;
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.mindustry.account.*
import com.xpdustry.imperium.common.lifecycle.LifecycleListener
import com.xpdustry.imperium.common.session.MindustrySession;
import com.xpdustry.imperium.common.session.MindustrySessionService;
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.sessionKey
import jakarta.inject.Inject
import mindustry.Vars
import mindustry.gen.Player
import mindustry.net.*

interface ClientDetector {
    fun isFooClient(player: Player): Boolean
}

class SimpleClientDetector @Inject constructor(plugin: MindustryPlugin, private val accounts: AccountManager, private val sessions: MindustrySessionService) : ClientDetector, LifecycleListener {

    private val fooClients = PlayerMap<Boolean>(plugin)

    override fun onImperiumInit() {
        Vars.netServer.addPacketHandler("fooCheck") { player, _ -> fooClients[player] = true }
        Vars.netServer.addPacketHandler("fooAutoLogin") { player, data -> 
            val json = JsonReader().parse(data)
            val username = json.getString("username")
            val password = json.getString("password").toCharArray()
            if (username.isEmpty() || password.isEmpty()) {
                player.sendMessage("[scarlet]Login failed: Missing username or password.")
                return@addPacketHandler
            }
            login(player, username, password)
        }
    }

    override fun isFooClient(player: Player) = fooClients[player] == true

    @EventHandler
    fun onPlayerJoin(event: PlayerJoin) {
        delay(2000) // ensure they sent the packet
        if (isFooClient(event.player)) {
            val exists = accounts.existsBySession(event.player.sessionKey)
            if (!exists) {
                event.player.sendMessage(
                    """
                    [scarlet]You are not logged in or do not have an account.
                    Your rank has been set to Rank.EVERYONE.length
                    Please login to update your rank.length
                    """.trimIndent()
                    )
            }
            val account = accounts.selectBySession(event.player.sessionKey)

            val playerdata = Vars.netServer.createPacket("playerdata")
            playerdata.put("id", event.player.id)
            playerdata.put("rank", account?.rank?.ordinal ?: 0)

            Call.serverPacketReliable("playerdata", playerdata, event.player.con)
        }
    }

    private fun login(player: Player, username: String, password: CharArray) {
        val result = this.sessions.login(
            key(player),
            username,
            password)
        when (result) {
            is AccountResult.Success -> {
                player.asAudience.sendAnnouncement(gui_login_success())
                Distributor.get().eventBus.post(PlayerLoginEvent(player))
            },
            AccountResult.NotFound -> {
                player.asAudience.sendAnnouncement(gui_login_failure_invalid_credentials())
            }
        }
    }

    private fun key(val player: Player): MindustrySession.Key {
        val address = InetAddress.getByName(player.ip());
        val muuid = MUUID.from(player);
        return MindustrySession.Key(muuid.uuidAsLong, muuid.usidAsLong, address)
    }
}

when (result) {
            is AccountResult.Success -> {
                window.viewer.asAudience.sendAnnouncement(gui_login_success())
                Distributor.get().eventBus.post(PlayerLoginEvent(window.viewer))
            }
            AccountResult.WrongPassword,
            AccountResult.NotFound -> {
                window.show()
                window.viewer.asAudience.sendAnnouncement(gui_login_failure_invalid_credentials())
            }
            else -> handleAccountResult(result, window)
        }