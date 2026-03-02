package com.thingspeak.monitor.feature.channel.data.mapper

import com.thingspeak.monitor.feature.channel.data.remote.dto.FeedEntryDto
import com.thingspeak.monitor.feature.channel.domain.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedMappersTest {

    @Test
    fun `toEntity maps all fields correctly`() {
        val dto = FeedEntryDto(
            entryId = 100,
            createdAt = "2026-02-24T12:00:00Z",
            field1 = "25.5",
            field2 = "nan"
        )
        val entity = dto.toEntity(channelId = 1L)

        assertEquals(1L, entity.channelId)
        assertEquals(100L, entity.entryId)
        assertEquals("25.5", entity.field1)
        assertEquals("nan", entity.field2)
    }

    @Test
    fun `toDomain maps fields to map correctly`() {
        val entity = com.thingspeak.monitor.feature.channel.data.local.FeedEntryEntity(
            channelId = 1L,
            entryId = 100L,
            createdAt = "2026-02-24T12:00:00Z",
            field1 = "25.5",
            field2 = "30.0"
        )
        val domain = entity.toDomain()

        assertEquals(100L, domain.entryId)
        assertEquals("25.5", domain.fields[1])
        assertEquals("30.0", domain.fields[2])
        assertEquals(2, domain.fields.size)
    }
}
