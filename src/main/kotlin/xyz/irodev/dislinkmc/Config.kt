package xyz.irodev.dislinkmc

import com.moandjiezana.toml.Toml
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path


internal data class Config(
    val version: Int = 2,
    val general: General = General(),
    val discord: Discord = Discord(),
    val mariadb: MariaDB = MariaDB(),
    val message: MessageList = MessageList(),
    val otp: OTP = OTP()
) {

    data class General(
        val verifyHost: String = ""
    )

    data class Discord(
        val token: String = "",
        val guildID: Long = 0,
        val newbieRoleID: Long = 0,
        val verifyChannelID: Long = 0,
        val unverifyChannelID: Long = 0,
    )

    data class MariaDB(
        val url: String = "", val user: String = "", val password: String = ""
    )

    data class MessageList(
        val prefix: String = "",
        val onSuccess: String = "{0}''s code : {2}",
        val onFail: String = "Fail to generate {0}''s code",
        val onAlready: String = "{0} is already linked with Discord",
        val onNotVerified: String = "{0} is not linked with Discord. Retry after link account with Discord"
    )

    data class OTP(
        val time: Long = 180L
    )

    companion object {
        internal fun loadConfig(path: Path, logger: Logger, server: ProxyServer): Config {
            val folder = path.toFile()
            val file = File(folder, "config.toml")

            if (!folder.exists()) folder.mkdirs()

            if (!file.exists()) {
                logger.info("Config file missing. Generating Default Config")
                try {
                    Config::class.java.getResourceAsStream("/" + file.name).use { input ->
                        if (input != null) {
                            Files.copy(input, file.toPath())
                            logger.info("Generate succeed. Restart server after edit config.")
                            server.shutdown()
                        } else {
                            logger.error("Default Config File missing. Is jarfile corrupted?")
                            server.shutdown()
                        }
                    }
                } catch (exception: IOException) {
                    exception.printStackTrace()
                    server.shutdown()
                }
            }
            return Toml().read(file).to(Config::class.java)
        }
    }
}
