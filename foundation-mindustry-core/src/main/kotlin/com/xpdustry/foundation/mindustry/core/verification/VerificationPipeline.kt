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
package com.xpdustry.foundation.mindustry.core.verification

import com.xpdustry.foundation.common.misc.LoggerDelegate
import com.xpdustry.foundation.common.misc.toValueFlux
import com.xpdustry.foundation.common.misc.toValueMono
import com.xpdustry.foundation.mindustry.core.processing.AbstractProcessorPipeline
import com.xpdustry.foundation.mindustry.core.processing.ProcessorPipeline
import reactor.core.publisher.Mono
import java.net.InetAddress
import java.time.Duration

data class VerificationContext(
    val name: String,
    val uuid: String,
    val usid: String,
    val address: InetAddress,
)

sealed interface VerificationResult {
    object Success : VerificationResult
    data class Failure(val reason: String, val time: Duration = Duration.ZERO) : VerificationResult
}

interface VerificationPipeline : ProcessorPipeline<VerificationContext, VerificationResult>

class SimpleVerificationPipeline : VerificationPipeline, AbstractProcessorPipeline<VerificationContext, VerificationResult>() {
    override fun build(context: VerificationContext): Mono<VerificationResult> =
        processors.toValueFlux()
            .flatMapSequential {
                it.process(context).onErrorResume { error ->
                    logger.error("Error while verifying player ${context.name}", error)
                    VerificationResult.Success.toValueMono()
                }
            }
            .takeUntil { it is VerificationResult.Failure }
            .last(VerificationResult.Success)

    companion object {
        private val logger by LoggerDelegate()
    }
}