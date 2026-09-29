package com.kingzcheung.xime.plugin.core.lua

/** Error deliberately bypasses Lua pcall's Exception handler. */
internal class LuaExecutionStopped : Error("Lua execution deadline exceeded")

internal class LuaExecutionBudget(timeoutMs: Long) {
    private val deadline = System.nanoTime() + timeoutMs * 1_000_000L
    fun check() {
        if (Thread.currentThread().isInterrupted || System.nanoTime() - deadline >= 0) {
            throw LuaExecutionStopped()
        }
    }
}
