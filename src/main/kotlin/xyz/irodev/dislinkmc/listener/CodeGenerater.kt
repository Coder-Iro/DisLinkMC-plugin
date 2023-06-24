package xyz.irodev.dislinkmc.listener

import com.github.benmanes.caffeine.cache.Cache
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

internal open class CodeGenerater(
    private val logger: Logger,
    private val database: Database,
    message: Config.MessageList,
    private val codeStore: Cache<String, VerifyCodeSet>
) {
    internal val prefix = message.prefix.takeIf { it.isNotEmpty() }?.let { "$it\n\n" } ?: ""

    private val onSuccess: MessageFormat = MessageFormat(message.onSuccess)

    private val onFail: MessageFormat = MessageFormat(message.onFail)

    private val onAlready: MessageFormat = MessageFormat(message.onAlready)

    @Subscribe
    open fun onLogin(event: LoginEvent) {
        event.player.run {
            if (!transaction(database) {
                    VerifyBot.Account.find { VerifyBot.LinkedAccounts.mcuuid eq uniqueId }.empty()
                }) {
                disconnect(
                    Component.text(
                        "$prefix${
                            onAlready.format(
                                arrayOf<String>(
                                    username, uniqueId.toString()
                                )
                            )
                        }"
                    )
                )
            } else {
                try {
                    var codeset: VerifyCodeSet? = codeStore.getIfPresent(username.lowercase())
                    if (codeset == null) {
                        codeset = VerifyCodeSet(username, uniqueId, (0..999999).random())
                        codeStore.put(username.lowercase(), codeset)
                    }
                    logger.info(codeset.toString())
                    disconnect(
                        Component.text(
                            prefix + onSuccess.format(
                                arrayOf<String>(
                                    username,
                                    uniqueId.toString(),
                                    String.format("%03d %03d", codeset.code / 1000, codeset.code % 1000)
                                )
                            )
                        )
                    )

                } catch (e: Exception) {
                    disconnect(
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