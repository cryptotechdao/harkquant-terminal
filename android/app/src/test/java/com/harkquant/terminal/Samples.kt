package com.harkquant.terminal

import com.harkquant.terminal.data.HqJson
import kotlinx.serialization.KSerializer

object Samples {
    fun text(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("samples/$name")) { "missing sample $name" }.bufferedReader().use { it.readText() }

    fun <T> load(name: String, s: KSerializer<T>): T = HqJson.decodeFromString(s, text(name))
}
