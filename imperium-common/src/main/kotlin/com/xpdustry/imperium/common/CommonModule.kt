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
package com.xpdustry.imperium.common

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.MongoAccountManager
import com.xpdustry.imperium.common.account.MongoUserManager
import com.xpdustry.imperium.common.account.UserManager
import com.xpdustry.imperium.common.application.ImperiumMetadata
import com.xpdustry.imperium.common.config.ImperiumConfigFactory
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.content.MongoMindustryMapManager
import com.xpdustry.imperium.common.database.MongoProvider
import com.xpdustry.imperium.common.database.SimpleMongoProvider
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.module
import com.xpdustry.imperium.common.inject.single
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.RabbitmqMessenger
import com.xpdustry.imperium.common.network.CoroutineHttpClient
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.network.IpHubVpnAddressDetector
import com.xpdustry.imperium.common.network.SimpleCoroutineHttpClient
import com.xpdustry.imperium.common.network.SimpleDiscovery
import com.xpdustry.imperium.common.network.VpnAddressDetector
import com.xpdustry.imperium.common.security.MongoPunishmentManager
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.storage.MinioStorage
import com.xpdustry.imperium.common.storage.Storage
import com.xpdustry.imperium.common.translator.DeeplTranslator
import com.xpdustry.imperium.common.translator.Translator

fun commonModule() = module("common") {
    single(ImperiumConfigFactory())

    single<Translator> {
        DeeplTranslator(get(), get())
    }

    single<Discovery> {
        SimpleDiscovery(get(), get(), get(), get())
    }

    single<VpnAddressDetector> {
        IpHubVpnAddressDetector(get(), get())
    }

    single<Messenger> {
        RabbitmqMessenger(get(), get())
    }

    single<Storage> {
        MinioStorage(get())
    }

    single<CoroutineHttpClient> {
        SimpleCoroutineHttpClient(get("scheduler"))
    }

    single {
        ImperiumMetadata()
    }

    single<MongoProvider> {
        SimpleMongoProvider(get())
    }

    single<AccountManager> {
        MongoAccountManager(get())
    }

    single<MindustryMapManager> {
        MongoMindustryMapManager(get(), get())
    }

    single<PunishmentManager> {
        MongoPunishmentManager(get())
    }

    single<UserManager> {
        MongoUserManager(get())
    }
}
