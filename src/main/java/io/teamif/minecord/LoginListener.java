package io.teamif.minecord;

import com.google.common.io.BaseEncoding;
import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import net.kyori.adventure.text.Component;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.UUID;

public class LoginListener {
    private final String secretSalt = TimeBasedOneTimePasswordUtil.generateBase32Secret();
    private MessageDigest sha256;
    public static Jedis redis = new Jedis(new HostAndPort("localhost", 6379));

    @Subscribe
    public void onLogin(LoginEvent event) {
        String name = event.getPlayer().getUsername();
        UUID uuid = event.getPlayer().getUniqueId();
        try {
            int code;
            if (!redis.exists(name)) {
                code = TimeBasedOneTimePasswordUtil.generateCurrentNumber(generateSecret(uuid), 6);
                HashMap<String, String> data = new HashMap<>();
                data.put("UUID", uuid.toString());
                data.put("code", Integer.toString(code));
                redis.hset(name, data);
                redis.expire(name, 300L);
            } else {
                code = Integer.parseInt(redis.hget(name, "code"));
            }
            event.getPlayer().disconnect(Component.text(formatOTP(code)));
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
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

    private String generateSecret(UUID uuid) throws NoSuchAlgorithmException {
        if (sha256 == null) {
            sha256 = MessageDigest.getInstance("SHA-256");
        }

        return BaseEncoding.base32().encode(sha256.digest((uuid.toString() + secretSalt).getBytes()));
    }
}
