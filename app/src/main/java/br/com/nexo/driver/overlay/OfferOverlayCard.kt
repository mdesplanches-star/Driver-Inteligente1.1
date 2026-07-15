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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme

private val OverlayShape = RoundedCornerShape(20.dp)

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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OverlayShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(2.dp, decisionColor),
        tonalElevation = 5.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
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

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "VALOR DA CORRIDA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        text = model.payout,
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = payoutColor,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "TEMPO • DISTÂNCIA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        text = "${model.totalDuration} · ${if (model.totalDistance.isAvailable) model.totalDistance.value else "â€”"}",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(7.dp))
            OverlayMetricGrid(model)
        }
    }
}

@Composable
private fun OverlayMetricGrid(model: OfferOverlayUiModel) {
    val metrics = model.gridFields.map { field -> field.label to model.metricFor(field) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OverlayMetricCell(metrics[0].first, metrics[0].second, Modifier.weight(1f))
            OverlayMetricCell(metrics[1].first, metrics[1].second, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (metric.isAvailable) metric.value else "—",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
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
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun HomeBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "⌂ Casa",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun statusColor(status: OverlayStatus): Color {
    val colors = DriverInteligenteTheme.statusColors
    return when (status) {
        OverlayStatus.ACCEPT -> colors.accept
        OverlayStatus.ANALYZE -> colors.analyze
        OverlayStatus.REJECT -> colors.reject
        OverlayStatus.UNKNOWN -> colors.unknown
    }
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
