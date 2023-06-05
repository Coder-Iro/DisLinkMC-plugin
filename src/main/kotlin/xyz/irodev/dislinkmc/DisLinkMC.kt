package xyz.irodev.dislinkmc

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.kyori.adventure.text.Component
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
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
    private val database: Database

    init {
        val config = Config.loadConfig(dataDirectory, logger)
        onSuccess = MessageFormat(config.message.onSuccess)
        onFail = MessageFormat(config.message.onFail)
        codeStore = Caffeine.newBuilder().expireAfterWrite(config.otp.time, TimeUnit.SECONDS).build()
        discord = try {
            JDABuilder.createDefault(config.discord.token).enableIntents(GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL).build()
        } catch (e: IllegalArgumentException) {
            logger.error("Invaild Discord Bot Token. Please Check in config.toml")
            null
        }
        database = Database.connect(
            config.mariadb.url,
            "org.mariadb.jdbc.Driver",
            config.mariadb.user,
            config.mariadb.password
        )
        transaction(database) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(VerifyBot.LinkedAccounts)
        }
        if (discord != null) {
            discord.awaitReady()
            val guild = discord.getGuildById(config.discord.guildID)
            if (guild != null) {
                logger.info(guild.toString())
                val newbieRole = guild.getRoleById(config.discord.newbieRoleID)
                if (newbieRole != null) {
                    logger.info(newbieRole.toString())
                    discord.addEventListener(VerifyBot(guild, newbieRole, logger, codeStore, database))
                } else logger.error("Invaild Newbie Role ID. Please Check in config.toml")
            } else logger.error("Invaild Discord Guild ID. Please Check in config.toml")
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
            logger.info(codeset.toString())
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

    @Subscribe
    private fun onExit(@Suppress("UNUSED_PARAMETER") event: ProxyShutdownEvent) {
        if (discord != null) {
            discord.shutdown()
            discord.awaitShutdown()
        }
    }


    internal data class VerifyCodeSet(
        val name: String = "", val uuid: UUID = UUID.randomUUID(), val code: Int = 0
    )

}