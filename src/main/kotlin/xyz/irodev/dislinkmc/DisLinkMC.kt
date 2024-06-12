package xyz.irodev.dislinkmc

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.inject.Inject
import com.velocitypowered.api.command.RawCommand
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ConsoleCommandSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import xyz.irodev.dislinkmc.discord.Discord
import xyz.irodev.dislinkmc.listeners.MotdChanger
import xyz.irodev.dislinkmc.listeners.OTPIssuer
import xyz.irodev.dislinkmc.listeners.Whitelist
import xyz.irodev.dislinkmc.utils.Blacklist
import xyz.irodev.dislinkmc.utils.Config
import xyz.irodev.dislinkmc.utils.LinkedAccounts
import xyz.irodev.dislinkmc.utils.Server
import xyz.irodev.dislinkmc.utils.VerifyCodeSet
import java.io.File
import java.nio.file.Path
import java.sql.SQLInvalidAuthorizationSpecException
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull


@Suppress("unused")
class DisLinkMC @Inject constructor(
    private val server: Server,
    private val logger: Logger,
    @DataDirectory dataDirectory: Path
) {

    private val config: Config = try {
        Config.load(File(dataDirectory.toFile(), "config.toml"))
    } catch (e: Exception) {
        logger.error(e.message)
        server.shutdown()
    }.let {
        it ?: run {
            logger.info("Default Config generated. Restart server after configure.")
            server.shutdown()
        }
    }

    private val codeStore: Cache<String, VerifyCodeSet> =
        Caffeine.newBuilder().expireAfterWrite(config.general.otpTime, TimeUnit.SECONDS).build()

    private val database: Database = Database.connect(config.mariadb.url,
        "org.mariadb.jdbc.Driver",
        config.mariadb.user,
        config.mariadb.password,
        databaseConfig = DatabaseConfig {
            sqlLogger = object : SqlLogger {
                override fun log(context: StatementContext, transaction: Transaction) {
                    logger.info("SQL: {}", context.expandArgs(transaction))
                }
            }
        })

    private val discord = Discord(server, logger, config.discord, codeStore, database)

    init {
        try {
            database.connector().close()
        } catch (e: SQLInvalidAuthorizationSpecException) {
            logger.error("Failed connect to database. Please check config.toml")
            server.shutdown()
        }
        transaction(database) {
            SchemaUtils.create(LinkedAccounts, Blacklist)
        }
    }

    @Subscribe
    private fun onInitialize(event: ProxyInitializeEvent) {
        server.commandManager.let { commandManager ->
            commandManager.register(
                commandManager.metaBuilder("init").plugin(this).build(),
                object : RawCommand {
                    override fun execute(p0: RawCommand.Invocation) {
                        discord.createButton()
                    }

                    override fun hasPermission(invocation: RawCommand.Invocation): Boolean =
                        invocation.source() is ConsoleCommandSource
                }
            )
        }
        server.eventManager.register(this, OTPIssuer(logger, database, config, codeStore))
        if (config.general.verifyHost.isNotEmpty()) {
            server.eventManager.register(
                this,
                MotdChanger(
                    config.general.verifyHost,
                    server.configuration.motd,
                    server.configuration.favicon.getOrNull()
                )
            )
        }
        if (config.general.useWhitelist)
            server.eventManager.register(this, Whitelist(database, config.message))
    }

    @Subscribe
    private fun onExit(event: ProxyShutdownEvent) {
        discord.shutdown()
    }
}