package com.safesteps.data

import android.util.Log
import java.io.IOException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path


private const val EMPTY_BODY_ERROR = "Resposta buida"

enum class IssueApiType {
    OBRES,
    ACCESSIBILITAT,
    SEGURETAT,
    ALTRES
}

data class Coord(
    val lat: Double,
    val lon: Double
)

data class IssueRequestDTO(
    val googleId: String,
    val type: IssueApiType,
    val description: String,
    val coordinates: Coord
)

data class IssueResponseDTO(
    val id: Long,
    val type: IssueApiType,
    val description: String?,
    val coordinates: Coord,
    val positiveVotes: Int,
    val negativeVotes: Int,
    val reliabilityIndex: Double,
    val status: String,
    val authorName: String?,
    val authorLevel: Int,
    val createdAt: String,
    val updatedAt: String
)

data class VoteRequestDTO(
    val googleId: String,
    val voteScore: Int,
    val voteScoreValid: Boolean
)

data class VoteResponseDTO(
    val id: Long,
    val incidenceId: Long,
    val googleId: String,
    val score: Double,
    val createdAt: String
)

data class VoteCountDTO(
    val id: Long,
    val positiveVotes: Int,
    val negativeVotes: Int,
    val reliabilityIndex: Double
)


private interface IssueApiService {

    @POST("api/v1/incidents")
    suspend fun reportIssue(@Body request: IssueRequestDTO): Response<IssueResponseDTO>

    @GET("api/v1/incidents")
    suspend fun getAllIssue(): Response<List<IssueResponseDTO>>

    @GET("api/v1/incidents/{id}")
    suspend fun getIssueById(@Path("id") id: Long): Response<IssueResponseDTO>

    @PUT("api/v1/incidents/{id}")
    suspend fun updateIssue(
        @Path("id") id: Long,
        @Body request: IssueRequestDTO
    ): Response<IssueResponseDTO>

    @DELETE("api/v1/incidents/{id}")
    suspend fun deleteIssue(@Path("id") id: Long): Response<Unit>

    @GET("api/v1/incidents/users/{googleId}")
    suspend fun getIssuesByUser(
        @Path("googleId") googleId: String
    ): Response<List<IssueResponseDTO>>
}

private interface VotesApiService {

    @POST("api/v1/incidents/{id}/votes")
    suspend fun voteIssue(
        @Path("id") id: Long,
        @Body request: VoteRequestDTO
    ): Response<VoteResponseDTO>

    @GET("api/v1/incidents/{id}/count-votes")
    suspend fun countVotes(@Path("id") id: Long): Response<VoteCountDTO>

    @GET("api/v1/incidents/votes/users/{googleId}")
    suspend fun getVotesByUser(
        @Path("googleId") googleId: String
    ): Response<List<VoteResponseDTO>>

    @DELETE("api/v1/incidents/{incidenceId}/users/{googleId}/vote")
    suspend fun deleteUserVoteFromIssue(
        @Path("incidenceId") incidenceId: Long,
        @Path("googleId") googleId: String
    ): Response<Unit>

    @DELETE("api/v1/incidents/votes/{voteId}")
    suspend fun deleteVoteById(@Path("voteId") voteId: Long): Response<Unit>
}

private object IssueBackend {
    private const val BASE_URL = "http://nattech.fib.upc.edu:40381/"

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: IssueApiService by lazy {
        retrofit.create(IssueApiService::class.java)
    }
}

private object VotesBackend {
    private const val BASE_URL = "http://nattech.fib.upc.edu:40381/"

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: VotesApiService by lazy {
        retrofit.create(VotesApiService::class.java)
    }
}

suspend fun crearIncidencia(request: IssueRequestDTO): IssueResponseDTO {
    Log.d("INCIDENTS_API", "Creant incidència: tipus=${request.type.name}")
    val response = IssueBackend.service.reportIssue(request)

    if (!response.isSuccessful) {
        throw IOException("Error creant l'incidència: ${response.code()}")
    }
    return response.body() ?: throw IOException(EMPTY_BODY_ERROR)
}

suspend fun getAllIssues(): List<IssueResponseDTO> {
    val response = IssueBackend.service.getAllIssue()
    if (!response.isSuccessful) {
        throw IOException("Error obtenint incidències: ${response.code()}")
    }
    return response.body() ?: emptyList()
}

suspend fun getIssuesByUser(googleId: String): List<IssueResponseDTO> {
    val response = IssueBackend.service.getIssuesByUser(googleId)
    if (!response.isSuccessful) {
        throw IOException("Error obtenint incidencies de l'usuari: ${response.code()}")
    }
    return response.body() ?: emptyList()
}

suspend fun votarIncidencia(idIncidencia: Long, request: VoteRequestDTO): VoteResponseDTO {
    val response = VotesBackend.service.voteIssue(idIncidencia, request)
    if (!response.isSuccessful) {
        val errorBody = response.errorBody()?.string().orEmpty()
        Log.e("INCIDENTS_API", "Vot fallit: code=${response.code()} body=$errorBody")
        throw IOException("Error al votar: ${response.code()} - $errorBody")
    }
    return response.body() ?: throw IOException(EMPTY_BODY_ERROR)
}
suspend fun eliminarIncidencia(idIncidencia: Long) {
    val response = IssueBackend.service.deleteIssue(idIncidencia)
    if (!response.isSuccessful) throw IOException("Error esborrant: ${response.code()}")
}

suspend fun eliminarVot(idIncidencia: Long, googleId: String) {
    val response = VotesBackend.service.deleteUserVoteFromIssue(idIncidencia, googleId)
    if (!response.isSuccessful) {
        throw IOException("Error esborrant vot: ${response.code()}")
    }
}

suspend fun eliminarVotPerId(voteId: Long) {
    val response = VotesBackend.service.deleteVoteById(voteId)
    if (!response.isSuccessful) {
        throw IOException("Error esborrant vot per id: ${response.code()}")
    }
}

suspend fun obtenirVotsUsuari(googleId: String): List<VoteResponseDTO> {
    val response = VotesBackend.service.getVotesByUser(googleId)
    if (!response.isSuccessful) {
        throw IOException("Error obtenint vots de l'usuari: ${response.code()}")
    }
    return response.body() ?: emptyList()
}

suspend fun actualitzarIncidencia(idIncidencia: Long, request: IssueRequestDTO): IssueResponseDTO {
    val response = IssueBackend.service.updateIssue(idIncidencia, request)
    if (!response.isSuccessful) {
        throw IOException("Error actualitzant: ${response.code()}")
    }
    return response.body() ?: throw IOException(EMPTY_BODY_ERROR)
}