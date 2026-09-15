package com.example.timetablescraper.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the APK download allowlist.
 *
 * The URL comes from a remote JSON response and its value decides what the install flow acts on,
 * so the interesting cases are the ones that look almost right: a lookalike host, a downgraded
 * scheme, or a host that merely *contains* the trusted name.
 */
class TrustedApkUrlTest {

    @Test
    fun `the hosts the updater actually uses are accepted`() {
        // Verified live: the Contents API for this repo returns raw.githubusercontent.com.
        assertTrue(UpdateChecker.isTrustedApkUrl(
            "https://raw.githubusercontent.com/Izzeddin-Hammad/TUD-TimeTable-APP/main/releases/TimeTable-v1.27-debug.apk"
        ))
        assertTrue(UpdateChecker.isTrustedApkUrl("https://github.com/o/r/releases/download/v1/a.apk"))
        assertTrue(UpdateChecker.isTrustedApkUrl("https://objects.githubusercontent.com/a/b.apk"))
        assertTrue(UpdateChecker.isTrustedApkUrl("https://github-releases.githubusercontent.com/a.apk"))
    }

    @Test
    fun `a downgraded scheme is refused`() {
        assertFalse(UpdateChecker.isTrustedApkUrl("http://raw.githubusercontent.com/a.apk"))
        assertFalse(UpdateChecker.isTrustedApkUrl("file:///data/local/tmp/evil.apk"))
        assertFalse(UpdateChecker.isTrustedApkUrl("ftp://github.com/a.apk"))
    }

    @Test
    fun `a lookalike host is not accepted by suffix matching`() {
        // Ends with "-githubusercontent.com", not ".githubusercontent.com".
        assertFalse(UpdateChecker.isTrustedApkUrl("https://evil-githubusercontent.com/a.apk"))
        // Contains the trusted name, but is a different domain.
        assertFalse(UpdateChecker.isTrustedApkUrl("https://githubusercontent.com.evil.example/a.apk"))
        assertFalse(UpdateChecker.isTrustedApkUrl("https://notgithub.com/a.apk"))
    }

    @Test
    fun `junk is refused rather than throwing`() {
        assertFalse(UpdateChecker.isTrustedApkUrl(""))
        assertFalse(UpdateChecker.isTrustedApkUrl("not a url"))
        assertFalse(UpdateChecker.isTrustedApkUrl("https://"))
    }
}
