-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.alcint.pargelium.CustomAudioProcessor { *; }

-keepattributes Signature, Exceptions, *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.alcint.pargelium.PlaylistModel { *; }
-keep class com.alcint.pargelium.AudioTrack { *; }
-keep class com.alcint.pargelium.LyricLine { *; }
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class coil.** { *; }
-dontwarn coil.**
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}