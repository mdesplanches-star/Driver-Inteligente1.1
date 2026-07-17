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
    val decisionColor = if (model.isTowardHome && model.status != OverlayStatus.REJECT) {
        Color(0xFFB45CFF)
    } else {
        statusColor(model.status)
    }
    val background = Color(0xFF101214).copy(alpha = 0.94f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = Color.White
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = background,
        border = BorderStroke(2.5.dp, decisionColor),
        tonalElevation = 0.dp,
        shadowElevation = 18.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(status = model.status, forcedLabel = if (model.isTowardHome && model.status != OverlayStatus.REJECT) "SENTIDO CASA" else null)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(50))
                        .background(decisionColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("◉", color = decisionColor, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(Modifier.height(12.dp))
            OverlayMetricStrip(model, decisionColor, labelColor, textColor)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f)),
            )
            Spacer(Modifier.height(11.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "⌁ Distância: ${if (model.totalDistance.isAvailable) model.totalDistance.value else "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.74f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "◷ Duração: ${model.totalDuration}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun OverlayMetricStrip(
    model: OfferOverlayUiModel,
    accent: Color,
    labelColor: Color,
    textColor: Color,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StripMetric("R$/km", model.ratePerKm.value, model.ratePerKm.status, Modifier.weight(1f), labelColor, textColor)
        StripMetric("R$/h", model.ratePerHour.value, model.ratePerHour.status, Modifier.weight(1f), labelColor, textColor)
        StripMetric("Avaliação", "${model.passengerRating.value} ★", model.passengerRating.status, Modifier.weight(1f), labelColor, textColor)
        StripMetric("Lucro", model.profit, model.payoutStatus, Modifier.weight(1f), labelColor, textColor, accent)
    }
}

@Composable
private fun StripMetric(
    label: String,
    value: String,
    status: OverlayStatus,
    modifier: Modifier = Modifier,
    labelColor: Color,
    textColor: Color,
    forcedColor: Color? = null,
) {
    val color = forcedColor ?: statusColor(status)
    Column(modifier = modifier.padding(end = 7.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            fontWeight = FontWeight.ExtraBold,
            color = if (status == OverlayStatus.UNKNOWN) textColor else color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusChip(status: OverlayStatus, forcedLabel: String? = null) {
    val color = statusColor(status)
    val label = forcedLabel ?: when (status) {
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
