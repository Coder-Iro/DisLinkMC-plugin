package xyz.irodev.dislinkmc.discord

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import xyz.irodev.dislinkmc.utils.Account

internal class MemberManager(
    logger: Logger,
    database: Database,
    private val guild: Guild,
    private val newbieRole: Role
) : Listener(logger, database) {

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        event.member.let { member ->
            logger.info("Member joined: ${member.user.name} (${member.id})")
            if (!member.user.isBot) {
                delete(member.id.toULong())
                guild.addRoleToMember(member, newbieRole).queue({
                    logger.info("Successfully set newbie role.")
                }, { err ->
                    logger.warn("Failed to set newbie role.", err)
                })
            }
        }
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        event.member?.let { member ->
            logger.info("Member left: ${member.user.name} (${member.id})")
            if (newbieRole !in member.roles) {
                delete(member.id.toULong())
            }
        }
    }

    override fun onUserContextInteraction(event: UserContextInteractionEvent) {
        event.targetMember?.let { target ->
            event.reply(
                if (newbieRole !in target.roles)
                    transaction(database) {
                        Account.findById(target.id.toULong())
                    }?.mcuuid?.let { uuid ->
                        "https://ko.namemc.com/profile/$uuid"
                    } ?: "인증 데이터가 존재하지 않습니다."
                else "인증 되지 않은 유저입니다."
            ).setEphemeral(true).complete()
        }
    }
}