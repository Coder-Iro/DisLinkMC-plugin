package xyz.irodev.dislinkmc.listener

import com.github.benmanes.caffeine.cache.Cache
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.ResultedEvent.ComponentResult.allowed
import com.velocitypowered.api.event.ResultedEvent.ComponentResult.denied
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import net.kyori.adventure.text.Component
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import xyz.irodev.dislinkmc.Config
import xyz.irodev.dislinkmc.VerifyBot
import xyz.irodev.dislinkmc.VerifyCodeSet
import java.text.MessageFormat
import kotlin.jvm.optionals.getOrNull

internal class CodeGenerater(
    private val logger: Logger,
    private val database: Database,
    message: Config.MessageList,
    private val prefix: String,
    private val codeStore: Cache<String, VerifyCodeSet>,
    private val verifyHost: String,
) {
    private val onSuccess: MessageFormat = MessageFormat(message.onSuccess)
    private val onFail: MessageFormat = MessageFormat(message.onFail)
    private val onAlready: MessageFormat = MessageFormat(message.onAlready)

    @Subscribe(order = PostOrder.FIRST)
    fun LoginEvent.onLogin() {
        player.run {
            val userhost = virtualHost.getOrNull()?.hostString
            logger.info("Hostname: $userhost")
            when {
                userhost != verifyHost -> {
                    result = allowed()
                }

                !transaction(database) {
                    VerifyBot.Account.find { VerifyBot.LinkedAccounts.mcuuid eq uniqueId }.empty()
                } -> {
                    result = denied(
                        Component.text(
                            "$prefix${onAlready.format(arrayOf(username, uniqueId))}"
                        )
                    )
                }

                else -> {
                    try {
                        var codeset: VerifyCodeSet? = codeStore.getIfPresent(username.lowercase())
                        if (codeset == null) {
                            codeset = VerifyCodeSet(username, uniqueId, (0..999999).random())
                            codeStore.put(username.lowercase(), codeset)
                        }
                        logger.info(codeset.toString())
                        val code = String.format("%03d %03d", codeset.code / 1000, codeset.code % 1000)
                        result = denied(
                            Component.text(
                                "$prefix${onSuccess.format(arrayOf(username, uniqueId, code))}"
                            )
                        )
                    } catch (e: Exception) {
                        result = denied(
                            Component.text(
                                "$prefix${
                                    onFail.format(
                                        arrayOf<String>(
                                            username, uniqueId.toString()
                                        )
                                    )
                                }"
                            )
                        )
                        logger.error("", e)
                    }
                }
            }
        }
    }
}