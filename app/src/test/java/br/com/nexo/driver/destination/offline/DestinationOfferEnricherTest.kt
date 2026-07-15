package br.com.nexo.driver.destination.offline

import br.com.nexo.driver.destination.DriverDestination
import br.com.nexo.driver.destination.GeoCoordinate
import br.com.nexo.driver.offer.Confidence
import br.com.nexo.driver.offer.Distance
import br.com.nexo.driver.offer.Duration
import br.com.nexo.driver.offer.FieldSource
import br.com.nexo.driver.offer.GeoText
import br.com.nexo.driver.offer.LayoutMetadata
import br.com.nexo.driver.offer.Money
import br.com.nexo.driver.offer.NormalizedOffer
import br.com.nexo.driver.offer.OfferField
import br.com.nexo.driver.offer.OfferKind
import br.com.nexo.driver.offer.OfferLeg
import br.com.nexo.driver.offer.OfferSource
import br.com.nexo.driver.offer.Passenger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationOfferEnricherTest {
    private val cityCenter = GeoCoordinate(-25.4284, -49.2733)
    private val homeNorth = GeoCoordinate(-25.3784, -49.2733)
    private val resolver = requireNotNull(
        OfflineAddressResolver.create(
            OfflineAddressPackage(
                metadata = OfflineAddressPackageMetadata("Curitiba", "1.0.0", "Curitiba"),
                places = listOf(
                    place("pickup", "Rua de Coleta, 1", cityCenter),
                    place("towards", "Rua Rumo a Casa, 2", GeoCoordinate(-25.3934, -49.2733)),
                    place("away", "Rua no Sentido Oposto, 3", GeoCoordinate(-25.4684, -49.2733)),
                ),
            ),
        ),
    )
    private val destination = DriverDestination(homeNorth)

    @Test
    fun `replaces Uber direction hint with an exact offline result`() {
        val enriched = DestinationOfferEnricher(resolver, destination).enrich(
            offer(pickupAddress = "Rua de Coleta, 1", dropoffAddress = "Rua Rumo a Casa, 2", uberHint = false),
        )

        assertEquals(true, enriched.destinationDirectionHint.value)
        assertEquals(1f, enriched.destinationDirectionHint.score)
        assertEquals(FieldSource.DERIVED, enriched.destinationDirectionHint.source)
        assertEquals(1f, enriched.fieldConfidence[OfferField.DESTINATION_DIRECTION])
    }

    @Test
    fun `derives false for a resolved trip that moves away from home`() {
        val enriched = DestinationOfferEnricher(resolver, destination).enrich(
            offer(pickupAddress = "Rua de Coleta, 1", dropoffAddress = "Rua no Sentido Oposto, 3", uberHint = true),
        )

        assertEquals(false, enriched.destinationDirectionHint.value)
        assertTrue(enriched.destinationDirectionHint.score >= 0.9f)
        assertEquals(FieldSource.DERIVED, enriched.destinationDirectionHint.source)
    }

    @Test
    fun `clears rather than trusts an Uber hint when either endpoint cannot resolve`() {
        val enriched = DestinationOfferEnricher(resolver, destination).enrich(
            offer(pickupAddress = "Rua de Coleta, 1", dropoffAddress = "Endere\u00e7o fora do pacote", uberHint = true),
        )

        assertNull(enriched.destinationDirectionHint.value)
        assertEquals(0f, enriched.destinationDirectionHint.score)
        assertEquals(FieldSource.DERIVED, enriched.destinationDirectionHint.source)
        assertEquals(0f, enriched.fieldConfidence[OfferField.DESTINATION_DIRECTION])
    }

    @Test
    fun `clears platform hint when no driver destination is configured`() {
        val enriched = DestinationOfferEnricher(resolver, driverDestination = null).enrich(
            offer(pickupAddress = "Rua de Coleta, 1", dropoffAddress = "Rua Rumo a Casa, 2", uberHint = true),
        )

        assertNull(enriched.destinationDirectionHint.value)
        assertFalse(enriched.destinationDirectionHint.score > 0f)
        assertEquals(FieldSource.DERIVED, enriched.destinationDirectionHint.source)
    }

    private fun place(id: String, label: String, coordinate: GeoCoordinate) = OfflineAddressPlace(
        id = id,
        label = label,
        coordinate = coordinate,
    )

    private fun offer(pickupAddress: String, dropoffAddress: String, uberHint: Boolean): NormalizedOffer {
        fun <T> unknown() = Confidence<T>(null, 0f, FieldSource.OCR)
        fun location(address: String) = Confidence(GeoText(address, "Curitiba"), 0.6f, FieldSource.OCR)
        fun leg(address: String) = OfferLeg(
            duration = Confidence(Duration(300), 0.7f, FieldSource.OCR),
            distance = Confidence(Distance(2_000), 0.7f, FieldSource.OCR),
            location = location(address),
        )
        return NormalizedOffer(
            source = OfferSource.UBER,
            kind = OfferKind.UBER_STANDARD,
            detectedAtEpochMs = 1L,
            payout = Confidence(Money(1_000), 0.8f, FieldSource.OCR),
            displayedRatePerKm = unknown(),
            bonus = unknown(),
            pickup = leg(pickupAddress),
            trip = leg(dropoffAddress),
            passenger = Passenger(unknown(), unknown(), unknown()),
            serviceType = unknown(),
            stopCount = unknown(),
            longTripHint = unknown(),
            destinationDirectionHint = Confidence(uberHint, 0.99f, FieldSource.OCR),
            rawLayoutVersion = "test",
            fieldConfidence = mapOf(OfferField.DESTINATION_DIRECTION to 0.99f),
            metadata = LayoutMetadata(),
        )
    }
}
