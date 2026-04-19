package com.PoorMenKindle.android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.PoorMenKindle.android.network.NetworkManager
import com.PoorMenKindle.android.ui.theme.PoorMensKindleTheme
import com.PoorMenKindle.android.ui.navigation.BookWormHoleApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Check for saved session data
        val sharedPrefs = getSharedPreferences("BookWormHolePrefs", Context.MODE_PRIVATE)
        val savedToken = sharedPrefs.getString("jwt_token", null)
        val savedIsAdmin = sharedPrefs.getBoolean("is_admin", false)

        // 2. Determine the starting screen
        val startScreen = if (savedToken != null) {
            NetworkManager.jwtToken = savedToken
            NetworkManager.isAdmin = savedIsAdmin
            "request" // Skip login!
        } else {
            "login"
        }

        com.PoorMenKindle.android.network.NetworkManager.init(applicationContext)

        setContent {
            PoorMensKindleTheme {
                // Pass the dynamic start destination to your NavHost
                BookWormHoleApp(startDestination = startScreen)
            }
        }
    }
}