// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.geometry

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ClusterManagerTest {

    @Test
    fun `test blocks that share a side`() {
        val manager = createManager()
        manager.upsertElement(createBlock(0, 0, 1))
        manager.upsertElement(createBlock(1, 0, 1))
        Assertions.assertEquals(1, manager.clusters.size)

        val cluster = manager.clusters[0]
        Assertions.assertEquals(2, cluster.blocks.size)
        Assertions.assertEquals(0, cluster.x)
        Assertions.assertEquals(0, cluster.y)
        Assertions.assertEquals(2, cluster.w)
        Assertions.assertEquals(1, cluster.h)
    }

    @Test
    fun `test blocks that do not share a side`() {
        val manager = createManager()
        manager.upsertElement(createBlock(2, 2, 2))
        manager.upsertElement(createBlock(-2, 0, 1))
        manager.upsertElement(createBlock(10, 10, 10))
        Assertions.assertEquals(3, manager.clusters.size)
    }

    @Test
    fun `test blocks that partially share a side`() {
        val manager = createManager()
        manager.upsertElement(createBlock(1, 1, 2))
        manager.upsertElement(createBlock(3, 2, 2))
        Assertions.assertEquals(1, manager.clusters.size)
    }

    @Test
    fun `test blocks that only share a corner`() {
        val manager = createManager()
        manager.upsertElement(createBlock(0, 0, 1))
        manager.upsertElement(createBlock(1, 1, 1))
        Assertions.assertEquals(2, manager.clusters.size)
    }

    @Test
    fun `test simple remove 1`() {
        val manager = createManager()
        for (x in 0..4) {
            for (y in 0..4) {
                manager.upsertElement(createBlock(x, y, 1))
            }
        }

        Assertions.assertEquals(1, manager.clusters.size)
        Assertions.assertEquals(25, manager.clusters[0].blocks.size)

        // Removes a U shape inside the 5 by 5 square
        for (x in 1..3) {
            for (y in 1..3) {
                if (x == 1 && (y == 1 || y == 2)) continue
                manager.removeElement(x, y)
            }
        }

        Assertions.assertEquals(1, manager.clusters.size)
        Assertions.assertEquals(18, manager.clusters[0].blocks.size)
    }

    @Test
    fun `test simple remove 2`() {
        val manager = createManager()
        for (x in 0..2) {
            for (y in 0..5) {
                manager.upsertElement(createBlock(x, y, 1))
            }
        }

        Assertions.assertEquals(1, manager.clusters.size)
        Assertions.assertEquals(18, manager.clusters[0].blocks.size)

        manager.removeElement(0, 1)
        manager.removeElement(1, 1)

        Assertions.assertEquals(1, manager.clusters.size)
        Assertions.assertEquals(16, manager.clusters[0].blocks.size)
    }

    @Test
    fun `test remove split`() {
        val manager = createManager()
        for (x in 0..2) {
            manager.upsertElement(createBlock(x, 0, 1))
        }
        manager.upsertElement(createBlock(1, 1, 1))
        Assertions.assertEquals(1, manager.clusters.size)
        manager.removeElement(1, 0)
        Assertions.assertEquals(3, manager.clusters.size)
    }

    @Test
    fun `test simple merge`() {
        val manager = createManager()
        for (y in 0..2) {
            for (x in 0..2) {
                manager.upsertElement(createBlock(x, y * 2, 1))
            }
        }
        Assertions.assertEquals(3, manager.clusters.size)
        manager.upsertElement(createBlock(1, 1, 1))
        Assertions.assertEquals(2, manager.clusters.size)
        manager.upsertElement(createBlock(1, 3, 1))
        Assertions.assertEquals(1, manager.clusters.size)
    }

    @Test
    fun `test upsert replaces occupied block`() {
        val manager = ClusterManager<String> { _, _ -> }
        manager.upsertElement(Cluster.Block(0, 0, 1, "old"))
        manager.upsertElement(Cluster.Block(0, 0, 1, "new"))

        Assertions.assertEquals(1, manager.clusters.size)
        Assertions.assertEquals(1, manager.clusters[0].blocks.size)
        Assertions.assertEquals("new", manager.getElement(0, 0)?.second?.data)
    }

    private fun createManager() = ClusterManager<Unit> { _, _ -> }

    private fun createBlock(x: Int, y: Int, size: Int) = Cluster.Block(x, y, size, Unit)
}
