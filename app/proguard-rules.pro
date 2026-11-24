# Keep LiteRT CompiledModel and JNI signatures intact in release builds
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn com.google.ai.edge.litert.**
