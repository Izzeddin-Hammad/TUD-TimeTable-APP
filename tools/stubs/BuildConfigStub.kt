// Compile-only stub for the BuildConfig class AGP generates at build time.
//
// `tools/jvm-test-harness.sh --compile` uses this so files that read BuildConfig (the in-app
// update checker) can be type-checked without running a full Gradle/AGP build. It is NOT part of
// the app: it lives under tools/ and is never on the app source set, so the real generated
// BuildConfig always wins in a real build.
package com.example.timetablescraper

object BuildConfig {
    const val APPLICATION_ID: String = "com.example.timetablescraper"
    const val BUILD_TYPE: String = "debug"
    const val DEBUG: Boolean = true
    const val VERSION_CODE: Int = 22
    const val VERSION_NAME: String = "1.22"
}
