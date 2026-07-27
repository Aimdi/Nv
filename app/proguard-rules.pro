# kotlinx.serialization
#
# Serializers are generated as nested classes and looked up reflectively from the companion, so
# R8 has to be told to keep them. Without this, backup import/export and pack-manifest parsing
# fail only in release builds.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static <1>$Companion Companion;
    static **$* *;
    <fields>;
}
-keepclasseswithmembers class ** {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    public static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# The app's own serialized models, kept by name so a shrunken build still round-trips the same
# JSON field names that an existing backup file uses.
-keep,includedescriptorclasses class dev.naicompanion.app.data.backup.** { *; }
-keep,includedescriptorclasses class dev.naicompanion.app.data.packs.Pack* { *; }
-keep,includedescriptorclasses class dev.naicompanion.app.data.packs.InstalledPack* { *; }

# Room generates implementations that are only referenced reflectively at database creation.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# OkHttp ships its own consumer rules; these silence optional compile-time dependencies.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
