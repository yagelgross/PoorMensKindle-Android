package com.poorMenKindle.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.poorMenKindle.android.network.NetworkManager
import com.poorMenKindle.android.ui.theme.PoorMensKindleTheme
import com.poorMenKindle.android.ui.navigation.BookWormHoleApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NetworkManager.init(applicationContext)

        // Check session data
        val sharedPrefs = getSharedPreferences("BookWormHolePrefs", MODE_PRIVATE)
        val savedToken = sharedPrefs.getString("jwt_token", null)
        val savedIsAdmin = sharedPrefs.getBoolean("is_admin", false)

        // Start screen logic
        val startScreen = if (savedToken != null) {
            NetworkManager.jwtToken = savedToken
            NetworkManager.isAdmin = savedIsAdmin
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