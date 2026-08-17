package org.moodle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.moodle.core.model.MoodleCalendarEvent
import org.moodle.core.model.MoodleConversation
import org.moodle.core.model.MoodleCourse

internal data class PortalDashboardSnapshot(
    val activeCourseCount: Int,
    val upcomingEventCount: Int,
    val unreadMessageCount: Int,
    val nextEvent: MoodleCalendarEvent?,
)

internal fun buildPortalDashboardSnapshot(
    courses: List<MoodleCourse>,
    events: List<MoodleCalendarEvent>,
    conversations: List<MoodleConversation>,
    nowEpochSeconds: Long,
): PortalDashboardSnapshot {
    val upcoming = events.filter { it.startEpochSeconds >= nowEpochSeconds }
    return PortalDashboardSnapshot(
        activeCourseCount = courses.count { it.endDate == null || it.endDate >= nowEpochSeconds },
        upcomingEventCount = upcoming.size,
        unreadMessageCount = conversations.sumOf { it.unreadCount },
        nextEvent = upcoming.minByOrNull { it.startEpochSeconds },
    )
}

internal fun coursePaletteIndex(seed: Long, paletteSize: Int = 5): Int {
    require(paletteSize > 0)
    return ((seed % paletteSize + paletteSize) % paletteSize).toInt()
}

internal data class PortalCoursePalette(
    val start: Color,
    val end: Color,
    val accent: Color,
)

internal fun portalCoursePalette(seed: Long, darkTheme: Boolean): PortalCoursePalette {
    val light = listOf(
        PortalCoursePalette(Color(0xFF073B4C), Color(0xFF0B6E69), Color(0xFF74D6C5)),
        PortalCoursePalette(Color(0xFF183A61), Color(0xFF38669B), Color(0xFFA6C8F2)),
        PortalCoursePalette(Color(0xFF4B294C), Color(0xFF7B536F), Color(0xFFE1B8D2)),
        PortalCoursePalette(Color(0xFF5A4311), Color(0xFF99752C), Color(0xFFF1D58A)),
        PortalCoursePalette(Color(0xFF23452F), Color(0xFF477557), Color(0xFFB4D9B8)),
    )
    val selected = light[coursePaletteIndex(seed, light.size)]
    return if (darkTheme) {
        selected.copy(
            start = selected.start.copy(alpha = 0.92f),
            end = selected.end.copy(alpha = 0.82f),
        )
    } else selected
}

@Composable
internal fun PortalBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to colors.background,
                    0.34f to colors.background,
                    1f to colors.surfaceContainer.copy(alpha = 0.45f),
                ),
            ),
    ) {
        content()
    }
}

@Composable
internal fun PortalBrandMark(size: Dp = 42.dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(size * 0.5f).clearAndSetSemantics {},
            )
        }
    }
}

@Composable
internal fun PortalEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
    )
}

@Composable
internal fun PortalSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            supportingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun PortalStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            icon?.let { Icon(it, null, Modifier.size(14.dp).clearAndSetSemantics {}) }
            Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
internal fun PortalCourseCover(
    courseId: Long,
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = portalCoursePalette(courseId, dark)
    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(palette.start, palette.end))),
    ) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            drawCircle(
                color = Color.White.copy(alpha = 0.09f),
                radius = size.minDimension * 0.58f,
                center = Offset(size.width * 0.91f, size.height * 0.08f),
            )
            drawCircle(
                color = palette.accent.copy(alpha = 0.22f),
                radius = size.minDimension * 0.34f,
                center = Offset(size.width * 0.84f, size.height * 0.9f),
                style = Stroke(width = size.minDimension * 0.055f),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.14f),
                start = Offset(size.width * 0.6f, -size.height * 0.1f),
                end = Offset(size.width * 1.05f, size.height * 0.72f),
                strokeWidth = size.minDimension * 0.035f,
                cap = StrokeCap.Round,
            )
        }
        Column(
            Modifier.fillMaxSize().padding(if (compact) 14.dp else 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
            ) {
                Text(
                    title.trim().firstOrNull()?.uppercase() ?: "M",
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!compact) {
                Spacer(Modifier.height(18.dp))
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
