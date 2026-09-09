package br.com.nexo.driver.accessibility

import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySpatialTextBuilderTest {
    @Test
    fun `joins duration and distance exposed by sibling nodes`() {
        val lines = AccessibilitySpatialTextBuilder.build(
            fragments = listOf(
                fragment(text = "5 min", left = 20, top = 100, right = 100, bottom = 140),
                fragment(text = "2,1 km", left = 120, top = 102, right = 220, bottom = 140),
            ),
        )

        assertTrue(lines.contains("5 min (2,1 km)"))
    }

    @Test
    fun `uses semantic content description while preserving visible text`() {
        val lines = AccessibilitySpatialTextBuilder.build(
            fragments = listOf(
                fragment(
                    text = "R$ 37,42",
                    contentDescription = "Ganhe R$ 37,42",
                    left = 20,
                    top = 20,
                    right = 220,
                    bottom = 70,
                ),
            ),
        )

        assertTrue(lines.contains("Ganhe R$ 37,42"))
        assertTrue(lines.contains("R$ 37,42"))
    }

    @Test
    fun `normalizes metres to kilometres for parser`() {
        val lines = AccessibilitySpatialTextBuilder.build(
            fragments = listOf(
                fragment(text = "3 min", left = 20, top = 100, right = 100, bottom = 140),
                fragment(text = "850 m", left = 120, top = 100, right = 220, bottom = 140),
            ),
        )

        assertTrue(lines.contains("3 min (0,85 km)"))
    }

    @Test
    fun `normalizes hour and minute duration`() {
        val lines = AccessibilitySpatialTextBuilder.build(
            fragments = listOf(
                fragment(text = "1 h 5 min", left = 20, top = 100, right = 140, bottom = 140),
                fragment(text = "42,3 km", left = 160, top = 100, right = 280, bottom = 140),
            ),
        )

        assertTrue(lines.contains("65 min (42,3 km)"))
    }

    @Test
    fun `does not combine values from different visual rows`() {
        val lines = AccessibilitySpatialTextBuilder.build(
            fragments = listOf(
                fragment(text = "5 min", left = 20, top = 100, right = 100, bottom = 140),
                fragment(text = "2,1 km", left = 120, top = 180, right = 220, bottom = 220),
            ),
        )

        assertTrue(lines.none { it == "5 min (2,1 km)" })
    }

    private fun fragment(
        text: String? = null,
        contentDescription: String? = null,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = AccessibilityTextFragment(
        text = text,
        contentDescription = contentDescription,
        viewIdResourceName = null,
        className = "android.widget.TextView",
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        depth = 3,
        visibleToUser = true,
    )
}
