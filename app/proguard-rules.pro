# Keep kotlinx.serialization & Room/Hilt models
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn kotlin.reflect.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# Preserve WebView JavascriptInterface methods and enclosing anonymous classes
-keep,allowobfuscation class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve kotlinx.serialization serializers & models in R8 full mode
-keepclassmembers class com.myfun.funclass.** {
    *** Companion;
}
-keepclasseswithmembers class com.myfun.funclass.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.myfun.funclass.**$$serializer { *; }
