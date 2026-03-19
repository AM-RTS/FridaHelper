package com.amrts.fridahelper.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amrts.fridahelper.app.theme.CodeTextStyle
import com.amrts.fridahelper.core.model.HookRequest

private const val COLLAPSE_THRESHOLD = 3

@Composable
fun HookQueueList(
    queue: List<HookRequest>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val collapsible = queue.size > COLLAPSE_THRESHOLD
    var expanded by remember { mutableStateOf(!collapsible) }

    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .let { if (collapsible) it.clickable { expanded = !expanded } else it }
                .padding(bottom = 4.dp)
        ) {
            Text(
                text = "Hook Queue (${queue.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (collapsible) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = !collapsible || expanded,
            enter = expandVertically(tween(250, easing = FastOutSlowInEasing)),
            exit = shrinkVertically(tween(200, easing = FastOutLinearInEasing))
        ) {
            Column {
                queue.forEachIndexed { index, request ->
                    HookQueueItem(
                        index = index,
                        request = request,
                        onRemove = { onRemove(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HookQueueItem(
    index: Int,
    request: HookRequest,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(
            text = "${index + 1}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = if (request.type == HookRequest.Type.JAVA) "JAVA" else "NAT",
            style = CodeTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Text(
            text = formatSummary(request),
            style = CodeTextStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Remove")
        }
    }
}

private fun formatSummary(request: HookRequest): String {
    return if (request.type == HookRequest.Type.JAVA) {
        val m = request.smaliMethod
        "${m.className}.${m.methodName}(${m.paramTypes.joinToString(",")})"
    } else {
        val s = request.nativeSymbol
        if (s.targetMode == com.amrts.fridahelper.core.model.NativeSymbol.TargetMode.EXPORT) {
            "${s.libName ?: ""}!${s.exportName}"
        } else {
            "addr:${s.address}"
        }
    }
}
