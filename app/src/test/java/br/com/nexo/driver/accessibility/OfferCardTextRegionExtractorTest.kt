package br.com.nexo.driver.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferCardTextRegionExtractorTest {
    @Test
    fun `Uber card excludes map balance and keeps offer fields`() {
        val result = OfferCardTextRegionExtractor.extract(
            rawLines = listOf(
                "R$ 0,00",
                "Mapa e zonas",
                "UberX",
                "R$ 13,58",
                "R$ 1,29/km est.",
                "4,89 (245)",
                "3 min (1.2 km)",
                "Retirada",
                "19 minutos (9.3 km)",
                "Destino",
                "Selecionar",
                "Texto fora do card",
            ),
            layoutHint = "uber",
        )

        assertNotNull(result)
        assertEquals("UberX", result!!.first())
        assertFalse(result.contains("R$ 0,00"))
        assertFalse(result.contains("Texto fora do card"))
        assertTrue(result.contains("R$ 13,58"))
    }

    @Test
    fun `99 standard card keeps two route legs`() {
        val result = OfferCardTextRegionExtractor.extract(
            rawLines = listOf(
                "Mapa",
                "Pgto. no app",
                "R$ 8,50",
                "R$ 1,50/km",
                "4,96 · 112 corridas · Perfil Essencial",
                "5min (1,6km)",
                "Retirada",
                "6min (4,1km)",
                "Destino",
            ),
            layoutHint = "99",
        )

        assertNotNull(result)
        assertEquals(2, result!!.count { it.contains("min") })
    }

    @Test
    fun `partial balance is not an offer card`() {
        val result = OfferCardTextRegionExtractor.extract(
            rawLines = listOf("UberX", "R$ 5,75"),
            layoutHint = "uber",
        )

        assertEquals(null, result)
    }
}
