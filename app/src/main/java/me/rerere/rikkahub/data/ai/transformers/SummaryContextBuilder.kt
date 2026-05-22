package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.SummaryConfig

/**
 * Summary Context Builder Transformer
 * 
 * Replaces early conversation messages with their summaries when the conversation
 * reaches the configured threshold. Uses a stair-step compression pattern to
 * maximize context cache efficiency.
 */
object SummaryContextBuilder : InputMessageTransformer {
    
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val config = ctx.assistant.summaryConfig
        
        // Check if summary feature is enabled
        if (!config.enabled) {
            return messages
        }
        
        // Count valid conversation rounds (skip messages with Tool parts)
        val rounds = countValidRounds(messages)
        
        // Calculate how many messages should use summaries
        val summaryCount = calculateSummaryCount(rounds, config)
        
        if (summaryCount <= 0) {
            return messages
        }
        
        // Replace early rounds with their summaries
        // Only count assistant non-Tool messages as rounds
        var roundsSeen = 0
        return messages.map { message ->
            if (message.role == MessageRole.ASSISTANT && !message.hasPart<UIMessagePart.Tool>()) {
                roundsSeen++
                if (roundsSeen <= summaryCount) {
                    replaceWithSummary(message)
                } else {
                    message
                }
            } else {
                message
            }
        }
    }
    
    /**
     * Count valid conversation rounds.
     * A "round" is an assistant message without Tool parts.
     */
    private fun countValidRounds(messages: List<UIMessage>): Int {
        return messages.count { message ->
            message.role == MessageRole.ASSISTANT && !message.hasPart<UIMessagePart.Tool>()
        }
    }

    /**
     * Calculate how many rounds should use summaries based on stair-step pattern.
     *
     * Pattern:
     * - rounds < startRound: 0 (no compression)
     * - rounds >= startRound: N rounds use summaries, where N increases by interval
     *
     * Example (startRound=30, interval=10):
     * - rounds 30-39: first 10 rounds use summaries
     * - rounds 40-49: first 20 rounds use summaries
     * - rounds 50-59: first 30 rounds use summaries
     */
    fun calculateSummaryCount(rounds: Int, config: SummaryConfig): Int {
        if (rounds < config.startRound) {
            return 0
        }

        val intervalsPassed = (rounds - config.startRound) / config.interval + 1
        return intervalsPassed * config.interval
    }
    
    /**
     * Replace message content with summary if available.
     * If the message has a Summary part, use its text as the message content.
     * Otherwise, return the original message.
     */
    private fun replaceWithSummary(message: UIMessage): UIMessage {
        val summaryPart = message.parts.filterIsInstance<UIMessagePart.Summary>().firstOrNull()
        
        if (summaryPart != null) {
            // Replace all parts with just the summary text
            return message.copy(
                parts = listOf(UIMessagePart.Text(text = summaryPart.text))
            )
        }
        
        return message
    }
}
