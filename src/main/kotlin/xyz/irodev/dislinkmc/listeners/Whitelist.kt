package xyz.irodev.dislinkmc.listeners

import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.ResultedEvent.ComponentResult.denied
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import xyz.irodev.dislinkmc.utils.Account
import xyz.irodev.dislinkmc.utils.Config
import xyz.irodev.dislinkmc.utils.LinkedAccounts

internal class Whitelist(
    private val database: Database,
    private val messageList: Config.MessageList,
) : MessageSender(messageList.prefix) {

    @Subscribe(order = PostOrder.EARLY)
    fun LoginEvent.onLogin() {
        if (result.isAllowed) {
            player.run {
                if (transaction(database) {
                        Account.find { LinkedAccounts.mcuuid eq uniqueId }.empty()
                    }) result = denied(
                    message(
                        MiniMessage.miniMessage().deserialize(
                            messageList.onNotVerified,
                            Placeholder.unparsed("name", username),
                            Placeholder.unparsed("uuid", uniqueId.toString())
                        )
                    )
                )
            }
        }
    }
}