package xyz.irodev.dislinkmc;

import com.google.common.io.BaseEncoding;
import com.google.inject.Inject;
import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.UUID;

@Plugin(id = "dislinkmc", name = "DisLinkMC", version = "0.0.4-SNAPSHOT", authors = "Coder-Iro")
public class DisLinkMC {
    private final String secretSalt = TimeBasedOneTimePasswordUtil.generateBase32Secret();
    private final MessageDigest sha256;
    private final RedisClient redisClient;
    private final Logger logger;
    private final MessageFormat onsuccess;
    private final MessageFormat onfail;

    private final long timelimit;

    @Inject
    public DisLinkMC(Logger logger, @DataDirectory Path dataDirectory) throws NoSuchAlgorithmException {
        this.logger = logger;
        Config config = loadConfig(dataDirectory);

        this.timelimit = config.otp.time;
        this.onsuccess = new MessageFormat(config.message.onsuccess);
        this.onfail = new MessageFormat(config.message.onfail);
        this.redisClient = RedisClient.create(config.redis.url);
        this.sha256 = MessageDigest.getInstance("SHA-256");

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

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
    }

    private Config loadConfig(Path path) {
        File folder = path.toFile();
        File file = new File(folder, "config.toml");
        assert file.getParentFile().exists() || file.getParentFile().mkdirs();

        if (!file.exists()) {
            logger.info("Config file doesn't exist. Generating Default Config");
            try (InputStream input = getClass().getResourceAsStream("/" + file.getName())) {
                if (input != null) {
                    Files.copy(input, file.toPath());
                } else {
                    assert file.createNewFile();
                }
            } catch (IOException exception) {
                exception.printStackTrace();
                return new Config();
            }
        }

        return new Toml().read(file).to(Config.class);
    }

    @Subscribe
    private void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String name = player.getUsername();
        UUID uuid = player.getUniqueId();
        try (StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            RedisCommands<String, String> redis = connection.sync();
            int code;
            if (redis.exists(name) == 0) {
                code = TimeBasedOneTimePasswordUtil.generateCurrentNumber(generateSecret(uuid), 6);
                HashMap<String, String> data = new HashMap<>();
                data.put("UUID", uuid.toString());
                data.put("code", Integer.toString(code));
                data.put("realname", name);
                redis.hset(name.toLowerCase(), data);
                redis.expire(name.toLowerCase(), timelimit);
            } else {
                code = Integer.parseInt(redis.hget(name, "code"));
            }
            player.disconnect(Component.text(onsuccess.format(new String[]{name, uuid.toString(), formatOTP(code)})));
        } catch (GeneralSecurityException e) {
            player.disconnect(Component.text(onfail.format(new String[]{name, uuid.toString()})));
            e.printStackTrace();
        }
    }

    private String generateSecret(UUID uuid) {
        return BaseEncoding.base32().encode(sha256.digest((uuid.toString() + secretSalt).getBytes()));
    }

    static class Config {
        @SuppressWarnings("unused")
        int version;
        Redis redis;
        MessageList message;

        OTP otp;
    }


    static class Redis {
        String url;
    }


    static class MessageList {
        String onsuccess;
        String onfail;
    }

    static class OTP {
        long time;
    }
}

