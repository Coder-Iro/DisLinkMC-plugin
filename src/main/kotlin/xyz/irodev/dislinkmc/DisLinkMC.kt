package xyz.irodev.dislinkmc

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.kyori.adventure.text.Component
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import java.io.File
import java.nio.file.Path
import java.sql.SQLInvalidAuthorizationSpecException
import java.text.MessageFormat
import java.util.*
import java.util.concurrent.TimeUnit


@Suppress("unused")
class DisLinkMC @Inject constructor(
    server: ProxyServer, private val logger: Logger, @DataDirectory dataDirectory: Path
) {

    private val config = Config.loadConfig(dataDirectory, logger, server)

    private val verifyHost = config.general.verifyHost

    private val isVerifyOnly = verifyHost.isEmpty()

    private val prefix = config.message.prefix.takeIf { it.isNotEmpty() }?.let { "$it\n\n" } ?: ""

    private val onSuccess: MessageFormat = MessageFormat(config.message.onSuccess)

    private val onFail: MessageFormat = MessageFormat(config.message.onFail)

    private val onAlready: MessageFormat = MessageFormat(config.message.onAlready)

    private val onNotVerified: MessageFormat = MessageFormat(config.message.onNotVerified)

    private val codeStore: Cache<String, VerifyCodeSet> =
        Caffeine.newBuilder().expireAfterWrite(config.otp.time, TimeUnit.SECONDS).build()

    private val database: Database = Database.connect(
        config.mariadb.url,
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
    private fun onLogin(event: LoginEvent) {
        event.player.run {
            if (isVerifyOnly || (virtualHost.orElse(null)?.hostString == verifyHost)) {
                if (!transaction(database) {
                        VerifyBot.Account.find { VerifyBot.LinkedAccounts.mcuuid eq uniqueId }.empty()
                    }) {
                    disconnect(
                        Component.text(
                            "$prefix${
                                onAlready.format(
                                    arrayOf<String>(
                                        username, uniqueId.toString()
                                    )
                                )
                            }"
                        )
                    )
                } else {
                    try {
                        var codeset: VerifyCodeSet? = codeStore.getIfPresent(username.lowercase())
                        if (codeset == null) {
                            codeset = VerifyCodeSet(username, uniqueId, (0..999999).random())
                            codeStore.put(username.lowercase(), codeset)
                        }
                        logger.info(codeset.toString())
                        disconnect(
                            Component.text(
                                prefix + onSuccess.format(
                                    arrayOf<String>(
                                        username,
                                        uniqueId.toString(),
                                        String.format("%03d %03d", codeset.code / 1000, codeset.code % 1000)
                                    )
                                )
                            )
                        )

                    } catch (e: Exception) {
                        disconnect(
                            Component.text(
                                "$prefix${
                                    onFail.format(
                                        arrayOf<String>(
                                            username, uniqueId.toString()
                                        )
                                    )
                                }"
                            )
                        )
                        e.printStackTrace()
                    }
                }
            } else {
                if (transaction(database) {
                        VerifyBot.Account.find { VerifyBot.LinkedAccounts.mcuuid eq uniqueId }.empty()
                    }) disconnect(
                    Component.text(
                        "$prefix${
                            onNotVerified.format(
                                arrayOf<String>(
                                    username, uniqueId.toString()
                                )
                            )
                        }"
                    )
                )
            }
        }
    }

    @Subscribe
    private fun onExit(@Suppress("UNUSED_PARAMETER") event: ProxyShutdownEvent) {
        discord?.run {
            shutdown()
            awaitShutdown()
        }
    }


    internal data class VerifyCodeSet(
        val name: String = "", val uuid: UUID = UUID.randomUUID(), val code: Int = 0
    )

}