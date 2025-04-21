package xyz.irodev.dislinkmc.discord

import com.google.gson.Gson
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import xyz.irodev.dislinkmc.utils.Account
import xyz.irodev.dislinkmc.utils.LinkedAccounts.mcuuid
import java.util.UUID

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

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name == "find") {
            when (event.subcommandname) {
                "uuid" -> {
                    try {
                        transaction(database) {
                            Account.find { mcuuid eq UUID.fromString(event.getOption("UUID")!!.asString) }.firstOrNull()
                        }
                    } catch (_: IllegalArgumentException) {
                        event.reply("유효하지 않은 UUID 형식입니다.").setEphemeral(true).queue()
                        return
                    }?.let {
                        event.reply("<@${it.id}> (${it.id})").setEphemeral(true).queue()
                    } ?: {
                        event.reply("인증된 디스코드 계정이 없는 마인크래프트 계정입니다.").setEphemeral(true).queue()
                    }
                }

                "nickname" -> {
                    val request = with(OkHttpClient()) {
                        newCall(
                            Request.Builder()
                                .url("https://api.mojang.com/users/profiles/minecraft/${event.getOption("nickname")!!.asString}")
                                .get().build()
                        ).execute()
                    }
                    if (request.isSuccessful) {
                        logger.info(request.toString())
                        val profile = Gson().fromJson(request.body?.string(), Discord.MCProfile::class.java)
                        transaction(database) {
                            Account.find { mcuuid eq UUID.fromString(profile.id) }.firstOrNull()
                        }?.let {
                            event.reply("<@${it.id}> (${it.id})").setEphemeral(true).queue()
                        } ?: {
                            event.reply("인증된 디스코드 계정이 없는 마인크래프트 계정입니다.").setEphemeral(true).queue()
                        }
                    } else {
                        event.reply("유효하지 않은 마인크래프트 닉네임입니다.").setEphemeral(true).queue()
                    }
                }
            }
        }
    }
}