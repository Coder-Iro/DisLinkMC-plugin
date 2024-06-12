package xyz.irodev.dislinkmc.listeners

import com.github.benmanes.caffeine.cache.Cache
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.ResultedEvent.ComponentResult.allowed
import com.velocitypowered.api.event.ResultedEvent.ComponentResult.denied
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import xyz.irodev.dislinkmc.utils.Account
import xyz.irodev.dislinkmc.utils.Config
import xyz.irodev.dislinkmc.utils.LinkedAccounts
import xyz.irodev.dislinkmc.utils.VerifyCodeSet
import kotlin.jvm.optionals.getOrNull

internal class OTPIssuer(
    private val logger: Logger,
    private val database: Database,
    private val config: Config,
    private val codeStore: Cache<String, VerifyCodeSet>,
) : MessageSender(config.message.prefix) {
    @Subscribe(order = PostOrder.FIRST)
    fun LoginEvent.onLogin() {
        player.run {
            val userhost = virtualHost.getOrNull()?.hostString
            logger.info("Hostname: $userhost")
            result = when {
                userhost != config.general.verifyHost -> {
                    allowed()
                }

                !transaction(database) {
                    Account.find { LinkedAccounts.mcuuid eq uniqueId }.empty()
                } -> {
                    denied(
                        message(
                            MiniMessage.miniMessage().deserialize(
                                config.message.onAlready,
                                Placeholder.unparsed("name", username),
                                Placeholder.unparsed("uuid", uniqueId.toString())
                            )
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
                        denied(
                            message(
                                MiniMessage.miniMessage().deserialize(
                                    config.message.onSuccess,
                                    Placeholder.unparsed("name", username),
                                    Placeholder.unparsed("uuid", uniqueId.toString()),
                                    Placeholder.unparsed("code", code)
                                )
                            )
                        )
                    } catch (e: Exception) {
                        logger.error("", e)
                        denied(
                            message(
                                MiniMessage.miniMessage().deserialize(
                                    config.message.onFail,
                                    Placeholder.unparsed("name", username),
                                    Placeholder.unparsed("uuid", uniqueId.toString())
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}