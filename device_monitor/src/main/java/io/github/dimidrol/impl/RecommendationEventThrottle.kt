package io.github.dimidrol.impl

import io.github.dimidrol.common.DEFAULT_LONG

internal class RecommendationEventThrottle(
    private var cooldownMs: Long
) {

    private var activeKey: String? = null
    private val lastEmittedAtByKey = mutableMapOf<String, Long>()

    fun updateCooldown(cooldownMs: Long) {
        this.cooldownMs = cooldownMs.coerceAtLeast(DEFAULT_LONG)
    }

    fun reset() {
        activeKey = null
        lastEmittedAtByKey.clear()
    }

    fun shouldEmit(key: String?, nowMs: Long): Boolean {
        if (key == null) {
            activeKey = null
            return false
        }

        if (activeKey == key) return false

        val lastEmittedAt = lastEmittedAtByKey[key]
        if (lastEmittedAt != null && (nowMs - lastEmittedAt) < cooldownMs) {
            activeKey = key
            return false
        }

        activeKey = key
        lastEmittedAtByKey[key] = nowMs
        return true
    }
}
