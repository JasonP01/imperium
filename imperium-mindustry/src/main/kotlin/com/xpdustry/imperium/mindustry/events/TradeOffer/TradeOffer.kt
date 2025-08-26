package com.xpdustry.imperium.mindustry.events

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.gui.menu.MenuGrid
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.gui.menu.MenuOption
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.events.TradeOffers.choicesChoices
import com.xpdustry.imperium.mindustry.game.MenuToPlayEvent
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.component2
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Player
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TradeOffer(val instances: InstanceManager): ImperiumApplication.Listener {
    private var job: Job? = null
    private var active: Boolean = false
    private val votes: Int = 0
    private val activeMenu: MutableList<Player> = mutableListOf()

    @EventHandler
    fun onGameStart(event: MenuToPlayEvent) {
        job = ImperiumScope.MAIN.launch {
            delay(5.minutes)
            while (isActive) {
                // start the function
                delay(5.minutes)
            }
        }
    }

    @EventHandler
    fun onGameOver(event: EventType.GameOverEvent) {
        job?.cancel()
        job = null
    }

    suspend fun choose() {
        Distributor.get().audienceProvider.everyone.sendMessage(translatable("imperium.tradeOffer.choicesSoon"))
        delay(10.seconds)
        displayMenu()
    }

    suspend fun displayMenu() {
        val menu = MenuManager.create(instances.get()).apply {
            addTransformer { (pane, state) ->
                pane.title = translatable("imperium.tradeOffer.title")

                pane.grid.addRow(MenuOption.of(), MenuOption.of(), MenuOption.of())
                pane.grid.addRow(MenuOption.of(), MenuOption.of(), MenuOption.of())

            }
        }

        runMindustryThread {
            for (player in Entities.getPlayers()) {
                if (player !in activeMenu && active) {
                    if (menu.getActiveWindow(player) == null) {
                        menu.create(player).show()
                        activeMenu.add(player)
                    }
                } else {
                    menu.getActiveWindow(player)?.hide()
                }
            }
        }


    }
}

data class TradeChoice(
    val name: String,
    val reward: () -> Unit,
)

object TradeOffers{
    fun choicesChoices(): List<Pair<TradeChoice, TradeChoice>> {
        val goods = goodOffer.shuffled().take(3)
        return goods.map { good -> good to badOffer.random() }
    }

    val badOffer = listOf(
        TradeChoice("imperium.tradeOffer.bad1", ) { println("You took the bad offer 1") },
        TradeChoice("Bad Offer 2") { println("You took the bad offer 2") },
    )
    val goodOffer = listOf(
        TradeChoice("Good Offer 1", ) { println("You took the good offer 1") },
        TradeChoice("Good Offer 2", ) { println("You took the good offer 2") },
    )
}