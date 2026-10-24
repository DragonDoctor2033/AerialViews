package com.neilturner.aerialviews.ui.helpers

import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import com.neilturner.aerialviews.testutils.ResourceXml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every slot dropdown is filled from the slot_entries and slot_values arrays by SlotHelper, so a
 * new OverlayType only shows up (and shows the right name) if the arrays line up with the enum.
 */
internal class SlotListResourcesTest {
    private val entries = ResourceXml.stringArray("values/arrays.xml", "slot_entries")
    private val values = ResourceXml.stringArray("values/arrays.xml", "slot_values")

    @Test
    fun `slot values are the overlay types in enum order`() {
        // SlotHelper.updateSummary() uses OverlayType.ordinal as the index into slot_entries
        assertEquals(OverlayType.entries.map { it.name }, values)
    }

    @Test
    fun `every slot value has a name to show`() {
        assertEquals(values.size, entries.size)
        entries.forEach { entry ->
            assertTrue(entry.startsWith("@string/"), "slot entry '$entry' should be a string resource")
        }
    }

    @Test
    fun `every slot name is a defined string`() {
        val defined = ResourceXml.defaultStringNames()
        entries.forEach { entry ->
            val name = entry.removePrefix("@string/")
            assertTrue(name in defined, "slot entry '$name' has no string")
        }
    }

    @Test
    fun `slot values can be turned back into overlay types`() {
        values.forEach { value ->
            assertEquals(value, OverlayType.valueOf(value).name)
        }
    }

    @Test
    fun `analog clock can be picked for a slot, next to the digital clock`() {
        val index = values.indexOf(OverlayType.ANALOG_CLOCK.name)

        assertEquals("ANALOG_CLOCK", values[index])
        assertEquals(OverlayType.ANALOG_CLOCK.ordinal, index)
        assertEquals("@string/slot_analog_clock", entries[index])

        // A separate overlay, so the digital clock is still on offer
        assertEquals("@string/slot_clock", entries[values.indexOf(OverlayType.CLOCK.name)])
        assertTrue(OverlayType.CLOCK != OverlayType.ANALOG_CLOCK)
    }

    @Test
    fun `slot dropdowns exist for every corner, upper and lower`() {
        val top = ResourceXml.elementsWithAttribute("xml/settings_overlays_row_top.xml", "app:key")
        val bottom = ResourceXml.elementsWithAttribute("xml/settings_overlays_row_bottom.xml", "app:key")
        val keys = (top + bottom).map { it.getAttribute("app:key") }

        // Each of the 8 slots is its own dropdown, and SlotHelper gives every one of them the same list
        assertEquals(SlotType.entries.map { it.name.lowercase() }.sorted(), keys.sorted())
        assertEquals(
            setOf(
                "slot_top_left1",
                "slot_top_left2",
                "slot_top_right1",
                "slot_top_right2",
                "slot_bottom_left1",
                "slot_bottom_left2",
                "slot_bottom_right1",
                "slot_bottom_right2",
            ),
            keys.toSet(),
        )
        (top + bottom).forEach { assertEquals("ListPreference", it.tagName) }
    }
}
