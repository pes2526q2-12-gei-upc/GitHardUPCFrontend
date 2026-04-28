package com.safesteps.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.safesteps.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.safesteps.data.Feature
import com.safesteps.data.PhotonApi
import com.safesteps.i18n.appString
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

enum class LocationWrapperState {
    MY_LOCATION,
    CUSTOM_ADDRESS,
    TYPING
}

@Composable
private fun LocationSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(1.dp, Color(0xFFFFCC80), RoundedCornerShape(26.dp))
            .background(Color.White, RoundedCornerShape(26.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = Color(0xFFFF8A80),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isBlank()) {
                    Text(
                        text = appString(R.string.report_location_placeholder),
                        color = Color(0xFF9AA7A0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF3D4A45)),
                    cursorBrush = SolidColor(Color(0xFF5AC98B)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationSuggestions(
    suggestions: List<Feature>,
    onSelect: (Feature) -> Unit
) {
    AnimatedVisibility(visible = suggestions.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFE4ECE8), RoundedCornerShape(16.dp))
        ) {
            suggestions.forEach { feature ->
                val textAdreca = feature.properties.getAddress()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(feature) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF9AA7A0),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = textAdreca,
                        color = Color(0xFF3D4A45),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (feature != suggestions.last()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFF4F6F5))
                    )
                }
            }
        }
    }
}

@Composable
private fun MyLocationRow(
    defaultLocationText: String,
    onSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE4ECE8), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = null,
                tint = Color(0xFF1A73E8),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = defaultLocationText,
                color = Color(0xFF1A73E8),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ReportIssueDialog(
    visible: Boolean,
    isLoggedIn: Boolean,
    defaultLocationText: String,
    onDismiss: () -> Unit,
    onConfirm: (IssueType, String, String) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false)
) {
    if (!visible) return

    val temaTaronja = Color(0xFFF57C00)

    var tipusSeleccionat by remember { mutableStateOf(IssueType.OBRES) }
    var descripcioText by remember { mutableStateOf("") }
    var errorMsgRes by remember { mutableStateOf<Int?>(null) }

    var ubicacioText by remember { mutableStateOf(defaultLocationText) }
    var wrapperState by remember { mutableStateOf(LocationWrapperState.MY_LOCATION) }

    var localSuggestions by remember { mutableStateOf<List<Feature>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun searchPhoton(query: String) {
        searchJob?.cancel()
        if (query.length < 3) {
            localSuggestions = emptyList()
            return
        }
        searchJob = scope.launch {
            delay(300)
            try {
                val queryFormatada = query.replace(Regex("(?<=[a-zA-Z])\\s+(?=\\d+)"), ", ")
                val resposta = PhotonApi.service.findAddress(query = queryFormatada)
                localSuggestions = resposta.features
                    .filter { !it.properties.street.isNullOrBlank() || !it.properties.name.isNullOrBlank() }
                    .distinctBy { it.properties.getAddress().lowercase() }
                    .take(5)
            } catch (_: Exception) {
                localSuggestions = emptyList()
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        ubicacioText = ""
        localSuggestions = emptyList()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = properties
    ) {
        Card(
            modifier = modifier.fillMaxWidth(0.95f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isLoggedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = temaTaronja)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = appString(R.string.report_issue_failed_label),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = temaTaronja
                        )
                    }
                    Text(
                        text = appString(R.string.report_issue_failed_text),
                        color = Color.DarkGray,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(appString(R.string.cancel_action), color = Color.Gray)
                        }
                        Button(
                            onClick = { onDismiss(); onNavigateToLogin() },
                            colors = ButtonDefaults.buttonColors(containerColor = temaTaronja)
                        ) {
                            Text(appString(R.string.log_in), color = Color.White)
                        }
                    }
                } else {
                    Text(
                        text = appString(R.string.report_issue_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Text(
                        appString(R.string.report_type_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IssueType.entries.forEach { type ->
                            FilterChip(
                                selected = tipusSeleccionat == type,
                                onClick = { tipusSeleccionat = type },
                                label = { Text(appString(type.labelRes)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    labelColor = Color.DarkGray,
                                    selectedLabelColor = Color.White,
                                    selectedContainerColor = temaTaronja
                                )
                            )
                        }
                    }

                    Text(
                        appString(R.string.report_location_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )

                    when (wrapperState) {
                        LocationWrapperState.MY_LOCATION,
                        LocationWrapperState.CUSTOM_ADDRESS -> {
                            val isMyLocation = wrapperState == LocationWrapperState.MY_LOCATION
                            val bgColor      = if (isMyLocation) Color(0xFFE8F0FE) else Color(0xFFFFF3E0)
                            val borderColor  = if (isMyLocation) Color(0xFFB9D4FB) else Color(0xFFFFCC80)
                            val contentColor = if (isMyLocation) Color(0xFF1A73E8) else temaTaronja
                            val iconVec      = if (isMyLocation) Icons.Default.MyLocation else Icons.Default.LocationOn

                            Surface(
                                color = bgColor,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, borderColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        wrapperState = LocationWrapperState.TYPING
                                        clearSearch()
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Icon(iconVec, contentDescription = null, tint = contentColor)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = ubicacioText,
                                        color = contentColor,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            wrapperState = LocationWrapperState.TYPING
                                            clearSearch()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Esborrar", tint = contentColor)
                                    }
                                }
                            }
                        }

                        LocationWrapperState.TYPING -> {
                            Column {
                                LocationSearchField(
                                    value = ubicacioText,
                                    onValueChange = { nouText ->
                                        ubicacioText = nouText
                                        errorMsgRes = null
                                        searchPhoton(nouText)
                                    },
                                    onClear = { clearSearch() }
                                )

                                MyLocationRow(
                                    defaultLocationText = defaultLocationText,
                                    onSelect = {
                                        ubicacioText = defaultLocationText
                                        wrapperState = LocationWrapperState.MY_LOCATION
                                        searchJob?.cancel()
                                        localSuggestions = emptyList()
                                    }
                                )
                                LocationSuggestions(
                                    suggestions = localSuggestions,
                                    onSelect = { feature ->
                                        ubicacioText = feature.properties.getAddress()
                                        wrapperState = LocationWrapperState.CUSTOM_ADDRESS
                                        searchJob?.cancel()
                                        localSuggestions = emptyList()
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        appString(R.string.report_description_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                    OutlinedTextField(
                        value = descripcioText,
                        onValueChange = { descripcioText = it; errorMsgRes = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text(appString(R.string.report_description_placeholder), color = Color.Gray) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = temaTaronja,
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color.Black
                        )
                    )

                    if (errorMsgRes != null) {
                        Text(
                            text = appString(errorMsgRes!!),
                            color = temaTaronja,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(text = appString(R.string.cancel_action), color = Color.DarkGray)
                        }
                        Button(
                            onClick = {
                                if (wrapperState == LocationWrapperState.TYPING || ubicacioText.isBlank() || descripcioText.isBlank()) {
                                    errorMsgRes = R.string.report_error_msg_text
                                } else {
                                    onConfirm(tipusSeleccionat, ubicacioText, descripcioText)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = temaTaronja)
                        ) {
                            Text(text = appString(R.string.confirm_action), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}