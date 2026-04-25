package com.zhousl.aether.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherAgentReconnectTest {
    private val client = OpenAiCompatibleClient()

    @Test
    fun retryableProviderErrorsTriggerReconnect() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"error":{"message":"temporary upstream failure"}}""")
        )
        server.start()

        try {
            val failure = client.createChatCompletion(
                settings = AppSettings(
                    provider = LlmProvider.OpenAiCompatible,
                    apiKey = "test-key",
                    baseUrl = server.url("/v1").toString(),
                    modelId = "gpt-5.4",
                ),
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "hello")
                    }
                ),
            ).exceptionOrNull() ?: error("Expected failure")

            assertTrue(shouldReconnectLlmRequest(failure))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun invalidRequestsDoNotTriggerReconnect() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"error":{"message":"invalid value at content 9, parts 0. 1 of 1 of data is already set, cannot set text"}}""")
        )
        server.start()

        try {
            val failure = client.createChatCompletion(
                settings = AppSettings(
                    provider = LlmProvider.OpenAiCompatible,
                    apiKey = "test-key",
                    baseUrl = server.url("/v1").toString(),
                    modelId = "gpt-5.4",
                ),
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "hello")
                    }
                ),
            ).exceptionOrNull() ?: error("Expected failure")

            assertFalse(shouldReconnectLlmRequest(failure))
        } finally {
            server.shutdown()
        }
    }
}
