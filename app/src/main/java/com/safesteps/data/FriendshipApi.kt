package com.safesteps.data

import android.util.Log
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
import retrofit2.http.Path

private const val FRIENDSHIP_BASE_URL = "http://nattech.fib.upc.edu:40382/"

private data class FriendshipRequest(
    val senderGoogleId: String,
    val receiverGoogleId: String
)

private interface FriendshipApiService {
    @POST("api/v1/friendships")
    suspend fun createFriendship(
        @Body request: FriendshipRequest
    ): Response<Unit>

    @PATCH("api/v1/friendships/{receiverGoogleId}/accept/{senderGoogleId}")
    suspend fun acceptFriendRequest(
        @Path("receiverGoogleId") receiverGoogleId: String,
        @Path("senderGoogleId") senderGoogleId: String
    ): Response<Unit>

    @GET("api/v1/friendships/{googleId}/pending")
    suspend fun getPendingFriendRequests(
        @Path("googleId") googleId: String
    ): Response<List<UserSummaryResponse>>

    @GET("api/v1/friendships/{googleId}/accepted")
    suspend fun getAcceptedFriends(
        @Path("googleId") googleId: String
    ): Response<List<UserSummaryResponse>>

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
}

private object FriendshipBackend {
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(FRIENDSHIP_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: FriendshipApiService by lazy {
        retrofit.create(FriendshipApiService::class.java)
    }
}

private fun <T> ensureFriendshipSuccess(
    response: Response<T>,
    action: String
) {
    Log.d(
        "FRIENDSHIP_API",
        "Respuesta HTTP al $action: code=${response.code()} success=${response.isSuccessful}"
    )

    if (!response.isSuccessful) {
        throw IOException("Error $action: ${response.code()} ${response.message()}")
    }
}

suspend fun cargarAmigosUsuario(
    requesterGoogleId: String
): List<FriendSearchUser> {
    if (requesterGoogleId.isBlank()) {
        return emptyList()
    }

    Log.d("FRIENDSHIP_API", "Cargando amigos aceptados: googleId=$requesterGoogleId")

    val response = FriendshipBackend.service.getAcceptedFriends(requesterGoogleId)
    ensureFriendshipSuccess(response, "cargando los amigos aceptados")

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

    Log.d(
        "FRIENDSHIP_API",
        "Cargando solicitudes pendientes de amistad: googleId=$receiverGoogleId"
    )

    val response = FriendshipBackend.service.getPendingFriendRequests(receiverGoogleId)
    ensureFriendshipSuccess(response, "cargando las solicitudes pendientes de amistad")

    return response.body()
        .orEmpty()
        .mapNotNull(UserSummaryResponse::toFriendSearchUser)
        .sortedBy { it.username.lowercase(Locale.ROOT) }
}

suspend fun enviarSolicitudAmistad(
    requesterGoogleId: String,
    targetGoogleId: String
) {
    if (requesterGoogleId.isBlank() || targetGoogleId.isBlank()) {
        return
    }

    Log.d(
        "FRIENDSHIP_API",
        "Enviando solicitud de amistad: senderGoogleId=$requesterGoogleId receiverGoogleId=$targetGoogleId"
    )

    val response = FriendshipBackend.service.createFriendship(
        FriendshipRequest(
            senderGoogleId = requesterGoogleId,
            receiverGoogleId = targetGoogleId
        )
    )
    ensureFriendshipSuccess(response, "enviando la solicitud de amistad")
}

suspend fun aceptarSolicitudAmistad(
    receiverGoogleId: String,
    senderGoogleId: String
) {
    if (receiverGoogleId.isBlank() || senderGoogleId.isBlank()) {
        return
    }

    Log.d(
        "FRIENDSHIP_API",
        "Aceptando solicitud de amistad: receiverGoogleId=$receiverGoogleId senderGoogleId=$senderGoogleId"
    )

    val response = FriendshipBackend.service.acceptFriendRequest(receiverGoogleId, senderGoogleId)
    ensureFriendshipSuccess(response, "aceptando la solicitud de amistad")
}

suspend fun denegarSolicitudAmistad(
    receiverGoogleId: String,
    senderGoogleId: String
) {
    if (receiverGoogleId.isBlank() || senderGoogleId.isBlank()) {
        return
    }

    Log.d(
        "FRIENDSHIP_API",
        "Denegando solicitud de amistad: receiverGoogleId=$receiverGoogleId senderGoogleId=$senderGoogleId"
    )

    val response = FriendshipBackend.service.declineFriendRequest(receiverGoogleId, senderGoogleId)
    ensureFriendshipSuccess(response, "denegando la solicitud de amistad")
}

suspend fun eliminarAmigoUsuario(
    requesterGoogleId: String,
    targetGoogleId: String
) {
    if (requesterGoogleId.isBlank() || targetGoogleId.isBlank()) {
        return
    }

    Log.d(
        "FRIENDSHIP_API",
        "Eliminando amistad: googleId=$requesterGoogleId friendGoogleId=$targetGoogleId"
    )

    val response = FriendshipBackend.service.deleteFriend(requesterGoogleId, targetGoogleId)
    ensureFriendshipSuccess(response, "eliminando la amistad")
}
