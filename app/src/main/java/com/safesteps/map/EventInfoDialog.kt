package com.safesteps.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.safesteps.R
import com.safesteps.data.PuntInteres
import com.safesteps.data.nomMostrat
import com.safesteps.i18n.appString

private val EventAccent = Color(0xFFB8522A)
private val EventCardBorder = Color(0xFFEAD8D0)

@Composable
fun EventInfoDialog(
    event: PuntInteres,
    canStartRoute: Boolean,
    onDismiss: () -> Unit,
    onRouteToEvent: () -> Unit
) {
    val description = event.descripcio
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: appString(R.string.event_description_empty)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFF7F2), Color.White)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                EventDialogHeader(
                    title = event.nomMostrat(),
                    onDismiss = onDismiss
                )

                EventDialogHighlightCard()

                EventDialogDescriptionCard(
                    title = appString(R.string.event_description_title),
                    description = description
                )

                Button(
                    onClick = onRouteToEvent,
                    enabled = canStartRoute,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EventAccent,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFD9C6BE),
                        disabledContentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = appString(R.string.event_route_action),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EventDialogHeader(
    title: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 6.dp
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = appString(R.string.close),
                    tint = Color(0xFF33413B)
                )
            }
        }

        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1F2C3B),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EventDialogHighlightCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFFFF1E8),
        border = androidx.compose.foundation.BorderStroke(1.dp, EventCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = EventAccent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Event,
                    contentDescription = null,
                    tint = EventAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = appString(R.string.event_highlight_title),
                    color = Color(0xFF4B2A1D),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = appString(R.string.event_highlight_subtitle),
                    color = Color(0xFF8A5A46),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun EventDialogDescriptionCard(
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF23333A)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .border(
                    width = 1.dp,
                    color = EventCardBorder,
                    shape = RoundedCornerShape(24.dp)
                )
                .background(
                    color = Color(0xFFFFFCFA),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(16.dp)
        ) {
            Text(
                text = description,
                modifier = Modifier.verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF46535B),
                lineHeight = 22.sp
            )
        }
    }
}
