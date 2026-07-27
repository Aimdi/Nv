# Keep kotlinx.serialization generated serializers (used via reflection-free
# @Serializable, but keep the Companion accessors for safety with R8 full mode).
-keepclassmembers class com.nai.promptcompanion.** {
    *** Companion;
}
-keepclasseswithmembers class com.nai.promptcompanion.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room + SQLDelight style generated code is kept automatically by AAR consumer rules.
