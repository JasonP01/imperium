/*
 * Imperium, the software collection powering the Xpdustry network.
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
package com.xpdustry.imperium.common.database.mongo

import org.bson.codecs.pojo.ClassModelBuilder
import org.bson.codecs.pojo.Convention

object SnakeCaseConvention : Convention {
    override fun apply(classModelBuilder: ClassModelBuilder<*>) {
        classModelBuilder.propertyModelBuilders.forEach {
            // Using null safe operator to avoid NPEs with ignored properties
            it.readName(it.readName?.camelToSnakeCase())
            it.writeName(it.writeName?.camelToSnakeCase())
        }
    }
}

// https://www.baeldung.com/kotlin/convert-camel-case-snake-case
private fun String.camelToSnakeCase(): String {
    return fold(StringBuilder()) { builder, char ->
        builder.append(if (builder.isNotEmpty() && char.isUpperCase()) "_${char.lowercase()}" else char.lowercase())
    }.toString()
}