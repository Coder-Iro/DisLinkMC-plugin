package xyz.irodev.dislinkmc

import com.google.inject.Inject
import com.moandjiezana.toml.Toml
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import io.lettuce.core.RedisClient
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.text.MessageFormat


@Plugin(id = "dislinkmc", name = "DisLinkMC", version = "1.0.0-SNAPSHOT", authors = ["Coder-Iro"])
class DisLinkMC @Inject constructor(private val logger: Logger, @DataDirectory private val dataDirectory: Path) {
    private val redisClient: RedisClient
    private val onSuccess: MessageFormat
    private val onFail: MessageFormat
    private val timelimit: Long


    init {
        val config = loadConfig(dataDirectory)
        onSuccess = MessageFormat(config.message.onSuccess)
        onFail = MessageFormat(config.message.onFail)
        redisClient = RedisClient.create(config.redis.url)
        timelimit = config.otp.time
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
                    code = (0..999999).random()
                    val data = HashMap<String, String>()
                    data["UUID"] = uuid.toString()
                    data["code"] = code.toString()
                    data["realname"] = name
                    redis.hset(name.lowercase(), data)
                    redis.expire(name.lowercase(), timelimit)
                } else {
                    code = redis.hget(name, "code").toInt()
                }
                player.disconnect(
                    Component.text(
                        onSuccess.format(
                            arrayOf<String>(
                                name, uuid.toString(), String.format("%06d", code)
                            )
                        )
                    )
                )
            }
        } catch (e: Exception) {
            player.disconnect(Component.text(onFail.format(arrayOf<String>(name, uuid.toString()))))
            e.printStackTrace()
        }
    }

    private fun loadConfig(path: Path): Config {
        val folder = path.toFile()
        val file = File(folder, "config.toml")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        if (!file.exists()) {
            logger.info("Config file missing. Generating Default Config")
            try {
                javaClass.getResourceAsStream("/" + file.name).use { input ->
                    if (input != null) {
                        Files.copy(input, file.toPath())
                    } else {
                        logger.error("Default Config File missing. Is it corrupted?")
                        return Config()
                    }
                }
            } catch (exception: IOException) {
                exception.printStackTrace()
                return Config()
            }
        }
        return Toml().read(file).to(Config::class.java)
    }

    data class Config(
        var version: Int = 1, var redis: Redis = Redis(), var message: MessageList = MessageList(), var otp: OTP = OTP()
    ) {
        data class Redis(
            var url: String = "redis://localhost:6379"
        )

        data class MessageList(
            var onSuccess: String = "{0}''s code : {2}", var onFail: String = "Fail to generate {0}''s code"
        )

        data class OTP(
            var time: Long = 180L
        )
    }


}