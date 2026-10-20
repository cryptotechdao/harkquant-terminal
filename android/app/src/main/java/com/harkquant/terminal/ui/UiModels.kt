package com.harkquant.terminal.ui

import com.harkquant.terminal.data.NewsItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** An indicator the user put on the chart (persisted as JSON in DataStore). */
@Serializable
data class ActiveIndicator(val id: String, val name: String, val params: JsonObject = JsonObject(emptyMap()))

fun paramValue(o: JsonObject, k: String): Double? = runCatching { o[k]?.jsonPrimitive?.doubleOrNull }.getOrNull()

private val LEVELS = listOf("LOW", "MEDIUM", "HIGH", "CRITICAL")

/** Client-side news filter: minimum impact, asset tag and all-words search; newest first. */
fun filterNews(list: List<NewsItem>, impact: String, asset: String, q: String): List<NewsItem> {
    val min = when (impact) { "MEDIUM+" -> 1; "HIGH+" -> 2; "CRITICAL" -> 3; else -> 0 }
    val words = q.lowercase().split(' ').filter { it.isNotBlank() }
    return list.filter { n ->
        LEVELS.indexOf(n.impact.uppercase()).coerceAtLeast(0) >= min &&
            (asset == "ALL" || n.assets.any { it.equals(asset, true) }) &&
            words.all { w -> n.title.lowercase().contains(w) || n.sourceName.lowercase().contains(w) }
    }.sortedByDescending { it.ts }
}
