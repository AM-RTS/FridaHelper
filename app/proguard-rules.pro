# FridaHelper ProGuard Rules

# Keep core model classes (used via reflection-like patterns in generators)
-keep class com.amrts.fridahelper.core.model.** { *; }
-keep class com.amrts.fridahelper.core.generator.** { *; }
-keep class com.amrts.fridahelper.core.parser.** { *; }
-keep class com.amrts.fridahelper.core.util.** { *; }
-keep class com.amrts.fridahelper.core.FridaHelperVersion { *; }

# Keep app fragments (referenced by FragmentStateAdapter)
-keep class com.amrts.fridahelper.app.** extends androidx.fragment.app.Fragment { *; }

# Standard Android keeps
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
