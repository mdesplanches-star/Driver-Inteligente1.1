package br.com.nexo.driver.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme

private val OverlayShape = RoundedCornerShape(22.dp)

/**
 * A compact, non-interactive card intended to sit immediately above an offer
 * in the driver app. The payout deliberately appears only once, as the primary
 * figure, while the four cells show derived and supporting metrics.
 */
@Composable
fun OfferOverlayCard(
    model: OfferOverlayUiModel,
    modifier: Modifier = Modifier,
) {
    val decisionColor = statusColor(model.status)
    val payoutColor = statusColor(
        if (model.isPayoutAvailable) model.payoutStatus else OverlayStatus.UNKNOWN,
    )
    val background = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.90f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OverlayShape,
        color = background,
        border = BorderStroke(3.dp, decisionColor),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(status = model.status)
                Spacer(Modifier.weight(1f))
                if (model.isTowardHome) {
                    HomeBadge()
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VALOR DA CORRIDA",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = labelColor,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = model.payout,
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = payoutColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(0.82f),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = "TEMPO • DISTÂNCIA",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = labelColor,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${model.totalDuration} · ${if (model.totalDistance.isAvailable) model.totalDistance.value else "—"}",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(9.dp))
            OverlayMetricGrid(model)
        }
    }
}

@Composable
private fun OverlayMetricGrid(model: OfferOverlayUiModel) {
    val metrics = model.gridFields.map { field -> field.label to model.metricFor(field) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OverlayMetricCell(metrics[0].first, metrics[0].second, Modifier.weight(1f))
            OverlayMetricCell(metrics[1].first, metrics[1].second, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OverlayMetricCell(metrics[2].first, metrics[2].second, Modifier.weight(1f))
            OverlayMetricCell(metrics[3].first, metrics[3].second, Modifier.weight(1f))
        }
    }
}

@Composable
private fun OverlayMetricCell(
    label: String,
    metric: OverlayMetricUi,
    modifier: Modifier = Modifier,
) {
    val color = statusColor(if (metric.isAvailable) metric.status else OverlayStatus.UNKNOWN)
    Surface(
        modifier = modifier.heightIn(min = 70.dp),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.9f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(1.dp))
            Text(
                text = if (metric.isAvailable) metric.value else "—",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp),
                fontWeight = FontWeight.ExtraBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusChip(status: OverlayStatus) {
    val color = statusColor(status)
    val label = when (status) {
        OverlayStatus.ACCEPT -> "ACEITAR"
        OverlayStatus.ANALYZE -> "ANALISAR"
        OverlayStatus.REJECT -> "RECUSAR"
        OverlayStatus.UNKNOWN -> "DADOS PARCIAIS"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = statusContentColor(status),
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun HomeBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
    ) {
        Text(
            text = "⌂ Próximo de casa",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun statusColor(status: OverlayStatus): Color = when (status) {
    OverlayStatus.ACCEPT -> DriverInteligenteTheme.statusColors.accept
    OverlayStatus.ANALYZE -> DriverInteligenteTheme.statusColors.analyze
    OverlayStatus.REJECT -> DriverInteligenteTheme.statusColors.reject
    OverlayStatus.UNKNOWN -> DriverInteligenteTheme.statusColors.unknown
}

@Composable
private fun statusContentColor(status: OverlayStatus): Color = when (status) {
    OverlayStatus.ACCEPT -> DriverInteligenteTheme.statusColors.onAccept
    OverlayStatus.ANALYZE -> DriverInteligenteTheme.statusColors.onAnalyze
    OverlayStatus.REJECT -> DriverInteligenteTheme.statusColors.onReject
    OverlayStatus.UNKNOWN -> DriverInteligenteTheme.statusColors.onUnknown
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5, widthDp = 380)
@Composable
private fun OfferOverlayCardAcceptPreview() {
    MaterialTheme {
        OfferOverlayCard(
            model = OfferOverlayUiModel(
                status = OverlayStatus.ACCEPT,
                totalDuration = "22 min",
                payout = "R$ 25,90",
                ratePerKm = OverlayMetricUi("R$ 2,50", OverlayStatus.ACCEPT),
                ratePerHour = OverlayMetricUi("R$ 70,64", OverlayStatus.ACCEPT),
                passengerRating = OverlayMetricUi("4,95", OverlayStatus.ACCEPT),
                pickup = OverlayMetricUi("3 min · 1,2 km", OverlayStatus.ACCEPT),
                isTowardHome = true,
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 380)
@Composable
private fun OfferOverlayCardReviewPreview() {
    MaterialTheme {
        OfferOverlayCard(
            model = OfferOverlayUiModel(
                status = OverlayStatus.ANALYZE,
                totalDuration = "17 min",
                payout = "R$ 13,58",
                ratePerKm = OverlayMetricUi("R$ 1,29", OverlayStatus.REJECT),
                ratePerHour = OverlayMetricUi("R$ 47,93", OverlayStatus.ACCEPT),
                passengerRating = OverlayMetricUi("4,89", OverlayStatus.ACCEPT),
                pickup = OverlayMetricUi("3 min · 1,2 km", OverlayStatus.ANALYZE),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
