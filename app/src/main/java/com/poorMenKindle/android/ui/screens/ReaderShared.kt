package com.poorMenKindle.android.ui.screens

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.view.KeyEvent
import android.view.View
import android.webkit.WebResourceResponse
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.poorMenKindle.android.network.NetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Both readers load their content against this origin; fonts (and, for local EPUBs,
// images) are served through shouldInterceptRequest, so WebView file access stays off.
const val READER_BASE_URL = "https://appassets.local/"

// CSS Font Generator
fun getAllFontsCss(): String {
    val basePath = "${READER_BASE_URL}fonts/"
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

/** Serves bundled fonts to the reader WebViews without enabling file access. */
fun serveReaderFont(context: Context, url: String?): WebResourceResponse? {
    if (url == null || !url.startsWith("${READER_BASE_URL}fonts/")) return null
    return try {
        val fileName = URLDecoder.decode(url.substringAfterLast("/"), "UTF-8")
        val inputStream = context.assets.open("fonts/$fileName")
        WebResourceResponse("font/ttf", "UTF-8", inputStream).apply {
            responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
        }
    } catch (e: Exception) {
        null
    }
}

// Chapter HTML comes from the server (or from arbitrary EPUB files); strip scripts,
// event handlers and unknown tags before it reaches a JS-enabled WebView.
private val chapterSafelist: Safelist = Safelist.relaxed()
    .addAttributes(":all", "dir")
    .addProtocols("img", "src", "http", "https", "data", "local")

fun sanitizeChapterHtml(html: String): String =
    Jsoup.clean(html, "", chapterSafelist, Document.OutputSettings().prettyPrint(false))

/** Returns (backgroundColor, textColor) for a reader theme name. */
fun readerThemeColors(theme: String): Pair<String, String> = when (theme) {
    "Light" -> "#ffffff" to "#000000"
    "Sepia" -> "#f4ecd8" to "#5b4636"
    else -> "#2b2b2b" to "#d4d4d4"
}

/** The reader page background for a theme, as a Compose Color (used for the page-slide fallback). */
fun composeReaderBgColor(theme: String): Color =
    Color(android.graphics.Color.parseColor(readerThemeColors(theme).first))

/** JS that applies the user's reading settings to the loaded chapter. */
fun readerStyleJs(settings: ReaderSettingsState): String {
    val (bgColor, textColor) = readerThemeColors(settings.theme)
    return """
        document.body.style.fontSize = '${settings.fontSize}px';
        document.body.style.lineHeight = '${settings.lineSpacing}';
        document.body.style.backgroundColor = '$bgColor';
        document.body.style.color = '$textColor';
        document.body.style.fontFamily = "'${settings.selectedFont}', serif";
    """.trimIndent()
}

/** Wraps sanitized chapter body HTML in the shared reader page template. */
fun buildReaderHtml(bodyHtml: String, selectedFont: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style id="custom-font-style">
            ${getAllFontsCss()}
        </style>
        <style>
            body {
                font-family: '$selectedFont', serif;
                font-size: 18px;
                padding: 20px 15px;
                color: #d4d4d4;
                background-color: #2b2b2b;
                line-height: 1.6;
                margin: 0;
            }
            p { margin-bottom: 1em; }
            img {
                max-width: 100%;
                height: auto;
                display: block;
                margin: 15px auto;
                border-radius: 8px;
            }
        </style>
    </head>
    <body dir="auto">
        $bodyHtml
    </body>
    </html>
""".trimIndent()

/** Reading preferences backed by the shared "ReadingSettings" prefs file. */
class ReaderSettingsState(private val prefs: SharedPreferences) {
    var fontSize by mutableIntStateOf(prefs.getInt("font_size", 18))
        private set
    var lineSpacing by mutableFloatStateOf(prefs.getFloat("line_spacing", 1.6f))
        private set
    var theme by mutableStateOf(prefs.getString("theme", "Dark") ?: "Dark")
        private set
    var selectedFont by mutableStateOf(prefs.getString("font", "Merriweather") ?: "Merriweather")
        private set
    var volumePagingEnabled by mutableStateOf(prefs.getBoolean("volume_paging", false))
        private set
    var swipeToChangeChaptersEnabled by mutableStateOf(prefs.getBoolean("swipe_to_change_chapters", true))
        private set

    fun updateFontSize(value: Int) {
        fontSize = value
        prefs.edit().putInt("font_size", value).apply()
    }

    fun updateLineSpacing(value: Float) {
        lineSpacing = value
        prefs.edit().putFloat("line_spacing", value).apply()
    }

    fun updateTheme(value: String) {
        theme = value
        prefs.edit().putString("theme", value).apply()
    }

    fun updateFont(value: String) {
        selectedFont = value
        prefs.edit().putString("font", value).apply()
    }

    fun updateVolumePaging(value: Boolean) {
        volumePagingEnabled = value
        prefs.edit().putBoolean("volume_paging", value).apply()
    }

    fun updateSwipeToChangeChapters(value: Boolean) {
        swipeToChangeChaptersEnabled = value
        prefs.edit().putBoolean("swipe_to_change_chapters", value).apply()
    }
}

@Composable
fun rememberReaderSettings(): ReaderSettingsState {
    val context = LocalContext.current
    return remember {
        ReaderSettingsState(context.getSharedPreferences("ReadingSettings", Context.MODE_PRIVATE))
    }
}

/** Keeps the screen on and hides system bars while the reader is visible. */
@Composable
fun ReaderImmersiveEffect() {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.let {
            it.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insetsController = WindowCompat.getInsetsController(it, view)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window?.let {
                it.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
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

// --- Smart tools (Dictionary / Translate / Wikipedia) ---

fun smartToolTitle(type: Int, query: String): String = when (type) {
    1 -> "📚 Dictionary: $query"
    2 -> "🌍 Hebrew Translation"
    else -> "🏛 Wikipedia: $query"
}

/** Blocking network lookup — call from Dispatchers.IO. */
suspend fun fetchSmartToolResult(client: OkHttpClient, type: Int, query: String): String {
    return try {
        if (type == 2) {
            val response = NetworkManager.api.translateText(query)
            if (response.isSuccessful) {
                response.body()?.get("translated_text") ?: "Translation missing."
            } else {
                "Server error: ${response.code()}"
            }
        } else {
            val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
            val url = if (type == 1) "https://api.dictionaryapi.dev/api/v2/entries/en/$encoded"
            else "https://en.wikipedia.org/api/rest_v1/page/summary/$encoded"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful && responseBody.isNotEmpty()) {
                if (type == 1) {
                    JSONArray(responseBody).getJSONObject(0).getJSONArray("meanings").getJSONObject(0)
                        .getJSONArray("definitions").getJSONObject(0).getString("definition")
                } else {
                    JSONObject(responseBody).getString("extract")
                }
            } else {
                "No results found."
            }
        }
    } catch (e: Exception) {
        "Error fetching data: ${e.localizedMessage}"
    }
}

/** Dialog state + fetch logic for the text-selection smart tools. */
class SmartToolState(
    private val scope: CoroutineScope,
    private val client: OkHttpClient
) {
    var title by mutableStateOf("")
        private set
    var content by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set

    fun fetch(type: Int, rawQuery: String) {
        val query = rawQuery.replace("\"", "").trim()
        if (query.isEmpty()) return

        isLoading = true
        content = "Fetching..."
        title = smartToolTitle(type, query)

        scope.launch(Dispatchers.IO) {
            val result = fetchSmartToolResult(client, type, query)
            withContext(Dispatchers.Main) {
                content = result
                isLoading = false
            }
        }
    }

    fun dismiss() {
        title = ""
    }
}

@Composable
fun rememberSmartToolState(): SmartToolState {
    val scope = rememberCoroutineScope()
    return remember { SmartToolState(scope, OkHttpClient()) }
}

@Composable
fun SmartToolDialog(state: SmartToolState) {
    SmartToolDialog(
        title = state.title,
        content = state.content,
        isLoading = state.isLoading,
        onDismiss = { state.dismiss() }
    )
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

// --- Shared reader chrome ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSearchTopBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onFindPrev: () -> Unit,
    onFindNext: () -> Unit,
    matchCounter: String? = null
) {
    TopAppBar(
        modifier = Modifier.displayCutoutPadding(),
        title = {
            TextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = { Text("Search...", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Text("✕", color = Color.White, fontSize = 20.sp)
            }
        },
        actions = {
            if (matchCounter != null) {
                Text(
                    text = matchCounter,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            IconButton(onClick = onFindPrev) {
                Text("▲", color = Color.White, fontSize = 20.sp)
            }
            IconButton(onClick = onFindNext) {
                Text("▼", color = Color.White, fontSize = 20.sp)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
    )
}

/** Small floating "scroll to top" button that overlays the reader content (bottom-left). */
@Composable
fun ReaderScrollTopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xCC1E1E1E),
        contentColor = Color(0xFF4dd0e1),
        modifier = modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("⤒", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ReaderBottomBar(
    currentChapterIndex: Int,
    totalChapters: Int,
    statusText: String,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSelectChapter: () -> Unit
) {
    Surface(color = Color(0xFF1E1E1E), contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPrevChapter,
                    enabled = currentChapterIndex > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF442694))
                ) { Text("◀ Prev") }
                TextButton(onClick = onSelectChapter) {
                    Text(
                        "Ch. ${currentChapterIndex + 1} / ${if (totalChapters == 0) "?" else totalChapters} ▾",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Button(
                    onClick = onNextChapter,
                    enabled = currentChapterIndex < totalChapters - 1,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF442694))
                ) { Text("Next ▶") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(statusText, color = Color.LightGray, fontSize = 12.sp, fontFamily = FontFamily.Serif)
        }
    }
}

/** Volume-button chapter paging shared by both readers. */
fun volumePagingKeyListener(
    isEnabled: () -> Boolean,
    onPageBack: () -> Unit,
    onPageForward: () -> Unit
): View.OnKeyListener = View.OnKeyListener { _, keyCode, event ->
    if (!isEnabled() || event.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false
    when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> {
            onPageForward()
            true
        }
        KeyEvent.KEYCODE_VOLUME_UP -> {
            onPageBack()
            true
        }
        else -> false
    }
}

// Reader Settings Dialog
@Composable
fun ReaderSettingsDialog(
    settings: ReaderSettingsState,
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
                    Text("Font Size: ${settings.fontSize}px", color = Color.White)
                    Slider(
                        value = settings.fontSize.toFloat(),
                        onValueChange = { settings.updateFontSize(it.toInt()) },
                        valueRange = 12f..36f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF4dd0e1), activeTrackColor = Color(0xFF4dd0e1))
                    )
                }
                // Line Spacing
                Column {
                    Text("Line Spacing: ${settings.lineSpacing}x", color = Color.White)
                    Slider(
                        value = settings.lineSpacing,
                        onValueChange = { settings.updateLineSpacing((Math.round(it * 10) / 10.0).toFloat()) },
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
                                onClick = { settings.updateFont(fontOption) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (settings.selectedFont == fontOption) Color(0xFF4dd0e1) else Color(0xFF444444),
                                    contentColor = if (settings.selectedFont == fontOption) Color.Black else Color.White
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
                                onClick = { settings.updateTheme(themeOption) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (settings.theme == themeOption) Color(0xFF4dd0e1) else Color(0xFF444444),
                                    contentColor = if (settings.theme == themeOption) Color.Black else Color.White
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
                        checked = settings.volumePagingEnabled,
                        onCheckedChange = { settings.updateVolumePaging(it) },
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
                        checked = settings.swipeToChangeChaptersEnabled,
                        onCheckedChange = { settings.updateSwipeToChangeChapters(it) },
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
