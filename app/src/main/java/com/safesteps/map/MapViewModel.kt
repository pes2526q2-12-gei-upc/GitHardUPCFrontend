package com.safesteps.map

import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safesteps.auth.UserInfo
import com.safesteps.data.Coord
import com.safesteps.data.Coordenada
import com.safesteps.data.Feature
import com.safesteps.data.IssueApiType
import com.safesteps.data.IssueRequestDTO
import com.safesteps.data.IssueResponseDTO
import com.safesteps.data.PhotonApi
import com.safesteps.data.PuntInteres
import com.safesteps.data.RouteCoordinatesRequest
import com.safesteps.data.RouteType
import com.safesteps.data.VoteRequestDTO
import com.safesteps.data.VotesCache
import com.safesteps.data.actualitzarIncidencia
import com.safesteps.data.crearIncidencia
import com.safesteps.data.eliminarIncidencia
import com.safesteps.data.eliminarVot
import com.safesteps.data.getAllIssues
import com.safesteps.data.obtenirCoordenadesRuta
import com.safesteps.data.votarIncidencia
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
    private val textProvider: MapTextProvider,
    private val votesCache: VotesCache
) : ViewModel() {

    private companion object {
        const val DEFAULT_DISTANCE_TEXT = "-- km"
        const val DEFAULT_DURATION_TEXT = "-- min"
        const val DEFAULT_ETA_TEXT = "--:--"
        const val OFF_ROUTE_RECALCULATION_THRESHOLD_METERS = 200.0
        const val OFF_ROUTE_RECALCULATION_COOLDOWN_MS = 15_000L
        const val MIN_DISTANCE_FOR_ACTIVE_NAVIGATION_METERS = 30.0
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

    init {
        loadIssuesMap()
    }
    fun onLanguageChanged(language: AppLanguage) {
        currentLanguage = language
    }

    fun onPrioritySelected(prioridad: RoutePriority) {
        _uiState.update { it.copy(prioridadSeleccionada = prioridad) }
    }

    fun onCurrentUserChanged(user: UserInfo?) {
        currentGoogleId = user?.googleId?.takeIf { it.isNotBlank() }
        val gid = currentGoogleId
        if (gid != null) {
            val cached = votesCache.load(gid)
            _uiState.update { it.copy(userVotes = cached) }

            /*viewModelScope.launch {
                try {
                    val remote = obtenirVotsUsuari(gid)
                    votesCache.save(gid, remote)
                    _uiState.update { it.copy(userVotes = remote) }
                } catch (e: Exception) {
                    Log.w("MapViewModel", "No s'han pogut sincronitzar vots: ${e.message}")
                    }
            }*/
        } else {
            _uiState.update { it.copy(userVotes = emptyMap()) }
        }
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
                    Log.e("PhotonAPI", "Error en la peticiÃ³: ${e.message}")
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

        if (!isInsideBarcelonaArea(origenLat, origenLong)) {
            _uiState.update { it.copy(navigationNotice = "L'inici de la ruta ha de ser dins de Barcelona") }
            return
        }

        if (!isInsideBarcelonaArea(destiLat, destiLong)) {
            _uiState.update { it.copy(navigationNotice = "El destÃ­ de la ruta ha de ser dins de Barcelona") }
            return
        }

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
                etaText = formatEta(remainingDurationMinutes)
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
                etaText = DEFAULT_ETA_TEXT
            )
        }
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

    fun toggleMenuIncidencies(show: Boolean){
        _uiState.update { it.copy(mostrarIncidencies = show) }
    }

    fun reportIssue(
        tipus: IssueType,
        adrecaText: String,
        descripcio: String,
        textMevaUbicacio: String
    ) {
        val currentGoogleIdLocal = currentGoogleId
        if (currentGoogleIdLocal == null) {
            Log.e("MapViewModel", "No es pot reportar sense estar logat.")
            return
        }

        viewModelScope.launch {
            try {
                val coord = if (adrecaText == textMevaUbicacio) {
                    val loc = _uiState.value.ultimaUbicacion
                    if (loc == null) {
                        Log.e("MapViewModel", "No s'ha pogut obtenir la ubicaciÃ³ GPS actual")
                        return@launch
                    }
                    Coord(lat = loc.latitude, lon = loc.longitude)
                } else {
                    val idiomaRecuperat = textProvider.photonLanguage(currentLanguage)
                    val photonResponse = PhotonApi.service.findAddress(
                        query = adrecaText,
                        lang = idiomaRecuperat,
                        limit = 1
                    )

                    val feature = photonResponse.features.firstOrNull()
                    if (feature == null) {
                        Log.e("MapViewModel", "No s'han pogut trobar coordenades per aquesta adreÃ§a")
                        return@launch
                    }

                    Coord(lat = feature.geometry.latitud, lon = feature.geometry.longitud)
                }

                val (finalLat, finalLon) = calcularCoordenadesLliures(
                    desiredLat = coord.lat,
                    desiredLon = coord.lon,
                    existingIssues = _uiState.value.issues
                )

                val coordDefinitiva = Coord(lat = finalLat, lon = finalLon)

                val tipusApi = when (tipus) {
                    IssueType.OBRES -> IssueApiType.OBRES
                    IssueType.ACCESSIBILITAT -> IssueApiType.ACCESSIBILITAT
                    IssueType.SEGURETAT -> IssueApiType.SEGURETAT
                    IssueType.ALTRES -> IssueApiType.ALTRES
                }

                val request = IssueRequestDTO(
                    googleId = currentGoogleIdLocal,
                    type = tipusApi,
                    description = descripcio,
                    coordinates = coordDefinitiva
                )

                val resposta = crearIncidencia(request)

                _uiState.update { currentState ->
                    currentState.copy(
                        issues = currentState.issues + resposta
                    )
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "Error on registering issue: ${e.message}")
            }
        }
    }

    fun loadIssuesMap() {

        viewModelScope.launch {
            try {
                val llista = getAllIssues()

                val llistaSenseDuplicats = llista
                    .groupBy { Pair(it.coordinates.lat, it.coordinates.lon) }
                    .map { (_, issuesEnAquestPunt) ->
                        issuesEnAquestPunt.minByOrNull { it.createdAt } ?: issuesEnAquestPunt.first()
                    }

                _uiState.update { it.copy(issues = llistaSenseDuplicats) }
            } catch (e: Exception) {
                Log.e("MapViewModel", "Error on loading issues: ${e.message}")
            }
        }
    }

    fun isInsideBarcelonaArea(lat: Double, lon: Double): Boolean {
        val minLat = 41.317
        val maxLat = 41.468
        val minLon = 2.052
        val maxLon = 2.228
        return lat in minLat..maxLat && lon in minLon..maxLon
    }

    fun selectIssue(incidencia: IssueResponseDTO?) {
        _uiState.update { it.copy(incidenciaSeleccionada = incidencia) }
    }

    fun voteIssue(incidenciaId: Long, esReal: Boolean, googleId: String) {
        viewModelScope.launch {
            try {
                val score = if (esReal) 1 else -1
                val request = VoteRequestDTO(
                    googleId = googleId,
                    voteScore = score,
                    voteScoreValid = true
                )

                votarIncidencia(incidenciaId, request)

                val totesSenseDuplicats = getAllIssues()
                    .groupBy { Pair(it.coordinates.lat, it.coordinates.lon) }
                    .map { (_, issuesEnAquestPunt) ->
                        issuesEnAquestPunt.minByOrNull { it.createdAt } ?: issuesEnAquestPunt.first()
                    }

                votesCache.update(googleId, incidenciaId, score)

                _uiState.update { currentState ->
                    val incidenciaObertaActualitzada = totesSenseDuplicats.find { it.id == incidenciaId }
                    currentState.copy(
                        issues = totesSenseDuplicats,
                        userVotes = currentState.userVotes + (incidenciaId to score),
                        incidenciaSeleccionada = if (currentState.incidenciaSeleccionada?.id == incidenciaId) {
                            incidenciaObertaActualitzada
                        } else {
                            currentState.incidenciaSeleccionada
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "Error al votar la incidència: ${e.message}")
            }
        }
    }

    private fun calcularCoordenadesLliures(
        desiredLat: Double,
        desiredLon: Double,
        existingIssues: List<IssueResponseDTO>,
        stepSize: Double = 0.0001,
        maxRadiusSteps: Int = 3
    ): Pair<Double, Double> {

        val isOriginalFree = existingIssues.none {
            it.coordinates.lat == desiredLat && it.coordinates.lon == desiredLon
        }
        if (isOriginalFree) return Pair(desiredLat, desiredLon)

        for (radius in 1..maxRadiusSteps) {
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    if (Math.abs(dx) == radius || Math.abs(dy) == radius) {
                        val testLat = desiredLat + (dx * stepSize)
                        val testLon = desiredLon + (dy * stepSize)

                        val isFree = existingIssues.none {
                            it.coordinates.lat == testLat && it.coordinates.lon == testLon
                        }

                        if (isFree) {
                            return Pair(testLat, testLon)
                        }
                    }
                }
            }
        }

        return Pair(desiredLat, desiredLon)
    }

    fun esborrarIncidencia(incidenciaId: Long) {
        viewModelScope.launch {
            try {
                eliminarIncidencia(incidenciaId)

                _uiState.update { currentState ->
                    currentState.copy(
                        issues = currentState.issues.filter { it.id != incidenciaId },
                        incidenciaSeleccionada = null
                    )
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "Error esborrant incidÃ¨ncia: ${e.message}")
            }
        }
    }

    fun iniciarEdicio(incidencia: IssueResponseDTO) {
        _uiState.update { currentState ->
            currentState.copy(
                incidenciaSeleccionada = null,
                incidenciaEnEdicio = incidencia
            )
        }
    }

    fun cancelarEdicio() {
        _uiState.update { it.copy(incidenciaEnEdicio = null) }
    }

    fun guardarEdicio(
        incidenciaId: Long,
        nouTipus: IssueApiType,
        novaDescripcio: String,
        coordenades: Coord,
        googleId: String
    ) {
        viewModelScope.launch {
            try {
                Log.d("MapViewModel", "guardarEdicio ENVIANT id=$incidenciaId coords=(${coordenades.lat},${coordenades.lon})")

                val request = IssueRequestDTO(
                    googleId = googleId,
                    type = nouTipus,
                    description = novaDescripcio,
                    coordinates = coordenades
                )

                val resposta = actualitzarIncidencia(incidenciaId, request)
                Log.d("MapViewModel", "guardarEdicio RESPOSTA id=${resposta.id} coords=(${resposta.coordinates.lat},${resposta.coordinates.lon})")

                _uiState.update { currentState ->
                    currentState.copy(
                        issues = currentState.issues.map { si ->
                            if (si.id == incidenciaId) resposta else si
                        },
                        incidenciaEnEdicio = null
                    )
                }

                loadIssuesMap()

            } catch (e: Exception) {
                Log.e("MapViewModel", "Error desant l'ediciÃ³: ${e.message}")
            }
        }
    }

    fun votar(idIncidencia: Long, esPositiu: Int, googleId: String) {
        viewModelScope.launch {
            try {
                val request = VoteRequestDTO(
                    googleId = googleId,
                    voteScore = esPositiu,
                    voteScoreValid = true
                )
                votarIncidencia(idIncidencia, request)

                _uiState.update { currentState ->
                    val nousVots = currentState.userVotes.toMutableMap()
                    nousVots[idIncidencia] = esPositiu

                    val issuesActualitzades = currentState.issues.map {
                        if (it.id == idIncidencia) {
                            if (esPositiu == 1) it.copy(positiveVotes = it.positiveVotes + 1)
                            else it.copy(negativeVotes = it.negativeVotes + 1)
                        } else it
                    }

                    currentState.copy(
                        userVotes = nousVots,
                        issues = issuesActualitzades,
                        incidenciaSeleccionada = issuesActualitzades.find { it.id == idIncidencia }
                    )
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "Error al votar: ${e.message}")
            }
        }
    }

    fun desferVot(idIncidencia: Long, googleId: String) {
        val votActual = uiState.value.userVotes[idIncidencia] ?: return

        viewModelScope.launch {
            try {
                eliminarVot(idIncidencia, googleId)

                _uiState.update { currentState ->
                    val nousVots = currentState.userVotes.toMutableMap()
                    nousVots.remove(idIncidencia)

                    val issuesActualitzades = currentState.issues.map {
                        if (it.id == idIncidencia) {
                            if (votActual == 1) it.copy(positiveVotes = (it.positiveVotes - 1).coerceAtLeast(0))
                            else it.copy(negativeVotes = (it.negativeVotes - 1).coerceAtLeast(0))
                        } else it
                    }

                    currentState.copy(
                        userVotes = nousVots,
                        issues = issuesActualitzades,
                        incidenciaSeleccionada = issuesActualitzades.find { it.id == idIncidencia }
                    )
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "Error al desfer: ${e.message}")
            }
        }
    }

}


