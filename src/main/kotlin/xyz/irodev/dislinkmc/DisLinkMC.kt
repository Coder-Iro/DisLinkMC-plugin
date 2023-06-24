package xyz.irodev.dislinkmc

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import xyz.irodev.dislinkmc.listener.CodeGenerater
import xyz.irodev.dislinkmc.listener.MotdChanger
import xyz.irodev.dislinkmc.listener.VerifyWhitelist
import java.io.File
import java.nio.file.Path
import java.sql.SQLInvalidAuthorizationSpecException
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull


@Suppress("unused")
class DisLinkMC @Inject constructor(
    private val server: ProxyServer, private val logger: Logger, @DataDirectory dataDirectory: Path
) {

    private val config = Config.loadConfig(dataDirectory, logger, server)

    private val verifyHost = config.general.verifyHost

    private val isVerifyOnly = verifyHost.isEmpty()

    private val codeStore: Cache<String, VerifyCodeSet> =
        Caffeine.newBuilder().expireAfterWrite(config.otp.time, TimeUnit.SECONDS).build()

    private val database: Database = Database.connect(config.mariadb.url,
        "org.mariadb.jdbc.Driver",
        config.mariadb.user,
        config.mariadb.password,
        databaseConfig = DatabaseConfig {
            sqlLogger = object : SqlLogger {
                override fun log(context: StatementContext, transaction: Transaction) {
                    logger.info("SQL: ${context.expandArgs(transaction)}")
                }
            }
        })

    private val discord: JDA? = config.discord.token.let { token ->
        try {
            JDABuilder.createDefault(token).enableIntents(GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL).build().apply {
                    addEventListener(
                        VerifyBot(
                            config.discord, server, logger, codeStore, database, File(dataDirectory.toFile(), ".inited")
                        )
                    )
                }
        } catch (e: Exception) {
            logger.error("Invalid Discord Bot Token. Please check config.toml")
            server.shutdown()
            null
        }
    }

    init {
        discord?.awaitReady()
        try {
            database.connector().close()
        } catch (e: SQLInvalidAuthorizationSpecException) {
            logger.error("Failed connect to database. Please check config.toml")
            server.shutdown()
        }
        transaction(database) {
            SchemaUtils.create(VerifyBot.LinkedAccounts)
        }
    }

    @Subscribe
    private fun onInitialize(@Suppress("UNUSED_PARAMETER") event: ProxyInitializeEvent?) {
        if (isVerifyOnly) {
            server.eventManager.register(this, CodeGenerater(logger, database, config.message, codeStore))
        } else {
            server.eventManager.register(
                this, MotdChanger(verifyHost, server.configuration.motd, server.configuration.favicon.getOrNull())
            )
            server.eventManager.register(this, VerifyWhitelist(logger, database, config.message, codeStore, verifyHost))
        }
    }

    @Subscribe
    private fun onExit(@Suppress("UNUSED_PARAMETER") event: ProxyShutdownEvent) {
        discord?.run {
            shutdown()
            awaitShutdown()
        }
    }

}