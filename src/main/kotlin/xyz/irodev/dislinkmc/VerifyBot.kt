package xyz.irodev.dislinkmc

import com.github.benmanes.caffeine.cache.Cache
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent
import net.dv8tion.jda.api.exceptions.HierarchyException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import java.io.File

internal class VerifyBot(
    private val config: Config.Discord,
    private val logger: Logger,
    private val codeStore: Cache<String, DisLinkMC.VerifyCodeSet>,
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
        guild = event.jda.getGuildById(config.guildID)?.let { guild ->
            logger.info("Guild: $guild")
            guild.getRoleById(config.newbieRoleID)?.let {
                newbieRole = it
                logger.info("Newbie Role: $newbieRole")
            } ?: run {
                logger.error("Invalid Newbie Role ID. Please check config.toml")
                return
            }
            (guild.getGuildChannelById(config.verifyChannelID) as? MessageChannel)?.let {
                verifyChannel = it
                logger.info("Verify Channel: $verifyChannel")
            } ?: run {
                logger.error("Invalid Verify Channel ID. Please check config.toml")
                return
            }
            (guild.getGuildChannelById(config.unverifyChannelID) as? MessageChannel)?.let {
                unverifyChannel = it
                logger.info("Unverify Channel: $unverifyChannel")
            } ?: run {
                logger.error("Invalid Unverify Channel ID. Please check config.toml")
                return
            }
            guild
        } ?: run {
            logger.error("Invalid Discord Guild ID. Please check config.toml")
            event.jda.eventManager.unregister(this)
            return
        }

        if (initFile.createNewFile()) {
            logger.warn("First run detected. Initializing...")
            verifyChannel.sendMessage(
                MessageCreateBuilder().addActionRow(
                    Button.secondary("dislinkmc:verify", "인증하기").withEmoji(Emoji.fromUnicode("🔓"))
                ).build()
            ).and(
                unverifyChannel.sendMessage(
                    MessageCreateBuilder().addActionRow(
                        Button.success("dislinkmc:update", "새로고침").withEmoji(Emoji.fromUnicode("🔄")),
                        Button.danger("dislinkmc:unverify", "인증 해제").withEmoji(Emoji.fromUnicode("🔒"))
                    ).build()
                )
            ).queue()
            logger.info("Successfully initialized.")
        }
    }

    override fun onUserUpdateName(event: UserUpdateNameEvent) {
        guild.getMember(event.user)?.takeIf { it.nickname == null }?.let { member ->
            logger.info("Member nick changed: ${event.oldName} => ${event.newName}}")
            try {
                member.modifyNickname(event.oldName).queue()
            } catch (e: HierarchyException) {
                logger.error("Nickname change failed due to Not Enough Permission")
            }
        }
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        val member = event.member ?: return
        logger.info("Member left: ${member.user.name}#${member.user.discriminator} (${member.id})")
        if (newbieRole !in member.roles) {
            transaction(database) {
                Account.findById(member.id.toULong())?.let { account ->
                    logger.info("Verify data exists. Deleting data...")
                    account.delete()
                }
            }
        }
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        logger.info("Member joined: ${event.member.user.name}#${event.member.user.discriminator} (${event.member.id})")
        if (!event.member.user.isBot) {
            try {
                guild.addRoleToMember(event.member, newbieRole).queue()
            } catch (e: HierarchyException) {
                logger.error("Add newbie role failed due to Not Enough Permission")
            }
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val name = event.member?.effectiveName ?: return
        when (event.componentId) {
            "dislinkmc:verify" -> {
                val nameInput = TextInput.create("name", "닉네임", TextInputStyle.SHORT)
                    .apply {
                        minLength = 3
                        maxLength = 16
                        placeholder = "마인크래프트 닉네임"
                    }

                val codeInput = TextInput.create("otpcode", "인증번호", TextInputStyle.SHORT)
                    .apply {
                        minLength = 6
                        maxLength = 7
                        placeholder = "000 000"
                    }

                event.replyModal(
                    Modal.create("verify", "인증하기")
                        .addActionRow(nameInput.build())
                        .addActionRow(codeInput.build())
                        .build()
                ).queue()
            }

            "dislinkmc:unverify" -> {
                val confirmInput = TextInput.create("confirm", "인증을 해제하시려면 본인의 닉네임을 정확히 입력해주세요.", TextInputStyle.SHORT)
                    .apply {
                        placeholder = name
                        minLength = name.length
                        maxLength = name.length
                    }
                event.replyModal(
                    Modal.create("unverify", "인증 해제")
                        .addActionRow(confirmInput.build())
                        .build()
                ).queue()
            }
        }
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        val member = event.member ?: return
        event.deferReply(true).setEphemeral(true).queue()
        when (event.modalId) {
            "verify" -> {
                val name = event.interaction.values[0].asString
                val otpcode = event.interaction.values[1].asString

                logger.info("Verify Request: User: ${member.user.name}#${member.user.discriminator} (${member.id})")
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
                                    guild.removeRoleFromMember(member, newbieRole)

                                    member.modifyNickname(it.name)
                                } catch (e: HierarchyException) {
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
                        val account = Account.findById(member.id.toULong())
                        if (account != null) {
                            account.delete()
                            guild.addRoleToMember(member, newbieRole)
                            logger.info("Unverify account succeeded")
                            event.hook.sendMessage(
                                "인증 해제되었습니다."
                            ).setEphemeral(true).queue()
                        } else {
                            logger.error("Cannot find verify data")
                            event.hook.sendMessage(
                                "인증 해제에 실패했습니다. 관리자에게 문의해주세요."
                            ).setEphemeral(true).queue()
                        }
                    }
                } else {
                    event.hook.sendMessage(
                        "닉네임이 일치하지 않습니다. 다시 시도해주세요."
                    ).setEphemeral(true).queue()
                }
            }
        }
    }

    object LinkedAccounts : IdTable<ULong>("linked_account") {
        @OptIn(ExperimentalUnsignedTypes::class)
        override val id: Column<EntityID<ULong>> = ulong("discord").entityId()
        val mcuuid = uuid("mcuuid")
            .uniqueIndex()
    }

    class Account(id: EntityID<ULong>) : Entity<ULong>(id) {
        companion object : EntityClass<ULong, Account>(LinkedAccounts)

        var mcuuid by LinkedAccounts.mcuuid
    }
}

