package xyz.irodev.dislinkmc

import com.github.benmanes.caffeine.cache.Cache
import com.google.gson.Gson
import com.velocitypowered.api.proxy.ProxyServer
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.guild.GuildBanEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import java.io.File
import java.util.UUID

internal class VerifyBot(
    private val config: Config.Discord,
    private val server: ProxyServer,
    private val logger: Logger,
    private val codeStore: Cache<String, VerifyCodeSet>,
    private val database: Database,
    private val initFile: File
) : ListenerAdapter() {

    private val otpRegex = Regex("\\d{3} ?\\d{3}")
    private val nicknameRegex = Regex("\\w{3,16}")
    private lateinit var guild: Guild
    private lateinit var newbieRole: Role
    private lateinit var verifyChannel: MessageChannel
    private lateinit var unverifyChannel: MessageChannel

    override fun onReady(event: ReadyEvent) {
        logger.info(event.jda.selfUser.toString())
        guild = event.jda.getGuildById(config.guildID)?.also { guild ->
            logger.info(guild.toString())
            guild.getRoleById(config.newbieRoleID)?.let {
                newbieRole = it
                logger.info("Newbie $newbieRole")
            } ?: run {
                logger.error("Invalid Newbie Role ID. Please check config.toml")
                server.shutdown()
            }
        } ?: run {
            logger.error("Invalid Discord Guild ID. Please check config.toml")
            server.shutdown()
            return
        }

        guild.updateCommands().addCommands(
            Commands.context(Command.Type.USER, "인증 계정 정보 조회")
        ).queue()

        if (initFile.createNewFile()) {
            logger.warn("First run detected. Initializing...")
            (guild.getGuildChannelById(config.verifyChannelID) as? MessageChannel)?.let {
                verifyChannel = it
                logger.info("Verify $verifyChannel")
            } ?: run {
                logger.error("Invalid Verify Channel ID. Please check config.toml")
                server.shutdown()
            }
            (guild.getGuildChannelById(config.unverifyChannelID) as? MessageChannel)?.let {
                unverifyChannel = it
                logger.info("Unverify $unverifyChannel")
            } ?: run {
                logger.error("Invalid Unverify Channel ID. Please check config.toml")
                server.shutdown()
            }
            verifyChannel.sendMessage(
                MessageCreateBuilder().addActionRow(
                    Button.secondary("dislinkmc:verify", "인증하기").withEmoji(Emoji.fromUnicode("🔓"))
                ).build()
            ).and(
                unverifyChannel.sendMessage(
                    MessageCreateBuilder().addActionRow(
                        Button.success("dislinkmc:update", "새로고침").withEmoji(Emoji.fromUnicode("🪄")),
                        Button.danger("dislinkmc:unverify", "인증 해제").withEmoji(Emoji.fromUnicode("🔒"))
                    ).build()
                )
            ).queue()
            logger.info("Successfully initialized.")
        }
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        event.member?.run {
            logger.info("Member left: ${user.name} (${id})")
            if (newbieRole !in roles) {
                transaction(database) {
                    Account.findById(id.toULong())?.let { account ->
                        logger.info("Verify data exists. Deleting data...")
                        account.delete()
                    }
                }
            }
        }
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        event.member.let { member ->
            logger.info("Member joined: ${member.user.name} (${member.id})")
            if (!member.user.isBot) {
                transaction(database) {
                    Account.findById(member.id.toULong())?.let { account ->
                        logger.info("Verify data exists. Deleting data...")
                        account.delete()
                    }
                }
                try {
                    guild.addRoleToMember(member, newbieRole).queue()
                } catch (e: Exception) {
                    logger.error("Add newbie role failed due to Not Enough Permission")
                }
            }
        }
    }

    override fun onGuildBan(event: GuildBanEvent) {
    }

    override fun onUserContextInteraction(event: UserContextInteractionEvent) {
        event.targetMember?.let {
            event.reply(if (newbieRole !in it.roles) transaction(database) {
                Account.findById(it.id.toULong())
            }?.mcuuid?.run {
                "https://ko.namemc.com/profile/$this"
            } ?: "인증 데이터가 존재하지 않습니다."
            else "인증 되지 않은 유저입니다.").setEphemeral(true).queue()
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val member = event.member ?: return
        when (event.componentId) {
            "dislinkmc:verify" -> {
                val nameInput = TextInput.create("name", "닉네임", TextInputStyle.SHORT).apply {
                    minLength = 3
                    maxLength = 16
                    placeholder = "마인크래프트 닉네임"
                }.build()

                val codeInput = TextInput.create("otpcode", "인증번호", TextInputStyle.SHORT).apply {
                    minLength = 6
                    maxLength = 7
                    placeholder = "000 000"
                }.build()

                event.replyModal(
                    Modal.create("verify", "인증하기").addActionRow(nameInput).addActionRow(codeInput).build()
                ).queue()
            }

            "dislinkmc:update" -> {
                event.deferReply(true).setEphemeral(true).queue()
                transaction(database) {
                    Account.findById(event.user.id.toULong())
                }?.let { account ->
                    val response = with(OkHttpClient()) {
                        newCall(
                            Request.Builder()
                                .url("https://sessionserver.mojang.com/session/minecraft/profile/${account.mcuuid}")
                                .get().build()
                        ).execute()
                    }
                    if (response.isSuccessful) {
                        logger.info(response.toString())
                        val profile = Gson().fromJson(response.body?.string(), Profile::class.java)
                        if (member.nickname != profile.name) {
                            try {
                                member.modifyNickname(profile.name).queue()
                                event.hook.sendMessage(
                                    "${profile.name} 으로 닉네임이 변경되었습니다."
                                ).setEphemeral(true).queue()
                            } catch (e: Exception) {
                                logger.error("Nickname change failed due to Not Enough Permission")
                                event.hook.sendMessage(
                                    "권한 부족으로 인해 닉네임 변경을 실패하였습니다."
                                ).setEphemeral(true).queue()
                            }
                        } else {
                            event.hook.sendMessage(
                                "이미 최신상태입니다."
                            ).setEphemeral(true).queue()
                        }
                    } else {
                        event.hook.sendMessage("새로고침에 실패하였습니다. 관리자에게 문의해주세요.").setEphemeral(true).queue()
                        logger.info(response.toString())
                    }
                } ?: {
                    event.hook.sendMessage("디스코드 계정에 연결된 마인크래프트 계정이 없습니다.").setEphemeral(true).queue()
                }
            }

            "dislinkmc:unverify" -> {
                val confirmInput =
                    TextInput.create("confirm", "인증을 해제하시려면 본인의 닉네임을 정확히 입력해주세요.", TextInputStyle.SHORT).apply {
                        placeholder = member.effectiveName
                        minLength = member.effectiveName.length
                        maxLength = member.effectiveName.length
                    }.build()
                event.replyModal(
                    Modal.create("unverify", "인증 해제").addActionRow(confirmInput).build()
                ).queue()
            }
        }
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        event.deferReply(true).setEphemeral(true).queue()
        event.member?.let { member ->
            when (event.modalId) {
                "verify" -> {
                    val name = event.interaction.values[0].asString
                    val otpcode = event.interaction.values[1].asString

                    logger.info("Verify Request: User: ${member.user.name} (${member.id})")
                    logger.info("Input: Name: $name Code: $otpcode")

                    when {
                        !otpRegex.matches(otpcode) -> {
                            logger.warn("\"${otpcode}\" is Invaild code. Verification Failed")
                            event.hook.sendMessage("인증 코드가 유효하지 않습니다.").setEphemeral(true).queue()
                        }

                        !nicknameRegex.matches(name) -> {
                            logger.warn("\"$name\" is Invaild Minecraft nickname. Verification Failed")
                            event.hook.sendMessage("유효하지 않은 닉네임입니다. 다시 한번 확인해주세요.").setEphemeral(true).queue()
                        }

                        else -> {
                            codeStore.getIfPresent(event.interaction.values[0].asString.lowercase())?.let {
                                val intcode = otpcode.replace(" ", "").toInt()
                                if (it.code == intcode) {
                                    transaction(database) {
                                        Account.new(id = member.id.toULong()) { mcuuid = it.uuid }
                                    }
                                    codeStore.invalidate(event.interaction.values[0].asString.lowercase())
                                    logger.info("Verification succeeded.")
                                    try {
                                        guild.removeRoleFromMember(member, newbieRole).and(
                                            member.modifyNickname(it.name)
                                        ).queue()
                                    } catch (e: Exception) {
                                        logger.warn("Either role removal or nickname change failed due to missing permission.")
                                        event.hook.sendMessage(
                                            "권한 부족으로 인해 닉네임 변경 또는 역할 제거가 실패하였습니다."
                                        ).setEphemeral(true).queue()
                                    }
                                    event.hook.sendMessage(
                                        "${it.name} (${it.uuid}) 계정으로 인증에 성공하였습니다."
                                    ).setEphemeral(true).queue()
                                } else {
                                    logger.warn(
                                        "\"Input: $intcode\" != Expected: ${it.code} Code mismatch. Verification Failed"
                                    )
                                    event.hook.sendMessage("인증 코드가 일치하지 않습니다. 다시 한번 확인해주세요.").setEphemeral(true).queue()
                                }
                            } ?: run {
                                logger.warn("\"${name}\" key doesn't exist in codeStore. Verification Failed")
                                event.hook.sendMessage("유효하지 않은 닉네임입니다. 다시 한번 확인해주세요.").setEphemeral(true).queue()
                            }
                        }
                    }
                }

                "unverify" -> {
                    if (newbieRole in member.roles) {
                        event.hook.sendMessage("인증된 유저만 인증 해제할 수 있습니다.").setEphemeral(true).queue()
                    } else if (event.interaction.values[0].asString == member.effectiveName) {
                        transaction(database) {
                            Account.findById(member.id.toULong())
                        }?.let { account ->
                            transaction(database) {
                                account.delete()
                            }
                            guild.addRoleToMember(member, newbieRole).and(
                                member.modifyNickname(null)
                            ).and(
                                event.hook.sendMessage(
                                    "인증 해제되었습니다."
                                ).setEphemeral(true)
                            ).queue()
                            logger.info("Unverify account succeeded")
                        } ?: {
                            logger.error("Cannot find verify data")
                            event.hook.sendMessage(
                                "인증 해제에 실패했습니다. 관리자에게 문의해주세요."
                            ).setEphemeral(true).queue()
                        }
                    } else {
                        event.hook.sendMessage(
                            "닉네임이 일치하지 않습니다. 다시 시도해주세요."
                        ).setEphemeral(true).queue()
                    }
                }

                else -> {}
            }
        }
    }

    data class Profile(
        val name: String = ""
    )

    object LinkedAccounts : IdTable<ULong>("linked_account") {
        @OptIn(ExperimentalUnsignedTypes::class)
        override val id: Column<EntityID<ULong>> = ulong("discord").entityId()
        override val primaryKey: PrimaryKey = PrimaryKey(id)
        val mcuuid = uuid("mcuuid").uniqueIndex()
    }

    class Account(id: EntityID<ULong>) : Entity<ULong>(id) {
        companion object : EntityClass<ULong, Account>(LinkedAccounts)

        var mcuuid by LinkedAccounts.mcuuid
    }

    object Blacklist : IdTable<UUID>("blacklist") {
        override val id: Column<EntityID<UUID>> = uuid("mcuuid").entityId()
        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    class BannedAccount(id: EntityID<UUID>) : Entity<UUID>(id) {
        companion object : EntityClass<UUID, BannedAccount>(Blacklist)
    }
}

