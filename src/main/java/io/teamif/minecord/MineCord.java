package io.teamif.minecord;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

@Plugin(id = "minecord", name = "MineCord", version = "0.0.2-SNAPSHOT", authors = "Team-IF")
public class MineCord {

    private final ProxyServer server;

    @Inject
    public MineCord(ProxyServer server, Logger logger) {
        this.server = server;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        server.getEventManager().register(this, new LoginListener());
    }

}

