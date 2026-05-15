package com.safesteps.data

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import java.io.IOException
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CHAT_BASE_URL = "http://nattech.fib.upc.edu:40382/"

// ── DTOs ──────────────────────────────────────────────────────────────────────

/**
 * Representació d'un xat retornada pel backend.
 *
 * NOTA PENDENT BACKEND: afegir [participantGoogleIds] a ChatResponseDTO.java:
 *   private List<String> participantGoogleIds;
 *   // al constructor: this.participantGoogleIds = chat.getParticipants().stream()
 *   //     .map(p -> p.getUser().getGoogleId()).toList();
 * Sense això, la identificació de l'altre participant es fa per username (fràgil).
 */
data class ChatDto(
    val id: Long,
    val type: String,
    val name: String? = null,
    val createdAt: String? = null,
    val participantUsernames: List<String> = emptyList(),
    val participantGoogleIds: List<String> = emptyList() // pendent d'afegir al backend
)

fun obtenirDataActual(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS +0200", Locale.getDefault())
    return sdf.format(Date())
}

data class MessageDto(
    val id: Long,
    val chatId: Long,
    val senderUsername: String,
    val senderGoogleId: String,
    val content: String,
    val createdAt: String
)

private data class CreateChatRequest(
    val type: String,
    val name: String?,
    val participantGoogleIds: List<String>
)

private data class SendMessageRequest(
    val content: String,
    val senderGoogleId: String
)


private interface ChatApiService {

    @GET("api/v1/chats/user/{googleId}")
    suspend fun getUserChats(@Path("googleId") googleId: String): Response<List<ChatDto>>

    @POST("api/v1/chats")
    suspend fun createChat(@Body request: CreateChatRequest): Response<ChatDto>

    @POST("api/v1/chats/{chatId}/messages")
    suspend fun sendMessage(@Path("chatId") chatId: Long, @Body request: SendMessageRequest): Response<MessageDto>

    @GET("api/v1/chats/{chatId}/messages")
    suspend fun getMessages(@Path("chatId") chatId: Long): Response<List<MessageDto>>

    @PUT("api/v1/chats/{chatId}/messages/{messageId}/read")
    suspend fun markAsRead(
        @Path("chatId") chatId: Long,
        @Path("messageId") messageId: Long,
        @Query("googleId") googleId: String
    ): Response<Unit>


    @POST("api/v1/chats/{chatId}/participants")
    suspend fun addParticipant(
        @Path("chatId") chatId: Long,
        @Query("adminId") adminId: String,
        @Query("newUserGoogleId") newUserGoogleId: String
    ): Response<Unit>

    @DELETE("api/v1/chats/{chatId}/participants/exit")
    suspend fun exitChat(
        @Path("chatId") chatId: Long,
        @Query("googleId") googleId: String
    ): Response<Unit>

    @DELETE("api/v1/chats/{chatId}/participants/{targetId}")
    suspend fun kickParticipant(
        @Path("chatId") chatId: Long,
        @Path("targetId") targetId: String,
        @Query("adminId") adminId: String
    ): Response<Unit>

    @PATCH("api/v1/chats/{chatId}/participants/{targetId}/admin")
    suspend fun promoteToAdmin(
        @Path("chatId") chatId: Long,
        @Path("targetId") targetId: String,
        @Query("adminId") adminId: String
    ): Response<Unit>

    @DELETE("api/v1/chats/{chatId}/participants/{targetId}/admin")
    suspend fun demoteFromAdmin(
        @Path("chatId") chatId: Long,
        @Path("targetId") targetId: String,
        @Query("adminId") adminId: String
    ): Response<Unit>
}


private object ChatBackend {
    private val gson = GsonBuilder()
        .registerTypeAdapter(MessageDto::class.java,
            JsonDeserializer<MessageDto> { json, _, context ->
                val obj = json.asJsonObject

                // Verificació de seguretat per al camp createdAt
                if (!obj.has("createdAt") || obj.get("createdAt").isJsonNull) {
                    // Si el servidor no l'envia (com passa al POST), el generem aquí
                    obj.addProperty("createdAt", obtenirDataActual())
                }

                // IMPORTANT: No creis un nou Gson(). Usa context.deserialize per evitar bucles infinits.
                // Per evitar que torni a cridar aquest adaptador, hem de parsejar els camps manualment
                // o fer que el DTO sigui una classe simple.

                // jsonStr: obté String segur d'un camp (gestiona Java-null i JsonNull)
                fun str(key: String): String {
                    val el = obj.get(key) ?: return ""
                    return if (el.isJsonNull) "" else try { el.asString ?: "" } catch (_: Exception) { "" }
                }
                fun safeLong(key: String): Long {
                    val el = obj.get(key) ?: return -1L
                    return if (el.isJsonNull) -1L else try { el.asLong } catch (_: Exception) { -1L }
                }
                MessageDto(
                    id             = safeLong("id"),
                    chatId         = safeLong("chatId"),
                    senderUsername = str("senderUsername"),
                    senderGoogleId = str("senderGoogleId"),
                    content        = str("content"),
                    createdAt      = str("createdAt")   // buit si el POST no el retorna; el VM usa hora local
                )
            })
        .create()

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(CHAT_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson)) // FIX: usar el gson custom amb el deserialitzador de MessageDto
            .build()
    }

    val service: ChatApiService by lazy {
        retrofit.create(ChatApiService::class.java)
    }
}


suspend fun obtenirXatsUsuari(googleId: String): List<ChatDto> {
    Log.d("CHAT_API", "Iniciant crida per a $googleId")
    return try {
        val response = ChatBackend.service.getUserChats(googleId)

        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            Log.e("CHAT_API", "Error servidor: ${response.code()}")
            throw IOException("Error backend")
        }
    } catch (t: Throwable) {
        Log.e("CHAT_API", "¡ERROR DETECTAT!", t)
        throw t
    }
}

suspend fun crearXatPrivat(myGoogleId: String, otherGoogleId: String): ChatDto =
    crearXat(type = "PRIVATE", name = null, participantGoogleIds = listOf(myGoogleId, otherGoogleId))

suspend fun crearXat(type: String, name: String?, participantGoogleIds: List<String>): ChatDto {
    Log.d("CHAT_API", "Creant xat type=$type name=$name participants=$participantGoogleIds")
    val request = CreateChatRequest(type = type, name = name, participantGoogleIds = participantGoogleIds)
    val response = ChatBackend.service.createChat(request)
    if (!response.isSuccessful) {
        val error = response.errorBody()?.string().orEmpty()
        Log.e("CHAT_API", "Error creant xat: code=${response.code()} body=$error")
        throw IOException("Error creant xat: ${response.code()}")
    }
    return response.body() ?: throw IOException("Resposta buida al crear xat")
}

suspend fun obtenirMissatges(chatId: Long): List<MessageDto> {
    val response = ChatBackend.service.getMessages(chatId)
    if (!response.isSuccessful) {
        throw IOException("Error obtenint missatges: ${response.code()}")
    }
    return response.body() ?: emptyList()
}

suspend fun  enviarMissatge(chatId: Long, senderGoogleId: String, content: String): MessageDto {
    Log.d("CHAT_API", "Enviant missatge a chatId=$chatId")
    val response = ChatBackend.service.sendMessage(
        chatId = chatId,
        request = SendMessageRequest(content = content, senderGoogleId = senderGoogleId)
    )
    if (!response.isSuccessful) {
        val error = response.errorBody()?.string().orEmpty()
        Log.e("CHAT_API", "Error enviant missatge: code=${response.code()} body=$error")
        throw IOException("Error enviant missatge: ${response.code()}")
    }
    val body = response.body() ?: throw IOException("Resposta buida al enviar missatge")
    val date = body.createdAt as String?
    return if (date.isNullOrBlank()) {
        body.copy(createdAt = obtenirDataActual())
    } else {
        body
    }
}

suspend fun marcarMissatgeLlegit(chatId: Long, messageId: Long, googleId: String) {
    val response = ChatBackend.service.markAsRead(chatId, messageId, googleId)
    if (!response.isSuccessful) {
        Log.w("CHAT_API", "No s'ha pogut marcar missatge $messageId com a llegit")
    }
}


suspend fun afegirParticipant(chatId: Long, adminId: String, newUserGoogleId: String) {
    Log.d("CHAT_API", "Afegint participant $newUserGoogleId al xat $chatId (admin=$adminId)")
    val response = ChatBackend.service.addParticipant(chatId, adminId, newUserGoogleId)
    if (!response.isSuccessful) {
        throw IOException("Error afegint participant: ${response.code()}")
    }
}

suspend fun sortirDelXat(chatId: Long, googleId: String) {
    Log.d("CHAT_API", "Sortint del xat $chatId (googleId=$googleId)")
    val response = ChatBackend.service.exitChat(chatId, googleId)
    if (!response.isSuccessful) {
        throw IOException("Error sortint del xat: ${response.code()}")
    }
}

suspend fun expulsarParticipant(chatId: Long, targetId: String, adminId: String) {
    Log.d("CHAT_API", "Expulsant $targetId del xat $chatId (admin=$adminId)")
    val response = ChatBackend.service.kickParticipant(chatId, targetId, adminId)
    if (!response.isSuccessful) {
        throw IOException("Error expulsant participant: ${response.code()}")
    }
}

suspend fun promocionarAdmin(chatId: Long, targetId: String, adminId: String) {
    val response = ChatBackend.service.promoteToAdmin(chatId, targetId, adminId)
    if (!response.isSuccessful) {
        throw IOException("Error promocionant admin: ${response.code()}")
    }
}

suspend fun revocarAdmin(chatId: Long, targetId: String, adminId: String) {
    val response = ChatBackend.service.demoteFromAdmin(chatId, targetId, adminId)
    if (!response.isSuccessful) {
        throw IOException("Error revocant admin: ${response.code()}")
    }
}