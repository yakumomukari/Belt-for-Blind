package com.beltforblind.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beltforblind.ui.theme.BeltColors
import com.beltforblind.ui.theme.BeltSpacing

enum class StatusType {
    Neutral,
    Interactive,
    Success,
    Warning,
    Error,
}

@Composable
fun AppHeaderCard(
    title: String,
    subtitle: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    statusContent: @Composable () -> Unit = {},
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(220)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BeltColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(BeltSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BeltSpacing.ExtraSmall),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BeltSpacing.ExtraSmall),
            ) {
                Surface(
                    shape = CircleShape,
                    color = BeltColors.PurpleContainer,
                    contentColor = BeltColors.PrimaryPurple,
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = BeltColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                statusContent()
            }
        }
    }
}

@Composable
fun StatusChip(
    icon: ImageVector,
    text: String,
    type: StatusType,
    modifier: Modifier = Modifier,
    contentDescription: String = text,
    onClick: (() -> Unit)? = null,
) {
    val targetColors = statusColors(type)
    val containerColor by animateColorAsState(
        targetValue = targetColors.container,
        animationSpec = tween(200),
    )
    val contentColor by animateColorAsState(
        targetValue = targetColors.content,
        animationSpec = tween(200),
    )
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    Surface(
        modifier = modifier
            .animateContentSize(animationSpec = tween(200))
            .pressScale(interactionSource, pressedScale = 0.97f)
            .heightIn(min = if (onClick == null) 36.dp else 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                enabled = onClick != null,
            ) {
                onClick?.invoke()
            }
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                if (onClick != null) role = Role.Button
        },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun PrimaryActionButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .heightIn(min = 72.dp),
        interactionSource = interactionSource,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BeltColors.PrimaryPurple,
            contentColor = Color.White,
            disabledContainerColor = BeltColors.Disabled,
            disabledContentColor = Color.White,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        } else {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(8.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
    statusColor: Color = BeltColors.PrimaryPurple,
) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(label, value, unit).joinToString(" ")
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BeltColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(BeltSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                icon?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = BeltColors.TextSecondary,
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInVertically { height -> height / 3 })
                            .togetherWith(
                                fadeOut(tween(120)) +
                                    slideOutVertically { height -> -height / 3 },
                            )
                    },
                ) { animatedValue ->
                    Text(
                        animatedValue,
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = BeltColors.TextPrimary,
                    )
                }
                unit?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BeltColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}

private data class StatusColors(
    val container: Color,
    val content: Color,
)

private fun statusColors(type: StatusType): StatusColors {
    return when (type) {
        StatusType.Neutral -> StatusColors(BeltColors.Background, BeltColors.TextSecondary)
        StatusType.Interactive -> StatusColors(BeltColors.PurpleContainer, BeltColors.PrimaryPurpleDark)
        StatusType.Success -> StatusColors(BeltColors.SuccessContainer, BeltColors.Success)
        StatusType.Warning -> StatusColors(BeltColors.WarningContainer, BeltColors.Warning)
        StatusType.Error -> StatusColors(BeltColors.ErrorContainer, BeltColors.Error)
    }
}
