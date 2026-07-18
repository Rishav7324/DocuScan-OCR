package com.example

import android.app.Activity
import android.os.Bundle
import android.net.Uri
import com.example.data.api.OAuthManager

/**
 * Invisible activity that catches the OAuth redirect (docuscan://oauth) and forwards it
 * to OAuthManager, then returns to the app.
 */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { uri: Uri ->
            OAuthManager.handleRedirect(uri)
        }
        finish()
    }
}
