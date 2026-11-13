# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class app.tidyphone.**$$serializer { *; }
-keepclassmembers class app.tidyphone.** { *** Companion; }
-keepclasseswithmembers class app.tidyphone.** { kotlinx.serialization.KSerializer serializer(...); }
# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
