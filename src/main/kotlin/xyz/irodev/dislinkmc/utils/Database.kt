package xyz.irodev.dislinkmc.utils

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import java.util.UUID

object LinkedAccounts : IdTable<ULong>("linked_account") {
    override val id: Column<EntityID<ULong>> = ulong("discord").entityId()
    override val primaryKey: PrimaryKey = PrimaryKey(id)
    val mcuuid = uuid("mcuuid").uniqueIndex()
}

class Account(id: EntityID<ULong>) : Entity<ULong>(id) {
    companion object : EntityClass<ULong, Account>(LinkedAccounts)

    var mcuuid by LinkedAccounts.mcuuid
}

object Blacklist : IdTable<UUID>("blacklist") {
    override val id: Column<EntityID<UUID>> = uuid("mcuuid").entityId()
    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

@Suppress("unused")
class BannedAccount(id: EntityID<UUID>) : Entity<UUID>(id) {
    companion object : EntityClass<UUID, BannedAccount>(Blacklist)
}