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
package com.xpdustry.imperium.common.user

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.misc.buildAsyncCache
import com.xpdustry.imperium.common.misc.getSuspending
import com.xpdustry.imperium.common.misc.toCRC32Muuid
import com.xpdustry.imperium.common.misc.toShortMuuid
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.snowflake.SnowflakeGenerator
import java.net.InetAddress
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert

interface UserManager {

    suspend fun findBySnowflake(snowflake: Long): User?

    suspend fun findByUuid(uuid: MindustryUUID): User?

    suspend fun findByLastAddress(address: InetAddress): Flow<User>

    suspend fun findNamesAndAddressesBySnowflake(snowflake: Long): User.NamesAndAddresses

    suspend fun searchUserByName(query: String): Flow<User>

    suspend fun incrementJoins(identity: Identity.Mindustry)

    suspend fun getSetting(uuid: MindustryUUID, setting: User.Setting): Boolean

    suspend fun getSettings(uuid: MindustryUUID): Map<User.Setting, Boolean>

    suspend fun setSetting(identity: Identity.Mindustry, setting: User.Setting, value: Boolean)

    suspend fun setSettings(identity: Identity.Mindustry, settings: Map<User.Setting, Boolean>)
}

class SimpleUserManager(
    private val provider: SQLProvider,
    private val generator: SnowflakeGenerator
) : UserManager, ImperiumApplication.Listener {
    private val settingsCache =
        buildAsyncCache<MindustryUUID, Map<User.Setting, Boolean>>(
            expireAfterAccess = 5.minutes, maximumSize = 1000)

    override fun onImperiumInit() {
        provider.newTransaction {
            SchemaUtils.create(UserTable, UserNameTable, UserAddressTable, UserSettingTable)
        }
    }

    override suspend fun findBySnowflake(snowflake: Long): User? =
        provider.newSuspendTransaction {
            UserTable.select { UserTable.id eq snowflake }.firstOrNull()?.toUser()
        }

    override suspend fun findByUuid(uuid: MindustryUUID): User? =
        provider.newSuspendTransaction {
            UserTable.select { UserTable.uuid eq uuid.toShortMuuid() }.firstOrNull()?.toUser()
        }

    override suspend fun findByLastAddress(address: InetAddress): Flow<User> =
        provider.newSuspendTransaction {
            UserTable.select { UserTable.lastAddress eq address.address }
                .asFlow()
                .map { it.toUser() }
        }

    override suspend fun findNamesAndAddressesBySnowflake(snowflake: Long): User.NamesAndAddresses =
        provider.newSuspendTransaction {
            val names =
                UserNameTable.select { UserNameTable.user eq snowflake }
                    .mapTo(mutableSetOf()) { it[UserNameTable.name] }
            val addresses =
                UserAddressTable.select { UserAddressTable.user eq snowflake }
                    .mapTo(mutableSetOf()) {
                        InetAddress.getByAddress(it[UserAddressTable.address])
                    }
            User.NamesAndAddresses(names, addresses)
        }

    override suspend fun searchUserByName(query: String): Flow<User> =
        provider.newSuspendTransaction {
            (UserTable leftJoin UserNameTable)
                .select { UserNameTable.name like "%$query%" }
                .asFlow()
                .map { it.toUser() }
        }

    override suspend fun incrementJoins(identity: Identity.Mindustry): Unit =
        provider.newSuspendTransaction {
            val snowflake = ensureUserExists(identity)

            UserTable.update({ UserTable.uuid eq identity.uuid.toShortMuuid() }) {
                it[lastName] = identity.name
                it[lastAddress] = identity.address.address
                it[timesJoined] = timesJoined.plus(1)
                it[lastJoin] = Instant.now()
            }

            UserNameTable.insertIgnore {
                it[user] = snowflake
                it[name] = identity.name
            }

            UserAddressTable.insertIgnore {
                it[user] = snowflake
                it[address] = identity.address.address
            }
        }

    override suspend fun getSetting(uuid: MindustryUUID, setting: User.Setting): Boolean =
        getSettings0(uuid)[setting] ?: setting.default

    override suspend fun getSettings(uuid: MindustryUUID): Map<User.Setting, Boolean> =
        getSettings0(uuid)

    private suspend fun getSettings0(uuid: MindustryUUID): Map<User.Setting, Boolean> =
        settingsCache.getSuspending(uuid) {
            provider.newSuspendTransaction {
                (UserSettingTable leftJoin UserTable)
                    .slice(UserSettingTable.setting, UserSettingTable.value)
                    .select { UserTable.uuid eq uuid.toShortMuuid() }
                    .associate { it[UserSettingTable.setting] to it[UserSettingTable.value] }
            }
        }

    override suspend fun setSetting(
        identity: Identity.Mindustry,
        setting: User.Setting,
        value: Boolean
    ): Unit =
        provider.newSuspendTransaction {
            val snowflake = ensureUserExists(identity)
            UserSettingTable.upsert {
                it[user] = snowflake
                it[UserSettingTable.setting] = setting
                it[UserSettingTable.value] = value
            }
            settingsCache.synchronous().invalidate(identity.uuid)
        }

    override suspend fun setSettings(
        identity: Identity.Mindustry,
        settings: Map<User.Setting, Boolean>
    ): Unit =
        provider.newSuspendTransaction {
            val snowflake = ensureUserExists(identity)
            UserSettingTable.batchUpsert(settings.entries) { (setting, value) ->
                this[UserSettingTable.user] = snowflake
                this[UserSettingTable.setting] = setting
                this[UserSettingTable.value] = value
            }
            settingsCache.synchronous().invalidate(identity.uuid)
        }

    private fun ensureUserExists(identity: Identity.Mindustry): Snowflake {
        var snowflake =
            UserTable.slice(UserTable.id)
                .select { UserTable.uuid eq identity.uuid.toShortMuuid() }
                .firstOrNull()
                ?.get(UserTable.id)
                ?.value
        if (snowflake != null) {
            return snowflake
        }

        snowflake = generator.generate()

        UserTable.insert {
            it[id] = snowflake
            it[uuid] = identity.uuid.toShortMuuid()
            it[lastName] = identity.name
            it[lastAddress] = identity.address.address
        }

        UserNameTable.insert {
            it[user] = snowflake
            it[name] = identity.name
        }

        UserAddressTable.insert {
            it[user] = snowflake
            it[address] = identity.address.address
        }

        return snowflake
    }

    private fun ResultRow.toUser() =
        User(
            snowflake = this[UserTable.id].value,
            uuid = this[UserTable.uuid].toCRC32Muuid(),
            lastName = this[UserTable.lastName],
            lastAddress = InetAddress.getByAddress(this[UserTable.lastAddress]),
            timesJoined = this[UserTable.timesJoined],
            lastJoin = this[UserTable.lastJoin])
}