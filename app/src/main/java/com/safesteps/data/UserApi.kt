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

private const val USER_BASE_URL = "http://nattech.fib.upc.edu:40381/"
private const val USERS_PATH = "api/v1/users"

enum class UserSyncResult {
    EXISTING_USER_LOGGED_IN,
    NEW_USER_CREATED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_BANNED
}

data class UserSyncOutcome(
    val result: UserSyncResult,
    val languageTag: String? = null
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

private data class UserRequest(
    val email: String,
    val username: String,
    val googleId: String,
    val pictureUrl: String? = null,
    val language: String? = null,
    val isAnonymous: Boolean
)

private data class UserResponse(
    val id: Long? = null,
    val email: String? = null,
    val username: String? = null,
    val googleId: String? = null,
    val pictureUrl: String? = null,
    val language: String? = null,
    val isAnonymous: Boolean? = null
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
