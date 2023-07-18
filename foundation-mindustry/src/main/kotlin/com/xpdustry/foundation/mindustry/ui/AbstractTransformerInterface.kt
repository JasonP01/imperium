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
package com.xpdustry.foundation.mindustry.ui

import com.xpdustry.foundation.mindustry.ui.state.State
import com.xpdustry.foundation.mindustry.ui.state.stateOf
import com.xpdustry.foundation.mindustry.ui.transform.PriorityTransformer
import com.xpdustry.foundation.mindustry.ui.transform.Transformer
import fr.xpdustry.distributor.api.DistributorProvider
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import fr.xpdustry.distributor.api.util.MUUID
import fr.xpdustry.distributor.api.util.Priority
import mindustry.game.EventType.PlayerLeave
import mindustry.gen.Player
import java.util.function.Supplier

abstract class AbstractTransformerInterface<P : Pane> protected constructor(
    protected val plugin: MindustryPlugin,
    private val paneProvider: Supplier<P>,
) : TransformerInterface<P> {

    private val _views: MutableMap<MUUID, SimpleView> = mutableMapOf()
    protected val views: Map<MUUID, SimpleView> get() = _views
    override val transformers: MutableList<PriorityTransformer<P>> = mutableListOf()

    init {
        DistributorProvider.get().eventBus.subscribe(PlayerLeave::class.java, plugin) { event ->
            views[MUUID.of(event.player)]?.close()
        }
    }

    override fun create(parent: View): View {
        return SimpleView(parent.viewer, parent)
    }

    override fun create(viewer: Player): View {
        return SimpleView(viewer, null)
    }

    override fun addTransformer(priority: Priority, transformer: Transformer<P>) {
        transformers.add(PriorityTransformer(transformer, priority))
    }

    protected abstract fun onViewOpen(view: SimpleView)
    protected open fun onViewClose(view: SimpleView) = Unit

    protected inner class SimpleView(override val viewer: Player, override val parent: View?) : View {
        override val state: State = stateOf()

        lateinit var pane: P
            private set

        override fun open() {
            val previous = _views[MUUID.of(viewer)]
            if (previous != this) {
                _views[MUUID.of(viewer)] = this
                previous?.close()
            }
            pane = paneProvider.get()
            for (transform in transformers) {
                transform.transform(this, pane)
            }
            onViewOpen(this)
        }

        override fun close() {
            if (_views.remove(MUUID.of(viewer), this)) {
                onViewClose(this)
            }
        }

        override val isOpen: Boolean get() = views.containsKey(MUUID.of(viewer))
        override val owner: Interface get() = this@AbstractTransformerInterface
    }
}
