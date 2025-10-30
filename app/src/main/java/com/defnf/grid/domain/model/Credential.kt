package com.defnf.grid.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Credential(
    val id: String,
    val username: String,
    val password: String,
    val privateKey: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)