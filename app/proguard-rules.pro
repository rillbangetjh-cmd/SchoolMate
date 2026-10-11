# Project-specific R8 rules. Room, DataStore and Navigation ship their own consumer rules.
# Keep @Serializable route classes used by type-safe Navigation Compose.
-keepclassmembers @kotlinx.serialization.Serializable class id.schoolmate.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
