package br.com.nexo.driver.parser

import br.com.nexo.driver.offer.Confidence
import br.com.nexo.driver.offer.Distance
import br.com.nexo.driver.offer.Duration
import br.com.nexo.driver.offer.FieldSource
import br.com.nexo.driver.offer.GeoText
import br.com.nexo.driver.offer.LayoutMetadata
import br.com.nexo.driver.offer.Money
import br.com.nexo.driver.offer.NormalizedOffer
import br.com.nexo.driver.offer.OfferKind
import br.com.nexo.driver.offer.OfferLeg
import br.com.nexo.driver.offer.OfferSource
import br.com.nexo.driver.offer.Passenger

data class RawOfferText(
    val text: String,
    val capturedAtEpochMs: Long,
    val layoutHint: String? = null,
)

interface OfferTextParser {
    val source: OfferSource
    fun canParse(raw: RawOfferText): Boolean
    fun parse(raw: RawOfferText): NormalizedOffer?
}

class OfferParserRegistry(parsers: List<OfferTextParser> = listOf(UberTextParser(), NinetyNineTextParser())) {
    private val parsers = parsers

    /**
     * A driver can leave the other platform open as a floating bubble.  Choosing the first
     * parser that merely recognises a word (for example, "Uber") would then misclassify a 99
     * card.  Prefer the platform with a card-specific marker, then let every eligible parser
     * try to extract a complete offer.
     */
    fun parse(raw: RawOfferText): NormalizedOffer? = rankedParsers(raw)
        .asSequence()
        .filter { it.canParse(raw) }
        .mapNotNull { it.parse(raw) }
        .firstOrNull()

    private fun rankedParsers(raw: RawOfferText): List<OfferTextParser> {
        val text = raw.text.lowercase()
        val preferredSource = when {
            raw.layoutHint == "99" ||
                "pgto. no app" in text ||
                "negocia" in text ||
                "perfil essencial" in text ||
                "perfil premium" in text -> OfferSource.NINETY_NINE
            raw.layoutHint == "uber" || "uberx" in text || "trip radar" in text -> OfferSource.UBER
            else -> null
        }
        return if (preferredSource == null) parsers else parsers.sortedBy { parser ->
            if (parser.source == preferredSource) 0 else 1
        }
    }
}

class UberTextParser(
    private val longTripDetection: LongTripDetectionConfig = LongTripDetectionConfig(),
) : OfferTextParser {
    override val source = OfferSource.UBER

    override fun canParse(raw: RawOfferText): Boolean {
        val text = raw.text.lowercase()
        return raw.layoutHint == "uber" || "uberx" in text || "trip radar" in text
    }

    override fun parse(raw: RawOfferText): NormalizedOffer? = parseCommon(
        raw = raw,
        source = source,
        kind = OfferKind.UBER_STANDARD,
        layoutVersion = "uber-text-v1",
        longTripDetection = longTripDetection,
    )
}

class NinetyNineTextParser(
    private val longTripDetection: LongTripDetectionConfig = LongTripDetectionConfig(),
) : OfferTextParser {
    override val source = OfferSource.NINETY_NINE

    override fun canParse(raw: RawOfferText): Boolean {
        val text = raw.text.lowercase()
        return raw.layoutHint == "99" || "pgto. no app" in text || "negocia" in text || "perfil essencial" in text || "perfil premium" in text
    }

    override fun parse(raw: RawOfferText): NormalizedOffer? {
        val kind = if (raw.text.contains("negocia", ignoreCase = true)) {
            OfferKind.NINETY_NINE_NEGOCIA
        } else {
            OfferKind.NINETY_NINE_STANDARD
        }
        return parseCommon(
            raw = raw,
            source = source,
            kind = kind,
            layoutVersion = if (kind == OfferKind.NINETY_NINE_NEGOCIA) "99-negocia-text-v1" else "99-text-v1",
            longTripDetection = longTripDetection,
        )
    }
}

/**
 * Thresholds used only when a platform does not explicitly label an offer as a long trip.
 *
 * Every threshold is optional: this keeps the parser from silently inventing a definition of
 * "long" for a driver. A configured threshold may use the passenger leg alone or the complete
 * offer (pickup + passenger leg). Reaching any known threshold marks the offer as long. A false
 * result is emitted only when all configured measurements are present and below their limits;
 * otherwise the field remains unknown.
 */
data class LongTripDetectionConfig(
    val minimumTripDurationSeconds: Long? = null,
    val minimumTripDistanceMeters: Long? = null,
    val minimumTotalDurationSeconds: Long? = null,
    val minimumTotalDistanceMeters: Long? = null,
) {
    init {
        listOf(
            minimumTripDurationSeconds,
            minimumTripDistanceMeters,
            minimumTotalDurationSeconds,
            minimumTotalDistanceMeters,
        ).filterNotNull().forEach { require(it > 0L) { "Long-trip thresholds must be positive." } }
    }

    val hasThresholds: Boolean
        get() = listOf(
            minimumTripDurationSeconds,
            minimumTripDistanceMeters,
            minimumTotalDurationSeconds,
            minimumTotalDistanceMeters,
        ).any { it != null }

    fun classify(pickup: OfferLeg, trip: OfferLeg): Boolean? {
        if (!hasThresholds) return null

        val observations = listOf(
            trip.duration.value?.seconds to minimumTripDurationSeconds,
            trip.distance.value?.meters to minimumTripDistanceMeters,
            totalDuration(pickup, trip) to minimumTotalDurationSeconds,
            totalDistance(pickup, trip) to minimumTotalDistanceMeters,
        ).filter { (_, threshold) -> threshold != null }

        if (observations.any { (value, threshold) -> value != null && value >= requireNotNull(threshold) }) {
            return true
        }
        return if (observations.all { (value, _) -> value != null }) false else null
    }
}

private fun parseCommon(
    raw: RawOfferText,
    source: OfferSource,
    kind: OfferKind,
    layoutVersion: String,
    longTripDetection: LongTripDetectionConfig,
): NormalizedOffer? {
    val lines = raw.text.lines().map(String::trim).filter(String::isNotBlank)
    val payout = findPayout(lines, source, kind) ?: return null
    val rate = lines.firstNotNullOfOrNull { line ->
        if (line.contains("/km", ignoreCase = true)) parseMoney(line) else null
    }
    val rating = lines.firstNotNullOfOrNull(::parseRating)
    val legs = lines.mapIndexedNotNull { index, line -> parseLeg(line)?.let { it to index } }
    val pickupLeg = legs.getOrNull(0)?.let { (leg, index) ->
        leg.withLocation(locationLineAfterLeg(lines, index))
    } ?: unknownLeg()
    val tripLeg = legs.getOrNull(1)?.let { (leg, index) ->
        leg.withLocation(locationLineAfterLeg(lines, index))
    } ?: unknownLeg()
    val profile = lines.firstOrNull { it.contains("perfil", ignoreCase = true) }
    val serviceType = lines.firstOrNull(::isUberServiceLine)
        ?: lines.firstOrNull { it.matches(Regex("(?i)(99pop|99plus).*")) }
    val stopCount = parseStopCount(lines)
    val explicitlyLongTrip = lines.any(::hasExplicitLongTripLabel)
    val longTripHint = when {
        explicitlyLongTrip -> true
        else -> longTripDetection.classify(pickupLeg, tripLeg)
    }
    val tripCount = parseTripCount(lines)
    val bonus = lines.firstNotNullOfOrNull { line ->
        if (line.contains("tarifa", true) || line.contains("espera", true) || line.contains("dinâmica", true)) parseMoney(line) else null
    }

    return NormalizedOffer(
        source = source,
        kind = kind,
        detectedAtEpochMs = raw.capturedAtEpochMs,
        payout = confident(payout),
        displayedRatePerKm = confident(rate),
        bonus = confident(bonus),
        pickup = pickupLeg,
        trip = tripLeg,
        passenger = Passenger(
            rating = confident(rating),
            tripCount = confident(tripCount),
            profile = confident(profile),
        ),
        serviceType = confident(serviceType),
        stopCount = confident(stopCount),
        longTripHint = confident(longTripHint),
        destinationDirectionHint = confident(lines.any { it.contains("direção ao seu destino", true) }.takeIf { it }),
        rawLayoutVersion = layoutVersion,
        fieldConfidence = emptyMap(),
        metadata = LayoutMetadata(
            hasVerificationBadge = lines.any { it.contains("verificado", true) }.takeIf { it },
            hasDynamicFare = lines.any { it.contains("dinâmica", true) }.takeIf { it },
            hasLongWaitBonus = lines.any { it.contains("espera longa", true) }.takeIf { it },
            negotiationAlternatives = if (kind == OfferKind.NINETY_NINE_NEGOCIA) {
                findNegotiationAlternatives(lines, payout)
            } else {
                emptyList()
            },
        ),
    )
}

/** Selects money from the card, not a visible earnings chip behind the offer. */
private fun findPayout(lines: List<String>, source: OfferSource, kind: OfferKind): Money? {
    val cardStart = when (source) {
        OfferSource.UBER -> lines.indexOfFirst(::isUberServiceLine)
        OfferSource.NINETY_NINE -> when (kind) {
            OfferKind.NINETY_NINE_NEGOCIA -> lines.indexOfFirst { it.contains("negocia", true) }
            else -> lines.indexOfFirst { it.contains("pgto", true) || it.contains("pagamento", true) }
        }
    }
    val offerRegion = if (cardStart >= 0) lines.drop(cardStart) else lines
    return offerRegion.firstNotNullOfOrNull { line ->
        if (line.contains("/km", true)) null else parseMoney(line)
    }
}

private fun isUberServiceLine(line: String): Boolean =
    line.matches(Regex("(?i)\\s*(uberx|uber black|uber comfort|uber flash|uber\\s*xl).*"))

private fun parseLeg(line: String): OfferLeg? {
    val match = Regex("(?i)\\(?\\s*(\\d+)\\s*(?:min|minutos)\\s*(?:\\(\\s*)?([\\d.,]+)\\s*km\\s*\\)?").find(line) ?: return null
    val minutes = match.groupValues[1].toLongOrNull() ?: return null
    val kilometres = match.groupValues[2].replace('.', ' ').replace(',', '.').replace(" ", "").toDoubleOrNull() ?: return null
    val inlineAddress = line
        .substring(match.range.last + 1)
        .trim()
        .trimStart('·', '-', '–', '—', ')')
        .trim()
        .takeIf(String::isNotBlank)
    return OfferLeg(
        duration = confident(Duration(minutes * 60)),
        distance = confident(Distance((kilometres * 1_000).toLong())),
        location = confident(inlineAddress?.let { GeoText(it, null) }),
    )
}

private fun OfferLeg.withLocation(line: String?): OfferLeg = if (location.value != null) {
    this
} else {
    copy(location = confident(line?.takeIf { it.isNotBlank() }?.let { GeoText(it, null) }))
}

private fun locationLineAfterLeg(lines: List<String>, index: Int): String? = lines
    .getOrNull(index + 1)
    ?.takeUnless { parseLeg(it) != null }

private fun parseAllMoney(text: String): List<Money> =
    Regex("(?i)r\\$\\s*([\\d.]+(?:,[\\d]{1,2})?)")
        .findAll(text)
        .mapNotNull { match -> moneyFromToken(match.groupValues[1]) }
        .toList()

private fun parseMoney(text: String): Money? = parseAllMoney(text).firstOrNull()

private fun moneyFromToken(token: String): Money? {
    val normalized = token.replace(".", "").replace(',', '.')
    val value = normalized.toBigDecimalOrNull() ?: return null
    return Money(value.movePointRight(2).toLong())
}

private fun parseRating(text: String): Long? {
    if (text.contains("R$", ignoreCase = true) || text.contains("/km", ignoreCase = true)) return null
    val hasRatingSignal = Regex("(?:â˜…|â­|\\bstar\\b|\\b(?:corrida|corridas|viagem|viagens)\\b)", RegexOption.IGNORE_CASE)
        .containsMatchIn(text) ||
        Regex("[0-5],[0-9]{1,2}\\s*\\(\\s*[+\\d.,]+\\s*\\)").containsMatchIn(text)
    // A bare decimal is also used by route legs (for example, "5 min (1,6 km)").
    if (!hasRatingSignal) return null
    val match = Regex("(?:★|⭐|\\bstar\\b)\\s*([0-5],[0-9]{1,2})|([0-5],[0-9]{1,2})\\s*(?:★|⭐)|(?<![R$\\d])([0-5],[0-9]{1,2})(?![\\d])").find(text) ?: return null
    val value = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.replace(',', '.')?.toBigDecimalOrNull() ?: return null
    return value.movePointRight(2).toLong()
}

private fun parseStopCount(lines: List<String>): Long? = lines.firstNotNullOfOrNull(::parseStopCount)

/**
 * Explicit platform labels without a number still mean the offer contains at least two stops.
 * The parser intentionally does not treat generic route text (such as "parada de ônibus") as a
 * stop indicator. An "additional stop" is normalized to the minimum total count of two.
 */
private fun parseStopCount(line: String): Long? {
    val normalized = line.foldedForMatching()
    val additional = Regex("\\b(\\d+)\\s*(?:parada|paradas|stop|stops)\\s*(?:adicional|adicionais|additional)\\b")
        .find(normalized)
        ?.groupValues
        ?.get(1)
        ?.toLongOrNull()
    if (additional != null) return additional + 1L

    Regex("\\b(\\d+)\\s*(?:parada|paradas|stop|stops)\\b")
        .find(normalized)
        ?.groupValues
        ?.get(1)
        ?.toLongOrNull()
        ?.let { return it }

    val explicitMultiple = Regex("\\b(?:multiplas|multiple)\\s+(?:paradas|stops)\\b").containsMatchIn(normalized)
    val unnumberedAdditional = Regex("\\b(?:parada|paradas|stop|stops)\\s+(?:adicional|adicionais|additional)\\b")
        .containsMatchIn(normalized)
    return if (explicitMultiple || unnumberedAdditional) 2L else null
}

private fun hasExplicitLongTripLabel(line: String): Boolean {
    val normalized = line.foldedForMatching()
    return Regex("\\b(?:viagem|corrida)\\s+longa\\b|\\blong\\s+trip\\b|\\blong-distance\\s+(?:trip|ride)\\b")
        .containsMatchIn(normalized)
}

private fun String.foldedForMatching(): String =
    java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

private fun totalDuration(pickup: OfferLeg, trip: OfferLeg): Long? {
    val pickupSeconds = pickup.duration.value?.seconds ?: return null
    val tripSeconds = trip.duration.value?.seconds ?: return null
    return pickupSeconds + tripSeconds
}

private fun totalDistance(pickup: OfferLeg, trip: OfferLeg): Long? {
    val pickupMeters = pickup.distance.value?.meters ?: return null
    val tripMeters = trip.distance.value?.meters ?: return null
    return pickupMeters + tripMeters
}

private fun parseTripCount(lines: List<String>): Long? = lines.firstNotNullOfOrNull { line ->
    Regex("(?i)(\\d[\\d.]*)\\s*corridas?").find(line)?.groupValues?.get(1)?.replace(".", "")?.toLongOrNull()
}

private fun findNegotiationAlternatives(lines: List<String>, payout: Money): List<Money> = lines
    .flatMap(::parseAllMoney)
    .filterNot { it == payout }
    .distinct()

private fun unknownLeg() = OfferLeg(unknown(), unknown(), unknown())

private fun <T> confident(value: T?): Confidence<T> = if (value == null) unknown() else Confidence(value, 0.9f, FieldSource.OCR)

private fun <T> unknown(): Confidence<T> = Confidence(null, 0f, FieldSource.OCR)
