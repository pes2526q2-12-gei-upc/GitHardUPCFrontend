package com.safesteps.map

import android.location.Location
import androidx.annotation.StringRes
import com.safesteps.R
import com.safesteps.data.Coordenada
import com.safesteps.data.Feature
import com.safesteps.data.IssueResponseDTO
import com.safesteps.data.PuntInteres
import com.safesteps.domain.RoutePriority
import org.maplibre.android.geometry.LatLng

enum class textField {
    NONE,
    ORIGIN,
    DESTINY
}

enum class IssueType(@StringRes val labelRes: Int) {
    OBRES(R.string.issue_type_worksite),
    ACCESSIBILITAT(R.string.issue_type_accessibility),
    SEGURETAT(R.string.issue_type_security),
    ALTRES(R.string.issue_type_others)
}

enum class ActiveRouteMode {
    NONE,
    USER_LOCATION_NAVIGATION,
    FIXED_OVERVIEW
}

data class RouteCompletionSummary(
    val distanceText: String,
    val durationText: String
)

data class EmergencyContactLocation(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val username: String? = null,
    val title: String? = null,
    val body: String? = null,
    val receivedAtMillis: Long = System.currentTimeMillis()
)

data class MapUiState(
    val destinoSeleccionado: LatLng? = null,
    val textoOrigen: String = "",
    val textoDestino: String = "",
    val mapaListo: Boolean = false,
    val locationGranted: Boolean = false,
    val estiloSatelite: Boolean = false,
    val mostrarOrigen: Boolean = false,
    val prioridadSeleccionada: RoutePriority = RoutePriority.SAFETY,
    val ultimaUbicacion: Location? = null,
    val distanceText: String = "-- km",
    val durationText: String = "-- min",
    val etaText: String = "--:--",
    val adrecesSuggerides: List<Feature> = emptyList(),
    val origenSeleccionado: LatLng? = null,
    val isTyping: Boolean = false,
    val firstLocationZoomDone: Boolean = false,
    val campActiu: textField = textField.NONE,
    val rutaCoordenades: List<Coordenada> = emptyList(),
    val activeRouteMode: ActiveRouteMode = ActiveRouteMode.NONE,
    val modoRuta: Boolean = false,
    val navigationCameraFollowing: Boolean = false,
    val routeCompleted: Boolean = false,
    val routeCompletionSummary: RouteCompletionSummary? = null,
    val activeNavigationInstruction: ActiveNavigationInstruction? = null,
    val navigationNotice: String? = null,
    val calculantRuta: Boolean = false,
    val routeColor: String? = null,
    val puntsInteres: List<PuntInteres> = emptyList(),
    val issues: List<IssueResponseDTO> = emptyList(),
    val mostrarPuntsInteres: Boolean = true,
    val puntInteresSeleccionat: PuntInteres? = null,
    val mostrarIncidencies: Boolean = false,
    val incidenciaSeleccionada: IssueResponseDTO? = null,
    val incidenciaEnEdicio: IssueResponseDTO? = null,
    val userVotes: Map<Long, Int> = emptyMap(),
    val emergencyContactLocations: List<EmergencyContactLocation> = emptyList(),
    val pendingEmergencyContactLocation: EmergencyContactLocation? = null,
)

val MapUiState.usesLiveNavigation: Boolean
    get() = modoRuta && activeRouteMode == ActiveRouteMode.USER_LOCATION_NAVIGATION

val MapUiState.showsFixedRouteSummary: Boolean
    get() = modoRuta && activeRouteMode == ActiveRouteMode.FIXED_OVERVIEW
