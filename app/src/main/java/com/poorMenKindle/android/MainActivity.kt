package com.poorMenKindle.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.poorMenKindle.android.data.SessionManager
import com.poorMenKindle.android.network.NetworkManager
import com.poorMenKindle.android.ui.theme.PoorMensKindleTheme
import com.poorMenKindle.android.ui.navigation.BookWormHoleApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NetworkManager.init(applicationContext)

        // Restore session from encrypted storage
        val session = SessionManager.loadSession(this)

        // Start screen logic
        val startScreen = if (session != null) {
            NetworkManager.jwtToken = session.token
            NetworkManager.isAdmin = session.isAdmin
            "request" // Skip login
        } else {
            "login"
        }

        setContent {
            PoorMensKindleTheme {
                // Start app navigation
                BookWormHoleApp(startDestination = startScreen)
            }
        }
    }
}