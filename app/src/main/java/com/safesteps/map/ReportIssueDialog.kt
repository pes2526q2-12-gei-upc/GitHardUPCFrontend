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

private val DIALOG_ACCENT_ORANGE = Color(0xFFF57C00)

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
    val borderColor = DIALOG_ACCENT_ORANGE
    val colorText   = DIALOG_ACCENT_ORANGE
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
                    cursorBrush = SolidColor(colorText),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotBlank()) {
                IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = appString(R.string.delete_action),
                        tint = colorText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationSuggestions(
    suggestions: List<Feature>,
    onSelect: (Feature) -> Unit,
) {
    AnimatedVisibility(visible = suggestions.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp))
                .background(Color.White, RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(20.dp))
                .padding(vertical = 8.dp)
        ) {
            suggestions.forEachIndexed { index, feat ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(feat) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFFF57C00),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = feat.properties.getAddress(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black,
                        modifier = Modifier.weight(1f),
                        maxLines = 2
                    )
                }
                if (index != suggestions.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = Color(0xFFEEEEEE),
                        modifier = Modifier.padding(horizontal = 16.dp)
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
    isEditMode: Boolean
) {
    if (isEditMode) return
    Surface(
        color = Color(0xFFE8F0FE),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFB9D4FB)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable { onSelect() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(
                Icons.Default.MyLocation,
                contentDescription = null,
                tint = Color(0xFF1A73E8)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appString(R.string.my_location),
                    color = Color(0xFF1A73E8),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = defaultLocationText,
                    color = Color(0xFF1A73E8),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
                    NotLoggedInDialogContent(
                        onDismiss = onDismiss,
                        onNavigateToLogin = onNavigateToLogin
                    )
                } else {
                    LoggedInReportDialogContent(
                        defaultLocationText = defaultLocationText,
                        onDismiss = onDismiss,
                        onConfirm = onConfirm,
                        initialType = initialType,
                        initialDescription = initialDescription,
                        isEditMode = isEditMode,
                        initialCoord = initialCoord,
                        myLocationText = myLocationText,
                        myLocationCoord = myLocationCoord
                    )
                }
            }
        }
    }
}

@Composable
private fun NotLoggedInDialogContent(
    onDismiss: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = DIALOG_ACCENT_ORANGE)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = appString(R.string.report_issue_failed_label),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = DIALOG_ACCENT_ORANGE
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
            colors = ButtonDefaults.buttonColors(containerColor = DIALOG_ACCENT_ORANGE)
        ) {
            Text(appString(R.string.log_in), color = Color.White)
        }
    }
}

@Composable
private fun LoggedInReportDialogContent(
    defaultLocationText: String,
    onDismiss: () -> Unit,
    onConfirm: (IssueType, String, String, Coord) -> Unit,
    initialType: IssueType,
    initialDescription: String,
    isEditMode: Boolean,
    initialCoord: Coord,
    myLocationText: String?,
    myLocationCoord: Coord?
) {
    var currentCoord by remember { mutableStateOf(initialCoord) }
    var tipusSeleccionat by remember(initialType) { mutableStateOf(initialType) }
    var descripcioText by remember(initialDescription) { mutableStateOf(initialDescription) }
    var errorMsgRes by remember { mutableStateOf<Int?>(null) }
    var ubicacioText by remember { mutableStateOf(defaultLocationText) }
    var wrapperState by remember {
        mutableStateOf(initialWrapperState(isEditMode))
    }
    var userHasModifiedLocation by remember { mutableStateOf(false) }

    LaunchedEffect(defaultLocationText) {
        if (!userHasModifiedLocation) {
            ubicacioText = defaultLocationText
        }
    }

    val effectiveMyLocationText = myLocationText ?: defaultLocationText
    val effectiveMyLocationCoord = myLocationCoord ?: initialCoord

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
    IssueTypeSelector(
        selectedType = tipusSeleccionat,
        onSelect = { tipusSeleccionat = it }
    )

    Text(
        appString(R.string.report_location_label),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.Black
    )
    if (isEditMode) {
        LockedLocationCard(text = ubicacioText)
    } else {
        EditableLocationSection(
            wrapperState = wrapperState,
            ubicacioText = ubicacioText,
            effectiveMyLocationText = effectiveMyLocationText,
            effectiveMyLocationCoord = effectiveMyLocationCoord,
            onWrapperStateChange = { wrapperState = it },
            onUbicacioTextChange = { ubicacioText = it },
            onUserModified = { userHasModifiedLocation = true },
            onCoordChange = { currentCoord = it },
            onErrorReset = { errorMsgRes = null }
        )
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
            focusedBorderColor = DIALOG_ACCENT_ORANGE,
            unfocusedBorderColor = Color.Gray,
            cursorColor = Color.Black
        )
    )

    errorMsgRes?.let { resId ->
        Text(
            text = appString(resId),
            color = DIALOG_ACCENT_ORANGE,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }

    DialogActionRow(
        onCancel = onDismiss,
        onConfirm = {
            errorMsgRes = validateAndSubmit(
                wrapperState = wrapperState,
                ubicacioText = ubicacioText,
                descripcioText = descripcioText,
                tipusSeleccionat = tipusSeleccionat,
                currentCoord = currentCoord,
                onConfirm = onConfirm
            )
        }
    )
}

private fun initialWrapperState(isEditMode: Boolean): LocationWrapperState =
    if (isEditMode) LocationWrapperState.CUSTOM_ADDRESS else LocationWrapperState.MY_LOCATION

private fun validateAndSubmit(
    wrapperState: LocationWrapperState,
    ubicacioText: String,
    descripcioText: String,
    tipusSeleccionat: IssueType,
    currentCoord: Coord,
    onConfirm: (IssueType, String, String, Coord) -> Unit
): Int? {
    val invalid = wrapperState == LocationWrapperState.TYPING ||
            ubicacioText.isBlank() ||
            descripcioText.isBlank()
    if (invalid) {
        return R.string.report_error_msg_text
    }
    onConfirm(tipusSeleccionat, ubicacioText, descripcioText, currentCoord)
    return null
}

@Composable
private fun IssueTypeSelector(
    selectedType: IssueType,
    onSelect: (IssueType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IssueType.entries.forEach { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onSelect(type) },
                label = { Text(appString(type.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = Color.DarkGray,
                    selectedLabelColor = Color.White,
                    selectedContainerColor = DIALOG_ACCENT_ORANGE
                )
            )
        }
    }
}

@Composable
private fun LockedLocationCard(text: String) {
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
                    tint = DIALOG_ACCENT_ORANGE
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    color = DIALOG_ACCENT_ORANGE,
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
}

@Composable
private fun EditableLocationSection(
    wrapperState: LocationWrapperState,
    ubicacioText: String,
    effectiveMyLocationText: String,
    effectiveMyLocationCoord: Coord,
    onWrapperStateChange: (LocationWrapperState) -> Unit,
    onUbicacioTextChange: (String) -> Unit,
    onUserModified: () -> Unit,
    onCoordChange: (Coord) -> Unit,
    onErrorReset: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var localSuggestions by remember { mutableStateOf<List<Feature>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val performSearch: (String) -> Unit = { query ->
        searchJob?.cancel()
        if (query.length < 3) {
            localSuggestions = emptyList()
        } else {
            searchJob = scope.launch {
                localSuggestions = fetchLocationSuggestions(query)
            }
        }
    }
    val resetSearch: () -> Unit = {
        searchJob?.cancel()
        onUbicacioTextChange("")
        localSuggestions = emptyList()
    }

    when (wrapperState) {
        LocationWrapperState.MY_LOCATION,
        LocationWrapperState.CUSTOM_ADDRESS -> {
            SelectedLocationCard(
                wrapperState = wrapperState,
                ubicacioText = ubicacioText,
                onClick = {
                    onUserModified()
                    onWrapperStateChange(LocationWrapperState.TYPING)
                    resetSearch()
                }
            )
        }
        LocationWrapperState.TYPING -> {
            LocationTypingSection(
                ubicacioText = ubicacioText,
                effectiveMyLocationText = effectiveMyLocationText,
                suggestions = localSuggestions,
                onUbicacioTextChange = { nouText ->
                    onUserModified()
                    onUbicacioTextChange(nouText)
                    onErrorReset()
                    performSearch(nouText)
                },
                onClear = resetSearch,
                onMyLocationSelected = {
                    onUserModified()
                    onUbicacioTextChange(effectiveMyLocationText)
                    onCoordChange(effectiveMyLocationCoord)
                    onWrapperStateChange(LocationWrapperState.MY_LOCATION)
                    searchJob?.cancel()
                    localSuggestions = emptyList()
                },
                onSuggestionSelected = { feature ->
                    onUserModified()
                    onUbicacioTextChange(feature.properties.getAddress())
                    onCoordChange(Coord(feature.geometry.latitud, feature.geometry.longitud))
                    onWrapperStateChange(LocationWrapperState.CUSTOM_ADDRESS)
                    searchJob?.cancel()
                    localSuggestions = emptyList()
                }
            )
        }
    }
}

@Composable
private fun LocationTypingSection(
    ubicacioText: String,
    effectiveMyLocationText: String,
    suggestions: List<Feature>,
    onUbicacioTextChange: (String) -> Unit,
    onClear: () -> Unit,
    onMyLocationSelected: () -> Unit,
    onSuggestionSelected: (Feature) -> Unit
) {
    Column {
        LocationSearchField(
            value = ubicacioText,
            onValueChange = onUbicacioTextChange,
            onClear = onClear
        )
        MyLocationRow(
            defaultLocationText = effectiveMyLocationText,
            onSelect = onMyLocationSelected,
            isEditMode = false
        )
        LocationSuggestions(
            suggestions = suggestions,
            onSelect = onSuggestionSelected
        )
    }
}

private suspend fun fetchLocationSuggestions(query: String): List<Feature> {
    return try {
        delay(300)
        val queryFormatada = query.replace(Regex("(?<=[a-zA-Z])\\s+(?=\\d+)"), ", ")
        val resposta = PhotonApi.service.findAddress(query = queryFormatada)
        resposta.features
            .filter { hasUsefulProperty(it) }
            .distinctBy { it.properties.getAddress().lowercase() }
            .take(5)
    } catch (_: Exception) {
        emptyList()
    }
}

private fun hasUsefulProperty(feature: Feature): Boolean {
    val hasStreet = !feature.properties.street.isNullOrBlank()
    val hasName = !feature.properties.name.isNullOrBlank()
    return hasStreet || hasName
}

@Composable
private fun SelectedLocationCard(
    wrapperState: LocationWrapperState,
    ubicacioText: String,
    onClick: () -> Unit
) {
    val isMyLocation = wrapperState == LocationWrapperState.MY_LOCATION
    val style = locationCardStyle(isMyLocation)

    Surface(
        color = style.bgColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, style.borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Icon(style.iconVec, contentDescription = null, tint = style.contentColor)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = ubicacioText,
                color = style.contentColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = appString(R.string.delete_action),
                    tint = style.contentColor
                )
            }
        }
    }
}

private data class LocationCardStyle(
    val bgColor: Color,
    val borderColor: Color,
    val contentColor: Color,
    val iconVec: ImageVector
)

private fun locationCardStyle(isMyLocation: Boolean): LocationCardStyle {
    return if (isMyLocation) {
        LocationCardStyle(
            bgColor = Color(0xFFE8F0FE),
            borderColor = Color(0xFFB9D4FB),
            contentColor = Color(0xFF1A73E8),
            iconVec = Icons.Default.MyLocation
        )
    } else {
        LocationCardStyle(
            bgColor = Color(0xFFFFF3E0),
            borderColor = Color(0xFFFFCC80),
            contentColor = DIALOG_ACCENT_ORANGE,
            iconVec = Icons.Default.LocationOn
        )
    }
}

@Composable
private fun DialogActionRow(
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onCancel) {
            Text(text = appString(R.string.cancel_action), color = Color.DarkGray)
        }
        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = DIALOG_ACCENT_ORANGE)
        ) {
            Text(text = appString(R.string.confirm_action), color = Color.White)
        }
    }
}


@Composable
fun showIssueDialog(
    incidencia: IssueResponseDTO,
    isOwner: Boolean,
    isLoggedIn: Boolean = true,
    miVot: Int? = null,
    onDismiss: () -> Unit,
    onConfirmar: () -> Unit,
    onRebutjar: () -> Unit,
    onEditar: () -> Unit,
    onEsborrar: () -> Unit,
    onDesferVot: () -> Unit = {}
) {
    val labels = rememberShowIssueDialogLabels(incidencia)
    var adrecaText by remember { mutableStateOf(labels.loadingAddressLabel) }

    LaunchedEffect(incidencia.coordinates) {
        adrecaText = resolveIssueDisplayAddress(
            incidencia = incidencia,
            unknownAddressLabel = labels.unknownAddressLabel,
            coordinatesFallbackLabel = labels.coordinatesFallbackLabel
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(0.92f).padding(16.dp),
        title = {
            Text(
                text = labels.issueDetailsLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1A1C1E)
            )
        },
        text = {
            IssueDialogBody(
                incidencia = incidencia,
                isOwner = isOwner,
                miVot = miVot,
                adrecaText = adrecaText,
                labels = labels,
                onDesferVot = onDesferVot
            )
        },
        confirmButton = {
            if (isLoggedIn) {
                ConfirmActionButton(
                    isOwner = isOwner,
                    miVot = miVot,
                    onEditar = onEditar,
                    onConfirmar = onConfirmar
                )
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = appString(R.string.close),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        },
        dismissButton = {
            if (isLoggedIn) {
                DismissActionButton(
                    isOwner = isOwner,
                    miVot = miVot,
                    deleteLabel = labels.deleteLabel,
                    onEsborrar = onEsborrar,
                    onRebutjar = onRebutjar
                )
            }
        }
    )
}

private data class ShowIssueDialogLabels(
    val loadingAddressLabel: String,
    val coordinatesFallbackLabel: String,
    val issueTypeLabel: String,
    val issueTypeValue: String,
    val issueStatusLabel: String,
    val issueStatusAccepted: String,
    val issueStatusPending: String,
    val issueLocationLabel: String,
    val positiveVotesLabel: String,
    val negativeVotesLabel: String,
    val descriptionLabel: String,
    val emptyDescriptionLabel: String,
    val anonymousUserLabel: String,
    val undoLabel: String,
    val confirmedVoteLabel: String,
    val falseReportVoteLabel: String,
    val issueDetailsLabel: String,
    val unknownAddressLabel: String,
    val deleteLabel: String
)

@Composable
private fun rememberShowIssueDialogLabels(
    incidencia: IssueResponseDTO
): ShowIssueDialogLabels {
    return ShowIssueDialogLabels(
        loadingAddressLabel = appString(R.string.issue_loading_address),
        coordinatesFallbackLabel = appString(
            R.string.issue_coordinates_fallback,
            incidencia.coordinates.lat,
            incidencia.coordinates.lon
        ),
        issueTypeLabel = appString(R.string.issue_type_card_label),
        issueTypeValue = issueTypeValueLabel(incidencia.type),
        issueStatusLabel = appString(R.string.issue_status_card_label),
        issueStatusAccepted = appString(R.string.issue_status_accepted),
        issueStatusPending = appString(R.string.issue_status_pending),
        issueLocationLabel = appString(R.string.issue_location_card_label),
        positiveVotesLabel = appString(R.string.issue_positive_votes_label),
        negativeVotesLabel = appString(R.string.issue_negative_votes_label),
        descriptionLabel = appString(R.string.issue_description_title),
        emptyDescriptionLabel = appString(R.string.issue_description_empty),
        anonymousUserLabel = appString(R.string.issue_anonymous_user),
        undoLabel = appString(R.string.issue_undo_action),
        confirmedVoteLabel = appString(R.string.issue_vote_confirmed),
        falseReportVoteLabel = appString(R.string.issue_vote_reported_false),
        issueDetailsLabel = appString(R.string.issue_details_title),
        unknownAddressLabel = appString(R.string.issue_unknown_address),
        deleteLabel = appString(R.string.delete_action)
    )
}

@Composable
private fun issueTypeValueLabel(type: com.safesteps.data.IssueApiType): String {
    return when (type) {
        com.safesteps.data.IssueApiType.OBRES -> appString(R.string.issue_type_worksite)
        com.safesteps.data.IssueApiType.ACCESSIBILITAT -> appString(R.string.issue_type_accessibility)
        com.safesteps.data.IssueApiType.SEGURETAT -> appString(R.string.issue_type_security)
        com.safesteps.data.IssueApiType.ALTRES -> appString(R.string.issue_type_others)
    }
}

private suspend fun resolveIssueDisplayAddress(
    incidencia: IssueResponseDTO,
    unknownAddressLabel: String,
    coordinatesFallbackLabel: String
): String {
    return try {
        val response = withContext(Dispatchers.IO) {
            PhotonApi.service.reverseGeocode(
                lat = incidencia.coordinates.lat,
                lon = incidencia.coordinates.lon
            )
        }
        response.features.firstOrNull()?.properties?.getAddress() ?: unknownAddressLabel
    } catch (_: Exception) {
        coordinatesFallbackLabel
    }
}

private fun extractIssueDate(incidencia: IssueResponseDTO): String {
    val source = incidencia.updatedAt.takeIf { !it.isNullOrBlank() } ?: incidencia.createdAt
    return source.substringBefore("T")
}

@Composable
private fun IssueDialogBody(
    incidencia: IssueResponseDTO,
    isOwner: Boolean,
    miVot: Int?,
    adrecaText: String,
    labels: ShowIssueDialogLabels,
    onDesferVot: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        IssueOverviewCards(incidencia = incidencia, labels = labels)
        Spacer(modifier = Modifier.height(14.dp))
        IssueLocationCard(adreca = adrecaText, locationLabel = labels.issueLocationLabel)
        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
        Spacer(modifier = Modifier.height(20.dp))
        IssueVoteIndicators(
            positiveVotes = incidencia.positiveVotes,
            negativeVotes = incidencia.negativeVotes,
            positiveLabel = labels.positiveVotesLabel,
            negativeLabel = labels.negativeVotesLabel
        )
        Spacer(modifier = Modifier.height(20.dp))
        IssueDescriptionSection(
            description = incidencia.description,
            descriptionLabel = labels.descriptionLabel,
            emptyDescriptionLabel = labels.emptyDescriptionLabel
        )
        Spacer(modifier = Modifier.height(24.dp))
        IssueAuthorCard(
            authorName = incidencia.authorName,
            authorLevel = incidencia.authorLevel,
            anonymousLabel = labels.anonymousUserLabel,
            lastUpdateText = appString(R.string.issue_last_update, extractIssueDate(incidencia))
        )
        if (!isOwner && miVot != null) {
            Spacer(modifier = Modifier.height(16.dp))
            UserVoteFeedbackBar(
                miVot = miVot,
                confirmedLabel = labels.confirmedVoteLabel,
                falseReportLabel = labels.falseReportVoteLabel,
                undoLabel = labels.undoLabel,
                onUndo = onDesferVot
            )
        }
    }
}

@Composable
private fun IssueOverviewCards(
    incidencia: IssueResponseDTO,
    labels: ShowIssueDialogLabels
) {
    val isAccepted = incidencia.status.lowercase() == "accepted"
    val statusValue = if (isAccepted) labels.issueStatusAccepted else labels.issueStatusPending
    val statusIcon = if (isAccepted) Icons.Filled.CheckCircle else Icons.Filled.HelpOutline
    val statusContent = if (isAccepted) Color(0xFF2E7D32) else Color(0xFFEF6C00)
    val statusBg = if (isAccepted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoCard(
            modifier = Modifier.weight(1f),
            label = labels.issueTypeLabel,
            value = labels.issueTypeValue,
            icon = Icons.Filled.Label,
            contentColor = Color(0xFF1976D2),
            backgroundColor = Color(0xFFE3F2FD)
        )
        InfoCard(
            modifier = Modifier.weight(1f),
            label = labels.issueStatusLabel,
            value = statusValue,
            icon = statusIcon,
            contentColor = statusContent,
            backgroundColor = statusBg
        )
    }
}

@Composable
private fun IssueLocationCard(adreca: String, locationLabel: String) {
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
                    text = locationLabel,
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = adreca,
                    color = Color(0xFF1A1C1E),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun IssueVoteIndicators(
    positiveVotes: Int,
    negativeVotes: Int,
    positiveLabel: String,
    negativeLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        VoteIndicator(
            count = positiveVotes,
            icon = Icons.Filled.ThumbUp,
            color = Color(0xFF4CAF50),
            label = positiveLabel
        )
        VoteIndicator(
            count = negativeVotes,
            icon = Icons.Filled.ThumbDown,
            color = Color(0xFFF44336),
            label = negativeLabel
        )
    }
}

@Composable
private fun IssueDescriptionSection(
    description: String?,
    descriptionLabel: String,
    emptyDescriptionLabel: String
) {
    Text(
        text = descriptionLabel,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Color.Black
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = description ?: emptyDescriptionLabel,
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF444746),
        lineHeight = 20.sp
    )
}

@Composable
private fun IssueAuthorCard(
    authorName: String?,
    authorLevel: Int,
    anonymousLabel: String,
    lastUpdateText: String
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = Color(0xFFF8F9FA),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE9ECEF))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFCED4DA)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = authorName ?: anonymousLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (authorName != null) {
                            Text(
                                text = appString(R.string.issue_user_level, authorLevel),
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = lastUpdateText,
                style = MaterialTheme.typography.labelSmall,
                color = Color.LightGray
            )
        }
    }
}

@Composable
private fun UserVoteFeedbackBar(
    miVot: Int,
    confirmedLabel: String,
    falseReportLabel: String,
    undoLabel: String,
    onUndo: () -> Unit
) {
    val isPositive = miVot == 1
    Surface(
        color = if (isPositive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isPositive) confirmedLabel else falseReportLabel,
                color = if (isPositive) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = undoLabel,
                modifier = Modifier.clickable { onUndo() },
                color = Color.Gray,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ConfirmActionButton(
    isOwner: Boolean,
    miVot: Int?,
    onEditar: () -> Unit,
    onConfirmar: () -> Unit
) {
    if (isOwner) {
        OwnerEditButton(onClick = onEditar)
    } else {
        VoteConfirmButton(miVot = miVot, onClick = onConfirmar)
    }
}

@Composable
private fun OwnerEditButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
        shape = RoundedCornerShape(12.dp)
    ) { Text("Editar", fontWeight = FontWeight.Bold, color = Color.White) }
}

@Composable
private fun VoteConfirmButton(miVot: Int?, onClick: () -> Unit) {
    val jaConfirmat = miVot == 1
    Button(
        onClick = onClick,
        enabled = miVot == null,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2E7D32),
            disabledContainerColor = if (jaConfirmat) Color(0xFF2E7D32) else Color(0xFFBDBDBD)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = if (jaConfirmat) "Confirmada ✓" else "Confirmar",
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun DismissActionButton(
    isOwner: Boolean,
    miVot: Int?,
    deleteLabel: String,
    onEsborrar: () -> Unit,
    onRebutjar: () -> Unit
) {
    if (isOwner) {
        OwnerDeleteButton(label = deleteLabel, onClick = onEsborrar)
    } else {
        VoteRejectButton(miVot = miVot, onClick = onRebutjar)
    }
}

@Composable
private fun OwnerDeleteButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
        shape = RoundedCornerShape(12.dp)
    ) { Text(label, fontWeight = FontWeight.Bold, color = Color.White) }
}

@Composable
private fun VoteRejectButton(miVot: Int?, onClick: () -> Unit) {
    val jaRebutjat = miVot == -1
    Button(
        onClick = onClick,
        enabled = miVot == null,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFD32F2F),
            disabledContainerColor = if (jaRebutjat) Color(0xFFD32F2F) else Color(0xFFBDBDBD)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = if (jaRebutjat) "Reportada ✓" else "Reportar com a fals",
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
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