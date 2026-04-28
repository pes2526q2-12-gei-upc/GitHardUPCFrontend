package com.safesteps.map

import android.location.Location
import com.safesteps.data.Coordenada
import com.safesteps.data.Feature
import com.safesteps.data.PuntInteres
import com.safesteps.domain.RoutePriority
import org.maplibre.android.geometry.LatLng

enum class textField {
    NONE,
    ORIGIN,
    DESTINY
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
    val puntsInteres: List<PuntInteres> = emptyList(),
    val mostrarPuntsInteres: Boolean = true,
    val puntInteresSeleccionat: PuntInteres? = null
)

val MapUiState.usesLiveNavigation: Boolean
    get() = modoRuta && activeRouteMode == ActiveRouteMode.USER_LOCATION_NAVIGATION

val MapUiState.showsFixedRouteSummary: Boolean
    get() = modoRuta && activeRouteMode == ActiveRouteMode.FIXED_OVERVIEW
