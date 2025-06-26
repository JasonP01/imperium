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
package com.xpdustry.imperium.mindustry.formation

import arc.math.Angles
import arc.math.Mathf
import arc.math.geom.Vec2
<<<<<<< HEAD
=======
import arc.util.Time
>>>>>>> origin/master
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt

interface FormationPattern {

<<<<<<< HEAD
    fun calculate(location: Vec2, slot: Int, slots: Int, spacing: Float = 20F, speed: Float = 0.5F)
}

object CircleFormationPattern : FormationPattern {
    override fun calculate(location: Vec2, slot: Int, slots: Int, spacing: Float, speed: Float) {
        if (slots > 1) {
            val angle = (360f * slot) / slots
            val radius = spacing / sin((180f / slots * Mathf.degRad).toDouble()).toFloat()
            location.set(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius))
        } else {
            location.set(0f, spacing * 1.1f)
=======
    fun calculate(context: FormationContext, target: Vec2, slot: Int)
}

object CircleFormationPattern : FormationPattern {
    override fun calculate(context: FormationContext, target: Vec2, slot: Int) {
        if (context.slots > 1) {
            val angle = (360f * slot) / context.slots
            val radius = context.spacing / sin((180f / context.slots * Mathf.degRad).toDouble()).toFloat()
            target.set(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius))
        } else {
            target.set(0f, context.spacing * 1.1f)
>>>>>>> origin/master
        }
    }
}

object SquareFormationPattern : FormationPattern {
<<<<<<< HEAD
    override fun calculate(location: Vec2, slot: Int, slots: Int, spacing: Float, speed: Float) {
        // side of each square of formation
        val side = ceil(sqrt((slots + 1).toFloat())).toInt()
=======
    override fun calculate(context: FormationContext, target: Vec2, slot: Int) {
        // side of each square of formation
        val side = ceil(sqrt((context.slots + 1).toFloat())).toInt()
>>>>>>> origin/master
        var cx = slot % side
        var cy = slot / side

        // don't hog the middle spot
        if ((cx == side / 2) && cy == side / 2 && (side % 2) == 1) {
<<<<<<< HEAD
            cx = slots % side
            cy = slots / side
        }

        location.set(cx - (side / 2f - 0.5f), cy - (side / 2f - 0.5f)).scl(spacing * 2F)
=======
            cx = context.slots % side
            cy = context.slots / side
        }

        target.set(cx - (side / 2f - 0.5f), cy - (side / 2f - 0.5f)).scl(context.spacing * 2F)
>>>>>>> origin/master
    }
}

object RotatingCircleFormationPattern : FormationPattern {
<<<<<<< HEAD
    override fun calculate(location: Vec2, slot: Int, slots: Int, spacing: Float, speed: Float) {
        val cycle = 30000L / speed
        val offset = ((System.currentTimeMillis() % cycle.toLong()) / cycle) * 360f
        val angle = ((360f * slot) / slots) + offset
        val radius = spacing / sin((180f / slots * Mathf.degRad).toDouble()).toFloat()
        location.set(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius))
=======
    override fun calculate(context: FormationContext, target: Vec2, slot: Int) {
        val cycle = (30 * Time.toSeconds) / context.leader.speed()
        val offset = ((context.progress.toLong() % cycle.toLong()) / cycle) * 360f
        val angle = ((360f * slot) / context.slots) + offset
        val radius = context.spacing / sin((180f / context.slots * Mathf.degRad).toDouble()).toFloat()
        target.set(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius))
>>>>>>> origin/master
    }
}
