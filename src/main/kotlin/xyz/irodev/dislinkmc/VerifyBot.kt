package xyz.irodev.dislinkmc

import com.github.benmanes.caffeine.cache.Cache
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent
import net.dv8tion.jda.api.exceptions.HierarchyException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import java.util.*
import kotlin.concurrent.schedule

internal class VerifyBot(
    private val guild: Guild,
    private val newbieRole: Role,
    private val logger: Logger,
    private val codeStore: Cache<String, DisLinkMC.VerifyCodeSet>,
    private val database: Database
) : ListenerAdapter() {

    private val otpRegex = Regex("\\d{3} ?\\d{3}")
    private val nicknameRegex = Regex("\\w{3,16}")

    override fun onUserUpdateName(event: UserUpdateNameEvent) {
        val member = guild.getMember(event.user)
        if (member != null) {
            if (member.nickname == null) {
                logger.info("Member nick changed: ${event.oldName} => ${event.newName}}")
                try {
                    member.modifyNickname(event.oldName).queue()
                } catch (e: HierarchyException) {
                    logger.error("Nickname change failed due to Not Enough Permission")
                }

            }
        }
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        val member = event.member ?: return
        logger.info("Member left: ${member.user.name}#${member.user.discriminator} (${member.id})")
        if (newbieRole !in member.roles) {
            transaction(database) {
                val account = Account.findById(member.id.toULong())
                if (account != null) {
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
                nameInput.minLength = 3
                nameInput.maxLength = 16
                nameInput.placeholder = "마인크래프트 닉네임"
                val codeInput = TextInput.create("otpcode", "인증번호", TextInputStyle.SHORT)
                codeInput.minLength = 6
                codeInput.maxLength = 7
                codeInput.placeholder = "000 000"
                event.replyModal(
                    Modal.create("verify", "인증하기").addActionRow(nameInput.build()).addActionRow(codeInput.build())
                        .build()
                ).queue()
            }

            "dislinkmc:unverify" -> {
                val confirmInput = TextInput.create("confirm", "인증을 해제하시려면 본인의 닉네임을 정확히 입력해주세요.", TextInputStyle.SHORT)
                confirmInput.placeholder = name
                confirmInput.minLength = name.length
                confirmInput.maxLength = name.length
                event.replyModal(Modal.create("unverify", "인증 해제").addActionRow(confirmInput.build()).build()).queue()
            }
        }
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        val member = event.member ?: return
        event.deferReply(true).queue()
        when (event.modalId) {
            "verify" -> {
                val name = event.interaction.values[0].asString
                val otpcode = event.interaction.values[1].asString
                logger.info("Verify Request: User: ${member.user.name}#${member.user.discriminator} (${member.id})")
                logger.info("Input: Name: $name Code: $otpcode")
                if (!otpRegex.matches(otpcode)) {
                    logger.warn("\"${otpcode}\" is Invaild code. Verification Failed")
                    event.reply("인증 코드가 유효하지 않습니다.").setEphemeral(true).queue()
                } else if (!nicknameRegex.matches(name)) {
                    logger.warn("\"$name\" is Invaild Minecraft nickname. Verification Failed")
                    event.reply(
                        "유효하지 않은 닉네임입니다. 다시 한번 확인해주세요."
                    ).setEphemeral(true).queue()
                } else {
                    val codeset = codeStore.getIfPresent(event.interaction.values[0].asString.lowercase())
                    if (codeset != null) {
                        val intcode = otpcode.replace(" ", "").toInt()
                        Timer().schedule(5000) {
                            event.hook.editOriginal(codeset.code.toString())
                                .queue()
                        }
                    } else {
                        logger.warn("\"${name}\" key doesn't exist in codeStore. Verification Failed")
                        event.reply(
                            "유효하지 않은 닉네임입니다. 다시 한번 확인해주세요."
                        ).setEphemeral(true).queue()
                    }
                }
            }

            "unverify" -> {
            }
        }
    }

    object LinkedAccounts : IdTable<ULong>("linked_account") {
        @OptIn(ExperimentalUnsignedTypes::class)
        override val id: Column<EntityID<ULong>> = ulong("discord").entityId()
        val mcuuid = uuid("mcuuid").uniqueIndex()
    }


    class Account(id: EntityID<ULong>) : Entity<ULong>(id) {
        companion object : EntityClass<ULong, Account>(LinkedAccounts)

        var mcuuid by LinkedAccounts.mcuuid
    }
}

