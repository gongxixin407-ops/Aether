package com.zhousl.aether.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhousl.aether.data.AgentModeAuthorizationMethod
import com.zhousl.aether.data.LlmProvider
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.termux.TermuxSetupIssue
import com.zhousl.aether.termux.TermuxSetupState
import com.zhousl.aether.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val StepFadeDuration = 560
private const val MessageTravelDuration = 1_520
private const val ContentFadeDuration = 920
private const val MessageSettleDelayMillis = 800L
private const val MessageMinDurationMillis = 1_000L
private const val MessageMaxDurationMillis = 3_300L

private val TourEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
private val InitialOnboardingSteps = listOf(OnboardingStep.Landing, OnboardingStep.ProviderSetup)
private val FollowUpOnboardingSteps = listOf(
    OnboardingStep.TermuxSetup,
    OnboardingStep.AgentModeAuthorization,
    OnboardingStep.TavilySetup,
    OnboardingStep.SkillsOverview,
    OnboardingStep.McpOverview,
)

private val TourBackground = Color.White
private val TourTextPrimary = Color(0xFF111111)
private val TourTextSecondary = Color(0xFF6A6A66)
private val TourTextTertiary = Color(0xFF8A8A84)
private val TourDivider = Color(0xFFE8E8E3)
private val TourSurface = Color(0xFFF6F6F2)
private val TourButton = Color(0xFF171717)
private val TourBlue = Color(0xFF3C6FE4)
private val TourGreen = Color(0xFF297A68)
private val TourGold = Color(0xFFB6781B)
private val TourPurple = Color(0xFF7E61E9)

private enum class ProviderTourStage {
    PickProvider,
    Credentials,
    Model,
}

private fun OnboardingStep.flowSteps(): List<OnboardingStep> = when (this) {
    OnboardingStep.Landing,
    OnboardingStep.ProviderSetup -> InitialOnboardingSteps

    OnboardingStep.TermuxSetup,
    OnboardingStep.AgentModeAuthorization,
    OnboardingStep.TavilySetup,
    OnboardingStep.SkillsOverview,
    OnboardingStep.McpOverview -> FollowUpOnboardingSteps
}

@Composable
fun OnboardingScreen(
    initialStep: OnboardingStep,
    replayMode: Boolean,
    existingProviderConfig: LlmProviderConfig?,
    isFetchingModels: Boolean,
    termuxSetupState: TermuxSetupState,
    agentModeAuthorizationMethod: AgentModeAuthorizationMethod,
    tavilyApiKey: String,
    installedSkillCount: Int,
    mcpServerCount: Int,
    onFetchModels: (LlmProviderConfig, (List<String>) -> Unit) -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit,
    onCompleteProviderSetup: (LlmProviderConfig) -> Unit,
    onSaveTavilyApiKey: (String) -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefreshTermuxSetup: () -> Unit,
    onSaveAgentModeAuthorization: (Boolean, AgentModeAuthorizationMethod) -> Unit,
    onExploreSettings: () -> Unit,
) {
    var currentStep by rememberSaveable(initialStep, replayMode) {
        mutableStateOf(initialStep)
    }
    var tavilyApiKeyValue by rememberSaveable(initialStep, replayMode, tavilyApiKey) {
        mutableStateOf(tavilyApiKey)
    }
    val formState = rememberProviderFormState(existingProviderConfig)
    var selectedProvider by rememberSaveable(initialStep, replayMode, existingProviderConfig?.id) {
        mutableStateOf(existingProviderConfig?.providerType)
    }
    val steps = remember(initialStep) { initialStep.flowSteps() }

    fun indexOf(step: OnboardingStep): Int = steps.indexOf(step).coerceAtLeast(0)

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            fadeIn(
                animationSpec = tween(
                    durationMillis = StepFadeDuration,
                    delayMillis = 180,
                    easing = TourEasing,
                )
            ) togetherWith fadeOut(
                animationSpec = tween(
                    durationMillis = 180,
                    easing = TourEasing,
                )
            )
        },
        label = "onboarding_step_transition",
    ) { step ->
        val stepIndex = indexOf(step) + 1
        when (step) {
            OnboardingStep.Landing -> LandingStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                replayMode = replayMode,
                onPrimary = { currentStep = OnboardingStep.ProviderSetup },
                onSecondary = if (replayMode) onClose else onSkip,
            )

            OnboardingStep.ProviderSetup -> ProviderSetupStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                replayMode = replayMode,
                selectedProvider = selectedProvider,
                formState = formState,
                isFetchingModels = isFetchingModels,
                onSelectProvider = { provider ->
                    selectedProvider = provider
                    formState.applyProviderDefaults(provider)
                },
                onFetchModels = onFetchModels,
                onExit = if (replayMode) onClose else onSkip,
                onClose = if (replayMode) onClose else onSkip,
                onReturnToLanding = { currentStep = OnboardingStep.Landing },
                onComplete = { onCompleteProviderSetup(formState.buildConfig(forceActive = true)) },
            )

            OnboardingStep.TermuxSetup -> TermuxStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                setupState = termuxSetupState,
                onClose = onClose,
                onContinue = { currentStep = OnboardingStep.AgentModeAuthorization },
                onRequestPermission = onRequestTermuxPermission,
                onOpenAppPermissions = onOpenAppPermissions,
                onOpenTermuxSettings = onOpenTermuxSettings,
                onOpenTermux = onOpenTermux,
                onInstallTermux = onInstallTermux,
                onRefresh = onRefreshTermuxSetup,
            )

            OnboardingStep.AgentModeAuthorization -> AgentModeAuthorizationStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                initialMethod = agentModeAuthorizationMethod,
                onBack = { currentStep = OnboardingStep.TermuxSetup },
                onClose = onClose,
                onContinue = { enabled, method ->
                    onSaveAgentModeAuthorization(enabled, method)
                    currentStep = OnboardingStep.TavilySetup
                },
            )

            OnboardingStep.TavilySetup -> TavilyStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                value = tavilyApiKeyValue,
                onValueChange = { tavilyApiKeyValue = it },
                onBack = { currentStep = OnboardingStep.AgentModeAuthorization },
                onClose = onClose,
                onContinue = {
                    val trimmed = tavilyApiKeyValue.trim()
                    if (trimmed.isNotBlank()) {
                        onSaveTavilyApiKey(trimmed)
                    }
                    currentStep = OnboardingStep.SkillsOverview
                },
            )

            OnboardingStep.SkillsOverview -> SummaryStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                message = "Later, you can add Skills so Aether remembers reusable workflows you like.",
                title = "Skills",
                icon = Icons.Rounded.Extension,
                accent = TourGold,
                lineOne = if (installedSkillCount == 0) {
                    "You do not need this now."
                } else {
                    "$installedSkillCount Skills are already installed."
                },
                lineTwo = "Use them for prompts, checks, and templates.",
                chips = listOf("Prompts", "Checks", "Templates"),
                primaryLabel = "Continue",
                onPrimary = { currentStep = OnboardingStep.McpOverview },
                secondaryLabel = "Back",
                onSecondary = { currentStep = OnboardingStep.TavilySetup },
                onClose = onClose,
            )

            OnboardingStep.McpOverview -> SummaryStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                message = "If you want live docs, search, or APIs later, you can connect MCP servers in Settings.",
                title = "MCP",
                icon = Icons.Rounded.Cloud,
                accent = TourBlue,
                lineOne = if (mcpServerCount == 0) {
                    "You can leave this for later."
                } else {
                    "$mcpServerCount MCP servers are already available."
                },
                lineTwo = "This is where Aether grows beyond local tools.",
                chips = listOf("Docs", "Search", "APIs"),
                primaryLabel = "Done",
                onPrimary = onClose,
                secondaryLabel = "Open settings",
                onSecondary = onExploreSettings,
                tertiaryLabel = "Back",
                onTertiary = { currentStep = OnboardingStep.SkillsOverview },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun LandingStep(
    stepIndex: Int,
    stepCount: Int,
    replayMode: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    var visible by remember(stepIndex, replayMode) { mutableStateOf(false) }
    LaunchedEffect(stepIndex, replayMode) {
        delay(180)
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TourBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            TourChromeBar(
                stepIndex = stepIndex,
                stepCount = stepCount,
                onBack = null,
                topRightLabel = if (replayMode) "Close" else "Skip",
                onTopRight = onSecondary,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, top = 4.dp, end = 28.dp, bottom = 20.dp),
            ) {
                Spacer(modifier = Modifier.weight(0.72f))
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = ContentFadeDuration,
                            easing = TourEasing,
                        )
                    ),
                    label = "landing_content",
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.aether_mark),
                            contentDescription = "Aether icon",
                            modifier = Modifier
                                .size(104.dp),
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            text = "Welcome to Aether",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TourTextPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "The on-device agent that works with everything.",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TourTextSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1.28f))
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = ContentFadeDuration,
                            delayMillis = 220,
                            easing = TourEasing,
                        )
                    ),
                    label = "landing_actions",
                ) {
                    Button(
                        onClick = onPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TourButton,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Get started")
                    }
                }
            }
        }
    }
}

@Composable
private fun TourChromeBar(
    stepIndex: Int,
    stepCount: Int,
    onBack: (() -> Unit)?,
    topRightLabel: String,
    onTopRight: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TourBackground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 14.dp),
        ) {
            StepTopBar(
                stepIndex = stepIndex,
                stepCount = stepCount,
                onBack = onBack,
                topRightLabel = topRightLabel,
                onTopRight = onTopRight,
            )
        }
    }
}

@Composable
private fun ConversationStepPage(
    stepIndex: Int,
    stepCount: Int,
    message: String,
    onBack: (() -> Unit)?,
    topRightLabel: String,
    onTopRight: () -> Unit,
    isExiting: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pageKey = remember(stepIndex, stepCount, message) { "$stepIndex/$stepCount:$message" }
    val contentVisible = rememberStepContentVisible(pageKey, message)
    val topPadding by animateDpAsState(
        targetValue = if (contentVisible) 56.dp else 168.dp,
        animationSpec = tween(
            durationMillis = MessageTravelDuration,
            easing = TourEasing,
        ),
        label = "tour_message_travel",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TourBackground),
    ) {
        AnimatedVisibility(
            visible = !isExiting,
            enter = fadeIn(animationSpec = tween(durationMillis = 0)),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = 280,
                    easing = TourEasing,
                )
            ),
            label = "step_page_visibility",
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                TourChromeBar(
                    stepIndex = stepIndex,
                    stepCount = stepCount,
                    onBack = onBack,
                    topRightLabel = topRightLabel,
                    onTopRight = onTopRight,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 28.dp, top = 12.dp, end = 28.dp, bottom = 20.dp),
                ) {
                    Spacer(modifier = Modifier.height(topPadding))
                    StreamingStepMessage(
                        playKey = pageKey,
                        text = message,
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = ContentFadeDuration,
                                delayMillis = 180,
                                easing = TourEasing,
                            )
                        ),
                        exit = fadeOut(animationSpec = tween(durationMillis = 180)),
                        label = "step_content_fade",
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            content = content,
                        )
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun ProviderSetupStep(
    stepIndex: Int,
    stepCount: Int,
    replayMode: Boolean,
    selectedProvider: LlmProvider?,
    formState: ProviderFormState,
    isFetchingModels: Boolean,
    onSelectProvider: (LlmProvider) -> Unit,
    onFetchModels: (LlmProviderConfig, (List<String>) -> Unit) -> Unit,
    onExit: () -> Unit,
    onClose: () -> Unit,
    onReturnToLanding: () -> Unit,
    onComplete: () -> Unit,
) {
    var stage by rememberSaveable(stepIndex, replayMode) { mutableStateOf(ProviderTourStage.PickProvider) }
    var isFinishing by rememberSaveable(stepIndex, replayMode) { mutableStateOf(false) }
    val provider = selectedProvider
    val isLoadingModels = formState.isFetchingModelsLocally || isFetchingModels
    val modelChoices = remember(provider, formState.baseUrl, formState.cachedModels) {
        prioritizedModelOptions(
            provider = provider,
            baseUrl = formState.baseUrl,
            cachedModels = formState.cachedModels,
        )
    }
    val canContinueFromCredentials = provider != null &&
        formState.baseUrl.trim().isNotBlank() &&
        (provider != LlmProvider.VertexExpress || formState.apiKey.trim().isNotBlank())

    val message = when (stage) {
        ProviderTourStage.PickProvider -> "First, let's choose your model provider."
        ProviderTourStage.Credentials -> "Great. Add your key and base URL. I'll fetch the models after this."
        ProviderTourStage.Model -> "Pick the model you want, and then we can go straight into chat."
    }
    val backAction: (() -> Unit)? = when (stage) {
        ProviderTourStage.PickProvider -> onReturnToLanding
        ProviderTourStage.Credentials -> { { stage = ProviderTourStage.PickProvider } }
        ProviderTourStage.Model -> { { stage = ProviderTourStage.Credentials } }
    }

    LaunchedEffect(isFinishing) {
        if (isFinishing) {
            delay(320)
            onComplete()
        }
    }

    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = message,
        onBack = backAction,
        topRightLabel = if (replayMode) "Close" else "Skip",
        onTopRight = if (replayMode) onClose else onExit,
        isExiting = isFinishing,
    ) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = ContentFadeDuration,
                        delayMillis = 160,
                        easing = TourEasing,
                    )
                ) togetherWith fadeOut(
                    animationSpec = tween(
                        durationMillis = 160,
                        easing = TourEasing,
                    )
                )
            },
            label = "provider_stage_transition",
        ) { currentStage ->
            when (currentStage) {
                ProviderTourStage.PickProvider -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ProviderStageButton(
                            label = "OpenAI",
                            subtitle = "Use OpenAI-compatible APIs",
                            provider = LlmProvider.OpenAiCompatible,
                            onClick = {
                                onSelectProvider(LlmProvider.OpenAiCompatible)
                                stage = ProviderTourStage.Credentials
                            },
                        )
                        ProviderStageButton(
                            label = "Vertex",
                            subtitle = "Use Google Cloud Vertex AI",
                            provider = LlmProvider.VertexExpress,
                            onClick = {
                                onSelectProvider(LlmProvider.VertexExpress)
                                stage = ProviderTourStage.Credentials
                            },
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "You can change this later in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TourTextSecondary,
                        )
                    }
                }

                ProviderTourStage.Credentials -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        TinyLabel(
                            text = when (provider) {
                                LlmProvider.VertexExpress -> "Using Vertex"
                                else -> "Using OpenAI"
                            },
                            color = when (provider) {
                                LlmProvider.VertexExpress -> TourBlue
                                else -> TourGreen
                            },
                        )
                        MinimalInputField(
                            label = "API key",
                            value = formState.apiKey,
                            placeholder = if (provider == LlmProvider.VertexExpress) {
                                "Required for Vertex"
                            } else {
                                "Optional"
                            },
                            onValueChange = { formState.apiKey = it },
                        )
                        MinimalInputField(
                            label = "Base URL",
                            value = formState.baseUrl,
                            placeholder = "https://...",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            onValueChange = { formState.baseUrl = it },
                        )
                        PrimaryActionButton(
                            label = if (isLoadingModels) "Loading models..." else "Next",
                            enabled = canContinueFromCredentials && !isLoadingModels,
                            onClick = {
                                formState.isFetchingModelsLocally = true
                                onFetchModels(formState.buildConfig()) { models ->
                                    val ordered = prioritizedModelOptions(
                                        provider = provider,
                                        baseUrl = formState.baseUrl,
                                        cachedModels = models,
                                    )
                                    formState.cachedModels = ordered
                                    if (ordered.isNotEmpty()) {
                                        formState.modelId = ordered.first()
                                    }
                                    formState.isFetchingModelsLocally = false
                                    stage = ProviderTourStage.Model
                                }
                            },
                            isLoading = isLoadingModels,
                        )
                    }
                }

                ProviderTourStage.Model -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            text = "I'll place the best matches first when I can find them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TourTextSecondary,
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            modelChoices.take(8).forEach { model ->
                                ModelOptionButton(
                                    label = model,
                                    selected = formState.modelId.trim().equals(model, ignoreCase = true),
                                    onClick = { formState.modelId = model },
                                )
                            }
                        }
                        MinimalInputField(
                            label = "Model",
                            value = if (modelChoices.any { it.equals(formState.modelId.trim(), ignoreCase = true) }) {
                                ""
                            } else {
                                formState.modelId
                            },
                            placeholder = "Or type your own model",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            onValueChange = { formState.modelId = it },
                        )
                        PrimaryActionButton(
                            label = "Start chat",
                            enabled = provider != null && formState.isValid,
                            onClick = { isFinishing = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TermuxStep(
    stepIndex: Int,
    stepCount: Int,
    setupState: TermuxSetupState,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefresh: () -> Unit,
) {
    var shouldAutoContinue by rememberSaveable(stepIndex) { mutableStateOf(!setupState.isReady) }
    LaunchedEffect(setupState.isReady) {
        if (shouldAutoContinue && setupState.isReady) {
            shouldAutoContinue = false
            delay(820)
            onContinue()
        }
    }

    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = "Great. Now let’s give Aether access to your device so tools can run locally.",
        onBack = null,
        topRightLabel = "Close",
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StepLead(
                icon = Icons.Rounded.Terminal,
                accent = termuxStatusColor(setupState.issue),
                title = "Termux",
                body = termuxStatusSentence(setupState),
            )
            when (setupState.issue) {
                TermuxSetupIssue.Ready -> TourActionRow(
                    primaryLabel = "Continue",
                    onPrimary = onContinue,
                    secondaryLabel = "Refresh",
                    onSecondary = onRefresh,
                )

                TermuxSetupIssue.NotInstalled -> TourActionRow(
                    primaryLabel = "Install",
                    onPrimary = onInstallTermux,
                    secondaryLabel = "Skip",
                    onSecondary = onContinue,
                )

                TermuxSetupIssue.PermissionMissing -> {
                    TourActionRow(
                        primaryLabel = "Grant access",
                        onPrimary = onRequestPermission,
                        secondaryLabel = "Skip",
                        onSecondary = onContinue,
                    )
                    SecondaryTextAction(label = "App settings", onClick = onOpenAppPermissions)
                }

                TermuxSetupIssue.ExternalAppsDisabled -> {
                    TourActionRow(
                        primaryLabel = "Termux settings",
                        onPrimary = onOpenTermuxSettings,
                        secondaryLabel = "Skip",
                        onSecondary = onContinue,
                    )
                    SecondaryTextAction(label = "Open Termux", onClick = onOpenTermux)
                }

                TermuxSetupIssue.DispatchFailed -> {
                    TourActionRow(
                        primaryLabel = "Open Termux",
                        onPrimary = onOpenTermux,
                        secondaryLabel = "Skip",
                        onSecondary = onContinue,
                    )
                    SecondaryTextAction(label = "Termux settings", onClick = onOpenTermuxSettings)
                }
            }
        }
    }
}

@Composable
private fun AgentModeAuthorizationStep(
    stepIndex: Int,
    stepCount: Int,
    initialMethod: AgentModeAuthorizationMethod,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onContinue: (Boolean, AgentModeAuthorizationMethod) -> Unit,
) {
    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = "Agent Mode is optional. It needs Root or Shizuku to control an isolated Android display.",
        onBack = onBack,
        topRightLabel = "Close",
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StepLead(
                icon = Icons.Rounded.SmartToy,
                accent = TourGreen,
                title = "Agent Mode",
                body = "Choose an authorization method, or skip this for now.",
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AgentModeStageButton(
                    label = "Shizuku",
                    subtitle = "Use the elevated Shizuku service when the app is installed.",
                    drawableRes = R.drawable.shizuku_mark,
                    onClick = { onContinue(true, AgentModeAuthorizationMethod.Shizuku) },
                )
                AgentModeStageButton(
                    label = "Root",
                    subtitle = "Use a root shell for privileged input on rooted devices.",
                    drawableRes = R.drawable.root_mark,
                    onClick = { onContinue(true, AgentModeAuthorizationMethod.Root) },
                )
                AgentModeStageButton(
                    label = "Skip",
                    subtitle = "Leave Agent Mode off and enable it later from Settings.",
                    drawableRes = R.drawable.skip_mark,
                    onClick = { onContinue(false, initialMethod) },
                )
            }
        }
    }
}

@Composable
private fun TavilyStep(
    stepIndex: Int,
    stepCount: Int,
    value: String,
    onValueChange: (String) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
) {
    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = "If you want fresher web answers later, you can add search here.",
        onBack = onBack,
        topRightLabel = "Close",
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BrandStepLead(
                drawableRes = R.drawable.tavily_mark,
                title = "Tavily",
                body = "This is optional. URL fetch already works without it.",
            )
            MinimalInputField(
                label = "API key",
                value = value,
                placeholder = "Paste it here",
                onValueChange = onValueChange,
            )
            PrimaryActionButton(
                label = "Continue",
                onClick = onContinue,
            )
        }
    }
}

@Composable
private fun SummaryStep(
    stepIndex: Int,
    stepCount: Int,
    message: String,
    title: String,
    icon: ImageVector,
    accent: Color,
    lineOne: String,
    lineTwo: String,
    chips: List<String>,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiaryLabel: String? = null,
    onTertiary: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = message,
        onBack = onTertiary,
        topRightLabel = "Close",
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StepLead(
                icon = icon,
                accent = accent,
                title = title,
                body = lineOne,
            )
            Text(
                text = lineTwo,
                style = MaterialTheme.typography.bodyMedium,
                color = TourTextSecondary,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                chips.forEach { chip ->
                    PassiveChip(label = chip)
                }
            }
            PrimaryActionButton(
                label = primaryLabel,
                onClick = onPrimary,
            )
            if (secondaryLabel != null && onSecondary != null) {
                SecondaryTextAction(
                    label = secondaryLabel,
                    onClick = onSecondary,
                )
            }
            if (tertiaryLabel != null && onTertiary != null) {
                SecondaryTextAction(
                    label = tertiaryLabel,
                    onClick = onTertiary,
                    color = TourTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun rememberStepContentVisible(
    key: Any,
    message: String,
): Boolean {
    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key, message) {
        visible = false
        delay(messageRevealDuration(message) + MessageSettleDelayMillis)
        visible = true
    }
    return visible
}

@Composable
private fun StepTopBar(
    stepIndex: Int,
    stepCount: Int,
    onBack: (() -> Unit)?,
    topRightLabel: String,
    onTopRight: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = TourTextPrimary,
                )
            }
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(stepCount) { index ->
                Box(
                    modifier = Modifier
                        .width(if (index + 1 == stepIndex) 20.dp else 7.dp)
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (index + 1 == stepIndex) TourTextPrimary else TourDivider),
                )
            }
        }

        TextButton(onClick = onTopRight) {
            Text(
                text = topRightLabel,
                color = TourTextSecondary,
            )
        }
    }
}

@Composable
private fun StreamingStepMessage(
    playKey: Any,
    text: String,
) {
    var revealed by remember(playKey, text) { mutableStateOf("") }
    LaunchedEffect(playKey, text) {
        revealed = ""
        splitRevealUnits(text).forEach { unit ->
            delay(revealUnitDelay(unit))
            revealed += unit
        }
    }
    Text(
        text = revealed.ifEmpty { " " },
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
        color = TourTextPrimary,
    )
}

@Composable
private fun StepLead(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TourTextPrimary,
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = TourTextSecondary,
        )
    }
}

@Composable
private fun BrandStepLead(
    @DrawableRes drawableRes: Int,
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandMarkBadge(drawableRes = drawableRes)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TourTextPrimary,
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = TourTextSecondary,
        )
    }
}

@Composable
private fun TinyLabel(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

@Composable
private fun ProviderStageButton(
    label: String,
    subtitle: String,
    provider: LlmProvider,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TourSurface,
            contentColor = TourTextPrimary,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProviderBrandBadge(provider = provider)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = TourTextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TourTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ProviderBrandBadge(
    provider: LlmProvider,
) {
    BrandMarkBadge(drawableRes = providerBrandDrawable(provider))
}

@DrawableRes
private fun providerBrandDrawable(provider: LlmProvider): Int = when (provider) {
    LlmProvider.OpenAiCompatible -> R.drawable.openai_mark
    LlmProvider.VertexExpress -> R.drawable.googlecloud_mark
}

@Composable
private fun BrandMarkBadge(
    @DrawableRes drawableRes: Int,
) {
    val imageSize = if (drawableRes == R.drawable.openai_mark) 30.dp else 32.dp
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = null,
            modifier = Modifier.size(imageSize),
        )
    }
}

@Composable
private fun AgentModeStageButton(
    label: String,
    subtitle: String,
    @DrawableRes drawableRes: Int,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TourSurface,
            contentColor = TourTextPrimary,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BrandMarkBadge(drawableRes = drawableRes)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = TourTextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TourTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ModelOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) TourButton else TourSurface,
            contentColor = if (selected) Color.White else TourTextPrimary,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun PassiveChip(
    label: String,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(TourSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TourTextPrimary,
        )
    }
}

@Composable
private fun MinimalInputField(
    label: String,
    value: String,
    placeholder: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TourTextSecondary,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .tourBringIntoViewOnFocus(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TourTextPrimary),
            cursorBrush = SolidColor(TourTextPrimary),
            singleLine = true,
            keyboardOptions = keyboardOptions,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 10.dp),
                ) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TourTextTertiary,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.tourBringIntoViewOnFocus(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    bringIntoViewRequester(requester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                scope.launch {
                    delay(250)
                    requester.bringIntoView()
                }
            }
        }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    onClick: () -> Unit,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TourButton,
            contentColor = Color.White,
            disabledContainerColor = TourDivider,
            disabledContentColor = TourTextSecondary,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 1.8.dp,
                color = Color.White,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
private fun TourActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryActionButton(
            label = primaryLabel,
            modifier = Modifier.weight(1f),
            onClick = onPrimary,
        )
        TextButton(
            onClick = onSecondary,
            modifier = Modifier.weight(0.62f),
        ) {
            Text(
                text = secondaryLabel,
                color = TourTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SecondaryTextAction(
    label: String,
    onClick: () -> Unit,
    color: Color = TourTextPrimary,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = color,
        )
    }
}

private fun splitRevealUnits(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val units = mutableListOf<String>()
    val builder = StringBuilder()
    text.forEach { char ->
        builder.append(char)
        val shouldSplit = char == ' ' || char == '\n' || char == '.' || char == '!' || char == '?' || char == ','
        if (shouldSplit) {
            units += builder.toString()
            builder.clear()
        }
    }
    if (builder.isNotEmpty()) {
        units += builder.toString()
    }
    return units
}

private fun revealUnitDelay(unit: String): Long {
    val trimmed = unit.trim()
    if (trimmed.isEmpty()) return 18L
    if (trimmed.length == 1 && trimmed.first() in setOf('.', ',', '!', '?')) return 180L
    return (80L + trimmed.length * 18L).coerceIn(96L, 240L)
}

private fun messageRevealDuration(message: String): Long {
    val total = splitRevealUnits(message).sumOf(::revealUnitDelay)
    return total.coerceIn(MessageMinDurationMillis, MessageMaxDurationMillis)
}

private fun prioritizedModelOptions(
    provider: LlmProvider?,
    baseUrl: String,
    cachedModels: List<String>,
): List<String> {
    val fallback = when (provider) {
        LlmProvider.OpenAiCompatible -> listOf(
            "gpt-5.4",
            "claude-4.6-sonnet",
            "gemini-3-flash",
            "gemini-3.1-pro",
        )

        LlmProvider.VertexExpress -> listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash",
        )

        null -> listOf(
            "gpt-5.4",
            "gemini-3-flash",
            "gemini-3.1-pro",
            "claude-4.6-sonnet",
        )
    }
    val patchedModels = if (
        provider == LlmProvider.VertexExpress &&
        baseUrl.trim() == LlmProvider.VertexExpress.defaultBaseUrl
    ) {
        listOf(
            "gemini-3.1-pro-preview",
            "gemini-3-flash-preview",
        )
    } else {
        emptyList()
    }
    return (cachedModels + patchedModels + fallback)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
        .sortedWith(
            compareBy<String> { preferredModelRank(it) }
                .thenBy { providerModelRank(provider, it) }
                .thenBy { it.lowercase() },
        )
}

private fun preferredModelRank(model: String): Int {
    val normalized = model.lowercase()
    return when {
        normalized.contains("gpt-5.4") -> 0
        normalized.contains("gemini-3-flash") || normalized.contains("gemini 3 flash") -> 1
        normalized.contains("gemini-3.1-pro") || normalized.contains("gemini 3.1 pro") -> 2
        normalized.contains("claude-4.6-sonnet") || normalized.contains("claude 4.6 sonnet") -> 3
        else -> 10
    }
}

private fun providerModelRank(
    provider: LlmProvider?,
    model: String,
): Int = when (provider) {
    LlmProvider.OpenAiCompatible -> when {
        model.lowercase().contains("gpt") -> 0
        model.lowercase().contains("claude") -> 1
        model.lowercase().contains("gemini") -> 2
        else -> 5
    }

    LlmProvider.VertexExpress -> when {
        model.lowercase().contains("gemini") -> 0
        else -> 5
    }

    null -> 5
}

private fun termuxStatusSentence(
    setupState: TermuxSetupState,
): String = setupState.detail.ifBlank {
    when (setupState.issue) {
        TermuxSetupIssue.Ready -> "Local tools are ready."
        TermuxSetupIssue.NotInstalled -> "Install Termux first, then come back here."
        TermuxSetupIssue.PermissionMissing -> "Grant the Termux command permission, then return here."
        TermuxSetupIssue.ExternalAppsDisabled -> "Open Termux settings and allow external apps."
        TermuxSetupIssue.DispatchFailed -> "Open Termux once, then refresh here."
    }
}

private fun termuxStatusColor(issue: TermuxSetupIssue): Color = when (issue) {
    TermuxSetupIssue.Ready -> TourGreen
    TermuxSetupIssue.NotInstalled -> TourGold
    TermuxSetupIssue.PermissionMissing -> TourGold
    TermuxSetupIssue.ExternalAppsDisabled -> TourPurple
    TermuxSetupIssue.DispatchFailed -> TourBlue
}
