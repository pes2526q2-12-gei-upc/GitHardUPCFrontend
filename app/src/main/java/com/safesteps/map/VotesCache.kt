package com.safesteps.data

import android.content.Context
import androidx.core.content.edit

class VotesCache(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("safesteps_votes", Context.MODE_PRIVATE)

    private fun keyFor(googleId: String) = "votes_$googleId"

    fun load(googleId: String): Map<Long, Int> {
        val raw = prefs.getString(keyFor(googleId), null) ?: return emptyMap()
        return raw.split(';')
            .mapNotNull { entry ->
                val parts = entry.split(':')
                if (parts.size != 2) return@mapNotNull null
                val id = parts[0].toLongOrNull() ?: return@mapNotNull null
                val score = parts[1].toIntOrNull() ?: return@mapNotNull null
                id to score
            }
            .toMap()
    }

    fun save(googleId: String, votes: Map<Long, Int>) {
        val serialized = votes.entries.joinToString(";") { "${it.key}:${it.value}" }
        prefs.edit { putString(keyFor(googleId), serialized) }
    }

    fun update(googleId: String, incidenciaId: Long, score: Int?) {
        val current = load(googleId).toMutableMap()
        if (score == null) current.remove(incidenciaId) else current[incidenciaId] = score
        save(googleId, current)
    }
}