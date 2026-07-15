package br.com.nexo.driver.destination.offline

import br.com.nexo.driver.destination.DestinationDirectionEvaluator
import br.com.nexo.driver.destination.DestinationDirectionStatus
import br.com.nexo.driver.destination.DirectionEvaluationInput
import br.com.nexo.driver.destination.DriverDestination
import br.com.nexo.driver.offer.Confidence
import br.com.nexo.driver.offer.FieldSource
import br.com.nexo.driver.offer.GeoText
import br.com.nexo.driver.offer.NormalizedOffer
import br.com.nexo.driver.offer.OfferField

/**
 * Replaces an offer's platform-provided direction hint with a deterministic, offline result.
 *
 * A platform badge such as Uber's "em dire\u00e7\u00e3o ao seu destino" is deliberately never used here:
 * the app's destination is independent from the platform destination quota. A Boolean is emitted
 * only when both offer endpoints are exact, unambiguous matches in the active offline package.
 */
class DestinationOfferEnricher(
    private val addressResolver: OfflineAddressResolver,
    private val driverDestination: DriverDestination?,
    private val evaluator: DestinationDirectionEvaluator = DestinationDirectionEvaluator(),
) {
    fun enrich(offer: NormalizedOffer): NormalizedOffer {
        val pickup = offer.pickup.location.resolvePlace()
        val dropoff = offer.trip.location.resolvePlace()
        val result = if (pickup != null && dropoff != null && driverDestination != null) {
            evaluator.evaluate(
                DirectionEvaluationInput(
                    currentPosition = null,
                    pickupPosition = pickup.coordinate,
                    dropoffPosition = dropoff.coordinate,
                    destination = driverDestination,
                ),
            )
        } else {
            null
        }

        val directionHint = result
            ?.takeUnless { it.status == DestinationDirectionStatus.UNKNOWN }
            ?.let {
                Confidence(
                    value = it.isTowardsDestination,
                    score = DERIVED_DIRECTION_CONFIDENCE,
                    source = FieldSource.DERIVED,
                )
            }
            ?: unknownDirection()

        return offer.copy(
            destinationDirectionHint = directionHint,
            fieldConfidence = offer.fieldConfidence +
                (OfferField.DESTINATION_DIRECTION to directionHint.score),
        )
    }

    private fun Confidence<GeoText>.resolvePlace(): OfflineAddressPlace? =
        value?.address?.let(addressResolver::resolve)?.place

    private fun unknownDirection() = Confidence<Boolean>(
        value = null,
        score = 0f,
        source = FieldSource.DERIVED,
    )

    private companion object {
        // Exact package lookup plus deterministic geometry makes this a high-confidence result.
        const val DERIVED_DIRECTION_CONFIDENCE = 1f
    }
}
