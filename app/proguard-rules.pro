# Project-specific ProGuard rules can be added here when needed.

# com.rosan.app_process intentionally references hidden Android framework
# classes that are present on-device but unavailable to R8's android.jar.
-dontwarn android.app.ActivityThread
-dontwarn android.app.ContextImpl
-dontwarn android.app.LoadedApk
