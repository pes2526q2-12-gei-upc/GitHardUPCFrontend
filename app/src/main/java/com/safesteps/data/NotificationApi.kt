package com.safesteps.data

import android.util.Log
import java.io.IOException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

private const val NOTIFICATION_BASE_URL = "http://nattech.fib.upc.edu:40382/"
private const val NOTIFICATION_USERS_PATH = "api/v1/users"
private const val NotificationApiTag = "NOTIFICATION_API"

private interface NotificationApiService {
    @POST("$NOTIFICATION_USERS_PATH/{googleId}/try-notifications")
    suspend fun sendTestNotification(
        @Path("googleId") googleId: String,
        @Query("title") title: String,
        @Query("body") body: String
    ): Response<Unit>

    @POST("$NOTIFICATION_USERS_PATH/{googleId}/fcm-token")
    suspend fun syncFcmToken(
        @Path("googleId") googleId: String,
        @Query("token") token: String
    ): Response<Unit>
}

private object NotificationBackend {
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(NOTIFICATION_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: NotificationApiService by lazy {
        retrofit.create(NotificationApiService::class.java)
    }
}

private fun <T> ensureNotificationSuccess(
    response: Response<T>,
    action: String
) {
    Log.d(
        NotificationApiTag,
        "Respuesta HTTP al $action: code=${response.code()} success=${response.isSuccessful}"
    )

    if (!response.isSuccessful) {
        throw IOException("Error $action: ${response.code()} ${response.message()}")
    }
}

suspend fun enviarNotificacionEmergenciaUsuario(
    googleId: String,
    title: String,
    body: String
) {
    if (googleId.isBlank() || title.isBlank() || body.isBlank()) {
        return
    }

    Log.d(NotificationApiTag, "Enviando notificacion de emergencia: googleId=$googleId")

    val response = NotificationBackend.service.sendTestNotification(
        googleId = googleId,
        title = title,
        body = body
    )
    ensureNotificationSuccess(response, "enviando la notificacion de emergencia")
}

suspend fun sincronizarTokenFcmUsuario(
    googleId: String,
    token: String
) {
    if (googleId.isBlank() || token.isBlank()) {
        return
    }

    Log.d(NotificationApiTag, "Sincronizando token FCM: googleId=$googleId")

    val response = NotificationBackend.service.syncFcmToken(
        googleId = googleId,
        token = token
    )
    ensureNotificationSuccess(response, "sincronizando el token FCM")
}
