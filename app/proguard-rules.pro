# Stack trace leggibili nei crash (logcat) senza bisogno del mapping.
-keepattributes SourceFile,LineNumberTable,*Annotation*,InnerClasses,Signature
-renamesourcefileattribute SourceFile

# Ponte JavaScript della WebView: i metodi annotati devono restare con il loro nome.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# kotlinx.serialization: modelli serializzati (regole dei siti, cache dei risultati)
-keepclassmembers @kotlinx.serialization.Serializable class com.eroideches.shopai.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.eroideches.shopai.**$$serializer { *; }
-dontwarn org.jspecify.annotations.**
-dontwarn com.google.re2j.**
