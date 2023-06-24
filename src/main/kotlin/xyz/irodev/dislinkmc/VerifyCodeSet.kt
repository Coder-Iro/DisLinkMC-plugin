package xyz.irodev.dislinkmc

import java.util.UUID


internal data class VerifyCodeSet(
    val name: String = "", val uuid: UUID = UUID.randomUUID(), val code: Int = 0
)