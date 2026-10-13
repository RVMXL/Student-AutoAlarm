# Kotlinx Serialization (DTO расписания КГЭУ)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ru.uniautoalarm.kgeu.**$$serializer { *; }
-keepclassmembers class ru.uniautoalarm.kgeu.** {
    *** Companion;
}
-keepclasseswithmembers class ru.uniautoalarm.kgeu.** {
    kotlinx.serialization.KSerializer serializer(...);
}
