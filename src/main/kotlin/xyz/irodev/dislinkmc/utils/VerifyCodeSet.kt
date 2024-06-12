package xyz.irodev.dislinkmc.utils

import java.util.UUID

internal data class VerifyCodeSet(
    val name: String, val uuid: UUID, val code: Int
)