package com.neomud.server.world

import kotlinx.serialization.Serializable
import java.io.Closeable
import java.io.InputStream

interface WorldDataSource : Closeable {
    fun readText(path: String): String?
    fun readBytes(path: String): ByteArray?
    fun list(prefix: String, suffix: String = ""): List<String>
    fun openStream(path: String): InputStream?
}

@Serializable
data class WorldManifest(
    val formatVersion: Int,
    val name: String,
    val author: String,
    val version: String,
    val description: String = "",
    val createdAt: String = "",
    val engineVersion: String = "0.0.0.0",
    val engineVersionMin: String = "0.0.0.0",
    val worldId: String = "",
    val createdWithMaker: Boolean = false,
    val coverImage: String = "",
    val loadingBgm: String = "",
    val introScript: String = ""
)
