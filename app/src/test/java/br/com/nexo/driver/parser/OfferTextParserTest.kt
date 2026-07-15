package br.com.nexo.driver.parser

import br.com.nexo.driver.offer.OfferKind
import br.com.nexo.driver.offer.OfferSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OfferTextParserTest {
    private val registry = OfferParserRegistry()

    @Test
    fun `parses Uber light offer fields`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 13,58
                    R$ 1,29/km est.
                    4,89 (245)
                    3 min (1,2 km)
                    Rua Arthur Manoel Iwersen, Curitiba
                    19 minutos (9,3 km)
                    Rua Adolfo Saviski, São José dos Pinhais
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertNotNull(offer)
        assertEquals(OfferSource.UBER, offer?.source)
        assertEquals(1_358L, offer?.payout?.value?.cents)
        assertEquals(1_200L, offer?.pickup?.distance?.value?.meters)
        assertEquals(1_140L, offer?.trip?.duration?.value?.seconds)
        assertEquals(489L, offer?.passenger?.rating?.value)
    }

    @Test
    fun `uses Uber card payout instead of unrelated earnings chip behind the overlay`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    R$0,00
                    UberX
                    R$ 13,58
                    R$ 1,29/km est.
                    4,89 (245) Verificado
                    3 min (1,2 km)
                    Rua Arthur Manoel Iwersen, Curitiba
                    19 minutos (9,3 km)
                    Rua Adolfo Saviski, São José dos Pinhais
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(1_358L, offer?.payout?.value?.cents)
        assertEquals(129L, offer?.displayedRatePerKm?.value?.cents)
        assertEquals(489L, offer?.passenger?.rating?.value)
        assertEquals(true, offer?.metadata?.hasVerificationBadge)
    }

    @Test
    fun `parses 99 standard offer and bonus`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Pgto. no app
                    R$8,50
                    R$1,50/km
                    R$2,71 Tarifa base dinâmica incl.
                    4,96 · 112 corridas · Perfil Essencial
                    5min (1,6km)
                    Rua Joaquim Nabuco, Centro
                    6min (4,1km)
                    Rua José Fernandes Alves, Uberaba
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(OfferSource.NINETY_NINE, offer?.source)
        assertEquals(OfferKind.NINETY_NINE_STANDARD, offer?.kind)
        assertEquals(850L, offer?.payout?.value?.cents)
        assertEquals(150L, offer?.displayedRatePerKm?.value?.cents)
        assertEquals(271L, offer?.bonus?.value?.cents)
        assertEquals(496L, offer?.passenger?.rating?.value)
        assertEquals(112L, offer?.passenger?.tripCount?.value)
        assertEquals(300L, offer?.pickup?.duration?.value?.seconds)
        assertEquals(4100L, offer?.trip?.distance?.value?.meters)
        assertEquals(true, offer?.metadata?.hasDynamicFare)
    }

    @Test
    fun `keeps 99 passenger rating separate from pickup distance digits`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Pgto. no app
                    R$8,50
                    4,96 · 112 corridas · Perfil Essencial
                    5min (1,6km)
                    Rua Joaquim Nabuco, Centro
                    6min (4,1km)
                    Rua José Fernandes Alves, Uberaba
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(496L, offer?.passenger?.rating?.value)
        assertEquals(1_600L, offer?.pickup?.distance?.value?.meters)
    }

    @Test
    fun `does not use a route distance as rating when OCR reading order is irregular`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Pgto. no app
                    R$8,50
                    5min (1,6km)
                    Rua Exemplo, Centro
                    6min (4,1km)
                    Rua Destino, Bairro
                    4,96 112 corridas Perfil Essencial
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(496L, offer?.passenger?.rating?.value)
    }

    @Test
    fun `treats Negocia as a separate layout using its initial value`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Negocia Nova
                    R$20,92
                    5,00 · 47 corridas · Perfil Essencial
                    (9 min 3,7 km) Rua Genoveva Forlepa Kopka
                    (35 min 18,3 km) Rua São Francisco Pereira dos Santos
                    R$21,97 R$23,01 R$24,06
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(OfferKind.NINETY_NINE_NEGOCIA, offer?.kind)
        assertEquals(2_092L, offer?.payout?.value?.cents)
        assertEquals(3_700L, offer?.pickup?.distance?.value?.meters)
        assertEquals(2_100L, offer?.trip?.duration?.value?.seconds)
        assertEquals("Rua Genoveva Forlepa Kopka", offer?.pickup?.location?.value?.address)
        assertEquals("Rua São Francisco Pereira dos Santos", offer?.trip?.location?.value?.address)
        assertEquals(listOf(2_197L, 2_301L, 2_406L), offer?.metadata?.negotiationAlternatives?.map { it.cents })
        assertEquals(500L, offer?.passenger?.rating?.value)
        assertEquals(47L, offer?.passenger?.tripCount?.value)
    }

    @Test
    fun `prioritizes a 99 Negocia card over an unrelated Uber floating bubble`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Uber →
                    Solicitações
                    Negocia Nova
                    R$20,92
                    5,00 · 47 corridas · Perfil Essencial
                    (9 min 3,7 km) Rua Genoveva Forlepa Kopka
                    (35 min 18,3 km) Rua São Francisco Pereira dos Santos
                    Aceitar por R$20,92
                    R$21,97 R$23,01 R$24,06
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(OfferKind.NINETY_NINE_NEGOCIA, offer?.kind)
        assertEquals(2_092L, offer?.payout?.value?.cents)
    }

    @Test
    fun `recognizes explicit multiple stops labels without a numeric count`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 16,40
                    4,92 (301)
                    Múltiplas paradas
                    4 min (1,1 km)
                    Rua das Flores, Curitiba
                    18 min (8,4 km)
                    Rua do Sol, Curitiba
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(2L, offer?.stopCount?.value)
    }

    @Test
    fun `normalizes an additional 99 stop as at least two total stops`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Pgto. no app
                    R$12,20
                    4,96 · 112 corridas · Perfil Essencial
                    1 parada adicional
                    5min (1,6km)
                    Rua Joaquim Nabuco, Centro
                    12min (5,1km)
                    Rua José Fernandes Alves, Uberaba
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(2L, offer?.stopCount?.value)
    }

    @Test
    fun `does not mistake an ordinary bus stop reference for a multiple-stop offer`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 13,58
                    4,89 (245)
                    3 min (1,2 km)
                    Próximo à parada de ônibus Rua Arthur Manoel Iwersen, Curitiba
                    19 minutos (9,3 km)
                    Rua Adolfo Saviski, São José dos Pinhais
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(null, offer?.stopCount?.value)
    }

    @Test
    fun `recognizes an explicit long trip label regardless of numeric thresholds`() {
        val parser = UberTextParser()
        val offer = parser.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 13,58
                    Long trip
                    3 min (1,2 km)
                    Rua Arthur Manoel Iwersen, Curitiba
                    19 minutos (9,3 km)
                    Rua Adolfo Saviski, São José dos Pinhais
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(true, offer?.longTripHint?.value)
    }

    @Test
    fun `can derive long trip from configurable total distance`() {
        val parser = UberTextParser(
            longTripDetection = LongTripDetectionConfig(
                minimumTotalDistanceMeters = 10_000L,
            ),
        )
        val offer = parser.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 13,58
                    3 min (1,2 km)
                    Rua Arthur Manoel Iwersen, Curitiba
                    19 minutos (9,3 km)
                    Rua Adolfo Saviski, São José dos Pinhais
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(true, offer?.longTripHint?.value)
    }

    @Test
    fun `can classify a known short trip as not long with configured trip thresholds`() {
        val parser = NinetyNineTextParser(
            longTripDetection = LongTripDetectionConfig(
                minimumTripDurationSeconds = 20 * 60L,
                minimumTripDistanceMeters = 10_000L,
            ),
        )
        val offer = parser.parse(
            RawOfferText(
                text = """
                    Pgto. no app
                    R$8,50
                    5min (1,6km)
                    Rua Joaquim Nabuco, Centro
                    6min (4,1km)
                    Rua José Fernandes Alves, Uberaba
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(false, offer?.longTripHint?.value)
    }

    @Test
    fun `reports the recognized source when a card marker is present but fields cannot be extracted`() {
        // "UberX" is a real card marker, but there is no "R$" payout line -- the layout drifted
        // (or OCR missed a block), so parsing must fail without silently pretending nothing
        // was on screen.
        val attempt = registry.parseAttempt(
            RawOfferText(
                text = """
                    UberX
                    4,89 (245)
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(null, attempt.offer)
        assertEquals(OfferSource.UBER, attempt.unrecognizedLayoutSource)
    }

    @Test
    fun `reports no unrecognized layout when no card marker is visible at all`() {
        val attempt = registry.parseAttempt(
            RawOfferText(text = "Texto sem card de oferta", capturedAtEpochMs = 1L),
        )

        assertEquals(null, attempt.offer)
        assertEquals(null, attempt.unrecognizedLayoutSource)
    }
}
