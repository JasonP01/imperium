package com.xpdustry.imperium.mindustry.events.powers

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import mindustry.game.EventType

class PlayerPowers : ImperiumApplication.Listener {
    val powers = mutableMapOf<MindustryUUID, String>()
    val powerList = listOf(
        "power1",
        "power2",
        "power3"
    )

    @ImperiumCommand(["vote"])
    @ClientSide
    fun onVoteCommand(sender: CommandSender, votee: String) {
        if (powers[sender.player.uuid()] != null) return sender.reply("There is currently no vote for you.")
        val vote = votee.lowercase()
        if (vote in powerList) powers[sender.player.uuid()] = vote
        else sender.reply("$vote is not a valid selection.")
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) {
        event.player.sendMessage("Pick a power cuh")
    }
}

// This was intended as a christmas event, doubt thats happening now