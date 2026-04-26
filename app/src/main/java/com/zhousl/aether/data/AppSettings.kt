package com.zhousl.aether.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

enum class LlmProvider(
    val storageValue: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModelId: String,
) {
    OpenAiCompatible(
        storageValue = "openai_compatible",
        displayName = "OpenAI format",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModelId = "gpt-5.4",
    ),
    VertexExpress(
        storageValue = "vertex_express",
        displayName = "Vertex AI (Express Mode)",
        defaultBaseUrl = "https://aiplatform.googleapis.com/v1",
        defaultModelId = "gemini-2.5-flash",
    );

    companion object {
        fun fromStorage(value: String?): LlmProvider =
            entries.firstOrNull { it.storageValue == value } ?: OpenAiCompatible
    }
}

enum class AgentModeAuthorizationMethod(
    val storageValue: String,
    val displayName: String,
) {
    Root(
        storageValue = "root",
        displayName = "Root",
    ),
    Shizuku(
        storageValue = "shizuku",
        displayName = "Shizuku",
    );

    companion object {
        fun fromStorage(
            value: String?,
            defaultValue: AgentModeAuthorizationMethod = Shizuku,
        ): AgentModeAuthorizationMethod =
            entries.firstOrNull { it.storageValue == value } ?: defaultValue
    }
}

enum class AppLanguage(
    val storageValue: String,
    val languageTag: String,
) {
    English(
        storageValue = "en",
        languageTag = "en",
    ),
    SimplifiedChinese(
        storageValue = "zh-CN",
        languageTag = "zh-CN",
    );

    companion object {
        fun fromStorage(
            value: String?,
            defaultValue: AppLanguage = defaultAppLanguage(),
        ): AppLanguage = entries.firstOrNull { it.storageValue == value } ?: defaultValue
    }
}

enum class AppThemeMode(
    val storageValue: String,
) {
    Light("light"),
    Dark("dark");

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: Light
    }
}

data class AppSettings(
    val provider: LlmProvider = LlmProvider.OpenAiCompatible,
    val apiKey: String = "",
    val baseUrl: String = LlmProvider.OpenAiCompatible.defaultBaseUrl,
    val modelId: String = LlmProvider.OpenAiCompatible.defaultModelId,
    val systemPrompt: String = "You are Aether, a local-first Android agent that can call tools and complete tasks on-device. Use available tools instead of guessing local state.",
    val tavilyApiKey: String = "",
    val llmInactivityReconnectTimeoutSeconds: Int = DefaultLlmInactivityReconnectTimeoutSeconds,
    val keepTasksRunningInBackground: Boolean = true,
    val notifyOnTaskCompletion: Boolean = true,
    val agentModeAuthorizationEnabled: Boolean = false,
    val agentModeAuthorizationMethod: AgentModeAuthorizationMethod = AgentModeAuthorizationMethod.Shizuku,
    val language: AppLanguage = defaultAppLanguage(),
    val themeMode: AppThemeMode = AppThemeMode.Light,
    val onboardingSeenVersion: Int = 0,
    val onboardingCompletedVersion: Int = 0,
)

const val CurrentOnboardingVersion = 1
const val DefaultLlmInactivityReconnectTimeoutSeconds = 360
private const val MinLlmInactivityReconnectTimeoutSeconds = 30
private const val MaxLlmInactivityReconnectTimeoutSeconds = 3600
const val OnboardingStarterPrompt = "Hi"

fun defaultAppLanguage(
    locale: Locale = Locale.getDefault(),
): AppLanguage = if (locale.language.equals("zh", ignoreCase = true)) {
    AppLanguage.SimplifiedChinese
} else {
    AppLanguage.English
}

fun normalizeLlmInactivityReconnectTimeoutSeconds(
    value: Int?,
): Int = when (value) {
    null -> DefaultLlmInactivityReconnectTimeoutSeconds
    else -> value.coerceIn(
        MinLlmInactivityReconnectTimeoutSeconds,
        MaxLlmInactivityReconnectTimeoutSeconds,
    )
}

fun AppSettings.shouldLaunchOnboarding(
    onboardingVersion: Int = CurrentOnboardingVersion,
): Boolean = onboardingSeenVersion < onboardingVersion

fun AppSettings.isOnboardingComplete(
    onboardingVersion: Int = CurrentOnboardingVersion,
): Boolean = onboardingCompletedVersion >= onboardingVersion

fun isProviderSetupValid(
    provider: LlmProvider,
    apiKey: String,
    baseUrl: String,
    modelId: String,
): Boolean {
    if (baseUrl.trim().isEmpty() || modelId.trim().isEmpty()) return false
    if (provider == LlmProvider.VertexExpress && apiKey.trim().isEmpty()) return false
    return true
}

fun shouldShowResumeSetupBanner(
    settings: AppSettings,
    messageCount: Int,
    draftInput: String,
    hasDraftAttachments: Boolean,
): Boolean = messageCount == 0 &&
    !settings.isOnboardingComplete() &&
    draftInput.isBlank() &&
    !hasDraftAttachments

fun shouldMarkOnboardingCompleted(
    settings: AppSettings,
    isSuccessfulAssistantReply: Boolean,
): Boolean = isSuccessfulAssistantReply && !settings.isOnboardingComplete()

fun shouldRevealFollowUpTourCard(
    isAwaitingFollowUpTour: Boolean,
    isSuccessfulAssistantReply: Boolean,
): Boolean = isAwaitingFollowUpTour && isSuccessfulAssistantReply

// ──────────────────────────────────────────────────────────────────────────────
// Multi-Provider Configuration
// ──────────────────────────────────────────────────────────────────────────────

data class LlmProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val providerType: LlmProvider,
    val apiKey: String,
    val baseUrl: String,
    val modelId: String,
    val cachedModels: List<String> = emptyList(),
    val isActive: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis,
)

internal fun LlmProviderConfig.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("providerType", providerType.storageValue)
    put("apiKey", apiKey)
    put("baseUrl", baseUrl)
    put("modelId", modelId)
    put("cachedModels", JSONArray().apply { cachedModels.forEach(::put) })
    put("isActive", isActive)
    put("createdAtMillis", createdAtMillis)
    put("updatedAtMillis", updatedAtMillis)
}

internal fun parseProviderConfigs(rawValue: String): List<LlmProviderConfig> {
    if (rawValue.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(rawValue)
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                add(
                    LlmProviderConfig(
                        id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = json.optString("name"),
                        providerType = LlmProvider.fromStorage(json.optString("providerType")),
                        apiKey = json.optString("apiKey"),
                        baseUrl = json.optString("baseUrl"),
                        modelId = json.optString("modelId"),
                        cachedModels = json.optJSONArray("cachedModels").toStringListSafe(),
                        isActive = json.optBoolean("isActive", false),
                        createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = json.optLong("updatedAtMillis", System.currentTimeMillis()),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun serializeProviderConfigs(configs: List<LlmProviderConfig>): String =
    JSONArray().apply { configs.forEach { put(it.toJson()) } }.toString()

private fun JSONArray?.toStringListSafe(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotEmpty()) {
                add(value)
            }
        }
    }
}
