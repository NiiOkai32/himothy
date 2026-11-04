-keepattributes Signature
-keepattributes *Annotation*
-keep class com.campusfit.data.remote.** { *; }

# Profile is persisted with Gson; preserve the serialized schema in minified builds.
-keep class com.campusfit.domain.Profile { *; }

# Offline plans, keyframes and run checkpoints use stable Gson field names.
-keep class com.campusfit.domain.workouts.** { *; }

-keep class com.campusfit.domain.tracking.** { *; }

-keep class com.campusfit.domain.social.** { *; }
