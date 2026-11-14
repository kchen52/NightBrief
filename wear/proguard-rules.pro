# kotlinx.serialization: keep serializers and companions for @Serializable models
# shared with the phone through :data (AppState, gear, sites, ...).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# AndroidX Wear tiles and protolayout used by the tile service and glance screen.
-keep class androidx.wear.tiles.** { *; }
-keep class androidx.wear.protolayout.** { *; }

# Play services Wearable (Data Layer) used to read the phone's published snapshot.
-keep class com.google.android.gms.wearable.** { *; }

# Manifest-declared entry points are kept as R8 roots; keep their public surface.
-keep class app.nightbrief.wear.WearActivity { *; }
-keep class app.nightbrief.wear.NightBriefTileService { *; }
