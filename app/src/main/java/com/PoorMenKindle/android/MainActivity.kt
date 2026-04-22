package com.PoorMenKindle.android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.PoorMenKindle.android.network.NetworkManager
import com.PoorMenKindle.android.ui.LanguageManager
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
            var currentLanguage by remember {
                mutableStateOf(LanguageManager.getLanguage(this))
            }

            val localeContext = remember(currentLanguage) {
                LanguageManager.updateContextLocale(this, currentLanguage)
            }

            CompositionLocalProvider(LocalContext provides localeContext) {
                PoorMensKindleTheme {
                    BookWormHoleApp(
                        startDestination = startScreen,
                        onLanguageChange = { newLang ->
                            LanguageManager.setLanguage(localeContext, newLang)
                            currentLanguage = newLang
                        }
                    )
                }
            }
        }
    }
}