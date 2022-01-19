package io.teamif.minecord;

import com.google.common.io.BaseEncoding;
import com.google.inject.Inject;
import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.UUID;

@Plugin(id = "minecord", name = "MineCord", version = "0.0.3-SNAPSHOT", authors = "Team-IF")
public class MineCord {
    private final String secretSalt = TimeBasedOneTimePasswordUtil.generateBase32Secret();
    private final MessageDigest sha256;
    private final ProxyServer server;
    private final RedisClient redisClient;

    @Inject
    public MineCord(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) throws NoSuchAlgorithmException {
        this.server = server;
        this.redisClient = RedisClient.create("redis://localhost:6379");
        this.sha256 = MessageDigest.getInstance("SHA-256");
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        server.getEventManager().register(this, LoginEvent.class, evnt -> {
            StatefulRedisConnection<String, String> connection = this.redisClient.connect();
            try (connection) {
                RedisCommands<String, String> redis = connection.sync();
                String name = evnt.getPlayer().getUsername();
                UUID uuid = evnt.getPlayer().getUniqueId();
                int code;
                if (redis.exists(name) == 0) {
                    code = TimeBasedOneTimePasswordUtil.generateCurrentNumber(generateSecret(uuid), 6);
                    HashMap<String, String> data = new HashMap<>();
                    data.put("UUID", uuid.toString());
                    data.put("code", Integer.toString(code));
                    redis.hset(name, data);
                    redis.expire(name, 300L);
                } else {
                    code = Integer.parseInt(redis.hget(name, "code"));
                }
                evnt.getPlayer().disconnect(Component.text(formatOTP(code)));
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
            }
        });
    }

    private static String formatOTP(long num) {
        String numStr = Long.toString(num);

        if (numStr.length() > 6)
            throw new IllegalArgumentException("Argument num may not consist of more than 6 digits");

        StringBuilder sb = new StringBuilder(7);

        if (numStr.length() != 6) {
            int zeroCount = 6 - numStr.length();
            sb.append("000000", 0, zeroCount);
        }

        sb.append(numStr);
        sb.insert(3, ' ');

        return sb.toString();
    }

    private String generateSecret(UUID uuid) {
        return BaseEncoding.base32().encode(sha256.digest((uuid.toString() + secretSalt).getBytes()));
    }
}

