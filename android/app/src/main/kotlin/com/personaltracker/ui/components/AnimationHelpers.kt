package com.personaltracker.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Expandable section with AnimatedVisibility (expand/collapse).
 */
@Composable
fun AnimatedSection(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(300)),
        exit = shrinkVertically(
            animationSpec = tween(250, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(200)),
        content = content
    )
}

/**
 * Column that fades and slides in on first appearance.
 */
@Composable
fun FadeInColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                ),
    ) {
        Column(modifier = modifier, content = content)
    }
}

/**
 * Animated integer counter text.
 */
@Composable
fun AnimatedCounter(
    targetValue: Int,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "counter"
    )
    Text(
        text = "$animatedValue",
        style = style,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier
    )
}

/**
 * Animated string counter (e.g., "5 / 7").
 */
@Composable
fun AnimatedFractionCounter(
    numerator: Int,
    denominator: Int,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val animatedNum by animateIntAsState(
        targetValue = numerator,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "numerator"
    )
    Text(
        text = "$animatedNum / $denominator",
        style = style,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier
    )
}

/**
 * Shimmer loading placeholder modifier.
 */
fun Modifier.shimmerLoading(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )

    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim - 200f, translateAnim - 200f),
            end = Offset(translateAnim, translateAnim),
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

/**
 * Shimmer placeholder for loading states — replaces CircularProgressIndicator.
 */
@Composable
fun ShimmerLoadingList(
    itemCount: Int = 3,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(itemCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .shimmerLoading()
            )
        }
    }
}
