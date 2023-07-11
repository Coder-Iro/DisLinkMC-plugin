package xyz.irodev.dislinkmc.listener

import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.ResultedEvent.ComponentResult.denied
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import net.kyori.adventure.text.Component
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import xyz.irodev.dislinkmc.Config
import xyz.irodev.dislinkmc.VerifyBot
import java.text.MessageFormat

internal class VerifyWhitelist(
    private val database: Database,
    private val prefix: String,
    message: Config.MessageList,
) {
    private val onNotVerified: MessageFormat = MessageFormat(message.onNotVerified)

    @Subscribe(order = PostOrder.EARLY)
    fun LoginEvent.onLogin() {
        if (result.isAllowed) {
            player.run {
                if (transaction(database) {
                        VerifyBot.Account.find { VerifyBot.LinkedAccounts.mcuuid eq uniqueId }.empty()
                    }) result = denied(
                    Component.text(
                        "$prefix${onNotVerified.format(arrayOf(username, uniqueId))}"
                    )
                )
            }
        }
    }
}
