package xyz.irodev.dislinkmc.discord

import com.github.benmanes.caffeine.cache.Cache
import com.google.gson.Gson
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import xyz.irodev.dislinkmc.utils.Account
import xyz.irodev.dislinkmc.utils.VerifyCodeSet


internal class Linker(
    logger: Logger,
    database: Database,
    private val guild: Guild,
    private val newbieRole: Role,
    private val changeNick: Boolean,
    private val codeStore: Cache<String, VerifyCodeSet>
) : Listener(logger, database) {

    private val otpRegex = Regex("""\d{3} ?\d{3}""")
    private val nicknameRegex = Regex("""\w{3,16}""")

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val member = event.member ?: return
        when (event.componentId) {
            "dislinkmc:verify" -> event.replyModal(
                Modal.create("verify", "인증하기")
                    .addActionRow(
                        TextInput.create("name", "닉네임", TextInputStyle.SHORT).apply {
                            minLength = 3
                            maxLength = 16
                            placeholder = "마인크래프트 닉네임"
                        }.build()
                    ).addActionRow(
                        TextInput.create("otpcode", "인증번호", TextInputStyle.SHORT).apply {
                            minLength = 6
                            maxLength = 7
                            placeholder = "000 000"
                        }.build()
                    ).build()
            ).queue()

            "dislinkmc:unverify" -> event.replyModal(
                Modal.create("unverify", "인증 해제").addActionRow(
                    TextInput.create(
                        "confirm",
                        "인증을 해제하시려면 본인의 닉네임을 정확히 입력해주세요.",
                        TextInputStyle.SHORT
                    ).apply {
                        placeholder = member.effectiveName
                        minLength = member.effectiveName.length
                        maxLength = member.effectiveName.length
                    }.build()
                ).build()
            ).queue()

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
                            member.modifyNickname(profile.name).and(
                                event.hook.sendMessage(
                                    "${profile.name} 으로 닉네임이 변경되었습니다."
                                ).setEphemeral(true)
                            ).queue({}, { err ->
                                logger.error("Nickname change failed due to Not Enough Permission", err)
                                event.hook.sendMessage(
                                    "권한 부족으로 인해 닉네임 변경을 실패하였습니다."
                                ).setEphemeral(true).queue()
                            })
                        } else {
                            event.hook.sendMessage(
                                "이미 최신상태입니다."
                            ).setEphemeral(true).complete()
                        }
                    } else {
                        event.hook.sendMessage("새로고침에 실패하였습니다. 관리자에게 문의해주세요.").setEphemeral(true).complete()
                        logger.info(response.toString())
                    }
                } ?: {
                    event.hook.sendMessage("디스코드 계정에 연결된 마인크래프트 계정이 없습니다.").setEphemeral(true).complete()
                }
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

                    logger.info("Verify Request: User: {} ({})", member.user.name, member.id)
                    logger.info("Input: Name: {} Code: {}", name, otpcode)

                    if (!otpRegex.matches(otpcode)) {
                        logger.warn("'{}' is Invaild code. Verification Failed", otpcode)
                        event.hook.sendMessage("인증 코드가 유효하지 않습니다.").setEphemeral(true).queue()
                    } else if (!nicknameRegex.matches(name)) {
                        logger.warn("'{}' is Invaild Minecraft nickname. Verification Failed", name)
                        event.hook.sendMessage("유효하지 않은 닉네임입니다. 다시 한번 확인해주세요.").setEphemeral(true).queue()
                    } else {
                        codeStore.getIfPresent(event.interaction.values[0].asString.lowercase())
                            ?.let { codeSet ->
                                val intcode = otpcode.replace(" ", "").toInt()
                                if (codeSet.code == intcode) {
                                    transaction(database) {
                                        Account.new(id = member.id.toULong()) { mcuuid = codeSet.uuid }
                                    }
                                    codeStore.invalidate(event.interaction.values[0].asString.lowercase())
                                    logger.info("Verification succeeded.")

                                    guild.removeRoleFromMember(member, newbieRole).run {
                                        if (changeNick) and(member.modifyNickname(codeSet.name)) else this
                                    }.queue({
                                        event.hook.sendMessage(
                                            "${codeSet.name} (${codeSet.uuid}) 계정으로 인증에 성공하였습니다."
                                        ).setEphemeral(true).queue()
                                    }, { err ->
                                        logger.warn(
                                            "Either role removal or nickname change failed due to missing permission.",
                                            err
                                        )
                                        event.hook.sendMessage(
                                            "권한 부족으로 인해 닉네임 변경 또는 역할 제거가 실패하였습니다."
                                        ).setEphemeral(true).queue()
                                    })
                                } else {
                                    logger.warn(
                                        "Input: {} != Expected: {} Code mismatch. Verification Failed",
                                        intcode, codeSet.code
                                    )
                                    event.hook.sendMessage("인증 코드가 일치하지 않습니다. 다시 한번 확인해주세요.").setEphemeral(true).queue()
                                }
                            } ?: run {
                            logger.warn("'{}' key doesn't exist in codeStore. Verification Failed", name)
                            event.hook.sendMessage("유효하지 않은 닉네임입니다. 다시 한번 확인해주세요.").setEphemeral(true).queue()
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
            }
        }
    }

    private data class Profile(
        val name: String = ""
    )
}