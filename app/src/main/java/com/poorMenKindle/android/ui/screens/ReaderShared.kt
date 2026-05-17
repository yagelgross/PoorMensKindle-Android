package com.poorMenKindle.android.ui.screens

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// CSS Font Generator
fun getAllFontsCss(): String {
    val basePath = "https://appassets.local/fonts/"
    return """
        @font-face { font-family: 'Merriweather'; src: url('${basePath}Merriweather_120pt-Regular.ttf'); }
        @font-face { font-family: 'Libre Baskerville'; src: url('${basePath}LibreBaskerville-Regular.ttf'); }
        @font-face { font-family: 'Alegreya'; src: url('${basePath}Alegreya-Regular.ttf'); }
        @font-face { font-family: 'Arimo'; src: url('${basePath}Arimo-Regular.ttf'); }
        @font-face { font-family: 'Georgia'; src: url('${basePath}Georgia.ttf'); }
        @font-face { font-family: 'Arial'; src: url('${basePath}Arial.ttf'); }
        @font-face { font-family: 'Times New Roman'; src: url('${basePath}Times%20New%20Roman.ttf'); }
        @font-face { font-family: 'Frank Ruhl Libre'; src: url('${basePath}FrankRuhlLibre-Regular.ttf'); }
        @font-face { font-family: 'Heebo'; src: url('${basePath}Heebo-Regular.ttf'); }
        @font-face { font-family: 'Rubik'; src: url('${basePath}Rubik-Regular.ttf'); }
    """.trimIndent()
}

// Battery & Time Background Poller
@Composable
fun rememberBatteryAndTime(context: Context): Pair<String, String> {
    var currentTime by remember { mutableStateOf("") }
    var batteryStatus by remember { mutableStateOf("🔋--%") }

    LaunchedEffect(Unit) {
        var ticks = 0
        while (isActive) {
            currentTime = LocalTime.now(ZoneId.of("Asia/Jerusalem")).format(DateTimeFormatter.ofPattern("HH:mm"))

            if (ticks % 60 == 0) {
                val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryIntent = context.registerReceiver(null, intentFilter)
                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val pct = if (scale > 0) (level * 100) / scale else 0
                batteryStatus = if (isCharging) "⚡$pct%" else "🔋$pct%"
            }
            ticks++
            delay(1000)
        }
    }
    return Pair(currentTime, batteryStatus)
}

// Smart Tool Dialog (Dictionary/Translate/Wiki)
@Composable
fun SmartToolDialog(
    title: String,
    content: String,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    if (title.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4dd0e1),
                    fontFamily = FontFamily.Serif
                )
            },
            text = {
                if (isLoading) {
                    CircularProgressIndicator(color = Color(0xFF4dd0e1))
                } else {
                    Text(text = content, fontSize = 16.sp, fontFamily = FontFamily.Serif, color = Color.White)
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Color(0xFF4dd0e1), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF2b2b2b),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// Reader Settings Dialog
@Composable
fun ReaderSettingsDialog(
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    lineSpacing: Float,
    onLineSpacingChange: (Float) -> Unit,
    selectedFont: String,
    onFontChange: (String) -> Unit,
    theme: String,
    onThemeChange: (String) -> Unit,
    volumePagingEnabled: Boolean,
    onVolumePagingChange: (Boolean) -> Unit,
    swipeToChangeChaptersEnabled: Boolean,
    onSwipeToChangeChaptersChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Reading Settings", color = Color(0xFF4dd0e1), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Font Size
                Column {
                    Text("Font Size: ${fontSize}px", color = Color.White)
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = { onFontSizeChange(it.toInt()) },
                        valueRange = 12f..36f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF4dd0e1), activeTrackColor = Color(0xFF4dd0e1))
                    )
                }
                // Line Spacing
                Column {
                    Text("Line Spacing: ${lineSpacing}x", color = Color.White)
                    Slider(
                        value = lineSpacing,
                        onValueChange = { onLineSpacingChange((Math.round(it * 10) / 10.0).toFloat()) },
                        valueRange = 1.0f..2.5f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF4dd0e1), activeTrackColor = Color(0xFF4dd0e1))
                    )
                }
                // Font Family
                Column {
                    Text("Font", color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            "Serif", "Sans-Serif", "Monospace", "Alegreya", "Arial", "Arimo", "Georgia",
                            "Libre Baskerville", "Merriweather", "Times New Roman", "Frank Ruhl Libre", "Heebo", "Rubik"
                        ).forEach { fontOption ->
                            Button(
                                onClick = { onFontChange(fontOption) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedFont == fontOption) Color(0xFF4dd0e1) else Color(0xFF444444),
                                    contentColor = if (selectedFont == fontOption) Color.Black else Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(fontOption, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                // Theme
                Column {
                    Text("Theme", color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("Dark", "Light", "Sepia").forEach { themeOption ->
                            Button(
                                onClick = { onThemeChange(themeOption) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (theme == themeOption) Color(0xFF4dd0e1) else Color(0xFF444444),
                                    contentColor = if (theme == themeOption) Color.Black else Color.White
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(themeOption, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                // Volume Paging
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Volume Button Paging", color = Color.White)
                    Switch(
                        checked = volumePagingEnabled,
                        onCheckedChange = onVolumePagingChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFF4dd0e1),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF444444)
                        )
                    )
                }
                // Swipe to Change Chapters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Swipe to Change Chapters", color = Color.White)
                    Switch(
                        checked = swipeToChangeChaptersEnabled,
                        onCheckedChange = onSwipeToChangeChaptersChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFF4dd0e1),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF444444)
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFF4dd0e1)) }
        },
        containerColor = Color(0xFF2b2b2b)
    )
}