package ru.andvl.chatter.koog.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

@LLMDescription("Tools for getting current real world time")
internal class CurrentTimeToolSet : ToolSet {
    @Tool("get-current-time")
    @LLMDescription("Get current time in milliseconds")
    internal fun getCurrentTimestamp(): Long {
        return System.currentTimeMillis()
    }
}
