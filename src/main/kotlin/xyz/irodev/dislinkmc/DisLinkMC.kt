package xyz.irodev.dislinkmc

import Config
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import java.nio.file.Path
import java.text.MessageFormat
import java.util.*
import java.util.concurrent.TimeUnit


@Suppress("unused")
class DisLinkMC @Inject constructor(private val logger: Logger, @DataDirectory private val dataDirectory: Path) {
    private val codeStore: Cache<String, VerifyCodeSet>
    private val onSuccess: MessageFormat
    private val onFail: MessageFormat
    private val discord: JDA?

    init {
        val config = Config.loadConfig(dataDirectory, logger)
        onSuccess = MessageFormat(config.message.onSuccess)
        onFail = MessageFormat(config.message.onFail)
        codeStore = Caffeine.newBuilder().expireAfterWrite(config.otp.time, TimeUnit.SECONDS).build()
        discord = try {
            JDABuilder.createDefault(config.discord.token).build()

        } catch (e: IllegalArgumentException) {
            logger.error("Invaild Discord Bot Token. Please Check in config.toml")
            null
        }
        if (discord != null) {
            val guild = discord.getGuildById(config.discord.guild)
            if (guild != null)
                discord.addEventListener(VerifyBot(guild))
            else
                logger.error("Invaild Discord Guild ID. Please Check in config.toml")
        }
    }

    @Subscribe
    private fun onLogin(event: LoginEvent) {
        val player = event.player
        val name = player.username
        val uuid = player.uniqueId
        try {
            var codeset: VerifyCodeSet? = codeStore.getIfPresent(name.lowercase())
            if (codeset == null) {
                codeset = VerifyCodeSet(name, uuid, (0..999999).random())
                codeStore.put(name.lowercase(), codeset)
            }
            println(codeset)
            player.disconnect(
                Component.text(
                    onSuccess.format(
                        arrayOf<String>(
                            name, uuid.toString(), String.format("%03d %03d", codeset.code / 1000, codeset.code % 1000)
                        )
                    )
                )
            )

        } catch (e: Exception) {
            player.disconnect(Component.text(onFail.format(arrayOf<String>(name, uuid.toString()))))
            e.printStackTrace()
        }
    }


    data class VerifyCodeSet(
        val name: String = "", val uuid: UUID = UUID.randomUUID(), val code: Int = 0
    )

}