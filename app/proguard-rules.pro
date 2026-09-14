# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.ybhgl.reminder.data.**$$serializer { *; }
-keepclassmembers class com.ybhgl.reminder.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.ybhgl.reminder.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# WebDAV reflection fallback
-keepclassmembers class java.net.HttpURLConnection {
    void setRequestMethod(java.lang.String);
}
