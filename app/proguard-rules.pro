# Keep launcher entry points referenced from the manifest.
-keep class com.acousticfish.wheelielauncher.WheelieApp { <init>(); }
-keep class com.acousticfish.wheelielauncher.HomeActivity { <init>(); }
-keep class com.acousticfish.wheelielauncher.media.MediaNotificationListener { <init>(); }

# Icon-pack discovery uses Class.forName on *other* apps' R$drawable; no app keep needed.
# Compose / Coroutines / DataStore / Coil ship their own consumer keep rules.
