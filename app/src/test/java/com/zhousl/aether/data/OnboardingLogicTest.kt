package com.zhousl.aether.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingLogicTest {
    @Test
    fun freshInstallLaunchesOnboarding() {
        val settings = AppSettings()

        assertTrue(settings.shouldLaunchOnboarding())
        assertFalse(settings.isOnboardingComplete())
    }

    @Test
    fun seenButIncompleteSetupDoesNotRelaunchAndStillShowsResumeBanner() {
        val settings = AppSettings(
            onboardingSeenVersion = CurrentOnboardingVersion,
            onboardingCompletedVersion = 0,
        )

        assertFalse(settings.shouldLaunchOnboarding())
        assertTrue(
            shouldShowResumeSetupBanner(
                settings = settings,
                messageCount = 0,
                draftInput = "",
                hasDraftAttachments = false,
            )
        )
    }

    @Test
    fun completedOnboardingSuppressesResumeBannerAndFurtherCompletionTriggers() {
        val settings = AppSettings(
            onboardingSeenVersion = CurrentOnboardingVersion,
            onboardingCompletedVersion = CurrentOnboardingVersion,
        )

        assertTrue(settings.isOnboardingComplete())
        assertFalse(
            shouldShowResumeSetupBanner(
                settings = settings,
                messageCount = 0,
                draftInput = "",
                hasDraftAttachments = false,
            )
        )
        assertFalse(
            shouldMarkOnboardingCompleted(
                settings = settings,
                isSuccessfulAssistantReply = true,
            )
        )
    }

    @Test
    fun providerValidationRequiresVertexApiKeyButNotOpenAiCompatibleApiKey() {
        assertTrue(
            isProviderSetupValid(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "",
                baseUrl = "https://api.openai.com/v1",
                modelId = "gpt-5.4",
            )
        )
        assertFalse(
            isProviderSetupValid(
                provider = LlmProvider.VertexExpress,
                apiKey = "",
                baseUrl = "https://aiplatform.googleapis.com/v1",
                modelId = "gemini-2.5-flash",
            )
        )
    }

    @Test
    fun followUpTourCardRequiresWaitingStateAndSuccessfulReply() {
        assertTrue(
            shouldRevealFollowUpTourCard(
                isAwaitingFollowUpTour = true,
                isSuccessfulAssistantReply = true,
            )
        )
        assertFalse(
            shouldRevealFollowUpTourCard(
                isAwaitingFollowUpTour = false,
                isSuccessfulAssistantReply = true,
            )
        )
        assertFalse(
            shouldRevealFollowUpTourCard(
                isAwaitingFollowUpTour = true,
                isSuccessfulAssistantReply = false,
            )
        )
    }

    @Test
    fun onlySuccessfulReplyCompletesIncompleteOnboarding() {
        val settings = AppSettings(
            onboardingSeenVersion = CurrentOnboardingVersion,
            onboardingCompletedVersion = 0,
        )

        assertTrue(
            shouldMarkOnboardingCompleted(
                settings = settings,
                isSuccessfulAssistantReply = true,
            )
        )
        assertFalse(
            shouldMarkOnboardingCompleted(
                settings = settings,
                isSuccessfulAssistantReply = false,
            )
        )
    }
}
