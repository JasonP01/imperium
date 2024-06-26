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
package com.xpdustry.imperium.common.translator

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.TranslatorConfig
import com.xpdustry.imperium.common.network.await
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request

class LibreTranslateTranslator(
    private val config: TranslatorConfig.LibreTranslate,
    private val http: OkHttpClient
) : Translator, ImperiumApplication.Listener {

    private lateinit var languages: List<SupportedLanguage>

    override fun onImperiumInit() {
        runBlocking { languages = fetchSupportedLanguages() }
    }

    override suspend fun translate(text: String, source: Locale, target: Locale): TranslatorResult {
        if (source.language == "router" || target.language == "router") {
            return TranslatorResult.Success("router")
        }
        if (text.isBlank() || source.language == target.language) {
            return TranslatorResult.Success(text)
        }

        val candidate = languages.firstOrNull { it.code == source.language }
        if (candidate == null) {
            return TranslatorResult.UnsupportedLanguage(source)
        } else if (target.language !in candidate.targets) {
            return TranslatorResult.UnsupportedLanguage(target)
        }

        return http
            .newCall(
                Request.Builder()
                    .url(
                        config.ltEndpoint
                            .toHttpUrlOrNull()!!
                            .newBuilder()
                            .addPathSegment("translate")
                            .build())
                    .post(
                        MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("q", text)
                            .addFormDataPart("source", source.language)
                            .addFormDataPart("target", target.language)
                            .addFormDataPart("api_key", config.ltToken.value)
                            .addFormDataPart("format", "text")
                            .build())
                    .build())
            .await()
            .use { response ->
                val json = Json.parseToJsonElement(response.body!!.string()).jsonObject
                if (response.code != 200) {
                    TranslatorResult.Failure(
                        Exception("Failed to translate: ${json["error"]} (code=${response.code})"))
                } else {
                    TranslatorResult.Success(json["translatedText"]!!.jsonPrimitive.content)
                }
            }
    }

    override fun isSupportedLanguage(locale: Locale) = languages.any { it.code == locale.language }

    private suspend fun fetchSupportedLanguages(): List<SupportedLanguage> =
        http
            .newCall(
                Request.Builder()
                    .url(
                        config.ltEndpoint
                            .toHttpUrlOrNull()!!
                            .newBuilder()
                            .addPathSegment("languages")
                            .build())
                    .header("Accept", "application/json")
                    .get()
                    .build())
            .await()
            .use { response ->
                if (response.code != 200) {
                    error(
                        "Failed to fetch supported languages: ${response.message} (code=${response.code})")
                }
                Json.decodeFromString<List<SupportedLanguage>>(response.body!!.string())
            }

    @Serializable
    data class SupportedLanguage(val code: String, val name: String, val targets: Set<String>)
}
