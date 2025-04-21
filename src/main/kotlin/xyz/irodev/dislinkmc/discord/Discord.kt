package xyz.irodev.dislinkmc.discord

import com.github.benmanes.caffeine.cache.Cache
import com.velocitypowered.api.proxy.ProxyServer
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.jetbrains.exposed.sql.Database
import org.slf4j.Logger
import xyz.irodev.dislinkmc.utils.Config
import xyz.irodev.dislinkmc.utils.VerifyCodeSet

internal class Discord(
    private val server: ProxyServer,
    logger: Logger,
    private val config: Config.Discord,
    private val codeStore: Cache<String, VerifyCodeSet>,
    database: Database,
) : Listener(logger, database) {

    private lateinit var guild: Guild
    private val newbieRole: Role by lazy {
        guild.getRoleById(config.newbieRoleID) ?: invalid("Newbie Role")
    }
    private val verifyChannel: MessageChannel by lazy {
        guild.getGuildChannelById(config.verifyChannelID) as? MessageChannel ?: invalid("Verify Channel")
    }
    private val unverifyChannel: MessageChannel by lazy {
        guild.getGuildChannelById(config.unverifyChannelID) as? MessageChannel ?: invalid("Unverify Channel")
    }

    private val jda: JDA = try {
        JDABuilder.createDefault(config.token).enableIntents(GatewayIntent.GUILD_MEMBERS)
            .setMemberCachePolicy(MemberCachePolicy.ALL).addEventListeners(this).build().awaitReady()
    } catch (_: Exception) {
        logger.error("Invalid Discord Bot Token. Please check config.toml")
        server.shutdown()
        null!!
    }

    override fun onReady(event: ReadyEvent) {
        logger.info(event.jda.selfUser.toString())
        guild = event.jda.getGuildById(config.guildID) ?: invalid("Discord Guild")
        event.jda.addEventListener(
            MemberManager(logger, database, guild, newbieRole),
            Linker(logger, database, guild, newbieRole, config.setNickname, codeStore)
        )

        guild.updateCommands().addCommands(
            Commands.user("인증 계정 정보 조회"),
            Commands.slash("find", "마인크래프트 UUID나 닉네임으로 디스코드 유저를 검색합니다.").addSubcommands(
                SubcommandData("uuid", "마인크래프트 UUID로 디스코드 유저를 검색합니다.")
                    .addOption(OptionType.STRING, "uuid", "마인크래프트 유저의 UUID를 입력하세요.", true),
                SubcommandData("nickname", "마인크래프트 닉네임으로 디스코드 유저를 검색합니다.")
                    .addOption(OptionType.STRING, "nickname", "마인크래프트 유저의 닉네임을 입력하세요", true)
            )
        )
    }

    internal fun createButton() {
        verifyChannel.sendMessage(
            MessageCreateBuilder().addActionRow(
                Button.secondary("dislinkmc:verify", "인증하기").withEmoji(Emoji.fromUnicode("🔓"))
            ).build()
        ).and(
            unverifyChannel.sendMessage(
                MessageCreateBuilder().apply {
                    if (config.setNickname) {
                        addActionRow(Button.success("dislinkmc:update", "새로고침").withEmoji(Emoji.fromUnicode("🪄")))
                    }
                    addActionRow(Button.danger("dislinkmc:unverify", "인증 해제").withEmoji(Emoji.fromUnicode("🔒")))
                }.build()
            )
        ).queue {
            logger.info("Successfully initialized.")
        }
    }

    internal fun shutdown(): Boolean? = jda.run {
        shutdown()
        awaitShutdown()
    }

    private fun invalid(type: String): Nothing {
        logger.error("Invalid {} ID. Please check configuration", type)
        server.shutdown()
        null!!
    }

    internal data class MCProfile(
        val name: String = "",
        val id: String = ""
    )
}