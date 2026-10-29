package com.example

import com.example.lib.CommuteFormatters
import com.example.lib.DataSourceLabel
import com.example.lib.Fares
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun formatDuration_formatsSecondsAcrossAllRanges() {
        assertEquals("4 min", CommuteFormatters.formatDuration(240))
        assertEquals("59 min", CommuteFormatters.formatDuration(3540))
        assertEquals("1 hr", CommuteFormatters.formatDuration(3600))
        assertEquals("1 hr 11 min", CommuteFormatters.formatDuration(4260))
        assertEquals("1 d", CommuteFormatters.formatDuration(86400))
        assertEquals("1 d 2 hr", CommuteFormatters.formatDuration(93600))
    }

    @Test
    fun formatVerifiedFareDisplay_handlesOfficialEstimatedAndUnavailableFares() {
        val official = Fares.formatVerifiedFareDisplay(totalInr = 40, isEstimate = false, status = "scheduled")
        assertEquals(40, official.costInr)
        assertEquals("₹40", official.display)
        assertFalse(official.isEstimate)

        val estimated = Fares.formatVerifiedFareDisplay(totalInr = 95, isEstimate = true, status = "estimate")
        assertEquals(95, estimated.costInr)
        assertEquals("₹95 (Estimated)", estimated.display)
        assertTrue(estimated.isEstimate)

        val unavailable = Fares.formatVerifiedFareDisplay(totalInr = null, isEstimate = false, status = "unavailable")
        assertEquals(0, unavailable.costInr)
        assertEquals("Fare unavailable", unavailable.display)
        assertFalse(unavailable.isEstimate)

        assertEquals(DataSourceLabel.LIVE, Fares.mapEnvelopeStatusToDataLabel("live"))
        assertEquals(DataSourceLabel.TIMETABLE, Fares.mapEnvelopeStatusToDataLabel("scheduled"))
        assertEquals(DataSourceLabel.ESTIMATED, Fares.mapEnvelopeStatusToDataLabel("estimate"))
        assertEquals(DataSourceLabel.STALE, Fares.mapEnvelopeStatusToDataLabel("stale"))
        assertEquals(DataSourceLabel.UNVERIFIED, Fares.mapEnvelopeStatusToDataLabel("unverified"))
        assertEquals(DataSourceLabel.UNAVAILABLE, Fares.mapEnvelopeStatusToDataLabel("unavailable"))
    }
}
