package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

private val SUMMARY_REGEX = Regex("<summary>([\\s\\S]*?)</summary>", RegexOption.DOT_MATCHES_ALL)

/**
 * Summary Extractor Transformer
 * 
 * Extracts <summary> tags from LLM responses and converts them to Summary parts.
 * The summary content is hidden from normal UI display and used for context compression.
 */
object SummaryExtractor : OutputMessageTransformer {
    
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        // Check if summary feature is enabled
        if (!ctx.assistant.summaryConfig.enabled) {
            return messages
        }
        
        return messages.map { message ->
            if (message.role == MessageRole.ASSISTANT && message.hasPart<UIMessagePart.Text>()) {
                // Skip messages with Tool parts
                if (message.hasPart<UIMessagePart.Tool>()) {
                    return@map message
                }
                
                message.copy(
                    parts = message.parts.flatMap { part ->
                        if (part is UIMessagePart.Text && SUMMARY_REGEX.containsMatchIn(part.text)) {
                            val summaryText = SUMMARY_REGEX.find(part.text)?.groupValues?.getOrNull(1)?.trim() ?: ""
                            val strippedText = part.text.replace(SUMMARY_REGEX, "").trim()
                            
                            buildList {
                                // Add summary part if not empty
                                if (summaryText.isNotEmpty()) {
                                    add(UIMessagePart.Summary(text = summaryText))
                                }
                                // Add remaining text if not empty
                                if (strippedText.isNotEmpty()) {
                                    add(part.copy(text = strippedText))
                                }
                            }
                        } else {
                            listOf(part)
                        }
                    }
                )
            } else {
                message
            }
        }
    }
}
