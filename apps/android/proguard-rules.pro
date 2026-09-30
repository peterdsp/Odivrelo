# Odivrelo release shrinking rules.
#
# The aim is to keep the release build genuinely shrunk and obfuscated while
# keeping the few things that are looked up by name at runtime. Every rule below
# names why it is here; a rule with no reason is a rule nobody can ever remove.

# -- kotlinx.serialization ----------------------------------------------------
# Serializers are found through a generated companion and through reflection on
# the annotation, so the generated members have to survive. Without these the
# application decodes nothing and fails only at runtime, on a real device, after
# the tests have passed.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# The contract model itself. These types are the product's agreement with the
# server, and they are decoded by name.
-keep class dev.peterdsp.odivrelo.core.model.** { *; }
-keep class dev.peterdsp.odivrelo.features.** { *; }

# -- SQLDelight and SQLite ----------------------------------------------------
# The Android driver reaches the framework SQLite classes through androidx.sqlite,
# which is annotation driven.
-keep class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**
-keep class androidx.sqlite.db.** { *; }

# -- Ktor and OkHttp ----------------------------------------------------------
# Ktor loads its engine through a service loader, so the engine factory has to
# keep its name. OkHttp references optional Conscrypt, BouncyCastle and OpenJSSE
# providers that are simply not on the classpath here.
-keep class io.ktor.client.engine.okhttp.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# -- Coroutines ---------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# -- Compose ------------------------------------------------------------------
# Compose ships its own consumer rules; this only silences the tooling classes
# that are deliberately absent from a release build.
-dontwarn androidx.compose.ui.tooling.**

# -- Tink, behind EncryptedFile and EncryptedSharedPreferences ----------------
# Tink is annotated with Error Prone's compile-time annotations, which are not
# on any runtime classpath and never will be. R8 treats the dangling references
# as missing classes and refuses to finish. They are annotations, so nothing
# looks them up at runtime and discarding them is safe; silencing them here is
# narrower than the blanket -dontwarn a generated missing_rules.txt would add.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Tink also carries an optional cloud key-downloader that pulls in the Google
# HTTP client and Joda-Time. Nothing in Odivrelo touches remote key material:
# the wallet's key is generated on the device and never leaves the Keystore. So
# those classes are absent on purpose, and the whole KeysDownloader path is left
# to be shrunk away rather than kept.
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# What androidx.security actually uses: the proto messages Tink deserialises its
# own keyset with, which are found reflectively.
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}

# -- Crash reports ------------------------------------------------------------
# Line numbers are kept and the original file name is hidden, so a stack trace
# from a beta tester can be de-obfuscated with the mapping file without the trace
# itself leaking the source layout.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# -- Deliberately not kept ----------------------------------------------------
# There is no analytics library, no crash reporter and no advertising identifier
# to keep rules for, and that is the intended state rather than an omission.
