package com.zhousl.aether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zhousl.aether.data.LlmProvider
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.isProviderSetupValid
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import java.util.UUID

private val ProviderFormPrimary = Color(0xFF5C5C5C)

@Stable
class ProviderFormState internal constructor(
    private val existingConfig: LlmProviderConfig?,
    name: String,
    providerStorageValue: String,
    apiKey: String,
    baseUrl: String,
    modelId: String,
    cachedModels: List<String>,
) {
    var name by mutableStateOf(name)
    var providerStorageValue by mutableStateOf(providerStorageValue)
    var apiKey by mutableStateOf(apiKey)
    var baseUrl by mutableStateOf(baseUrl)
    var modelId by mutableStateOf(modelId)
    var cachedModels by mutableStateOf(cachedModels)
    var showModelDropdown by mutableStateOf(false)
    var isFetchingModelsLocally by mutableStateOf(false)

    val selectedProvider: LlmProvider
        get() = LlmProvider.fromStorage(providerStorageValue)

    val isCurrentlyActive: Boolean
        get() = existingConfig?.isActive == true

    val isValid: Boolean
        get() = isProviderSetupValid(
            provider = selectedProvider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            modelId = modelId,
        )

    fun applyProviderDefaults(newProvider: LlmProvider) {
        val previousProvider = selectedProvider
        providerStorageValue = newProvider.storageValue
        if (baseUrl.isBlank() || baseUrl.trim() == previousProvider.defaultBaseUrl) {
            baseUrl = newProvider.defaultBaseUrl
        }
        if (modelId.isBlank() || modelId.trim() == previousProvider.defaultModelId) {
            modelId = newProvider.defaultModelId
        }
    }

    fun buildConfig(
        forceActive: Boolean = existingConfig?.isActive ?: false,
    ): LlmProviderConfig = LlmProviderConfig(
        id = existingConfig?.id ?: UUID.randomUUID().toString(),
        name = name.trim().ifBlank { selectedProvider.displayName },
        providerType = selectedProvider,
        apiKey = apiKey.trim(),
        baseUrl = baseUrl.trim(),
        modelId = modelId.trim(),
        cachedModels = cachedModels,
        isActive = forceActive,
        createdAtMillis = existingConfig?.createdAtMillis ?: System.currentTimeMillis(),
    )

    companion object {
        fun fromConfig(existingConfig: LlmProviderConfig?): ProviderFormState {
            val initialProvider = existingConfig?.providerType ?: LlmProvider.OpenAiCompatible
            return ProviderFormState(
                existingConfig = existingConfig,
                name = existingConfig?.name.orEmpty(),
                providerStorageValue = initialProvider.storageValue,
                apiKey = existingConfig?.apiKey.orEmpty(),
                baseUrl = existingConfig?.baseUrl ?: initialProvider.defaultBaseUrl,
                modelId = existingConfig?.modelId ?: initialProvider.defaultModelId,
                cachedModels = existingConfig?.cachedModels.orEmpty(),
            )
        }
    }
}

@Composable
fun rememberProviderFormState(
    existingConfig: LlmProviderConfig?,
): ProviderFormState = rememberSaveable(
    existingConfig?.id,
    saver = providerFormStateSaver(existingConfig),
) {
    ProviderFormState.fromConfig(existingConfig)
}

@Composable
fun ProviderConfigurationForm(
    state: ProviderFormState,
    isFetchingModels: Boolean,
    onFetchModels: (LlmProviderConfig, (List<String>) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    cardColor: Color = AetherSurfaceHigh,
) {
    val selectedProvider = state.selectedProvider

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProviderFormCard(cardColor = cardColor) {
            ProviderFormTextField(
                label = "Provider name",
                value = state.name,
                onValueChange = { state.name = it },
            )
        }

        ProviderFormCard(cardColor = cardColor) {
            ProviderFormDropdownField(
                label = "Request format",
                selectedValue = selectedProvider.displayName,
                options = LlmProvider.entries,
                onSelected = state::applyProviderDefaults,
            )
        }

        Text(
            text = when (selectedProvider) {
                LlmProvider.OpenAiCompatible -> "Use OpenAI-compatible chat/completions endpoints."
                LlmProvider.VertexExpress -> "Use Vertex AI Express Mode generateContent."
            },
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        ProviderFormCard(cardColor = cardColor) {
            ProviderFormTextField(
                label = "API Key",
                value = state.apiKey,
                onValueChange = { state.apiKey = it },
            )
            ProviderFormDivider()
            ProviderFormTextField(
                label = "Base URL",
                value = state.baseUrl,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                onValueChange = { state.baseUrl = it },
            )
            ProviderFormDivider()
            ProviderModelField(
                value = state.modelId,
                onValueChange = { state.modelId = it },
                cachedModels = state.cachedModels,
                showModelDropdown = state.showModelDropdown,
                onDismissModels = { state.showModelDropdown = false },
                onSelectModel = {
                    state.modelId = it
                    state.showModelDropdown = false
                },
                isFetchingModels = state.isFetchingModelsLocally || isFetchingModels,
                onFetchModels = {
                    state.isFetchingModelsLocally = true
                    onFetchModels(state.buildConfig()) { models ->
                        state.cachedModels = models
                        state.isFetchingModelsLocally = false
                        if (models.isNotEmpty()) {
                            state.showModelDropdown = true
                        }
                    }
                },
            )
        }

        Text(
            text = if (selectedProvider == LlmProvider.VertexExpress) {
                "Vertex Express requires an API key. Base URL and Model ID are required for every provider."
            } else {
                "Base URL and Model ID are required. Tap refresh to fetch available models from the API."
            },
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private fun providerFormStateSaver(
    existingConfig: LlmProviderConfig?,
): Saver<ProviderFormState, Any> = listSaver(
    save = { state ->
        listOf(
            state.name,
            state.providerStorageValue,
            state.apiKey,
            state.baseUrl,
            state.modelId,
            state.cachedModels,
        )
    },
    restore = { restored ->
        @Suppress("UNCHECKED_CAST")
        ProviderFormState(
            existingConfig = existingConfig,
            name = restored[0] as String,
            providerStorageValue = restored[1] as String,
            apiKey = restored[2] as String,
            baseUrl = restored[3] as String,
            modelId = restored[4] as String,
            cachedModels = restored[5] as List<String>,
        )
    },
)

@Composable
private fun ProviderFormCard(
    cardColor: Color,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardColor, RoundedCornerShape(20.dp)),
    ) {
        content()
    }
}

@Composable
private fun ProviderFormDivider() {
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ProviderFormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AetherOnSurface),
            cursorBrush = SolidColor(ProviderFormPrimary),
            keyboardOptions = keyboardOptions,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AetherOnSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun ProviderFormDropdownField(
    label: String,
    selectedValue: String,
    options: List<LlmProvider>,
    onSelected: (LlmProvider) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedValue,
                style = MaterialTheme.typography.bodyLarge,
                color = AetherOnSurface,
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = "Choose provider",
                tint = AetherOnSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName, color = AetherOnSurface) },
                    trailingIcon = if (option.displayName == selectedValue) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = ProviderFormPrimary) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProviderModelField(
    value: String,
    onValueChange: (String) -> Unit,
    cachedModels: List<String>,
    showModelDropdown: Boolean,
    onDismissModels: () -> Unit,
    onSelectModel: (String) -> Unit,
    isFetchingModels: Boolean,
    onFetchModels: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Model ID",
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = AetherOnSurface),
                    cursorBrush = SolidColor(ProviderFormPrimary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = "Model ID",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AetherOnSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                DropdownMenu(
                    expanded = showModelDropdown && cachedModels.isNotEmpty(),
                    onDismissRequest = onDismissModels,
                    modifier = Modifier.background(Color.White),
                ) {
                    cachedModels.take(20).forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model, color = AetherOnSurface) },
                            onClick = { onSelectModel(model) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isFetchingModels) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = ProviderFormPrimary,
                )
            } else {
                IconButton(onClick = onFetchModels) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Fetch models",
                        tint = ProviderFormPrimary,
                    )
                }
            }
        }
    }
}
