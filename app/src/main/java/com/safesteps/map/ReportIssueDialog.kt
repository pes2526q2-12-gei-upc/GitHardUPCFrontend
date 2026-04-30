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
import com.safesteps.data.IssueResponseDTO
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.safesteps.data.Coord

enum class LocationWrapperState {
    MY_LOCATION,
    CUSTOM_ADDRESS,
    TYPING
}

@Composable
private fun LocationSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val borderColor = Color(0xFFF57C00)
    val colorText   = Color(0xFFF57C00)
    val icona       = Icons.Default.LocationOn

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(1.dp, borderColor, RoundedCornerShape(26.dp))
            .background(Color.White, RoundedCornerShape(26.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icona,
                contentDescription = null,
                tint = colorText,
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
                    enabled = true,
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
    onSelect: () -> Unit,
    isEditMode: Boolean = false
) {
    val colorText = if (isEditMode) Color(0xFFF57C00) else Color(0xFF1A73E8)
    val icona = if (isEditMode) Icons.Default.LocationOn else Icons.Default.MyLocation

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
                imageVector = icona,
                contentDescription = null,
                tint = colorText,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = defaultLocationText,
                color = colorText,
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
    onConfirm: (IssueType, String, String, Coord) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    initialType: IssueType = IssueType.OBRES,
    initialDescription: String = "",
    isEditMode: Boolean = false,
    initialCoord: Coord,
    myLocationText: String? = null,
    myLocationCoord: Coord? = null
) {
    if (!visible) return

    val temaTaronja = Color(0xFFF57C00)
    var currentCoord by remember { mutableStateOf(initialCoord) }

    var tipusSeleccionat by remember(initialType) { mutableStateOf(initialType) }
    var descripcioText by remember(initialDescription) { mutableStateOf(initialDescription) }

    var errorMsgRes by remember { mutableStateOf<Int?>(null) }

    var ubicacioText by remember { mutableStateOf(defaultLocationText) }

    var wrapperState by remember {
        mutableStateOf(
            if (isEditMode) LocationWrapperState.CUSTOM_ADDRESS
            else LocationWrapperState.MY_LOCATION
        )
    }

    var userHasModifiedLocation by remember { mutableStateOf(false) }

    LaunchedEffect(defaultLocationText) {
        if (!userHasModifiedLocation) {
            ubicacioText = defaultLocationText
        }
    }

    val effectiveMyLocationText = myLocationText ?: defaultLocationText
    val effectiveMyLocationCoord = myLocationCoord ?: initialCoord

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
                    if (isEditMode) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFF3E0),
                            border = BorderStroke(1.dp, Color(0xFFFFCC80)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = temaTaronja
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = ubicacioText,
                                        color = temaTaronja,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFF8B6E4F),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = appString(R.string.issue_location_locked_message),
                                    color = Color(0xFF8B6E4F),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
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
                                        userHasModifiedLocation = true
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
                                            userHasModifiedLocation = true
                                            wrapperState = LocationWrapperState.TYPING
                                            clearSearch()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = appString(R.string.delete_action), tint = contentColor)
                                    }
                                }
                            }
                        }

                            LocationWrapperState.TYPING -> {
                            Column {
                                LocationSearchField(
                                    value = ubicacioText,
                                    onValueChange = { nouText ->
                                        userHasModifiedLocation = true
                                        ubicacioText = nouText
                                        errorMsgRes = null
                                        searchPhoton(nouText)
                                    },
                                    onClear = { clearSearch() },
                                )

                                MyLocationRow(
                                    defaultLocationText = effectiveMyLocationText,
                                    onSelect = {
                                        userHasModifiedLocation = true
                                        ubicacioText = effectiveMyLocationText
                                        currentCoord = effectiveMyLocationCoord
                                        wrapperState = LocationWrapperState.MY_LOCATION
                                        searchJob?.cancel()
                                        localSuggestions = emptyList()
                                    },
                                    isEditMode = false
                                )
                                LocationSuggestions(
                                    suggestions = localSuggestions,
                                    onSelect = { feature ->
                                        userHasModifiedLocation = true
                                        ubicacioText = feature.properties.getAddress()
                                        currentCoord = Coord(feature.geometry.latitud, feature.geometry.longitud)
                                        wrapperState = LocationWrapperState.CUSTOM_ADDRESS
                                        searchJob?.cancel()
                                        localSuggestions = emptyList()
                                    }
                                )
                            }
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
                        placeholder = {
                            Text(
                                appString(R.string.report_description_placeholder),
                                color = Color.Gray
                            )
                        },
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
                                    onConfirm(tipusSeleccionat, ubicacioText, descripcioText, currentCoord)
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

@Composable
fun showIssueDialog(
    incidencia: IssueResponseDTO,
    isOwner: Boolean,
    miVot: Int? = null,
    userVote: Int?,
    onDismiss: () -> Unit,
    onConfirmar: () -> Unit,
    onRebutjar: () -> Unit,
    onEditar: () -> Unit,
    onEsborrar: () -> Unit,
    onDesferVot: () -> Unit
) {
    val loadingAddressLabel = appString(R.string.issue_loading_address)
    val coordinatesFallbackLabel = appString(
        R.string.issue_coordinates_fallback,
        incidencia.coordinates.lat,
        incidencia.coordinates.lon
    )
    var adrecaText by remember { mutableStateOf(loadingAddressLabel) }
    val issueTypeLabel = appString(R.string.issue_type_card_label)
    val issueTypeValue = when (incidencia.type) {
        com.safesteps.data.IssueApiType.OBRES -> appString(R.string.issue_type_worksite)
        com.safesteps.data.IssueApiType.ACCESSIBILITAT -> appString(R.string.issue_type_accessibility)
        com.safesteps.data.IssueApiType.SEGURETAT -> appString(R.string.issue_type_security)
        com.safesteps.data.IssueApiType.ALTRES -> appString(R.string.issue_type_others)
    }
    val issueStatusLabel = appString(R.string.issue_status_card_label)
    val issueStatusAccepted = appString(R.string.issue_status_accepted)
    val issueStatusPending = appString(R.string.issue_status_pending)
    val issueLocationLabel = appString(R.string.issue_location_card_label)
    val positiveVotesLabel = appString(R.string.issue_positive_votes_label)
    val negativeVotesLabel = appString(R.string.issue_negative_votes_label)
    val descriptionLabel = appString(R.string.issue_description_title)
    val emptyDescriptionLabel = appString(R.string.issue_description_empty)
    val anonymousUserLabel = appString(R.string.issue_anonymous_user)
    val undoLabel = appString(R.string.issue_undo_action)
    val confirmedVoteLabel = appString(R.string.issue_vote_confirmed)
    val falseReportVoteLabel = appString(R.string.issue_vote_reported_false)
    val issueDetailsLabel = appString(R.string.issue_details_title)
    val unknownAddressLabel = appString(R.string.issue_unknown_address)
    val editLabel = appString(R.string.edit_action)
    val deleteLabel = appString(R.string.delete_action)
    val reportFalseLabel = appString(R.string.issue_report_false_action)
    val closeLabel = appString(R.string.close)

    LaunchedEffect(incidencia.coordinates) {
        try {
            val response = withContext(Dispatchers.IO) {
                PhotonApi.service.reverseGeocode(
                    lat = incidencia.coordinates.lat,
                    lon = incidencia.coordinates.lon
                )
            }
            adrecaText = response.features.firstOrNull()?.properties?.getAddress() ?: unknownAddressLabel
        } catch (_: Exception) {
            adrecaText = coordinatesFallbackLabel
        }
    }

    val dataAMostrar = if (!incidencia.updatedAt.isNullOrBlank()) {
        incidencia.updatedAt.substringBefore("T")
    } else {
        incidencia.createdAt.substringBefore("T")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(0.92f).padding(16.dp),
        title = {
            Text(
                text = issueDetailsLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1A1C1E)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        label = issueTypeLabel,
                        value = issueTypeValue,
                        icon = Icons.Filled.Label,
                        contentColor = Color(0xFF1976D2),
                        backgroundColor = Color(0xFFE3F2FD)
                    )
                    val isAccepted = incidencia.status.lowercase() == "accepted"
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        label = issueStatusLabel,
                        value = if (isAccepted) issueStatusAccepted else issueStatusPending,
                        icon = if (isAccepted) Icons.Filled.CheckCircle else Icons.Filled.HelpOutline,
                        contentColor = if (isAccepted) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                        backgroundColor = if (isAccepted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFF5F6F7),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = Color(0xFFE3E5E8)
                        ) {
                            Icon(
                                Icons.Filled.Place,
                                contentDescription = null,
                                tint = Color(0xFF444746),
                                modifier = Modifier.padding(9.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = issueLocationLabel,
                                fontSize = 11.sp,
                                color = Color(0xFF6B7280),
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.4.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = adrecaText,
                                color = Color(0xFF1A1C1E),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    VoteIndicator(count = incidencia.positiveVotes, icon = Icons.Filled.ThumbUp, color = Color(0xFF4CAF50), label = positiveVotesLabel)
                    VoteIndicator(count = incidencia.negativeVotes, icon = Icons.Filled.ThumbDown, color = Color(0xFFF44336), label = negativeVotesLabel)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(text = descriptionLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = incidencia.description ?: emptyDescriptionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF444746),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(color = Color(0xFFF8F9FA), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE9ECEF))) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(8.dp), color = Color(0xFFCED4DA)) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.padding(6.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    val nomUsuari = incidencia.authorName ?: anonymousUserLabel
                                    Text(text = nomUsuari, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                                    if (incidencia.authorName != null) {
                                        Text(text = appString(R.string.issue_user_level, incidencia.authorLevel), fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = appString(R.string.issue_last_update, dataAMostrar), style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                    }
                }

                if (!isOwner && userVote != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = if (userVote == 1) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (userVote == 1) confirmedVoteLabel else falseReportVoteLabel,
                                color = if (userVote == 1) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = undoLabel,
                                modifier = Modifier.clickable { onDesferVot() },
                                color = Color.Gray,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isOwner) {
                Button(
                    onClick = onEditar,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Editar", fontWeight = FontWeight.Bold, color = Color.White) }
            } else {
                val jaConfirmat = miVot == 1
                Button(
                    onClick = onConfirmar,
                    enabled = miVot == null,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        disabledContainerColor = if (jaConfirmat) Color(0xFF2E7D32) else Color(0xFFBDBDBD)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (jaConfirmat) "Confirmada ✓" else "Confirmar",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        },
        dismissButton = {
            if (isOwner) {
                Button(
                    onClick = onEsborrar,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(deleteLabel, fontWeight = FontWeight.Bold, color = Color.White) }
            } else {
                val jaRebutjat = miVot == -1
                Button(
                    onClick = onRebutjar,
                    enabled = miVot == null,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        disabledContainerColor = if (jaRebutjat) Color(0xFFD32F2F) else Color(0xFFBDBDBD)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (jaRebutjat) "Reportada ✓" else "Reportar com a fals",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    )
}@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    contentColor: Color,
    backgroundColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = contentColor.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = contentColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VoteIndicator(count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(text = count.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = color)
        Text(text = label, fontSize = 10.sp, color = Color.Gray)
    }
}



