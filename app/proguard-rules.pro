# BlueWhisper ProGuard rules
# AGP's getDefaultProguardFile("proguard-android-optimize.txt") covers most
# of what's needed. App-specific keeps go below.

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Gson — keep model classes that are serialized
-keep class com.bluewhisper.domain.model.** { *; }

# Compose stability
-keep class androidx.compose.runtime.** { *; }

# Kotlinx coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
