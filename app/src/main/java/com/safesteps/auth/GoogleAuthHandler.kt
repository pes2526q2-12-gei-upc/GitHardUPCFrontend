package com.safesteps.auth

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.safesteps.R

@Composable
fun rememberGoogleSignInAction(
    onUserLoggedIn: (UserInfo) -> Unit,
    onSessionRestored: (UserInfo?) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val googleSignInOptions = remember {
        createGoogleSignInOptions()
    }
    val googleSignInClient = remember(context, googleSignInOptions) {
        GoogleSignIn.getClient(context, googleSignInOptions)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                onUserLoggedIn(account.toUserInfo())
            } else {
                notifySignInFailure(context)
            }
        } catch (e: ApiException) {
            Log.e("GOOGLE_AUTH", "Sign in failed: ${e.statusCode}")
            notifySignInFailure(context)
        }
    }

    LaunchedEffect(context) {
        onSessionRestored(GoogleSignIn.getLastSignedInAccount(context)?.toUserInfo())
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onSessionRestored(GoogleSignIn.getLastSignedInAccount(context)?.toUserInfo())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return remember(googleSignInLauncher, googleSignInClient) {
        { googleSignInLauncher.launch(googleSignInClient.signInIntent) }
    }
}

@Composable
fun rememberGoogleSignOutAction(
    onLoggedOut: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val googleSignInOptions = remember {
        createGoogleSignInOptions()
    }
    val googleSignInClient = remember(context, googleSignInOptions) {
        GoogleSignIn.getClient(context, googleSignInOptions)
    }

    return remember(googleSignInClient, onLoggedOut) {
        {
            googleSignInClient.signOut().addOnCompleteListener {
                onLoggedOut()
            }
        }
    }
}

private fun GoogleSignInAccount.toUserInfo(): UserInfo {
    val resolvedEmail = email.orEmpty()
    val resolvedUsername = displayName?.takeIf { it.isNotBlank() }
        ?: resolvedEmail.substringBefore("@").takeIf { it.isNotBlank() }
        ?: resolvedEmail

    return UserInfo(
        username = resolvedUsername,
        email = resolvedEmail,
        photoUrl = photoUrl?.toString(),
        idToken = idToken
    )
}

private fun createGoogleSignInOptions(): GoogleSignInOptions {
    return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestProfile()
        .build()
}

private fun notifySignInFailure(context: Context) {
    Toast.makeText(
        context,
        context.getString(R.string.sign_in_failed),
        Toast.LENGTH_SHORT
    ).show()
}
