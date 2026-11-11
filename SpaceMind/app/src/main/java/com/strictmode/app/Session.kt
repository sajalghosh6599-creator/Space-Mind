package com.strictmode.app

/** PIN dewar por kichu somoy ar PIN chaibe na. */
object Session {
    private var until = 0L

    fun unlock(minutes: Int = 3) {
        until = System.currentTimeMillis() + minutes * 60_000L
    }

    fun isUnlocked() = System.currentTimeMillis() < until

    fun lock() {
        until = 0L
    }
}
