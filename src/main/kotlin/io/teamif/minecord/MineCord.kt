package io.teamif.minecord

import com.google.common.io.BaseEncoding
import com.google.inject.Inject
import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil
import com.moandjiezana.toml.Toml
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import io.lettuce.core.RedisClient
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.text.MessageFormat
import java.util.UUID

@Plugin(id = "minecord", name = "MineCord", version = "0.0.4-SNAPSHOT", authors = ["Team-IF"])
class MineCord @Inject constructor(private val logger: Logger, @DataDirectory dataDirectory: Path) {
    private val secretSalt = TimeBasedOneTimePasswordUtil.generateBase32Secret()
    private val sha256: MessageDigest
    private val redisClient: RedisClient
    private val onSuccess: MessageFormat
    private val onFail: MessageFormat

    init {
        val config = loadConfig(dataDirectory)
        onSuccess = MessageFormat(config.message.onSuccess)
        onFail = MessageFormat(config.message.onFail)
        redisClient = RedisClient.create(config.redis.url)
        sha256 = MessageDigest.getInstance("SHA-256")
    }

    @Subscribe
    fun onInitialize(event: ProxyInitializeEvent?) {
    }

    private fun loadConfig(path: Path): Config {
        val folder = path.toFile()
        val file = File(folder, "config.toml")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        if (!file.exists()) {
            logger.info("Config file doesn't exist. Generating Default Config")
            try {
                javaClass.getResourceAsStream("/" + file.name).use { input ->
                    if (input != null) {
                        Files.copy(input, file.toPath())
                    } else {
                        file.createNewFile()
                    }
                }
            } catch (exception: IOException) {
                exception.printStackTrace()
                return Config()
            }
        }
        return Toml().read(file).to(Config::class.java)
    }

    @Subscribe
    private fun onLogin(event: LoginEvent) {
        val player = event.player
        val name = player.username
        val uuid = player.uniqueId
        try {
            redisClient.connect().use { connection ->
                val redis = connection.sync()
                val code: Int
                if (redis.exists(name) == 0L) {
                    code = TimeBasedOneTimePasswordUtil.generateCurrentNumber(
                        generateSecret(uuid), 6)
                    val data = HashMap<String, String>()
                    data["UUID"] = uuid.toString()
                    data["code"] = code.toString()
                    redis.hset(name, data)
                    redis.expire(name, 300L)
                } else {
                    code = redis.hget(name, "code").toInt()
                }
                player.disconnect(Component.text(onSuccess.format(
                    arrayOf(name, uuid.toString(), formatOTP(code.toLong()))))
                )
            }
        } catch (exception: GeneralSecurityException) {
            player.disconnect(Component.text(onFail.format(
                arrayOf(name, uuid.toString()))))
            exception.printStackTrace()
        }
    }

    private fun generateSecret(uuid: UUID): String {
        return BaseEncoding.base32().encode(
            sha256.digest((uuid.toString() + secretSalt).toByteArray()))
    }

    data class Config(
        val version: Int = 1,
        val redis: Redis = Redis(),
        val message: MessageList = MessageList()
    )

    data class Redis(
        val url: String = "redis://localhost:6379"
    )

    data class MessageList(
        val onSuccess: String = "{0}''s code : {2}",
        val onFail: String = "Fail to generate {0}''s code"
    )

    companion object {
        private fun formatOTP(num: Long): String {
            val numStr = num.toString()
            require(numStr.length <= 6) { "Argument num may not consist of more than 6 digits" }
            val sb = StringBuilder(7)
            if (numStr.length != 6) {
                val zeroCount = 6 - numStr.length
                sb.append("000000", 0, zeroCount)
            }
            sb.append(numStr)
            sb.insert(3, ' ')
            return sb.toString()
        }
    }
}