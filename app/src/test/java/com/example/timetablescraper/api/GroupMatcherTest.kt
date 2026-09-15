package com.example.timetablescraper.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Group ("subgroup") semantics — replaces `GroupFilteringTest`, which re-implemented the
 * filtering rules inline instead of calling production code and therefore passed even when
 * production filtering was wrong.
 */
class GroupMatcherTest {

    // ── parsing ────────────────────────────────────────────────────────────────

    @Test
    fun `blank and null groups mean the whole cohort`() {
        assertEquals(emptySet<String>(), GroupMatcher.parse(null))
        assertEquals(emptySet<String>(), GroupMatcher.parse(""))
        assertEquals(emptySet<String>(), GroupMatcher.parse("   "))
        assertTrue(GroupMatcher.appliesToAll(""))
        assertTrue(GroupMatcher.appliesToAll(null))
    }

    @Test
    fun `a single group parses to a single token`() {
        assertEquals(setOf("G1"), GroupMatcher.parse("G1"))
        assertEquals(setOf("G1"), GroupMatcher.parse(" g1 "))
    }

    @Test
    fun `compound groups parse across every observed separator dialect`() {
        val expected = setOf("G1", "G2")
        assertEquals(expected, GroupMatcher.parse("G1 + G2"))   // the parser's canonical form
        assertEquals(expected, GroupMatcher.parse("G1+G2"))
        assertEquals(expected, GroupMatcher.parse("G1,G2"))
        assertEquals(expected, GroupMatcher.parse("G1,G2"))
        assertEquals(expected, GroupMatcher.parse("G1; G2"))
        assertEquals(expected, GroupMatcher.parse("G1 & G2"))
    }

    @Test
    fun `separator only input is not a group`() {
        assertTrue(GroupMatcher.appliesToAll("+"))
        assertTrue(GroupMatcher.appliesToAll(" , "))
    }

    @Test
    fun `format produces the canonical alphabetical display form`() {
        assertEquals("G1 + G2", GroupMatcher.format("G2,G1"))
        assertEquals("A + C + G1", GroupMatcher.format("G1 + C + A"))
        assertEquals("", GroupMatcher.format(""))
    }

    // ── matching ───────────────────────────────────────────────────────────────

    @Test
    fun `no filter selected shows every session`() {
        assertTrue(GroupMatcher.matches("G1", null))
        assertTrue(GroupMatcher.matches("", null))
        assertTrue(GroupMatcher.matches("G2", ""))
        assertTrue(GroupMatcher.matches("", "  "))
    }

    @Test
    fun `a selected group hides sessions belonging to other groups`() {
        assertTrue(GroupMatcher.matches("G1", "G1"))
        assertFalse(GroupMatcher.matches("G2", "G1"))
    }

    @Test
    fun `P0 plenary sessions stay visible when a subgroup is selected`() {
        // Regression (high): the old inline filter was `group.split("+").any { it == selected }`,
        // so a blank group produced the token "" which never equals "G1" — every all-cohort
        // lecture vanished the moment a student picked their subgroup. The classes that apply
        // to everyone were exactly the ones filtered out.
        assertTrue(GroupMatcher.matches("", "G1"))
        assertTrue(GroupMatcher.matches(null, "G1"))
        assertTrue(GroupMatcher.matches("   ", "G2"))
    }

    @Test
    fun `matching is case insensitive and whitespace tolerant on both sides`() {
        assertTrue(GroupMatcher.matches("g1", "G1"))
        assertTrue(GroupMatcher.matches(" G1 ", "g1"))
        assertTrue(GroupMatcher.matches("G1+G2", " g2 "))
    }

    @Test
    fun `a shared session matches either of its groups`() {
        assertTrue(GroupMatcher.matches("G1 + G2", "G1"))
        assertTrue(GroupMatcher.matches("G1 + G2", "G2"))
        assertFalse(GroupMatcher.matches("G1 + G2", "G3"))
    }

    @Test
    fun `multi select is a union and keeps plenary sessions`() {
        val selected = setOf("G1", "MLAI")
        assertTrue(GroupMatcher.matchesAny("G1", selected))
        assertTrue(GroupMatcher.matchesAny("MLAI", selected))
        assertFalse(GroupMatcher.matchesAny("G2", selected))
        assertTrue(GroupMatcher.matchesAny("", selected))
        assertTrue(GroupMatcher.matchesAny("G3", emptySet()))
    }

    @Test
    fun `a path is one group, so its segments are not groups in their own right`() {
        // Upstream names carry several segments ("Y3/C/G1"), but the segments are parts of one
        // name. Treating them as separate groups made two unrelated cohorts match: picking
        // TU859/MLAI/G2 pulled in TU859/CS/G2's classes because they share "TU859" and "G2".
        assertTrue(GroupMatcher.matches("Y3/C/G1", "Y3/C/G1"))
        assertTrue(GroupMatcher.matches("Y3/C/G1", " y3/c/g1 "))
        assertFalse(GroupMatcher.matches("Y3/C/G1", "G1"))
        assertFalse(GroupMatcher.matches("Y3/C/G1", "C"))
        assertFalse(GroupMatcher.matches("TU859/MLAI/G2", "TU859/CS/G2"))
    }
}
