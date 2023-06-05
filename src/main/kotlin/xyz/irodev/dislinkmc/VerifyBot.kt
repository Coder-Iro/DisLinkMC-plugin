package xyz.irodev.dislinkmc

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import java.util.*
import kotlin.concurrent.schedule

internal class VerifyBot(private val guild: Guild) : ListenerAdapter() {

    override fun onUserUpdateName(event: UserUpdateNameEvent) {
        val member = guild.getMember(event.user)
        if (member != null) {
            if (member.nickname == null) {
                member.modifyNickname(event.oldName)
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
        when (event.modalId) {
            "verify" -> {
                event.deferReply(true).queue()
                Timer().schedule(5000) {
                    event.hook.editOriginal("인증 완료").queue()
                }
            }

            "unverify" -> {}
        }
    }
}