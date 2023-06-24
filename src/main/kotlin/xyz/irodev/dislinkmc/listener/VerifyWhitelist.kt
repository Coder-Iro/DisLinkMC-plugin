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
import kotlin.jvm.optionals.getOrNull

internal class VerifyWhitelist(
    private val logger: Logger,
    private val database: Database,
    message: Config.MessageList,
    codeStore: Cache<String, VerifyCodeSet>,
    private val verifyHost: String,
) : CodeGenerater(logger, database, message, codeStore) {

    private val onNotVerified: MessageFormat = MessageFormat(message.onNotVerified)

    @Subscribe
    override fun onLogin(event: LoginEvent) {
        event.player.run {
            logger.info("Hostname: ${virtualHost.getOrNull()?.hostString}")
            if (virtualHost.getOrNull()?.hostString == verifyHost) {
                super.onLogin(event)
            } else {
                if (transaction(database) {
                        VerifyBot.Account.find { VerifyBot.LinkedAccounts.mcuuid eq uniqueId }.empty()
                    }) disconnect(
                    Component.text(
                        "$prefix${
                            onNotVerified.format(
                                arrayOf<String>(
                                    username, uniqueId.toString()
                                )
                            )
                        }"
                    )
                )
            }
        }
    }
}