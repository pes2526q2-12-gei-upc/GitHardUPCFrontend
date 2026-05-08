package com.safesteps.data

import android.util.Log
import com.safesteps.auth.UserInfo
import com.safesteps.i18n.AppLanguage
import java.io.IOException
import java.util.Locale
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

private const val USER_BASE_URL = "http://nattech.fib.upc.edu:40383/"
private const val USERS_PATH = "api/v1/users"

enum class UserSyncResult {
    EXISTING_USER_LOGGED_IN,
    NEW_USER_CREATED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_BANNED
}

data class PremiResponse(
    val id: String? = null,
    val url: String? = null
)
data class UserSyncOutcome(
    val result: UserSyncResult,
    val languageTag: String? = null
)

data class RouteCompletionResponse(
    val level: Long? = null,
    val levelUpdated: Boolean? = null,
    val pointsAdded: Long? = null,
    val totalPoints: Long? = null,
    val recompenses: Long? = null
)

data class UserFilters(
    val comissaries: Double = 0.5,
    val fetsPenals: Double = 0.5,
    val cameresSeguretat: Double = 0.5,
    val infraccions: Double = 0.5,
    val fontsAigua: Double = 0.5,
    val bancs: Double = 0.5,
    val contaminacioAcustica: Double = 0.5,
    val escalesMecaniques: Double = 0.5,
    val arbres: Double = 0.5,
    val refugisClimatics: Double = 0.5,
    val qualitatAire: Double = 0.5
)

data class FriendSearchUser(
    val googleId: String,
    val username: String,
    val email: String,
    val photoUrl: String? = null
)

private data class UserSummaryResponse(
    val googleId: String? = null,
    val username: String? = null,
    val pictureUrl: String? = null,
    val email: String? = null
)

private data class FriendshipRequest(
    val senderGoogleId: String,
    val receiverGoogleId: String
)

private data class UserRequest(
    val email: String,
    val username: String,
    val googleId: String,
    val pictureUrl: String? = null,
    val language: String? = null,
    val isAnonymous: Boolean
)

data class UserResponse(
    val id: Long? = null,
    val email: String? = null,
    val username: String? = null,
    val googleId: String? = null,
    val pictureUrl: String? = null,
    val language: String? = null,
    val isAnonymous: Boolean? = null,
    val points: Long? = null,
    val level: Long? = null,
    val reputacio: Double? = null,
    val createdAt: String? = null,
    val recompenses: Long? = null,
    val premis: List<PremiResponse>? = null
)

private data class FilterRequest(
    val comissaries: Double? = null,
    val fetsPenals: Double? = null,
    val cameresSeguretat: Double? = null,
    val infraccions: Double? = null,
    val fontsAigua: Double? = null,
    val bancs: Double? = null,
    val contaminacioAcustica: Double? = null,
    val escalesMecaniques: Double? = null,
    val arbres: Double? = null,
    val refugisClimatics: Double? = null,
    val qualitatAire: Double? = null
)

private data class UserFiltersResponse(
    val googleId: String? = null,
    val comissaries: Double? = null,
    val fetsPenals: Double? = null,
    val cameresSeguretat: Double? = null,
    val infraccions: Double? = null,
    val fontsAigua: Double? = null,
    val bancs: Double? = null,
    val contaminacioAcustica: Double? = null,
    val escalesMecaniques: Double? = null,
    val arbres: Double? = null,
    val refugisClimatics: Double? = null,
    val qualitatAire: Double? = null
)

private interface UserApiService {
    @GET("$USERS_PATH/{googleId}")
    suspend fun getUserByGoogleId(
        @Path("googleId") googleId: String
    ): Response<UserResponse>

    @GET("$USERS_PATH/search/username")
    suspend fun searchUsersByUsername(
        @Query("username") username: String
    ): Response<List<UserSummaryResponse>>

    @POST("api/v1/friendships")
    suspend fun createFriendship(
        @Body request: FriendshipRequest
    ): Response<Unit>

    @GET("api/v1/friendships/{googleId}/pending")
    suspend fun getPendingFriendRequests(
        @Path("googleId") googleId: String
    ): Response<List<UserSummaryResponse>>

    @GET("api/v1/friendships/{googleId}/accepted")
    suspend fun getAcceptedFriends(
        @Path("googleId") googleId: String
    ): Response<List<UserSummaryResponse>>

    @PATCH("api/v1/friendships/{receiverGoogleId}/accept/{senderGoogleId}")
    suspend fun acceptFriendRequest(
        @Path("receiverGoogleId") receiverGoogleId: String,
        @Path("senderGoogleId") senderGoogleId: String
    ): Response<Unit>

    @DELETE("api/v1/friendships/{receiverGoogleId}/decline/{senderGoogleId}")
    suspend fun declineFriendRequest(
        @Path("receiverGoogleId") receiverGoogleId: String,
        @Path("senderGoogleId") senderGoogleId: String
    ): Response<Unit>

    @DELETE("api/v1/friendships/{googleId}/friend/{friendGoogleId}")
    suspend fun deleteFriend(
        @Path("googleId") googleId: String,
        @Path("friendGoogleId") friendGoogleId: String
    ): Response<Unit>

    @POST(USERS_PATH)
    suspend fun createUser(
        @Body request: UserRequest
    ): Response<UserResponse>

    @PUT("$USERS_PATH/{googleId}")
    suspend fun updateUser(
        @Path("googleId") googleId: String,
        @Body request: UserRequest
    ): Response<UserResponse>

    @PATCH("$USERS_PATH/{googleId}/language")
    suspend fun updateLanguage(
        @Path("googleId") googleId: String,
        @Query("lang") language: String
    ): Response<UserResponse>

    @PUT("$USERS_PATH/{googleId}/filters")
    suspend fun updateFilters(
        @Path("googleId") googleId: String,
        @Body request: FilterRequest
    ): Response<UserFiltersResponse>

    @DELETE("$USERS_PATH/{googleId}")
    suspend fun deleteUser(
        @Path("googleId") googleId: String
    ): Response<Unit>

    @GET("$USERS_PATH/{googleId}/complete-route")
    suspend fun completeRoute(
        @Path("googleId") googleId: String,
        @Query("meters") meters: Double
    ): Response<RouteCompletionResponse>

    @GET("$USERS_PATH/{googleId}/open-prize")
    suspend fun openPrize(
        @Path("googleId") googleId: String
    ): Response<PremiResponse>
}

private object UserBackend {
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(USER_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: UserApiService by lazy {
        retrofit.create(UserApiService::class.java)
    }
}

suspend fun sincronizarUsuarioConBackend(user: UserInfo): UserSyncOutcome {
    validarDatosUsuario(user)
    val resolvedLanguageTag = defaultLanguage()
    val createRequest = buildUserRequest(
        user = user,
        language = resolvedLanguageTag,
        isAnonymous = false
    )

    Log.d("USER_API", "Sincronizando inicio de sesion para googleId=${user.googleId}")

    val createResponse = UserBackend.service.createUser(createRequest)
    Log.d(
        "USER_API",
        "Respuesta HTTP al iniciar sesion: code=${createResponse.code()} success=${createResponse.isSuccessful}"
    )

    return when (createResponse.code()) {
        201 -> {
            UserSyncOutcome(
                result = UserSyncResult.NEW_USER_CREATED,
                languageTag = createResponse.body()
                    ?.language
                    ?.takeIf { it.isNotBlank() }
                    ?: resolvedLanguageTag
            )
        }

        400 -> {
            UserSyncOutcome(
                result = UserSyncResult.EXISTING_USER_LOGGED_IN
            )
        }

        402 -> {
            UserSyncOutcome(
                result = UserSyncResult.ACCOUNT_SUSPENDED
            )
        }

        403 -> {
            UserSyncOutcome(
                result = UserSyncResult.ACCOUNT_BANNED
            )
        }

        else -> {
            throw IOException(
                "Error iniciando sesion: ${createResponse.code()} ${createResponse.message()}"
            )
        }
    }
}

suspend fun eliminarUsuarioDelBackend(googleId: String) {
    if (googleId.isBlank()) {
        throw IOException("Falta el googleId para eliminar el usuario del backend")
    }

    Log.d("USER_API", "Eliminando usuario del backend: googleId=$googleId")

    val response = UserBackend.service.deleteUser(googleId)

    Log.d(
        "USER_API",
        "Respuesta HTTP al eliminar usuario: code=${response.code()} success=${response.isSuccessful}"
    )

    if (response.isSuccessful || response.code() == 404) {
        return
    }

    throw IOException("Error eliminando el usuario: ${response.code()} ${response.message()}")
}

suspend fun updateLenguage(
    user: UserInfo,
    language: AppLanguage
): String {
    if (user.googleId.isBlank()) {
        throw IOException("Falta el googleId para actualizar el idioma del usuario")
    }

    Log.d(
        "USER_API",
        "Actualizando idioma del usuario en backend: googleId=${user.googleId} lang=${language.languageTag}"
    )

    val response = UserBackend.service.updateLanguage(user.googleId, language.languageTag)
    if (response.isSuccessful) {
        return response.body()
            ?.language
            ?.takeIf { it.isNotBlank() }
            ?: language.languageTag
    }

    if (response.code() == 404) {
        Log.w(
            "USER_API",
            "El endpoint PATCH de idioma no existe en este backend. Se usa fallback con PUT."
        )

        val updateResponse = UserBackend.service.updateUser(
            user.googleId,
            buildUserRequest(
                user = user,
                language = language.languageTag,
                isAnonymous = false
            )
        )
        ensureSuccess(updateResponse, "actualizando el idioma del usuario con PUT")

        return updateResponse.body()
            ?.language
            ?.takeIf { it.isNotBlank() }
            ?: language.languageTag
    }

    ensureSuccess(response, "actualizando el idioma del usuario")

    return response.body()
        ?.language
        ?.takeIf { it.isNotBlank() }
        ?: language.languageTag
}

suspend fun cargarFiltrosUsuario(googleId: String): UserFilters {
    if (googleId.isBlank()) {
        throw IOException("Falta el googleId para cargar los filtros del usuario")
    }

    Log.d("USER_API", "Cargando filtros del usuario: googleId=$googleId")

    val response = UserBackend.service.updateFilters(googleId, FilterRequest())
    if (response.isSuccessful) {
        return response.body()?.toUserFilters() ?: UserFilters()
    }

    if (response.code() == 404) {
        Log.w(
            "USER_API",
            "El endpoint de filtros no esta desplegado en este backend. Se usan filtros por defecto."
        )
        return UserFilters()
    }

    throw IOException("Error cargando los filtros del usuario: ${response.code()} ${response.message()}")
}

suspend fun actualizarFiltrosUsuario(
    googleId: String,
    filters: UserFilters
): UserFilters {
    if (googleId.isBlank()) {
        throw IOException("Falta el googleId para actualizar los filtros del usuario")
    }

    Log.d("USER_API", "Actualizando filtros del usuario: googleId=$googleId")

    val response = UserBackend.service.updateFilters(googleId, filters.toFilterRequest())
    if (response.isSuccessful) {
        return response.body()?.toUserFilters() ?: filters
    }

    if (response.code() == 404) {
        Log.w(
            "USER_API",
            "El endpoint de filtros no esta desplegado en este backend. Se mantiene el estado local."
        )
        return filters
    }

    throw IOException(
        "Error actualizando los filtros del usuario: ${response.code()} ${response.message()}"
    )
}

private fun validarDatosUsuario(user: UserInfo) {
    if (user.email.isBlank() || user.username.isBlank() || user.googleId.isBlank()) {
        throw IOException("Faltan datos de Google para sincronizar el usuario con el backend")
    }
}

private fun buildUserRequest(
    user: UserInfo,
    language: String?,
    isAnonymous: Boolean
): UserRequest {
    return UserRequest(
        email = user.email,
        username = user.username,
        googleId = user.googleId,
        pictureUrl = user.photoUrl,
        language = language,
        isAnonymous = isAnonymous
    )
}

private fun defaultLanguage(): String {
    val languageTag = Locale.getDefault().language.takeIf { it.isNotBlank() }
    return AppLanguage.fromLanguageTag(languageTag).languageTag
}

private fun UserFilters.toFilterRequest(): FilterRequest {
    return FilterRequest(
        comissaries = comissaries,
        fetsPenals = fetsPenals,
        cameresSeguretat = cameresSeguretat,
        infraccions = infraccions,
        fontsAigua = fontsAigua,
        bancs = bancs,
        contaminacioAcustica = contaminacioAcustica,
        escalesMecaniques = escalesMecaniques,
        arbres = arbres,
        refugisClimatics = refugisClimatics,
        qualitatAire = qualitatAire
    )
}

private fun UserFiltersResponse.toUserFilters(): UserFilters {
    return UserFilters(
        comissaries = comissaries ?: 0.5,
        fetsPenals = fetsPenals ?: 0.5,
        cameresSeguretat = cameresSeguretat ?: 0.5,
        infraccions = infraccions ?: 0.5,
        fontsAigua = fontsAigua ?: 0.5,
        bancs = bancs ?: 0.5,
        contaminacioAcustica = contaminacioAcustica ?: 0.5,
        escalesMecaniques = escalesMecaniques ?: 0.5,
        arbres = arbres ?: 0.5,
        refugisClimatics = refugisClimatics ?: 0.5,
        qualitatAire = qualitatAire ?: 0.5
    )
}

private fun <T> ensureSuccess(
    response: Response<T>,
    action: String
) {
    Log.d(
        "USER_API",
        "Respuesta HTTP al $action: code=${response.code()} success=${response.isSuccessful}"
    )

    if (!response.isSuccessful) {
        throw IOException("Error $action: ${response.code()} ${response.message()}")
    }
}

suspend fun completarRutaEnBackend(googleId: String, distanceMeters: Double): RouteCompletionResponse? {
    if (googleId.isBlank()) {
        Log.e("USER_API", "Falta el googleId para guardar la ruta en el backend")
        return null
    }

    val response = UserBackend.service.completeRoute(googleId, distanceMeters)

    if (response.isSuccessful) {
        return response.body()
    }

    Log.e("USER_API", "Error al completar ruta: ${response.code()} ${response.message()}")
    return null
}

suspend fun cargarPerfilDeUsuario(googleId: String): UserResponse? {
    val response = UserBackend.service.getUserByGoogleId(googleId)
    if (response.isSuccessful) {
        return response.body()
    }
    return null
}

suspend fun abrirPremioEnBackend(googleId: String): PremiResponse? {
    if (googleId.isBlank()) {
        Log.e("USER_API", "Falta el googleId para abrir premio")
        return null
    }

    Log.d("USER_API", "Abriendo premio: googleId=$googleId")
    val response = UserBackend.service.openPrize(googleId)

    if (response.isSuccessful) {
        return response.body()
    }

    Log.e("USER_API", "Error al abrir premio: ${response.code()} ${response.message()}")
    return null
}

suspend fun cargarAmigosUsuario(
    requesterGoogleId: String
): List<FriendSearchUser> {
    if (requesterGoogleId.isBlank()) {
        return emptyList()
    }

    Log.d("USER_API", "Cargando amigos aceptados: googleId=$requesterGoogleId")

    val response = UserBackend.service.getAcceptedFriends(requesterGoogleId)
    ensureSuccess(response, "cargando los amigos aceptados")

    return response.body()
        .orEmpty()
        .mapNotNull(UserSummaryResponse::toFriendSearchUser)
        .sortedBy { it.username.lowercase(Locale.ROOT) }
}

suspend fun cargarSolicitudesPendientesAmistad(
    receiverGoogleId: String
): List<FriendSearchUser> {
    if (receiverGoogleId.isBlank()) {
        return emptyList()
    }

    Log.d("USER_API", "Cargando solicitudes pendientes de amistad: googleId=$receiverGoogleId")

    val response = UserBackend.service.getPendingFriendRequests(receiverGoogleId)
    ensureSuccess(response, "cargando las solicitudes pendientes de amistad")

    return response.body()
        .orEmpty()
        .mapNotNull(UserSummaryResponse::toFriendSearchUser)
        .sortedBy { it.username.lowercase(Locale.ROOT) }
}

suspend fun buscarUsuariosParaAmistad(
    query: String
): List<FriendSearchUser> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) {
        return emptyList()
    }

    Log.d(
        "USER_API",
        "Buscando usuarios para amistad: username=$normalizedQuery"
    )

    val response = UserBackend.service.searchUsersByUsername(normalizedQuery)
    Log.d(
        "USER_API",
        "Respuesta HTTP al buscar usuarios por username: code=${response.code()} success=${response.isSuccessful}"
    )
    ensureSuccess(response, "buscando usuarios por username")

    return response.body()
        .orEmpty()
        .mapNotNull(UserSummaryResponse::toFriendSearchUser)
        .distinctBy(FriendSearchUser::googleId)
}

suspend fun enviarSolicitudAmistad(
    requesterGoogleId: String,
    targetGoogleId: String
) {
    if (requesterGoogleId.isBlank() || targetGoogleId.isBlank()) {
        return
    }

    Log.d(
        "USER_API",
        "Enviando solicitud de amistad: senderGoogleId=$requesterGoogleId receiverGoogleId=$targetGoogleId"
    )

    val response = UserBackend.service.createFriendship(
        FriendshipRequest(
            senderGoogleId = requesterGoogleId,
            receiverGoogleId = targetGoogleId
        )
    )
    ensureSuccess(response, "enviando la solicitud de amistad")
}

suspend fun aceptarSolicitudAmistad(
    receiverGoogleId: String,
    senderGoogleId: String
) {
    if (receiverGoogleId.isBlank() || senderGoogleId.isBlank()) {
        return
    }

    Log.d(
        "USER_API",
        "Aceptando solicitud de amistad: receiverGoogleId=$receiverGoogleId senderGoogleId=$senderGoogleId"
    )

    val response = UserBackend.service.acceptFriendRequest(receiverGoogleId, senderGoogleId)
    ensureSuccess(response, "aceptando la solicitud de amistad")
}

suspend fun denegarSolicitudAmistad(
    receiverGoogleId: String,
    senderGoogleId: String
) {
    if (receiverGoogleId.isBlank() || senderGoogleId.isBlank()) {
        return
    }

    Log.d(
        "USER_API",
        "Denegando solicitud de amistad: receiverGoogleId=$receiverGoogleId senderGoogleId=$senderGoogleId"
    )

    val response = UserBackend.service.declineFriendRequest(receiverGoogleId, senderGoogleId)
    ensureSuccess(response, "denegando la solicitud de amistad")
}

suspend fun eliminarAmigoUsuario(
    requesterGoogleId: String,
    targetGoogleId: String
) {
    if (requesterGoogleId.isBlank() || targetGoogleId.isBlank()) {
        return
    }

    Log.d(
        "USER_API",
        "Eliminando amistad: googleId=$requesterGoogleId friendGoogleId=$targetGoogleId"
    )

    val response = UserBackend.service.deleteFriend(requesterGoogleId, targetGoogleId)
    ensureSuccess(response, "eliminando la amistad")
}

private fun UserSummaryResponse.toFriendSearchUser(): FriendSearchUser? {
    val resolvedGoogleId = googleId?.trim().orEmpty()
    val resolvedUsername = username?.trim().orEmpty()
    val resolvedEmail = email?.trim().orEmpty()
    if (resolvedGoogleId.isBlank() || resolvedUsername.isBlank() || resolvedEmail.isBlank()) {
        return null
    }

    return FriendSearchUser(
        googleId = resolvedGoogleId,
        username = resolvedUsername,
        email = resolvedEmail,
        photoUrl = pictureUrl?.takeIf { it.isNotBlank() }
    )
}
