package xyz.irodev.dislinkmc.utils

import com.moandjiezana.toml.Toml
import java.io.File
import java.nio.file.Files

internal data class Config(
    val version: Int,
    val general: General,
    val discord: Discord,
    val mariadb: MariaDB,
    val message: MessageList
) {

    internal data class General(
        val verifyHost: String,
        val otpTime: Long,
        val useWhitelist: Boolean
    )

    internal data class Discord(
        val token: String,
        val setNickname: Boolean,
        val guildID: Long,
        val newbieRoleID: Long,
        val verifyChannelID: Long,
        val unverifyChannelID: Long,
    )

    internal data class MariaDB(
        val url: String,
        val user: String,
        val password: String
    )

    internal data class MessageList(
        val prefix: String,
        val onSuccess: String,
        val onFail: String,
        val onAlready: String,
        val onNotVerified: String
    )

    internal companion object {
        internal fun load(file: File): Config? {
            file.parentFile.run {
                if (!exists()) mkdirs()
            }
            if (!file.exists()) {
                Config::class.java.getResourceAsStream("/" + file.name)?.use { input ->
                    Files.copy(input, file.toPath())
                } ?: error("Default Config File missing. Is jarfile corrupted?")
                return null
            }
            return Toml().read(file).to(Config::class.java)
        }
    }
}
