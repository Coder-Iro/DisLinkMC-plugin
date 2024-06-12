package xyz.irodev.dislinkmc.discord

import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import xyz.irodev.dislinkmc.utils.Account

internal abstract class Listener(
    protected val logger: Logger,
    protected val database: Database,
) : ListenerAdapter() {
    protected fun delete(id: ULong) {
        transaction(database) {
            Account.findById(id)?.let { account ->
                logger.info("Deleting Account {}", id)
                account.delete()
            }
        }
    }
}