package xyz.irodev.dislinkmc.listeners

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

internal abstract class MessageSender(
    prefix: String
) {
    private val prefix = prefix + (if (prefix.isNotEmpty()) "<newline><newline>" else "") + "<message>"

    protected fun message(orgMessage: ComponentLike): Component =
        MiniMessage.miniMessage().deserialize(prefix, Placeholder.component("message", orgMessage))
}
