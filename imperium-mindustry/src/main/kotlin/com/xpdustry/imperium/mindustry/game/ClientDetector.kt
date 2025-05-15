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

import arc.util.serialization.*
import com.google.gson.Gson
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.key.Key;
import com.xpdustry.distributor.api.player.MUUID;
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.*
import com.xpdustry.imperium.mindustry.account.*
import com.xpdustry.imperium.common.lifecycle.LifecycleListener
import com.xpdustry.imperium.common.session.MindustrySession;
import com.xpdustry.imperium.common.session.MindustrySessionService;
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.sessionKey
import jakarta.inject.Inject
import java.net.InetAddress
import kotlinx.coroutines.delay
import mindustry.Vars
import mindustry.core.NetServer
import mindustry.game.EventType
import mindustry.gen.*
import mindustry.net.*

interface ClientDetector {
    fun isFooClient(player: Player): Boolean
}

class SimpleClientDetector @Inject constructor(plugin: MindustryPlugin, private val accounts: AccountManager, private val sessions: MindustrySessionService) : ClientDetector, LifecycleListener {

    private val fooClients = PlayerMap<Boolean>(plugin)

    override fun onImperiumInit() {
        Vars.netServer.addPacketHandler("fooCheck") { player, _ -> fooClients[player] = true }
        println("Registered fooAutoLogin and fooCheck")
        Vars.netServer.addPacketHandler("fooAutoLogin") { player, data -> 
            val json = JsonReader().parse(data)
            val username = json.getString("username")
            val password = json.getString("password").toCharArray()
            if (username.isEmpty() || password.isEmpty()) {
                // TODO: translations
                player.sendMessage("[scarlet]Login failed: Missing username or password.")
                return@addPacketHandler
            }
            login(player, username, password)
        }
    }

    override fun isFooClient(player: Player) = fooClients[player] == true

    @EventHandler
    suspend private fun onPlayerJoin(event: EventType.PlayerJoin) {
        println("onPlayerJoin fired")
        delay(2000) // ensure they sent the packet
        // FINISHME: this should wait for a login attempt before sending the packet
        if (isFooClient(event.player)) {
            println("Is foos client")
            val exists = accounts.existsBySession(event.player.sessionKey)
            println("Account exists: $exists")
            if (!exists) {
                event.player.sendMessage(
                    // TODO: translations
                    """
                    [scarlet]You are not logged in or do not have an account.
                    Your rank has been set to everyone.
                    Please login to update your rank.
                    """.trimIndent()
                    )
            }
            val account = accounts.selectBySession(event.player.sessionKey)

            val playerdata = Gson().toJson(mapOf(
                "id" to event.player.id,
                "rank" to (account?.rank?.ordinal ?: 0)
            ))
            println("Sending playerdata: $playerdata to ${event.player.name}")
            mindustry.core.NetServer.serverPacketReliable(event.player, "playerdata", playerdata)
        }
    }

    private fun login(player: Player, username: String, password: CharArray) {
        val result = sessions.login(
            key(player),
            username,
            password)
        when (result) {
            is AccountResult.Success -> {
                player.asAudience.sendAnnouncement(gui_login_success())
                Distributor.get().eventBus.post(PlayerLoginEvent(player))
            }
            is AccountResult.NotFound -> {
                player.asAudience.sendAnnouncement(gui_login_failure_invalid_credentials())
            }
            else -> {
                // This shouldnt happen
                return
            }
        }
    }

    private fun key(player: Player): MindustrySession.Key {
        val address = InetAddress.getByName(player.ip());
        val muuid = MUUID.from(player);
        return MindustrySession.Key(muuid.uuidAsLong, muuid.usidAsLong, address)
    }
}