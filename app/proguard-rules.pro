# R8 configuration for the release build.
#
# Most of what would need keeping is already covered by the libraries' own consumer rules:
# androidx.room ships rules for the generated `*_Impl` and the entities the builder looks up by
# name, androidx.work keeps `ListenableWorker`'s constructor so workers can be instantiated from a
# WorkRequest, and OkHttp needs none. What is left is below.

# Crash reports are read by a student on a recovery screen and pasted into a bug report, so keep
# the line numbers and the original file names even though R8 renames the classes.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The Room builder resolves the generated database class by name
# (`Room.databaseBuilder(…, TimetableDatabase::class.java, …)`), so it must survive shrinking.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Room generates `*_Impl` companions that the runtime reaches by reflection.
-keep class * implements androidx.room.RoomDatabase_Impl { *; }

# The app parses upstream JSON with org.json; keep the parser free of missing-class warnings.
-dontwarn org.json.**
