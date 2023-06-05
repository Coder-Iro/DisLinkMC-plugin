package xyz.irodev.dislinkmc

import com.moandjiezana.toml.Toml
import org.slf4j.Logger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path


internal data class Config(
    val version: Int = 2,
    val discord: Discord = Discord(),
    val mariadb: MariaDB = MariaDB(),
    val message: MessageList = MessageList(),
    val otp: OTP = OTP()
) {

    data class Discord(
        val token: String = "",
        val guildID: Long = 0,
        val newbieRoleID: Long = 0
    )

    data class MariaDB(
        val url: String = "",
        val user: String = "",
        val password: String = ""
    )

    data class MessageList(
        val onSuccess: String = "{0}''s code : {2}",
        val onFail: String = "Fail to generate {0}''s code"
    )

    data class OTP(
        val time: Long = 180L
    )

    companion object {
        internal fun loadConfig(path: Path, logger: Logger): Config {
            val folder = path.toFile()
            val file = File(folder, "config.toml")

            if (!folder.exists()) folder.mkdirs()

            if (!file.exists()) {
                logger.info("xyz.irodev.dislinkmc.Config file missing. Generating Default xyz.irodev.dislinkmc.Config")
                try {
                    Config::class.java.getResourceAsStream("/" + file.name).use { input ->
                        if (input != null) {
                            Files.copy(input, file.toPath())
                        } else {
                            logger.error("Default xyz.irodev.dislinkmc.Config File missing. Is it corrupted?")
                            return Config()
                        }
                    }
                } catch (exception: IOException) {
                    exception.printStackTrace()
                    return Config()
                }
            }
            return Toml().read(file).to(Config::class.java)
        }
    }
}
