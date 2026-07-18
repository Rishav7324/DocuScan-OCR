package com.example.data.api

import android.net.Uri
import android.util.Base64
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Minimal browser-based OAuth2 helper for Google Drive and Dropbox.
 * One redirect (docuscan://oauth) is shared by both providers; the provider is encoded in the state param.
 */
object OAuthManager {

    private const val REDIRECT_URI = "docuscan://oauth"
    const val SCHEME = "docuscan"
    const val HOST = "oauth"

    private val client = OkHttpClient.Builder().build()
    // Owned scope so the token exchange survives the finishing OAuthRedirectActivity without GlobalScope.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Load OAuth client credentials from string resources. Call once from Application/MainActivity. */
    fun init(context: android.content.Context) {
        val r = context.resources
        googleClientId = r.getString(R.string.oauth_google_client_id)
        googleClientSecret = r.getString(R.string.oauth_google_client_secret)
        dropboxClientId = r.getString(R.string.oauth_dropbox_client_id)
        dropboxClientSecret = r.getString(R.string.oauth_dropbox_client_secret)
    }

    // Set by the launching screen; consumed by OAuthRedirectActivity when the browser redirects back.
    private var pending: ((Result<OAuthResult>) -> Unit)? = null
    private var pendingVerifier: String? = null   // PKCE verifier for the in-flight auth request

    data class OAuthResult(
        val provider: String,
        val accessToken: String,
        val refreshToken: String? = null,
        val account: String? = null
    )

    enum class Provider(val key: String) {
        GOOGLE("google"), DROPBOX("dropbox")
    }

    // Set these from your OAuth app credentials (each provider's developer console).
    var googleClientId: String = ""
    var googleClientSecret: String = ""
    var dropboxClientId: String = ""
    var dropboxClientSecret: String = ""

    data class AuthRequest(
        val provider: Provider,
        val url: String,
        val codeVerifier: String? = null   // Dropbox PKCE
    )

    fun buildAuthRequest(provider: Provider): AuthRequest {
        val state = "${provider.key}_${randomString(8)}"
        return when (provider) {
            Provider.GOOGLE -> {
                val verifier = randomString(64)
                val challenge = pkceChallenge(verifier)
                AuthRequest(
                    provider,
                    Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
                        .appendQueryParameter("response_type", "code")
                        .appendQueryParameter("client_id", googleClientId)
                        .appendQueryParameter("redirect_uri", REDIRECT_URI)
                        .appendQueryParameter("scope", "https://www.googleapis.com/auth/drive.file")
                        .appendQueryParameter("access_type", "offline")
                        .appendQueryParameter("include_granted_scopes", "true")
                        .appendQueryParameter("code_challenge", challenge)
                        .appendQueryParameter("code_challenge_method", "S256")
                        .appendQueryParameter("state", state)
                        .build().toString(),
                    codeVerifier = verifier
                )
            }
            Provider.DROPBOX -> {
                val verifier = randomString(64)
                val challenge = pkceChallenge(verifier)
                AuthRequest(
                    provider,
                    Uri.parse("https://www.dropbox.com/oauth2/authorize").buildUpon()
                        .appendQueryParameter("response_type", "code")
                        .appendQueryParameter("client_id", dropboxClientId)
                        .appendQueryParameter("redirect_uri", REDIRECT_URI)
                        .appendQueryParameter("token_access_type", "offline")
                        .appendQueryParameter("code_challenge", challenge)
                        .appendQueryParameter("code_challenge_method", "S256")
                        .appendQueryParameter("state", state)
                        .build().toString(),
                    codeVerifier = verifier
                )
            }
        }
    }

    fun startAuth(provider: Provider, onResult: (Result<OAuthResult>) -> Unit): AuthRequest {
        pending = onResult
        val req = buildAuthRequest(provider)
        pendingVerifier = req.codeVerifier
        return req
    }

    /** Called from OAuthRedirectActivity with the redirected URI. */
    fun handleRedirect(uri: Uri) {
        val cb = pending ?: return
        pending = null
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")
        if (error != null) {
            cb(Result.failure(Exception("OAuth error: $error")))
            return
        }
        if (code == null || state == null) {
            cb(Result.failure(Exception("OAuth redirect missing code/state")))
            return
        }
        val provider = when {
            state.startsWith(Provider.GOOGLE.key) -> Provider.GOOGLE
            state.startsWith(Provider.DROPBOX.key) -> Provider.DROPBOX
            else -> null
        }
        if (provider == null) {
            cb(Result.failure(Exception("Unknown OAuth provider in state: $state")))
            return
        }
        // Exchange happens on a background thread; resolve via the callback.
        val verifier = pendingVerifier
        pendingVerifier = null
        exchange(provider, code, verifier, cb)
    }

    private fun exchange(provider: Provider, code: String, verifier: String?, cb: (Result<OAuthResult>) -> Unit) {
        scope.launch {
            runCatching {
                when (provider) {
                    Provider.GOOGLE -> exchangeForm(
                        url = "https://oauth2.googleapis.com/token",
                        code = code,
                        clientId = googleClientId,
                        clientSecret = googleClientSecret,
                        codeVerifier = verifier
                    ).let { OAuthResult(Provider.GOOGLE.key, it.first, it.second) }
                    Provider.DROPBOX -> exchangeForm(
                        url = "https://api.dropboxapi.com/oauth2/token",
                        code = code,
                        clientId = dropboxClientId,
                        clientSecret = dropboxClientSecret,
                        codeVerifier = verifier
                    ).let { res ->
                        val account = parseDropboxAccount(res.third)
                        OAuthResult(Provider.DROPBOX.key, res.first, res.second, account)
                    }
                }
            }.onSuccess { cb(Result.success(it)) }
                .onFailure { cb(Result.failure(it)) }
        }
    }

    // Returns Triple(accessToken, refreshToken?, rawBody)
    private fun exchangeForm(
        url: String,
        code: String,
        clientId: String,
        clientSecret: String,
        codeVerifier: String?
    ): Triple<String, String?, String> {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", clientId)
        // Installed/native clients have no usable secret; send it only when it's a real value.
        if (clientSecret.isNotBlank() && !clientSecret.startsWith("NOT_USED") && !clientSecret.startsWith("YOUR_")) {
            form.add("client_secret", clientSecret)
        }
        codeVerifier?.let { form.add("code_verifier", it) }
        val body = form.build()
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: throw Exception("Empty token response (${resp.code})")
            if (!resp.isSuccessful) throw Exception("Token exchange failed (${resp.code}): $text")
            val access = Regex("\"access_token\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                ?: throw Exception("No access_token in response")
            val refresh = Regex("\"refresh_token\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            return Triple(access, refresh, text)
        }
    }

    /**
     * Exchange a stored refresh token for a fresh access token. Blocking; call from a background thread.
     * Returns the new access token, or throws on failure.
     */
    fun refreshAccessToken(provider: Provider, refreshToken: String): String {
        val (url, clientId, clientSecret) = when (provider) {
            Provider.GOOGLE -> Triple("https://oauth2.googleapis.com/token", googleClientId, googleClientSecret)
            Provider.DROPBOX -> Triple("https://api.dropboxapi.com/oauth2/token", dropboxClientId, dropboxClientSecret)
        }
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", clientId)
        if (clientSecret.isNotBlank() && !clientSecret.startsWith("NOT_USED") && !clientSecret.startsWith("YOUR_")) {
            form.add("client_secret", clientSecret)
        }
        val request = Request.Builder().url(url).post(form.build()).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: throw Exception("Empty refresh response (${resp.code})")
            if (!resp.isSuccessful) throw Exception("Token refresh failed (${resp.code}): $text")
            return Regex("\"access_token\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                ?: throw Exception("No access_token in refresh response")
        }
    }

    private fun parseDropboxAccount(raw: String): String? =
        Regex("\"email\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)

    private fun randomString(len: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rng = SecureRandom()
        return (1..len).map { chars[rng.nextInt(chars.length)] }.joinToString("")
    }

    private fun pkceChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
