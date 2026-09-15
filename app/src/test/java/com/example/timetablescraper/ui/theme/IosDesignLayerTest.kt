package com.example.timetablescraper.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the app on one colour and type layer.
 *
 * [TimetableScraperTheme] still populates the Material 3 slots, and that is deliberate: stock
 * Material components that were never restyled — menus, dialogs, dropdowns, text fields — then
 * land on the palette instead of Material's purple. That indirection is for third-party
 * components. When our own screens use it, two things go wrong: a reader can no longer tell which
 * token is in play (`colorScheme.tertiary` is the palette's purple; nothing at the call site says
 * so), and the layers drift — which is how a screen ends up on a hard-coded Material hue while
 * the rest of the app is on the iOS palette, as the session-type table did.
 *
 * The rules, for everything under the app's main source set:
 *  - screens and components read `IosTheme.colors` / `IosType`, never `MaterialTheme.colorScheme`
 *    or `MaterialTheme.typography`;
 *  - hex literals live only in the palette file.
 *
 * `Theme.kt` and `Type.kt` are exempt from the first rule because defining that mapping is
 * exactly their job.
 */
class IosDesignLayerTest {

    private val sourceRoot: File = findSourceRoot()

    private fun findSourceRoot(): File {
        // Under Gradle the working directory is the module (app/); tolerate a run from the repo root.
        val cwd = File("").absoluteFile
        val candidates = listOf(
            File(cwd, "src/main/java/com/example/timetablescraper"),
            File(cwd, "app/src/main/java/com/example/timetablescraper"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: throw AssertionError("Cannot locate the main source set from ${cwd.path}")
    }

    /**
     * A line that is inside a comment mentions an API without using it, so drop comments before
     * matching. A text string containing "//" would be truncated here, which can only ever cause
     * a false pass, never a false failure.
     */
    private fun codeOnly(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("*") || trimmed.startsWith("/*")) return ""
        return raw.substringBefore("//")
    }

    private fun violations(predicate: (File, String) -> String?): List<String> =
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, raw ->
                    predicate(file, codeOnly(raw))?.let { "${file.relativeTo(sourceRoot).path}:${index + 1} $it" }
                }
            }
            .toList()

    @Test
    fun `screens and components read the iOS layer, not the Material slots`() {
        val exempt = setOf("Theme.kt", "Type.kt")
        val found = violations { file, line ->
            if (file.name in exempt) return@violations null
            when {
                line.contains("MaterialTheme.colorScheme.") ->
                    "uses MaterialTheme.colorScheme — read IosTheme.colors instead"
                line.contains("MaterialTheme.typography.") ->
                    "uses MaterialTheme.typography — read IosType instead"
                else -> null
            }
        }
        assertTrue(
            "Our own screens must go through the iOS layer:\n" + found.joinToString("\n"),
            found.isEmpty(),
        )
    }

    @Test
    fun `hex colours live only in the palette`() {
        val found = violations { file, line ->
            if (file.name == "Color.kt") return@violations null
            if (line.contains("Color(0x")) "hard-codes a colour — add a token to IosColors" else null
        }
        assertTrue(
            "Colours must come from the palette:\n" + found.joinToString("\n"),
            found.isEmpty(),
        )
    }

    @Test
    fun `the guard actually inspects the sources it claims to`() {
        // Without this, a rename or a wrong working directory would turn the two rules above into
        // no-ops that still report success.
        val files = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("expected to find the app's Kotlin sources under ${sourceRoot.path}", files.size > 20)
        assertTrue(
            "expected the palette file to be among them",
            files.any { it.name == "Color.kt" },
        )
    }
}
