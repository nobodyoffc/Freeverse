# Add project specific ProGuard rules here.

# Keep Bitcoinj classes
-keep class org.bitcoinj.** { *; }

# Keep Netty classes
-keep class io.netty.** { *; }

# Keep ZXing classes
-keep class com.google.zxing.** { *; }

# Keep Gson classes
-keep class com.google.gson.** { *; }

# Keep LevelDB classes
-keep class org.iq80.leveldb.** { *; }

# Keep your SDK classes
-keep class com.freeverse.fcsdk.** { *; } 