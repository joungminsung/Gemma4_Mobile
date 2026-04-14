package com.gemma4mobile.tools.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gemma4mobile.ui.theme.GemmaTheme

@Composable
fun SearchResultCard(
    title: String,
    snippet: String,
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = GemmaTheme.gemmaColors.aiIcon,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (snippet.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = Uri.parse(url).host ?: url,
                style = MaterialTheme.typography.labelSmall,
                color = GemmaTheme.gemmaColors.placeholder,
            )
        }
    }
}

@Composable
fun CalendarEventCard(
    title: String,
    start: String,
    end: String,
    location: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = GemmaTheme.gemmaColors.aiIcon,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("$start ~ $end", style = MaterialTheme.typography.bodySmall, color = GemmaTheme.gemmaColors.placeholder)
                if (location.isNotBlank()) {
                    Text(location, style = MaterialTheme.typography.bodySmall, color = GemmaTheme.gemmaColors.placeholder)
                }
            }
        }
    }
}

@Composable
fun ContactCard(
    name: String,
    phone: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = GemmaTheme.gemmaColors.aiIcon,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(phone, style = MaterialTheme.typography.bodySmall, color = GemmaTheme.gemmaColors.placeholder)
            }
        }
    }
}

@Composable
fun CallLogCard(
    name: String,
    number: String,
    date: String,
    durationSeconds: Int,
    type: String,
    modifier: Modifier = Modifier,
) {
    val icon = when (type) {
        "incoming" -> Icons.Default.CallReceived
        "outgoing" -> Icons.Default.CallMade
        "missed" -> Icons.Default.CallMissed
        else -> Icons.Default.Phone
    }
    val typeLabel = when (type) {
        "incoming" -> "수신"
        "outgoing" -> "발신"
        "missed" -> "부재중"
        else -> type
    }
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = GemmaTheme.gemmaColors.aiIcon)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name.ifBlank { number },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "$typeLabel · $date · ${durationSeconds / 60}분 ${durationSeconds % 60}초",
                    style = MaterialTheme.typography.bodySmall,
                    color = GemmaTheme.gemmaColors.placeholder,
                )
            }
        }
    }
}

@Composable
fun SmsCard(
    address: String,
    body: String,
    date: String,
    type: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (type == "sent") Icons.Default.Send else Icons.Default.Email,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = GemmaTheme.gemmaColors.aiIcon,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Row {
                    Text(address, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(date, style = MaterialTheme.typography.labelSmall, color = GemmaTheme.gemmaColors.placeholder)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
