package com.thingspeak.monitor.feature.widget

import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for latest-entry selection used by both widget types.
 *
 * BUG: Both ValueGrid and Chart widgets displayed "Measured: HH:mm" based on
 * the entry with the highest entryId, not the newest createdAt. ThingSpeak can
 * return entries out of order (wind channel 2057381: entryId 520047@23:45 vs
 * 520043@23:53), so the widget showed a stale measurement even though newer
 * data existed. The DAO also contains a corrupt future entry (year 2106,
 * entryId 414905) which must never be selected as "latest".
 *
 * Fix: select the latest entry by parsed createdAt, ignoring future/unparseable
 * timestamps, for both widget paths (glance chart + value grid).
 */
class WidgetLatestEntrySelectionTest {

    private val now = Instant.parse("2026-08-02T00:00:00Z")

    private fun entry(entryId: Long, createdAt: String, fieldValues: Map<Int, String> = emptyMap()): FeedEntry =
        FeedEntry(entryId = entryId, createdAt = createdAt, fields = fieldValues)

    // -------------------------------------------------------------------------
    // Bug Condition
    // -------------------------------------------------------------------------

    /**
     * Bug Condition: highest entryId is NOT the newest data. Channel 2057381:
     * entryId 520047 createdAt 2026-08-01T23:45:08Z (voltage only) vs
     * entryId 520043 createdAt 2026-08-01T23:53:20Z (wind 3.7). The widget
     * showed "Measured: 23:45" although 23:53 was newer.
     */
    @Test
    fun bugCondition_highEntryId_withOlderTimestamp_isNotSelected() {
        val wind = entry(520043, "2026-08-01T23:53:20Z", mapOf(1 to "3.7"))
        val voltage = entry(520047, "2026-08-01T23:45:08Z", mapOf(3 to "26.27"))

        val latest = WidgetUtils.selectLatestEntry(listOf(wind, voltage), now)

        assertEquals(520043L, latest?.entryId)
        assertEquals("3.7", latest?.fields?.get(1))
    }

    /**
     * Bug Condition: a corrupt future timestamp (year 2106) must never win,
     * otherwise the widget would show "Measured: 2106".
     */
    @Test
    fun bugCondition_futureTimestamp_isIgnored() {
        val wind = entry(520043, "2026-08-01T23:53:20Z", mapOf(1 to "3.7"))
        val corrupt = entry(414905, "2106-02-07T06:28:15Z")

        val latest = WidgetUtils.selectLatestEntry(listOf(corrupt, wind), now)

        assertEquals(520043L, latest?.entryId)
    }

    @Test
    fun allEntriesInFuture_returnsNull() {
        val corrupt = entry(414905, "2106-02-07T06:28:15Z")

        assertNull(WidgetUtils.selectLatestEntry(listOf(corrupt), now))
    }

    // -------------------------------------------------------------------------
    // Selection Behavior
    // -------------------------------------------------------------------------

    @Test
    fun picksNewestCreatedAt_amongValidEntries() {
        val old = entry(100, "2026-08-01T20:00:00Z", mapOf(1 to "1.0"))
        val mid = entry(200, "2026-08-01T22:00:00Z", mapOf(1 to "2.0"))
        val newest = entry(300, "2026-08-01T23:59:00Z", mapOf(1 to "3.0"))

        val latest = WidgetUtils.selectLatestEntry(listOf(mid, newest, old), now)

        assertEquals(300L, latest?.entryId)
    }

    @Test
    fun entryExactlyAtNow_isSelected() {
        val atNow = entry(500, "2026-08-02T00:00:00Z", mapOf(1 to "5.0"))

        val latest = WidgetUtils.selectLatestEntry(listOf(atNow), now)

        assertEquals(500L, latest?.entryId)
    }

    @Test
    fun unparseableTimestamp_isIgnored() {
        val garbage = entry(1, "not-a-date")
        val valid = entry(2, "2026-08-01T22:00:00Z")

        val latest = WidgetUtils.selectLatestEntry(listOf(garbage, valid), now)

        assertEquals(2L, latest?.entryId)
    }

    @Test
    fun emptyList_returnsNull() {
        assertNull(WidgetUtils.selectLatestEntry(emptyList(), now))
    }
}
