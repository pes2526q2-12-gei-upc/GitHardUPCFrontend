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
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

private const val USER_BASE_URL = "http://nattech.fib.upc.edu:40381/"
private const val USERS_PATH = "api/v1/users"

enum class UserSyncResult {
    EXISTING_USER_UPDATED,
    NEW_USER_CREATED
}

data class UserSyncOutcome(
    val result: UserSyncResult,
    val languageTag: String,
    // val routeColor: String? = null,
    // val nameStyle: String? = "Normal"
)

private data class UserRequest(
    val email: String,
    val username: String,
    val googleId: String,
    val pictureUrl: String? = null,
    val language: String? = null,
    val isAnonymous: Boolean,
    // val routeColor: String? = null,
    // val nameStyle: String? = null
)

private data class UserResponse(
    val id: Long? = null,
    val email: String? = null,
    val username: String? = null,
    val googleId: String? = null,
    val pictureUrl: String? = null,
    val language: String? = null,
    val isAnonymous: Boolean? = null,
    // val routeColor: String? = null,
    // val nameStyle: String? = null
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

    val existingUserResponse = UserBackend.service.getUserByGoogleId(user.googleId)

    when {
        existingUserResponse.isSuccessful -> {
            val existingUser = existingUserResponse.body()
                ?: throw IOException("La respuesta del backend no contiene el usuario esperado")
            val resolvedLanguageTag = existingUser.language
                ?.takeIf { it.isNotBlank() }
                ?: defaultLanguage()

            Log.d("USER_API", "Usuario existente encontrado, actualizando datos")

            val updateRequest = buildUserRequest(
                user = user,
                language = resolvedLanguageTag,
                isAnonymous = existingUser.isAnonymous ?: false
            )

            val updateResponse = UserBackend.service.updateUser(user.googleId, updateRequest)
            ensureSuccess(updateResponse, "actualizando el usuario")
            return UserSyncOutcome(
                result = UserSyncResult.EXISTING_USER_UPDATED,
                languageTag = resolvedLanguageTag,
                // routeColor = existingUser.routeColor,
                // nameStyle = existingUser.nameStyle
            )
        }

        existingUserResponse.code() == 404 -> {
            Log.d("USER_API", "Usuario no encontrado, creando registro")
            val resolvedLanguageTag = defaultLanguage()

            val createRequest = buildUserRequest(
                user = user,
                language = resolvedLanguageTag,
                isAnonymous = false
            )
            val createResponse = UserBackend.service.createUser(createRequest)
            ensureSuccess(createResponse, "creando el usuario")
            val createdUser = createResponse.body()
            return UserSyncOutcome(
                result = UserSyncResult.NEW_USER_CREATED,
                languageTag = resolvedLanguageTag,
                // routeColor = createdUser?.routeColor,
                // nameStyle = createdUser?.nameStyle
            )
        }

        else -> {
            throw IOException(
                "Error consultando el usuario: ${existingUserResponse.code()} ${existingUserResponse.message()}"
            )
        }
    }
}

// suspend fun sincronizarPersonalizacionUsuario(user: UserInfo) { ... } (Commented out for now)
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
        isAnonymous = isAnonymous,
        // routeColor = user.routeColor,
        // nameStyle = user.nameStyle
    )
}

private fun defaultLanguage(): String {
    val languageTag = Locale.getDefault().language.takeIf { it.isNotBlank() }
    return AppLanguage.fromLanguageTag(languageTag).languageTag
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

