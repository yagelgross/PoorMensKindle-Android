# 1. Essential Metadata (Required for Reflection)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# 2. Retrofit + KOTLIN COROUTINES (This fixes the suspend function crash!)
-keep,allowobfuscation,allowshrinking interface kotlin.coroutines.Continuation
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# 3. Gson TypeTokens (Required for List<T> parsing)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }

# 4. Protect Your API & Models
-keep class com.PoorMenKindle.android.network.** { *; }

# 5. Ignore the EPUB XML Warning
-dontwarn org.xmlpull.v1.**