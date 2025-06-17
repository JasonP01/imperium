/* package com.xpdustry.imperium.mindustry.events

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Scope
import com.xpdustry.imperium.mindustry.command.vote.AbstractVoteCommand
import com.xpdustry.imperium.mindustry.command.vote.Vote
import com.xpdustry.imperium.mindustry.command.vote.VoteManager
import kotlin.time.Duration.Companion.seconds

class TrickOrTreat(instances: InstanceManager) :
    AbstractVoteCommand<Unit>(instances.get(), "trickortreat", 60.seconds), ImperiumApplication.Listener {

    @ImperiumCommand(["choose"])
    fun onChooseCommand(sender: CommandSender) {
        
    }

    override fun getVoteSessionDetails(session: VoteManager.Session<Unit>): String =
        "Type [orange]/choose[] to choose your vote."
}
*/