package com.safesteps.data

import android.util.Log
import java.io.IOException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

private const val EMERGENCY_CONTACT_BASE_URL = "http://nattech.fib.upc.edu:40381/"
private const val EMERGENCY_USERS_PATH = "api/v1/users"

data class EmergencyContactProfile(
    val username: String,
    val photoUrl: String? = null,
    val points: Long? = null,
    val level: Long? = null,
    val createdAt: String? = null,
    val status: String? = null
)

private data class EmergencyContactProfileResponse(
    val username: String? = null,
    val pictureUrl: String? = null,
    val points: Long? = null,
    val level: Long? = null,
    val createdAt: String? = null,
    val status: String? = null
)

private interface EmergencyContactApiService {
    @GET("$EMERGENCY_USERS_PATH/{googleId}/emergency-contacts")
    suspend fun getEmergencyContacts(
        @Path("googleId") googleId: String
    ): Response<List<EmergencyContactProfileResponse>>

    @POST("$EMERGENCY_USERS_PATH/{googleId}/emergency-contacts")
    suspend fun addEmergencyContacts(
        @Path("googleId") googleId: String,
        @Query("emergencyContacts") emergencyContacts: List<String>
    ): Response<List<EmergencyContactProfileResponse>>

    @DELETE("$EMERGENCY_USERS_PATH/{googleId}/emergency-contacts")
    suspend fun deleteEmergencyContacts(
        @Path("googleId") googleId: String,
        @Query("emergencyContacts") emergencyContacts: List<String>
    ): Response<List<String>>

    @GET("$EMERGENCY_USERS_PATH/profile/{googleId}")
    suspend fun getPublicProfile(
        @Path("googleId") googleId: String
    ): Response<EmergencyContactProfileResponse>
}

private object EmergencyContactBackend {
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(EMERGENCY_CONTACT_BASE_URL)
            .client(sharedOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: EmergencyContactApiService by lazy {
        retrofit.create(EmergencyContactApiService::class.java)
    }
}

private fun <T> ensureEmergencyContactSuccess(
    response: Response<T>,
    action: String
) {
    Log.d(
        "EMERGENCY_CONTACT_API",
        "Respuesta HTTP al $action: code=${response.code()} success=${response.isSuccessful}"
    )

    if (!response.isSuccessful) {
        throw IOException("Error $action: ${response.code()} ${response.message()}")
    }
}

suspend fun cargarContactosEmergenciaUsuario(
    googleId: String
): List<EmergencyContactProfile> {
    if (googleId.isBlank()) {
        return emptyList()
    }

    Log.d("EMERGENCY_CONTACT_API", "Cargando contactos de emergencia: googleId=$googleId")

    val response = EmergencyContactBackend.service.getEmergencyContacts(googleId)
    ensureEmergencyContactSuccess(response, "cargando los contactos de emergencia")

    return response.body()
        .orEmpty()
        .mapNotNull(EmergencyContactProfileResponse::toEmergencyContactProfile)
}

suspend fun marcarContactoEmergenciaUsuario(
    googleId: String,
    emergencyContactGoogleId: String
) {
    if (googleId.isBlank() || emergencyContactGoogleId.isBlank()) {
        return
    }

    Log.d(
        "EMERGENCY_CONTACT_API",
        "Marcando contacto de emergencia: googleId=$googleId emergencyContact=$emergencyContactGoogleId"
    )

    val response = EmergencyContactBackend.service.addEmergencyContacts(
        googleId = googleId,
        emergencyContacts = listOf(emergencyContactGoogleId)
    )
    ensureEmergencyContactSuccess(response, "marcando el contacto de emergencia")
}

suspend fun desmarcarContactoEmergenciaUsuario(
    googleId: String,
    emergencyContactGoogleId: String
) {
    if (googleId.isBlank() || emergencyContactGoogleId.isBlank()) {
        return
    }

    Log.d(
        "EMERGENCY_CONTACT_API",
        "Desmarcando contacto de emergencia: googleId=$googleId emergencyContact=$emergencyContactGoogleId"
    )

    val response = EmergencyContactBackend.service.deleteEmergencyContacts(
        googleId = googleId,
        emergencyContacts = listOf(emergencyContactGoogleId)
    )
    ensureEmergencyContactSuccess(response, "desmarcando el contacto de emergencia")
}

suspend fun cargarPerfilPublicoUsuario(
    googleId: String
): EmergencyContactProfile? {
    if (googleId.isBlank()) {
        return null
    }

    val response = EmergencyContactBackend.service.getPublicProfile(googleId)
    ensureEmergencyContactSuccess(response, "cargando el perfil publico del usuario")

    return response.body()?.toEmergencyContactProfile()
}

private fun EmergencyContactProfileResponse.toEmergencyContactProfile(): EmergencyContactProfile? {
    val resolvedUsername = username?.trim().orEmpty()
    if (resolvedUsername.isBlank()) {
        return null
    }

    return EmergencyContactProfile(
        username = resolvedUsername,
        photoUrl = pictureUrl?.takeIf { it.isNotBlank() },
        points = points,
        level = level,
        createdAt = createdAt?.takeIf { it.isNotBlank() },
        status = status?.takeIf { it.isNotBlank() }
    )
}
