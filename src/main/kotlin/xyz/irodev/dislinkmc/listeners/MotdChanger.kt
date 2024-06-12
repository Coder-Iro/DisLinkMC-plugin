package xyz.irodev.dislinkmc.listeners

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.proxy.server.ServerPing
import com.velocitypowered.api.util.Favicon
import net.kyori.adventure.text.Component
import kotlin.jvm.optionals.getOrNull

internal class MotdChanger(private val verifyHost: String, private val motd: Component, private val favicon: Favicon?) {
    @Subscribe
    private fun ProxyPingEvent.onPing() {
        if (connection.virtualHost.getOrNull()?.hostString == verifyHost) {
            ping = ServerPing(
                ServerPing.Version(connection.protocolVersion.protocol, connection.protocolVersion.name),
                ServerPing.Players(0, 0, listOf()),
                motd,
                favicon
            )
        }
    }
}