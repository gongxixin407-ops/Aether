package com.zhousl.aether.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleClientStreamingTest {
    private val client = OpenAiCompatibleClient()

    @Test
    fun streamChatCompletionMarksToolCallOnlyChunksAsActivity() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"read","arguments":"{\"path\":\"README.md\"}"}}]}}]}

                    data: [DONE]

                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "gpt-5.4",
            )
            var activityCount = 0

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Read README.md")
                    }
                ),
                onStreamActivity = {
                    activityCount += 1
                },
            ).getOrThrow()

            assertEquals("", result.assistantText)
            assertEquals(1, result.toolCalls.size)
            assertEquals("read", result.toolCalls.single().name)
            assertTrue(activityCount >= 1)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun streamChatCompletionFailsAfterInactivityTimeoutWithoutAnyResponseActivity() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "gpt-5.4",
                llmInactivityReconnectTimeoutSeconds = 1,
            )

            val startedAt = System.nanoTime()
            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Say hello")
                    }
                ),
            )
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L

            assertTrue(result.isFailure)
            assertTrue(elapsedMillis in 800L..5_000L)
            val message = result.exceptionOrNull()?.message.orEmpty().lowercase()
            assertTrue(
                message.contains("timeout") ||
                    message.contains("timed out") ||
                    message.contains("failed to connect") ||
                    message.contains("canceled") ||
                    message.contains("cancelled"),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun streamChatCompletionFailsWhenOpenAiStreamClosesBeforeDoneMarker() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"content":"hello"}}]}

                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "gpt-5.4",
                llmInactivityReconnectTimeoutSeconds = 5,
            )

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Say hello")
                    }
                ),
            )

            assertTrue(result.isFailure)
            val message = result.exceptionOrNull()?.message.orEmpty().lowercase()
            assertTrue(message.contains("completion"))
        } finally {
            server.shutdown()
        }
    }
}
