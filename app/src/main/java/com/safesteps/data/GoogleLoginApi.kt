package com.safesteps.data

import android.util.Log
import com.safesteps.auth.UserInfo
import java.io.IOException
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

private const val GOOGLE_LOGIN_BASE_URL = "http://nattech.fib.upc.edu:40382/"
private const val GOOGLE_LOGIN_PATH = "api/v1/google-login"

private data class GoogleLoginRequest(
    val email: String,
    val username: String,
    val googleId: String
)

private interface GoogleLoginApiService {
    @POST(GOOGLE_LOGIN_PATH)
    suspend fun enviarUsuarioGoogle(
        @Body request: GoogleLoginRequest
    ): Response<ResponseBody>
}

private object GoogleLoginBackend {
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(GOOGLE_LOGIN_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: GoogleLoginApiService by lazy {
        retrofit.create(GoogleLoginApiService::class.java)
    }
}

suspend fun enviarUsuarioGoogleAlBackend(user: UserInfo) {
    if (user.email.isBlank() || user.username.isBlank() || user.googleId.isBlank()) {
        throw IOException("Faltan datos de Google para informar el inicio de sesion al backend")
    }

    Log.d(
        "GOOGLE_LOGIN_API",
        "Enviando usuario Google al backend: email=${user.email}, googleId=${user.googleId}"
    )

    val response = GoogleLoginBackend.service.enviarUsuarioGoogle(
        GoogleLoginRequest(
            email = user.email,
            username = user.username,
            googleId = user.googleId
        )
    )

    Log.d(
        "GOOGLE_LOGIN_API",
        "Respuesta HTTP: code=${response.code()} success=${response.isSuccessful}"
    )

    if (!response.isSuccessful) {
        throw IOException("Error enviando el inicio de sesion: ${response.code()} ${response.message()}")
    }
}
