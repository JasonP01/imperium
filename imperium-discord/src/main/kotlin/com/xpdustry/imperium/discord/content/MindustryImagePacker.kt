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
package com.xpdustry.imperium.discord.content

import arc.Core
import arc.files.Fi
import arc.graphics.Pixmap
import arc.graphics.g2d.Draw
import arc.graphics.g2d.PixmapRegion
import arc.graphics.g2d.TextureAtlas
import arc.graphics.g2d.TextureAtlas.AtlasRegion
import arc.graphics.g2d.TextureRegion
import arc.struct.Seq
import com.xpdustry.imperium.common.misc.logger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.time.measureTime
import mindustry.Vars
import mindustry.game.Team
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.environment.OreBlock
import mindustry.world.blocks.legacy.LegacyBlock

// TODO
//  - Move to MindustryContentHandler
//  - Add proper logging
class MindustryImagePacker(private val directory: Path) {
    private val cache = mutableMapOf<String, PackIndex>()

    fun pack() {
        Files.walk(directory.resolve("raw-sprites"))
            .filter { it.extension == "png" }
            .forEach { cache[it.nameWithoutExtension] = PackIndex(Fi(it.toFile())) }

        Core.atlas =
            object : TextureAtlas() {
                override fun find(name: String): AtlasRegion {
                    if (!cache.containsKey(name)) {
                        val region = GenRegion(name)
                        region.invalid = true
                        return region
                    }

                    val index = cache[name]!!
                    if (index.pixmap == null) {
                        index.pixmap = Pixmap(index.file)
                        index.region =
                            object : GenRegion(name) {
                                init {
                                    width = index.pixmap!!.width
                                    height = index.pixmap!!.height
                                    v2 = 1f
                                    u2 = v2
                                    v = 0f
                                    u = v
                                }
                            }
                    }
                    return index.region!!
                }

                override fun find(name: String, def: TextureRegion): AtlasRegion {
                    return if (!cache.containsKey(name)) {
                        def as AtlasRegion
                    } else {
                        find(name)
                    }
                }

                override fun find(name: String, def: String): AtlasRegion {
                    return if (!cache.containsKey(name)) {
                        find(def)
                    } else {
                        find(name)
                    }
                }

                override fun getPixmap(region: AtlasRegion): PixmapRegion {
                    return PixmapRegion(get(region.name))
                }

                override fun has(s: String): Boolean {
                    return cache.containsKey(s)
                }
            }

        Draw.scl = 1f / Core.atlas.find("scale_marker").width
        Vars.content.load()

        val time = measureTime { generateBlockIcons() }

        logger.info("Time to generate block images: {}ms", time.inWholeMilliseconds)
    }

    private fun generateBlockIcons() {
        logger.info("Generating full block images...")
        for (block in Vars.content.blocks()) {
            if (block.isAir || block is ConstructBlock || block is OreBlock || block is LegacyBlock) {
                continue
            }

            val toOutline = Seq<TextureRegion>()
            block.getRegionsToOutline(toOutline)
            val regions = block.generatedIcons

            var shardTeamTop: Pixmap? = null
            if (block.teamRegion.found()) {
                val teamRegion = get(block.teamRegion)
                if (Team.sharded.hasPalette) {
                    val out = Pixmap(teamRegion.width, teamRegion.height)
                    teamRegion.each { x, y ->
                        val color = teamRegion.getRaw(x, y)
                        val index =
                            if (color == -0x1) 0
                            else if (color == -0x23393901) 1 else if (color == -0x62808001) 2 else -1
                        out.setRaw(x, y, if (index == -1) teamRegion.getRaw(x, y) else Team.sharded.palettei[index])
                    }
                    shardTeamTop = out
                }
            }

            if (regions.isEmpty()) {
                continue
            }

            var last: Pixmap? = null
            if (block.outlineIcon) {
                val region = regions[if (block.outlinedIcon >= 0) block.outlinedIcon else regions.size - 1] as GenRegion
                val base = get(region)
                last = base.outline(block.outlineColor, block.outlineRadius)
                val out = last

                // do not run for legacy ones
                if (block.outlinedIcon >= 0) {
                    // prevents the regions above from being ignored/invisible/etc
                    for (i in block.outlinedIcon + 1 until regions.size) {
                        out!!.draw(get(regions[i]), true)
                    }
                }

                // 1 pixel of padding to prevent edges with linear filtering
                val padding = 1
                var padded = Pixmap(base.width + padding * 2, base.height + padding * 2)
                padded.draw(base, padding, padding)
                padded = padded.outline(block.outlineColor, block.outlineRadius)
                save(padded, region.name)
            }

            if (!regions[0].found()) {
                continue
            }

            val image = get(regions[0])
            var i = 0
            for (region in regions) {
                i++
                if (i != regions.size || last == null) {
                    image.draw(get(region), true)
                } else {
                    image.draw(last, true)
                }

                // draw shard (default team top) on top of first sprite
                if (region === block.teamRegions[Team.sharded.id] && shardTeamTop != null) {
                    image.draw(shardTeamTop, true)
                }
            }

            if (!(regions.size == 1 && regions[0] === Core.atlas.find(block.name) && shardTeamTop == null)) {
                save(image, "block-" + block.name + "-full")
            }
        }
    }

    private fun save(pix: Pixmap, path: String) {
        Fi(directory.resolve("generated/$path.png").toFile()).writePng(pix)
    }

    private fun get(name: String): Pixmap {
        return get(Core.atlas.find(name))
    }

    private fun get(region: TextureRegion): Pixmap {
        if ((region as GenRegion).invalid) {
            throw IllegalArgumentException("Region does not exist: ${region.name}")
        }
        return cache[(region as AtlasRegion).name]!!.pixmap!!.copy()
    }

    private open class GenRegion(name: String) : AtlasRegion() {
        var invalid = false

        init {
            this.name = name
        }

        override fun found() = !invalid
    }

    private class PackIndex(var file: Fi) {
        var region: AtlasRegion? = null
        var pixmap: Pixmap? = null
    }

    companion object {
        private val logger = logger<MindustryImagePacker>()
    }
}
