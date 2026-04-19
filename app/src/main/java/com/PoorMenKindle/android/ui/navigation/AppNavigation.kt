package com.PoorMenKindle.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.PoorMenKindle.android.ui.screens.LocalReadScreen
import com.PoorMenKindle.android.ui.screens.LoginScreen
import com.PoorMenKindle.android.ui.screens.ReadScreen
import com.PoorMenKindle.android.ui.screens.RequestNewScreen
import com.PoorMenKindle.android.ui.screens.LibraryScreen
import com.PoorMenKindle.android.ui.screens.AdminScreen
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.PoorMenKindle.android.network.NetworkManager
import com.PoorMenKindle.android.ui.screens.BookDetailScreen
import android.util.Base64
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Request : Screen("request")
    object Read : Screen("read/{bookId}/{totalChapters}/{savedChapter}") {
        fun createRoute(bookId: Int, totalChapters: Int, savedChapter: Int) =
            "read/$bookId/$totalChapters/$savedChapter"
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

        composable(Screen.Request.route) {
            LibraryScreen(
                onNavigateToRead = { bookId, totalChapters, savedChapter ->
                    navController.navigate(Screen.Read.createRoute(bookId, totalChapters, savedChapter))
                },
                onNavigateToRequestNew = { navController.navigate(Screen.RequestNew.route) },
                onNavigateToAdmin = { navController.navigate(Screen.Admin.route) },
                onNavigateToLocalRead = { uriString -> navController.navigate(Screen.LocalRead.createRoute(uriString)) },
                onNavigateToBookDetail = { bookId ->
                    navController.navigate("book_detail/$bookId")
                },
                onLogout = performLogout
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
                onNavigateToRead = { id, totalChapters, currentChapter ->
                    navController.navigate("read/$id/$totalChapters/$currentChapter")
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
                navArgument("savedChapter") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getInt("bookId") ?: 0
            val totalChapters = backStackEntry.arguments?.getInt("totalChapters") ?: 0
            val savedChapter = backStackEntry.arguments?.getInt("savedChapter") ?: 0

            ReadScreen(
                bookId = bookId,
                totalChapters = totalChapters,
                initialChapter = savedChapter,
                onNavigateBack = { navController.popBackStack() }
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