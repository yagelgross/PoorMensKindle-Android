package com.poorMenKindle.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.poorMenKindle.android.ui.screens.LocalReadScreen
import com.poorMenKindle.android.ui.screens.LoginScreen
import com.poorMenKindle.android.ui.screens.ReadScreen
import com.poorMenKindle.android.ui.screens.RequestNewScreen
import com.poorMenKindle.android.ui.screens.LibraryScreen
import com.poorMenKindle.android.ui.screens.AdminScreen
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.poorMenKindle.android.network.NetworkManager
import com.poorMenKindle.android.ui.screens.BookDetailScreen
import android.util.Base64
import androidx.compose.foundation.clickable
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Request : Screen("request")
    // inside sealed class Screen
    object Read : Screen("read/{bookId}/{totalChapters}/{savedChapter}/{scrollProgress}/{returnChapter}/{returnScroll}") {
        fun createRoute(bookId: Int, totalChapters: Int, savedChapter: Int, scrollProgress: Float, returnChapter: Int = -1, returnScroll: Float = -1f) =
            "read/$bookId/$totalChapters/$savedChapter/$scrollProgress/$returnChapter/$returnScroll"
    }

    object RequestNew : Screen("request_new")
    object Admin : Screen("admin")

    object LocalRead : Screen("local_read/{uri}") {
        fun createRoute(uri: String): String {
            val encodedUri = Base64.encodeToString(
                uri.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP
            )
            return "local_read/$encodedUri"
        }
    }
}

@Composable
fun BookWormHoleApp(startDestination: String = Screen.Login.route) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Define a reusable logout function
    val performLogout = {
        try {
            // Clear the saved token from device storage
            context.getSharedPreferences("BookWormHolePrefs", Context.MODE_PRIVATE).edit {
                clear()
            }
            NetworkManager.jwtToken = null
            NetworkManager.isAdmin = false
            NetworkManager.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            navController.navigate(Screen.Login.route) {
                popUpTo(0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRequest = {
                    navController.navigate(Screen.Request.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // In your NavHost, add this alongside your other routes:
        composable("app_license") {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "MIT License",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    // Use a scrollable text area or just standard Text if it fits
                    Text(
                        text = """
                    Copyright (c) 2026 Yagel Gross

                    Permission is hereby granted, free of charge, to any person obtaining a copy
                    of this software and associated documentation files (the "Software"), to deal
                    in the Software without restriction, including without limitation the rights
                    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
                    copies of the Software, and to permit persons to whom the Software is
                    furnished to do so, subject to the following conditions:

                    The above copyright notice and this permission notice shall be included in all
                    copies or substantial portions of the Software.

                    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
                    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
                    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
                    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
                    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
                    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
                    SOFTWARE.
                """.trimIndent(),
                        fontSize = 14.sp
                    )
                }
            }
        }

        composable("licenses") {
            Surface(modifier = Modifier.fillMaxSize()) {
                LibrariesContainer(
                    modifier = Modifier.fillMaxSize(),
                    header = {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { navController.navigate("app_license") }
                                    .padding(horizontal = 24.dp, vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Poor Men's Kindle",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1E3A8A) // Matching your LibraryScreen theme
                                )

                                Text(
                                    text = "Developed solely by Yagel Gross, no Noam included",
                                    fontSize = 16.sp,
                                    color = Color.DarkGray,
                                    modifier = Modifier.padding(top = 8.dp)
                                )

                                Text(
                                    text = "Copyright © 2026",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                Text(
                                    text = "Licensed under the MIT License",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 12.dp)
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 24.dp),
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                )
            }
        }

        composable(Screen.Request.route) {
            LibraryScreen(
                onNavigateToRead = { bookId, totalChapters, savedChapter, scrollProgress ->
                    navController.navigate(Screen.Read.createRoute(bookId, totalChapters, savedChapter, scrollProgress))
                },
                onNavigateToRequestNew = { navController.navigate(Screen.RequestNew.route) },
                onNavigateToAdmin = { navController.navigate(Screen.Admin.route) },
                onNavigateToLocalRead = { uriString -> navController.navigate(Screen.LocalRead.createRoute(uriString)) },
                onNavigateToBookDetail = { bookId ->
                    navController.navigate("book_detail/$bookId")
                },
                onLogout = performLogout,
                onNavigateToLicenses = { navController.navigate("licenses") }
            )
        }



        composable(
            route = "book_detail/{bookId}",
            arguments = listOf(androidx.navigation.navArgument("bookId") { type = androidx.navigation.NavType.IntType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getInt("bookId") ?: 0

            BookDetailScreen(
                bookId = bookId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRead = { id, totalChapters, currentChapter, currentScroll  ->
                    navController.navigate(Screen.Read.createRoute(id, totalChapters, currentChapter, currentScroll))
                }
            )
        }

        composable(
            route = "highlights/{bookId}/{returnChapter}/{returnScroll}",
            arguments = listOf(
                androidx.navigation.navArgument("bookId") { type = androidx.navigation.NavType.IntType },
                androidx.navigation.navArgument("returnChapter") { type = androidx.navigation.NavType.IntType },
                androidx.navigation.navArgument("returnScroll") { type = androidx.navigation.NavType.FloatType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getInt("bookId") ?: 0
            val returnChapter = backStackEntry.arguments?.getInt("returnChapter") ?: -1
            val returnScroll = backStackEntry.arguments?.getFloat("returnScroll") ?: -1f

            com.poorMenKindle.android.ui.screens.HighlightsScreen(
                bookId = bookId,
                returnChapter = returnChapter,
                returnScroll = returnScroll,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRead = { id, totalChapters, currentChapter, currentScroll, retCh, retScroll ->
                    navController.navigate("read/$id/$totalChapters/$currentChapter/$currentScroll/$retCh/$retScroll") {
                        popUpTo("highlights/{bookId}/{returnChapter}/{returnScroll}") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.LocalRead.route,
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("uri") ?: ""
            // Decode the Base64 back into the exact, untouched Android URI string
            val decodedUri = String(
                Base64.decode(encodedUri, Base64.URL_SAFE or Base64.NO_WRAP),
                Charsets.UTF_8
            )

            LocalReadScreen(
                fileUriString = decodedUri,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Read.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.IntType },
                navArgument("totalChapters") { type = NavType.IntType },
                navArgument("savedChapter") { type = NavType.IntType },
                navArgument("scrollProgress") { type = NavType.FloatType },
                navArgument("returnChapter") { type = NavType.IntType },
                navArgument("returnScroll") { type = NavType.FloatType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getInt("bookId") ?: 0
            val totalChapters = backStackEntry.arguments?.getInt("totalChapters") ?: 0
            val savedChapter = backStackEntry.arguments?.getInt("savedChapter") ?: 0
            val scrollProgress = backStackEntry.arguments?.getFloat("scrollProgress") ?: 0f
            val returnChapter = backStackEntry.arguments?.getInt("returnChapter") ?: -1
            val returnScroll = backStackEntry.arguments?.getFloat("returnScroll") ?: -1f

            ReadScreen(
                bookId = bookId,
                totalChapters = totalChapters,
                initialChapter = savedChapter,
                initialScrollProgress = scrollProgress,
                returnChapterArg = returnChapter,
                returnScrollArg = returnScroll,
                onNavigateBack = { 
                    navController.navigate(Screen.Request.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToHighlights = { id, curCh, curScroll -> 
                    navController.navigate("highlights/$id/$curCh/$curScroll") 
                }
            )
        }

        composable(Screen.RequestNew.route) {
            RequestNewScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAdmin = { navController.navigate(Screen.Admin.route) },
                onLogout = performLogout
            )
        }

        composable(Screen.Admin.route) {
            AdminScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = performLogout
            )
        }

    }
}