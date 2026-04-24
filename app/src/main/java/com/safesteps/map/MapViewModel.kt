package com.safesteps.map

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.auth.UserInfo
import com.safesteps.data.Coordenada
import com.safesteps.data.Feature
import com.safesteps.data.PhotonApi
import com.safesteps.data.PuntInteres
import com.safesteps.data.RouteCoordinatesRequest
import com.safesteps.data.RouteType
import com.safesteps.data.obtenirCoordenadesRuta
import com.safesteps.domain.RoutePriority
import com.safesteps.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class MapViewModel(
    private val textProvider: MapTextProvider
) : ViewModel() {
    private companion object {
        const val DEFAULT_DISTANCE_TEXT = "-- km"
        const val DEFAULT_DURATION_TEXT = "-- min"
        const val DEFAULT_ETA_TEXT = "--:--"
        const val OFF_ROUTE_RECALCULATION_THRESHOLD_METERS = 200.0
        const val OFF_ROUTE_RECALCULATION_COOLDOWN_MS = 15_000L
        const val MIN_DISTANCE_FOR_ACTIVE_NAVIGATION_METERS = 30.0
        const val ROUTE_CONSUMPTION_MAX_DISTANCE_METERS = 30.0
    }

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private var currentLanguage: AppLanguage = AppLanguage.default
    private var searchJob: kotlinx.coroutines.Job? = null
    private var navigationRoute: NavigationRouteModel? = null
    private var currentRouteSummary: RouteSummary? = null
    private var lastNavigationProgressMeters: Double = 0.0
    private var lastAutomaticRecalculationAtMs: Long = 0L
    private var currentGoogleId: String? = null

    fun onLanguageChanged(language: AppLanguage) {
        currentLanguage = language
    }

    fun onPrioritySelected(prioridad: RoutePriority) {
        _uiState.update { it.copy(prioridadSeleccionada = prioridad) }
    }

    fun onCurrentUserChanged(user: UserInfo?) {
        currentGoogleId = user?.googleId?.takeIf { it.isNotBlank() }
    }

    fun toggleEstiloSatelite() {
        _uiState.update { it.copy(estiloSatelite = !it.estiloSatelite) }
    }

    fun onLocationPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(locationGranted = granted) }
    }

    fun clearRuta() {
        resetNavigationState()
        _uiState.update {
            it.copy(
                destinoSeleccionado = null,
                textoDestino = "",
                distanceText = DEFAULT_DISTANCE_TEXT,
                durationText = DEFAULT_DURATION_TEXT,
                etaText = DEFAULT_ETA_TEXT,
                rutaCoordenades = emptyList(),
                rutaVisibleCoordenades = emptyList(),
                modoRuta = false,
                navigationCameraFollowing = false,
                routeCompleted = false,
                routeCompletionSummary = null,
                activeNavigationInstruction = null,
                navigationNotice = null,
                adrecesSuggerides = emptyList(),
                campActiu = textField.NONE,
                isTyping = false,
                mostrarOrigen = false,
                puntsInteres = emptyList(),
                puntInteresSeleccionat = null
            )
        }
    }

    fun cancelarRutaVisual() {
        resetNavigationState()
        _uiState.update { it.copy(
            rutaCoordenades = emptyList(),
            rutaVisibleCoordenades = emptyList(),
            modoRuta = false,
            navigationCameraFollowing = false,
            routeCompleted = false,
            routeCompletionSummary = null,
            activeNavigationInstruction = null,
            navigationNotice = null,
            distanceText = DEFAULT_DISTANCE_TEXT,
            durationText = DEFAULT_DURATION_TEXT,
            etaText = DEFAULT_ETA_TEXT,
            calculantRuta = false,
            puntsInteres = emptyList(),
            puntInteresSeleccionat = null
        ) }
    }

    fun updateLocation(location: Location) {
        _uiState.update { it.copy(ultimaUbicacion = location) }
        refreshNavigationProgress(location.toCoordenada())
    }

    fun onNavigationNoticeConsumed() {
        _uiState.update { it.copy(navigationNotice = null) }
    }

    fun onTextoBuscadorModificado(texto: String, campo: textField) {
        _uiState.update {
            it.copy(
                campActiu = campo,
                isTyping = true,
                textoOrigen = if (campo == textField.ORIGIN) texto else it.textoOrigen,
                textoDestino = if (campo == textField.DESTINY) texto else it.textoDestino,
                mostrarOrigen = campo == textField.DESTINY || it.mostrarOrigen
            )
        }

        searchJob?.cancel()

        if (texto.length >= 3) {
            searchJob = viewModelScope.launch {
                kotlinx.coroutines.delay(300)
                try {
                    val queryFormatada = texto.replace(Regex("(?<=[a-zA-Z])\\s+(?=\\d+)"), ", ")

                    val idiomaRecuperat = textProvider.photonLanguage(currentLanguage)

                    val latActual = _uiState.value.ultimaUbicacion?.latitude
                    val lonActual = _uiState.value.ultimaUbicacion?.longitude

                    val respuesta = PhotonApi.service.findAddress(
                        query = queryFormatada,
                        lang = idiomaRecuperat,
                        lat = latActual,
                        lon = lonActual
                    )

                    val resultatsNets = respuesta.features
                        .filter { feature ->
                            !feature.properties.street.isNullOrBlank() || !feature.properties.name.isNullOrBlank()
                        }
                        .distinctBy { it.properties.getAddress().lowercase(Locale.ROOT) }
                        .take(5)

                    _uiState.update { state ->
                        state.copy(adrecesSuggerides = resultatsNets)
                    }
                } catch (e: Exception) {
                    Log.e("PhotonAPI", "Error en la petició: ${e.message}")
                    _uiState.update { it.copy(adrecesSuggerides = emptyList()) }
                }
            }
        } else {
            _uiState.update { it.copy(adrecesSuggerides = emptyList()) }
        }
    }

    fun onAdrecaSeleccionada(feature: Feature) {
        if (_uiState.value.modoRuta) return

        val puntSeleccionat = LatLng(feature.geometry.latitud, feature.geometry.longitud)
        val textAdreca = feature.properties.getAddress()

        _uiState.update { state ->
            if (state.campActiu == textField.ORIGIN) {
                state.copy(
                    textoOrigen = textAdreca,
                    origenSeleccionado = puntSeleccionat,
                    campActiu = textField.NONE,
                    isTyping = false,
                    adrecesSuggerides = emptyList()
                )
            } else {
                state.copy(
                    textoDestino = textAdreca,
                    destinoSeleccionado = puntSeleccionat,
                    campActiu = textField.NONE,
                    isTyping = false,
                    adrecesSuggerides = emptyList()
                )
            }
        }
    }

    fun limpiarOrigen() {
        _uiState.update { it.copy(
            origenSeleccionado = null,
            textoOrigen = ""
        ) }
    }

    fun limpiarDestino() {
        _uiState.update { it.copy(
            destinoSeleccionado = null,
            textoDestino = ""
        ) }
    }



    fun onMapClicked(point: LatLng) {
        if (_uiState.value.modoRuta) return

        _uiState.update {
            it.copy(
                destinoSeleccionado = point,
                textoDestino = textProvider.searchingAddress(currentLanguage),
                mostrarOrigen = true,
                distanceText = DEFAULT_DISTANCE_TEXT,
                durationText = DEFAULT_DURATION_TEXT,
                etaText = DEFAULT_ETA_TEXT
            )
        }

        viewModelScope.launch {
            val direccionReal = getTextoDestino(point)
            _uiState.update { it.copy(textoDestino = direccionReal) }
        }
    }

    fun marcarZoomInicialHecho() {
        _uiState.update { it.copy(firstLocationZoomDone = true) }
    }

    fun prepararNuevaSesionMapa() {
        _uiState.update {
            it.copy(
                mapaListo = false,
                firstLocationZoomDone = false
            )
        }
    }

    fun calcularRuta(
        origenLong: Double,
        origenLat: Double,
        destiLong: Double,
        destiLat: Double
    ) {
        if (_uiState.value.calculantRuta) return

        val prioridad = _uiState.value.prioridadSeleccionada
        val routeType = routeTypeFor(prioridad)
        Log.d("PRUEBA_RUTA", "Llamando a calcularRuta. Prioridad actual: $prioridad")

        _uiState.update { it.copy(calculantRuta = true) }
        viewModelScope.launch {
            try {
                val infoRuta = obtenirCoordenadesRuta(
                    RouteCoordinatesRequest(
                        googleId = currentGoogleId,
                        origenLong = origenLong,
                        origenLat = origenLat,
                        destiLong = destiLong,
                        destiLat = destiLat,
                        nRoutes = 1,
                        routeType = routeType
                    )
                )

                val coordenadas = infoRuta.first
                val tiempoDistancia = infoRuta.second
                val puntosInteres = infoRuta.third

                Log.d("ROUTE_VM", "Coordenadas = ${coordenadas.size}")
                Log.d("time", "time = ${tiempoDistancia.first}")
                Log.d("distance", "distancia = ${tiempoDistancia.second}")
                Log.d("POIS", "Puntos encontrados = ${puntosInteres.size}")

                val routeDurationMinutes = resolveRouteDurationMinutes(tiempoDistancia)
                applyCalculatedRoute(
                    coordenadas = coordenadas,
                    tiempoDistancia = tiempoDistancia,
                    routeDurationMinutes = routeDurationMinutes,
                    puntosInteres = puntosInteres
                )
            } catch (e: Exception) {
                Log.e("ROUTE_VM", "Error calculant la ruta: ${e.message}")
            } finally {
                _uiState.update { it.copy(calculantRuta = false) }
            }
        }
    }

    fun iniciarNavegacio() {
        lastNavigationProgressMeters = 0.0
        _uiState.update {
            it.copy(
                modoRuta = true,
                navigationCameraFollowing = true,
                routeCompleted = false,
                routeCompletionSummary = null,
                navigationNotice = null
            )
        }
        refreshNavigationProgress(
            currentLocation = _uiState.value.ultimaUbicacion?.toCoordenada() ?: routeStartCoordinate(),
            force = true
        )
    }

    fun onMapaListo() {
        _uiState.update { it.copy(mapaListo = true) }
    }

    fun onNavigationCameraDismissedByGesture() {
        _uiState.update { state ->
            if (!state.modoRuta || !state.navigationCameraFollowing) {
                state
            } else {
                state.copy(navigationCameraFollowing = false)
            }
        }
    }

    fun resumeNavigationCameraTracking() {
        _uiState.update { state ->
            if (!state.modoRuta || state.routeCompleted) {
                state
            } else {
                state.copy(navigationCameraFollowing = true)
            }
        }
    }

    fun togglePuntsInteres() {
        _uiState.update { it.copy(mostrarPuntsInteres = !it.mostrarPuntsInteres) }
    }

    fun onPuntInteresSeleccionat(punt: com.safesteps.data.PuntInteres?) {
        _uiState.update { it.copy(puntInteresSeleccionat = punt) }
    }

    private suspend fun getTextoDestino(point: LatLng): String {
        return try {
            val resposta = PhotonApi.service.reverseGeocode(
                lat = point.latitude,
                lon = point.longitude,
                lang = textProvider.photonLanguage(currentLanguage)
            )
            val adreca = resposta.features.firstOrNull()?.properties?.getAddress()

            if (adreca.isNullOrBlank() || adreca.contains("LatLng") || adreca.contains("Location")) {
                "${point.longitude}, ${point.latitude}"
            } else {
                adreca
            }
        } catch (_: Exception) {
            textProvider.selectedMapLocation(currentLanguage)
        }
    }

    private fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters < 1000.0) {
            "${distanceMeters.roundToInt()} m"
        } else {
            String.format(localeForCurrentLanguage(), "%.1f km", distanceMeters / 1000.0)
        }
    }

    private fun formatDuration(durationMinutes: Int): String {
        if (durationMinutes <= 0) {
            return DEFAULT_DURATION_TEXT
        }

        if (durationMinutes <= 60) {
            return "$durationMinutes min"
        }

        val hours = durationMinutes / 60
        val minutes = durationMinutes % 60
        return "${hours}h y ${minutes}min"
    }

    private fun formatEta(durationMinutes: Int): String {
        if (durationMinutes <= 0) return DEFAULT_ETA_TEXT
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MINUTE, durationMinutes)
        }
        return SimpleDateFormat("h:mm a", localeForCurrentLanguage()).format(calendar.time)
    }

    private fun estimateMinutesFromDistanceMeters(distanceMeters: Double): Int {
        val km = distanceMeters / 1000.0
        return max(1, (km * 12.0).roundToInt())
    }

    private fun localeForCurrentLanguage(): Locale {
        return Locale.forLanguageTag(currentLanguage.languageTag)
    }

    private fun routeTypeFor(priority: RoutePriority): RouteType {
        return when (priority) {
            RoutePriority.SAFETY -> RouteType.SEGURETAT
            RoutePriority.ACCESSIBILITY -> RouteType.CONFORT
            RoutePriority.HEAT -> RouteType.CLIMA
            RoutePriority.PERSONALIZED -> RouteType.PERSONALITZAT
        }
    }

    private fun resolveRouteDurationMinutes(tiempoDistancia: Pair<Int, Double>): Int {
        return when {
            tiempoDistancia.first > 0 -> tiempoDistancia.first
            tiempoDistancia.second > 0.0 -> estimateMinutesFromDistanceMeters(tiempoDistancia.second)
            else -> 0
        }
    }

    private fun applyCalculatedRoute(
        coordenadas: List<Coordenada>,
        tiempoDistancia: Pair<Int, Double>,
        routeDurationMinutes: Int,
        puntosInteres: List<PuntInteres>
    ) {
        val currentState = _uiState.value
        val totalDistanceMeters = tiempoDistancia.second
            .takeIf { distance -> distance > 0.0 }
            ?: estimateTotalDistanceMeters(coordenadas)

        navigationRoute = buildNavigationRouteModel(
            coordinates = coordenadas,
            origin = routeOriginForState(currentState),
            destination = currentState.destinoSeleccionado?.toCoordenada()
        )
        currentRouteSummary = RouteSummary(
            totalDurationMinutes = routeDurationMinutes,
            totalDistanceMeters = totalDistanceMeters
        )
        lastNavigationProgressMeters = 0.0

        _uiState.update {
            it.copy(
                rutaCoordenades = coordenadas,
                rutaVisibleCoordenades = coordenadas,
                routeCompleted = false,
                routeCompletionSummary = null,
                distanceText = tiempoDistancia.second
                    .takeIf { distance -> distance > 0.0 }
                    ?.let(::formatDistance)
                    ?: it.distanceText,
                durationText = routeDurationMinutes
                    .takeIf { duration -> duration > 0 }
                    ?.let(::formatDuration)
                    ?: it.durationText,
                etaText = routeDurationMinutes
                    .takeIf { duration -> duration > 0 }
                    ?.let(::formatEta)
                    ?: it.etaText,
                activeNavigationInstruction = if (it.modoRuta) it.activeNavigationInstruction else null,
                adrecesSuggerides = emptyList(),
                campActiu = textField.NONE,
                isTyping = false,
                puntsInteres = puntosInteres
            )
        }

        if (_uiState.value.modoRuta) {
            refreshNavigationProgress(
                currentLocation = _uiState.value.ultimaUbicacion?.toCoordenada() ?: routeStartCoordinate(),
                force = true
            )
        }
    }

    private fun resetNavigationState() {
        navigationRoute = null
        currentRouteSummary = null
        lastNavigationProgressMeters = 0.0
        lastAutomaticRecalculationAtMs = 0L
    }

    private fun refreshNavigationProgress(
        currentLocation: Coordenada?,
        force: Boolean = false
    ) {
        val route = navigationRoute ?: return
        val location = currentLocation ?: return
        val state = _uiState.value

        if (!force && !state.modoRuta) {
            return
        }

        if (state.routeCompleted) {
            return
        }

        val progress = resolveNavigationProgress(
            route = route,
            currentLocation = location,
            minimumProgressMeters = lastNavigationProgressMeters
        )

        if (shouldCompleteRoute(progress)) {
            completeRoute(progress)
            return
        }

        if (shouldAutomaticallyRecalculateRoute(state, progress)) {
            triggerAutomaticRouteRecalculation(location)
            return
        }

        lastNavigationProgressMeters = progress.progressMeters

        val remainingDurationMinutes = remainingDurationMinutes(progress.instruction.remainingDistanceMeters)

        _uiState.update {
            it.copy(
                activeNavigationInstruction = progress.instruction,
                distanceText = formatDistance(progress.instruction.remainingDistanceMeters),
                durationText = formatDuration(remainingDurationMinutes),
                etaText = formatEta(remainingDurationMinutes),
                rutaVisibleCoordenades = if (shouldConsumeRouteProgress(progress)) {
                    remainingRouteCoordinates(
                        route = route,
                        progressMeters = progress.currentProgressMeters
                    )
                } else {
                    it.rutaVisibleCoordenades
                }
            )
        }
    }

    private fun shouldAutomaticallyRecalculateRoute(
        state: MapUiState,
        progress: NavigationProgressResult
    ): Boolean {
        if (!state.modoRuta || state.calculantRuta || state.routeCompleted) {
            return false
        }

        if (state.destinoSeleccionado == null) {
            return false
        }

        if (progress.distanceToRouteMeters <= OFF_ROUTE_RECALCULATION_THRESHOLD_METERS) {
            return false
        }

        if (progress.instruction.remainingDistanceMeters <= MIN_DISTANCE_FOR_ACTIVE_NAVIGATION_METERS) {
            return false
        }

        val now = System.currentTimeMillis()
        return now - lastAutomaticRecalculationAtMs >= OFF_ROUTE_RECALCULATION_COOLDOWN_MS
    }

    private fun triggerAutomaticRouteRecalculation(currentLocation: Coordenada) {
        val destination = _uiState.value.destinoSeleccionado ?: return
        lastAutomaticRecalculationAtMs = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                navigationNotice = textProvider.routeRecalculated(
                    language = currentLanguage,
                    distanceText = formatDistance(OFF_ROUTE_RECALCULATION_THRESHOLD_METERS)
                )
            )
        }
        calcularRuta(
            origenLong = currentLocation.lon,
            origenLat = currentLocation.lat,
            destiLong = destination.longitude,
            destiLat = destination.latitude
        )
    }

    private fun shouldCompleteRoute(progress: NavigationProgressResult): Boolean {
        return progress.instruction.maneuver == NavigationManeuver.ARRIVE &&
            progress.instruction.remainingDistanceMeters <= ARRIVAL_DISTANCE_METERS
    }

    private fun completeRoute(progress: NavigationProgressResult) {
        val routeSummary = currentRouteSummary
        val totalDistanceMeters = routeSummary?.totalDistanceMeters
            ?.takeIf { it > 0.0 }
            ?: navigationRoute?.totalDistanceMeters
            ?: progress.progressMeters
        val totalDurationMinutes = routeSummary?.totalDurationMinutes ?: 0
        lastNavigationProgressMeters = navigationRoute?.totalDistanceMeters ?: progress.progressMeters

        _uiState.update {
            it.copy(
                navigationCameraFollowing = false,
                routeCompleted = true,
                routeCompletionSummary = RouteCompletionSummary(
                    distanceText = formatDistance(totalDistanceMeters),
                    durationText = totalDurationMinutes
                        .takeIf { duration -> duration > 0 }
                        ?.let(::formatDuration)
                        ?: it.durationText
                ),
                activeNavigationInstruction = progress.instruction,
                distanceText = formatDistance(progress.instruction.remainingDistanceMeters),
                durationText = formatDuration(0),
                etaText = DEFAULT_ETA_TEXT,
                rutaVisibleCoordenades = emptyList()
            )
        }
    }

    private fun shouldConsumeRouteProgress(progress: NavigationProgressResult): Boolean {
        return !progress.instruction.isCorrective &&
            progress.distanceToRouteMeters <= ROUTE_CONSUMPTION_MAX_DISTANCE_METERS
    }

    private fun remainingDurationMinutes(remainingDistanceMeters: Double): Int {
        if (remainingDistanceMeters <= 0.0) {
            return 0
        }

        val routeSummary = currentRouteSummary
        return when {
            routeSummary != null &&
                routeSummary.totalDurationMinutes > 0 &&
                routeSummary.totalDistanceMeters > 0.0 -> {
                max(
                    1,
                    (
                        routeSummary.totalDurationMinutes.toDouble() *
                            remainingDistanceMeters /
                            routeSummary.totalDistanceMeters
                        ).roundToInt()
                )
            }

            else -> estimateMinutesFromDistanceMeters(remainingDistanceMeters)
        }
    }

    private fun estimateTotalDistanceMeters(coordenadas: List<Coordenada>): Double {
        return buildNavigationRouteModel(coordenadas)?.totalDistanceMeters ?: 0.0
    }

    private fun routeOriginForState(state: MapUiState): Coordenada? {
        return if (state.modoRuta) {
            state.ultimaUbicacion?.toCoordenada() ?: state.origenSeleccionado?.toCoordenada()
        } else {
            state.origenSeleccionado?.toCoordenada() ?: state.ultimaUbicacion?.toCoordenada()
        }
    }

    private fun routeStartCoordinate(): Coordenada? {
        return navigationRoute?.points?.firstOrNull()
    }

    private fun LatLng.toCoordenada(): Coordenada = Coordenada(lat = latitude, lon = longitude)

    private fun Location.toCoordenada(): Coordenada = Coordenada(lat = latitude, lon = longitude)

    private data class RouteSummary(
        val totalDurationMinutes: Int,
        val totalDistanceMeters: Double
    )
}
