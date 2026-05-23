package com.safesteps.data

import android.util.Log
import java.io.IOException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

private const val USER_EMERGENCY_BASE_URL = "http://nattech.fib.upc.edu:40382/"
private const val USER_EMERGENCY_USERS_PATH = "api/v1/users"
private const val UserEmergencyApiTag = "USER_EMERGENCY_API"

private interface UserEmergencyApiService {
    @GET("$USER_EMERGENCY_USERS_PATH/{googleId}/emergency")
    suspend fun getEmergencyStatus(
        @Path("googleId") googleId: String
    ): Response<Boolean>

    @POST("$USER_EMERGENCY_USERS_PATH/{googleId}/emergency")
    suspend fun toggleEmergencyStatus(
        @Path("googleId") googleId: String
    ): Response<Unit>
}

private object UserEmergencyBackend {
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(USER_EMERGENCY_BASE_URL)
            .client(sharedOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: UserEmergencyApiService by lazy {
        retrofit.create(UserEmergencyApiService::class.java)
    }
}

private fun <T> ensureUserEmergencySuccess(
    response: Response<T>,
    action: String
) {
    Log.d(
        UserEmergencyApiTag,
        "Respuesta HTTP al $action: code=${response.code()} success=${response.isSuccessful}"
    )

    if (!response.isSuccessful) {
        throw IOException("Error $action: ${response.code()} ${response.message()}")
    }
}

suspend fun obtenerEstadoEmergenciaUsuario(googleId: String): Boolean {
    if (googleId.isBlank()) {
        return false
    }

    Log.d(UserEmergencyApiTag, "Consultando estado de emergencia: googleId=$googleId")

    val response = UserEmergencyBackend.service.getEmergencyStatus(googleId)
    ensureUserEmergencySuccess(response, "consultando el estado de emergencia")
    return response.body() == true
}

suspend fun alternarEstadoEmergenciaUsuario(googleId: String): Boolean {
    if (googleId.isBlank()) {
        return false
    }

    Log.d(UserEmergencyApiTag, "Alternando estado de emergencia: googleId=$googleId")

    val response = UserEmergencyBackend.service.toggleEmergencyStatus(googleId)
    ensureUserEmergencySuccess(response, "actualizando el estado de emergencia")
    return obtenerEstadoEmergenciaUsuario(googleId)
}
