package xyz.irodev.dislinkmc.utils

import com.velocitypowered.api.proxy.ProxyServer

interface Server : ProxyServer {
    override fun shutdown(): Nothing
}