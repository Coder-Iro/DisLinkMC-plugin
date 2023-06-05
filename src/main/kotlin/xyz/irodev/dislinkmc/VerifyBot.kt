package xyz.irodev.dislinkmc

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

internal class VerifyBot(private val guild: Guild) : ListenerAdapter() {

    override fun onUserUpdateName(event: UserUpdateNameEvent) {
        val member = guild.getMember(event.user)
        if (member != null) {
            if (member.nickname == null) {
                member.modifyNickname(event.oldName)
            }
        }
    }
}