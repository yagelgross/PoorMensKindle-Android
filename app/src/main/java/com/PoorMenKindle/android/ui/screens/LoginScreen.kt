package com.PoorMenKindle.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.PoorMenKindle.android.network.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onNavigateToRequest: () -> Unit) {
    val context = LocalContext.current
    // State variables (replacing your JavaFX text fields and boolean flags)
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var loginInProgress by remember { mutableStateOf(false) }

    // Coroutine scope for network calls (replaces new Thread(() -> {...}).start())
    val coroutineScope = rememberCoroutineScope()

    // 1. OUTER BACKGROUND – cyan-to-light-blue gradient
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF99e2ff),
            Color(0xFF8ee1f3),
            Color(0xFF5bccee),
            Color(0xFF5b97f5)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        // 2. FROSTED-GLASS CARD
        Box(
            modifier = Modifier
                .width(380.dp)
                .padding(20.dp)
                .shadow(elevation = 20.dp, shape = RoundedCornerShape(24.dp), spotColor = Color(0xFF7ec8e3))
                .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(horizontal = 30.dp, vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // --- ANIMATED TITLE ---
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.08f, // Pulses 8% larger
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                Text(
                    text = "⟡ BookWormHole ⟡",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFc9a84c),
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                )

                Text(
                    text = "— enter the wormhole —",
                    fontSize = 12.sp,
                    color = Color(0xFF8a8a9a),
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. INPUT FIELDS
                val textFieldColors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.65f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.45f),
                    focusedIndicatorColor = Color(0xFFc9a84c),
                    unfocusedIndicatorColor = Color(0x6696A0B4),
                    focusedTextColor = Color(0xFF0e0e1e),
                    unfocusedTextColor = Color(0xFF1a1a2e)
                )

                TextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = { Text("✦ Username", color = Color(0xFF6a7a8a)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    colors = textFieldColors,
                    singleLine = true
                )

                TextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("✦ Password", color = Color(0xFF6a7a8a)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    colors = textFieldColors,
                    singleLine = true
                )

                // 5. ERROR LABEL
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFff6b6b),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // 6. LOGIN BUTTON
                Button(
                    onClick = {
                        if (username.isEmpty() || password.isEmpty()) {
                            errorMessage = "Please fill in all fields!"
                            return@Button
                        }

                        errorMessage = ""
                        loginInProgress = true

                        // Launch network request safely
                        coroutineScope.launch {
                            try {
                                val response = withContext(Dispatchers.IO) {
                                    NetworkManager.api.login(username = username, password = password)
                                }

                                if (response.isSuccessful) {
                                    val loginBody = response.body()
                                    if (loginBody != null) {
                                        NetworkManager.jwtToken = loginBody.access_token
                                        NetworkManager.isAdmin = loginBody.is_admin

                                        // Save to SharedPreferences
                                        val sharedPrefs = context.getSharedPreferences("BookWormHolePrefs", android.content.Context.MODE_PRIVATE)
                                        sharedPrefs.edit()
                                            .putString("jwt_token", loginBody.access_token)
                                            .putBoolean("is_admin", loginBody.is_admin)
                                            .apply()

                                        onNavigateToRequest()
                                    } else {
                                        errorMessage = "Invalid response from server."
                                    }
                                } else {
                                    errorMessage = "Invalid username or password."
                                }
                            } catch (e: Exception) {
                                errorMessage = "Connection error: ${e.message}"
                            } finally {
                                loginInProgress = false
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFc9a84c)),
                    shape = RoundedCornerShape(20.dp),
                    enabled = !loginInProgress
                ) {
                    if (loginInProgress) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = "⟐ Enter ⟐",
                            color = Color(0xFF0a0a0f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}