package com.zhousl.aether.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSelectionResolverTest {
    @Test
    fun resolveModelSettingsUsesSessionPreferredModelBeforeDefault() {
        val defaultConfig = providerConfig(
            id = "default-provider",
            providerId = "default",
            baseUrl = "https://default.example/v1",
            modelId = "gpt-5.4",
        )
        val sessionConfig = providerConfig(
            id = "session-provider",
            providerId = "session",
            baseUrl = "https://session.example/v1",
            modelId = "claude-sonnet-4-5",
        )
        val providerConfigs = listOf(defaultConfig, sessionConfig)
        val settings = AppSettings(
            baseUrl = "https://legacy.example/v1",
            modelId = "legacy",
            defaultChatModelKey = buildModelOptionKey(defaultConfig.id, defaultConfig.modelId),
        )

        val resolved = resolveModelSettings(
            baseSettings = settings,
            providerConfigs = providerConfigs,
            preferredModelKey = buildModelOptionKey(sessionConfig.id, sessionConfig.modelId),
            fallbackModelKey = resolveDefaultChatModelKey(settings, providerConfigs),
        )

        assertEquals("https://session.example/v1", resolved.baseUrl)
        assertEquals("claude-sonnet-4-5", resolved.modelId)
    }

    @Test
    fun resolveModelSettingsFallsBackToDefaultWhenPreferredMissing() {
        val defaultConfig = providerConfig(
            id = "default-provider",
            providerId = "default",
            baseUrl = "https://default.example/v1",
            modelId = "gpt-5.4",
        )
        val settings = AppSettings(
            baseUrl = "https://legacy.example/v1",
            modelId = "legacy",
            defaultChatModelKey = buildModelOptionKey(defaultConfig.id, defaultConfig.modelId),
        )

        val resolved = resolveModelSettings(
            baseSettings = settings,
            providerConfigs = listOf(defaultConfig),
            preferredModelKey = "missing-provider::missing-model",
            fallbackModelKey = resolveDefaultChatModelKey(settings, listOf(defaultConfig)),
        )

        assertEquals("https://default.example/v1", resolved.baseUrl)
        assertEquals("gpt-5.4", resolved.modelId)
    }

    private fun providerConfig(
        id: String,
        providerId: String,
        baseUrl: String,
        modelId: String,
    ): LlmProviderConfig = LlmProviderConfig(
        id = id,
        providerId = providerId,
        name = providerId,
        providerType = LlmProvider.OpenAiCompatible,
        apiKey = "test-key",
        baseUrl = baseUrl,
        modelId = modelId,
    )
}
