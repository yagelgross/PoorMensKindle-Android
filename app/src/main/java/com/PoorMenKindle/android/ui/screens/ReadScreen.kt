package com.PoorMenKindle.android.ui.screens

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.PoorMenKindle.android.network.ChapterData
import com.PoorMenKindle.android.network.NetworkManager
import com.PoorMenKindle.android.network.ProgressUpdateRequest
import kotlinx.coroutines.DelicateCoroutinesApi
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
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.PoorMenKindle.android.network.HighlightItem
import com.PoorMenKindle.android.network.HighlightRequest
import kotlin.math.abs

fun getAllFontsCss(): String {
    val basePath = "https://appassets.local/fonts/"
    return """
        @font-face { font-family: 'Merriweather'; src: url('${basePath}Merriweather_120pt-Regular.ttf'); font-weight: normal; font-style: normal; }
        @font-face { font-family: 'Merriweather'; src: url('${basePath}Merriweather_120pt-Bold.ttf'); font-weight: bold; font-style: normal; }
        @font-face { font-family: 'Merriweather'; src: url('${basePath}Merriweather_120pt-Italic.ttf'); font-weight: normal; font-style: italic; }
        @font-face { font-family: 'Merriweather'; src: url('${basePath}Merriweather_120pt-BoldItalic.ttf'); font-weight: bold; font-style: italic; }

        @font-face { font-family: 'Libre Baskerville'; src: url('${basePath}LibreBaskerville-Regular.ttf'); font-weight: normal; font-style: normal; }
        @font-face { font-family: 'Libre Baskerville'; src: url('${basePath}LibreBaskerville-Bold.ttf'); font-weight: bold; font-style: normal; }
        @font-face { font-family: 'Libre Baskerville'; src: url('${basePath}LibreBaskerville-Italic.ttf'); font-weight: normal; font-style: italic; }
        @font-face { font-family: 'Libre Baskerville'; src: url('${basePath}LibreBaskerville-BoldItalic.ttf'); font-weight: bold; font-style: italic; }

        @font-face { font-family: 'Alegreya'; src: url('${basePath}Alegreya-Regular.ttf'); font-weight: normal; font-style: normal; }
        @font-face { font-family: 'Alegreya'; src: url('${basePath}Alegreya-Black.ttf'); font-weight: bold; font-style: normal; }
        @font-face { font-family: 'Alegreya'; src: url('${basePath}Alegreya-Italic.ttf'); font-weight: normal; font-style: italic; }
        @font-face { font-family: 'Alegreya'; src: url('${basePath}Alegreya-BlackItalic.ttf'); font-weight: bold; font-style: italic; }

        @font-face { font-family: 'Arimo'; src: url('${basePath}Arimo-Regular.ttf'); font-weight: normal; font-style: normal; }
        @font-face { font-family: 'Arimo'; src: url('${basePath}Arimo-Bold.ttf'); font-weight: bold; font-style: normal; }
        @font-face { font-family: 'Arimo'; src: url('${basePath}Arimo-Italic.ttf'); font-weight: normal; font-style: italic; }
        @font-face { font-family: 'Arimo'; src: url('${basePath}Arimo-BoldItalic.ttf'); font-weight: bold; font-style: italic; }

        @font-face { font-family: 'Georgia'; src: url('${basePath}Georgia.ttf'); font-weight: normal; font-style: normal; }
        @font-face { font-family: 'Georgia'; src: url('${basePath}Georgia%20Bold.ttf'); font-weight: bold; font-style: normal; }
        @font-face { font-family: 'Georgia'; src: url('${basePath}Georgia%20Italic.ttf'); font-weight: normal; font-style: italic; }
        @font-face { font-family: 'Georgia'; src: url('${basePath}Georgia%20Bold%20Italic.ttf'); font-weight: bold; font-style: italic; }

        @font-face { font-family: 'Arial'; src: url('${basePath}Arial.ttf'); font-weight: normal; font-style: normal; }
        @font-face { font-family: 'Arial'; src: url('${basePath}Arial%20Bold.ttf'); font-weight: bold; font-style: normal; }
        @font-face { font-family: 'Arial'; src: url('${basePath}Arial%20Italic.ttf'); font-weight: normal; font-style: italic; }
        @font-face { font-family: 'Arial'; src: url('${basePath}Arial%20Bold%20Italic.ttf'); font-weight: bold; font-style: italic; }

        @font-face { font-family: 'Times New Roman'; src: url('${basePath}Times%20New%20Roman.ttf'); font-weight: normal; font-style: normal; }
        @font-face { font-family: 'Times New Roman'; src: url('${basePath}Times%20New%20Roman%20Bold.ttf'); font-weight: bold; font-style: normal; }
        @font-face { font-family: 'Times New Roman'; src: url('${basePath}Times%20New%20Roman%20Italic.ttf'); font-weight: normal; font-style: italic; }
        @font-face { font-family: 'Times New Roman'; src: url('${basePath}Times%20New%20Roman%20Bold%20Italic.ttf'); font-weight: bold; font-style: italic; }
        
        @font-face { font-family: 'Frank Ruhl Libre'; src: url('${'$'}{basePath}FrankRuhlLibre-Regular.ttf'); font-weight: normal; font-style: normal; }
        @font-face { font-family: 'Frank Ruhl Libre'; src: url('${'$'}{basePath}FrankRuhlLibre-Bold.ttf'); font-weight: bold; font-style: normal; }

        @font-face { font-family: 'Heebo'; src: url('${'$'}{basePath}Heebo-Regular.ttf'); font-weight: normal; font-style: normal; }
        @font-face { font-family: 'Heebo'; src: url('${'$'}{basePath}Heebo-Bold.ttf'); font-weight: bold; font-style: normal; }

        @font-face { font-family: 'Rubik'; src: url('${'$'}{basePath}Rubik-Regular.ttf'); font-weight: normal; font-style: normal; }
        @font-face { font-family: 'Rubik'; src: url('${'$'}{basePath}Rubik-Bold.ttf'); font-weight: bold; font-style: normal; }
    """.trimIndent()
}

@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun ReadScreen(
    bookId: Int,
    totalChapters: Int,
    initialChapter: Int,
    initialScrollProgress: Float = 0f,
    onNavigateBack: () -> Unit,
    onNavigateToHighlights: (Int) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val view = androidx.compose.ui.platform.LocalView.current
    var currentScrollPercent by remember { mutableFloatStateOf(initialScrollProgress) }

    DisposableEffect(Unit) {
        activity?.window?.let { window ->
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.window?.let { window ->
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    val coroutineScope = rememberCoroutineScope()

    val sharedPrefs = context.getSharedPreferences("ReadingSettings", android.content.Context.MODE_PRIVATE)

    var fontSize by remember { mutableIntStateOf(sharedPrefs.getInt("font_size", 18)) }
    var lineSpacing by remember { mutableFloatStateOf(sharedPrefs.getFloat("line_spacing", 1.6f)) }
    var theme by remember { mutableStateOf(sharedPrefs.getString("theme", "Dark") ?: "Dark") }
    var selectedFont by remember { mutableStateOf(sharedPrefs.getString("font", "Merriweather") ?: "Merriweather") }
    var volumePagingEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("volume_paging", false)) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showChapterDialog by remember { mutableStateOf(false) }

    var currentChapterIndex by remember { mutableIntStateOf(initialChapter) }
    var chapterHtml by remember { mutableStateOf("Loading...") }
    var chapterTitle by remember { mutableStateOf("") }

    var bookHighlights by remember { mutableStateOf<List<HighlightItem>>(emptyList()) }
    var highlightsFetched by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var pendingHighlightText by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Load Local First + Sync from Server
    LaunchedEffect(bookId) {
        coroutineScope.launch(Dispatchers.IO) {
            val db = com.PoorMenKindle.android.data.local.AppDatabase.getDatabase(context)
            val dao = db.bookDao()

            val localHls = dao.getHighlightsForBook(bookId)
            val localConverted = localHls.map {
                HighlightItem(it.localId, it.chapterIndex, it.highlightedText, null, it.color, "")
            }
            bookHighlights = localConverted
            highlightsFetched = true

            try {
                val response = NetworkManager.api.getHighlights(bookId)
                if (response.isSuccessful) {
                    val serverHls = response.body() ?: emptyList()

                    // Sync: Save new server highlights to local DB
                    serverHls.forEach { serverHl ->
                        val existsLocally = localHls.any { it.highlightedText == serverHl.highlighted_text && it.chapterIndex == serverHl.chapter_index }
                        if (!existsLocally) {
                            val newLocal = com.PoorMenKindle.android.data.local.LocalHighlight(
                                bookId = bookId,
                                chapterIndex = serverHl.chapter_index,
                                highlightedText = serverHl.highlighted_text,
                                color = serverHl.color ?: "rgba(255, 235, 59, 0.4)"
                            )
                            dao.insertHighlight(newLocal)
                        }
                    }

                    val updatedLocalHls = dao.getHighlightsForBook(bookId)
                    bookHighlights = updatedLocalHls.map {
                        HighlightItem(it.localId, it.chapterIndex, it.highlightedText, null, it.color, "")
                    }
                }
            } catch (e: Exception) { }
        }
    }

    val chapterCache = remember { ConcurrentHashMap<Int, ChapterData>() }

    var currentTime by remember { mutableStateOf("") }
    var batteryStatus by remember { mutableStateOf("🔋--%") }
    var wordCount by remember { mutableIntStateOf(0) }
    var timeRemaining by remember { mutableStateOf("< 1 min left") }

    var toolDialogTitle by remember { mutableStateOf("") }
    var toolDialogContent by remember { mutableStateOf("") }
    var isLoadingTool by remember { mutableStateOf(false) }

    val publicApiClient = remember { OkHttpClient() }

    fun fetchSmartTool(type: Int, rawQuery: String) {
        val query = rawQuery.replace("\"", "").trim()
        if (query.isEmpty()) return

        isLoadingTool = true
        toolDialogContent = "Fetching..."

        when (type) {
            1 -> toolDialogTitle = "📚 Dictionary: $query"
            2 -> toolDialogTitle = "🌍 Hebrew Translation"
            3 -> toolDialogTitle = "🏛 Wikipedia: $query"
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val result = if (type == 2) {
                    val response = NetworkManager.api.translateText(query)
                    if (response.isSuccessful) {
                        response.body()?.get("translated_text") ?: "Translation missing."
                    } else {
                        "Server error: ${response.code()}"
                    }
                } else {
                    val url = if (type == 1) "https://api.dictionaryapi.dev/api/v2/entries/en/$query"
                    else "https://en.wikipedia.org/api/rest_v1/page/summary/$query"

                    val request = Request.Builder().url(url).build()
                    val response = publicApiClient.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful && responseBody.isNotEmpty()) {
                        if (type == 1) {
                            val jsonArray = JSONArray(responseBody)
                            val meanings = jsonArray.getJSONObject(0).getJSONArray("meanings")
                            meanings.getJSONObject(0).getJSONArray("definitions").getJSONObject(0)
                                .getString("definition")
                        } else {
                            val jsonObj = JSONObject(responseBody)
                            jsonObj.getString("extract")
                        }
                    } else {
                        "No results found."
                    }
                }

                withContext(Dispatchers.Main) {
                    toolDialogContent = result
                    isLoadingTool = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toolDialogContent = "Error fetching data: ${e.localizedMessage}"
                    isLoadingTool = false
                }
            }
        }
    }

    if (toolDialogTitle.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { toolDialogTitle = "" },
            title = {
                Text(
                    text = toolDialogTitle,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4dd0e1),
                    fontFamily = FontFamily.Serif
                )
            },
            text = {
                if (isLoadingTool) {
                    CircularProgressIndicator(color = Color(0xFF4dd0e1))
                } else {
                    Text(text = toolDialogContent, fontSize = 16.sp, fontFamily = FontFamily.Serif, color = Color.White)
                }
            },
            confirmButton = {
                TextButton(onClick = { toolDialogTitle = "" }) {
                    Text("Close", color = Color(0xFF4dd0e1), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF2b2b2b),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = {
                Text("Select Highlight Color", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val colorOptions = listOf(
                        Color(0xFFFFF59D) to "rgba(255, 235, 59, 0.4)",
                        Color(0xFFA5D6A7) to "rgba(76, 175, 80, 0.4)",
                        Color(0xFF90CAF9) to "rgba(33, 150, 243, 0.4)",
                        Color(0xFFEF9A9A) to "rgba(244, 67, 54, 0.4)",
                        Color(0xFFCE93D8) to "rgba(156, 39, 176, 0.4)"
                    )

                    colorOptions.forEach { (composeColor, cssColor) ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(composeColor)
                                .clickable {
                                    showColorPicker = false
                                    webViewRef?.evaluateJavascript("""
                                        (function(){
                                            var sel = window.getSelection();
                                            sel.removeAllRanges();
                                            if(window.savedHighlightRange) {
                                                sel.addRange(window.savedHighlightRange);
                                                document.execCommand('BackColor', false, '$cssColor');
                                                window.savedHighlightRange = null;
                                            }
                                        })();
                                    """.trimIndent(), null)

                                    // Save Local + Remote
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val db = com.PoorMenKindle.android.data.local.AppDatabase.getDatabase(context)
                                        val newLocalHl = com.PoorMenKindle.android.data.local.LocalHighlight(
                                            bookId = bookId,
                                            chapterIndex = currentChapterIndex,
                                            highlightedText = pendingHighlightText,
                                            color = cssColor
                                        )
                                        val generatedId = db.bookDao().insertHighlight(newLocalHl).toInt()

                                        val newHighlight = HighlightItem(generatedId, currentChapterIndex, pendingHighlightText, null, cssColor, "")
                                        withContext(Dispatchers.Main) {
                                            bookHighlights = bookHighlights + newHighlight
                                        }

                                        try {
                                            NetworkManager.api.addHighlight(
                                                bookId,
                                                HighlightRequest(currentChapterIndex, pendingHighlightText, null, cssColor)
                                            )
                                        } catch (e: Exception) {}
                                    }
                                }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorPicker = false }) { Text("Cancel", color = Color.Gray) }
            },
            containerColor = Color(0xFF2b2b2b)
        )
    }

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

    // Check Offline First
    LaunchedEffect(currentChapterIndex, highlightsFetched) {
        if (!highlightsFetched) return@LaunchedEffect

        if (currentChapterIndex != initialChapter) {
            currentScrollPercent = 0f
        }
        chapterHtml = "<div style='color: #888; text-align: center; padding: 50px;'>Loading chapter ${currentChapterIndex + 1}...</div>"

        try {
            var data = chapterCache[currentChapterIndex]

            if (data == null) {
                val db = com.PoorMenKindle.android.data.local.AppDatabase.getDatabase(context)
                val localChapter = withContext(Dispatchers.IO) {
                    db.bookDao().getChapter(bookId, currentChapterIndex)
                }

                if (localChapter != null) {
                    data = ChapterData(bookId, localChapter.chapterTitle, localChapter.chapterIndex, localChapter.contentHtml)
                    chapterCache[currentChapterIndex] = data!!
                } else {
                    val response = withContext(Dispatchers.IO) {
                        NetworkManager.api.getChapter(bookId, currentChapterIndex)
                    }
                    if (response.isSuccessful && response.body() != null) {
                        data = response.body()
                        chapterCache[currentChapterIndex] = data!!
                    } else {
                        chapterHtml = "<div style='color: #ff6b6b;'>Error loading chapter: ${response.code()}</div>"
                        return@LaunchedEffect
                    }
                }
            }

            chapterTitle = data.chapter_title

            var htmlContent = data.text
            val chapterHighlights = bookHighlights.filter { it.chapter_index == currentChapterIndex }

            chapterHighlights.forEach { highlight ->
                val bgColor = highlight.color ?: "rgba(255, 235, 59, 0.4)"
                val replacement = "<mark style='background-color: $bgColor; color: inherit; border-radius: 3px; padding: 0 2px;'>${highlight.highlighted_text}</mark>"
                htmlContent = safeHtmlReplace(htmlContent, highlight.highlighted_text, replacement)
            }

            val plainText = Jsoup.parse(htmlContent).text()
            wordCount = plainText.split("\\s+".toRegex()).size
            val minutes = ceil(wordCount / 225.0).toInt()
            timeRemaining = if (minutes > 1) "$minutes mins left" else "< 1 min left"

            val allFontsCss = getAllFontsCss()

            chapterHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style id="custom-font-style">
                    $allFontsCss
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
                ${htmlContent.replace("\n\n", "</p><p>").replace("\n", "<br>")}
            </body>
            </html>
        """.trimIndent()

            coroutineScope.launch(Dispatchers.IO) {
                val windowStart = max(0, currentChapterIndex - 3)
                val windowEnd = min(totalChapters - 1, currentChapterIndex + 10)
                val keysToRemove = chapterCache.keys.filter { it !in windowStart..windowEnd }
                keysToRemove.forEach { chapterCache.remove(it) }

                for (i in windowStart..windowEnd) {
                    if (i != currentChapterIndex && !chapterCache.containsKey(i)) {
                        try {
                            val response = NetworkManager.api.getChapter(bookId, i)
                            if (response.isSuccessful) { response.body()?.let { chapterCache[i] = it } }
                        } catch (e: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            chapterHtml = "<div style='color: #ff6b6b;'>Connection error: ${e.message}</div>"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            chapterCache.clear()
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try { NetworkManager.api.saveProgress(bookId, ProgressUpdateRequest(currentChapterIndex, currentScrollPercent)) } catch (_: Exception) {}
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text("Reading Settings", color = Color(0xFF4dd0e1), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Font Size: ${fontSize}px", color = Color.White)
                        Slider(
                            value = fontSize.toFloat(),
                            onValueChange = {
                                fontSize = it.toInt()
                                sharedPrefs.edit().putInt("font_size", fontSize).apply()
                            },
                            valueRange = 12f..36f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF4dd0e1), activeTrackColor = Color(0xFF4dd0e1))
                        )
                    }
                    Column {
                        Text("Line Spacing: ${lineSpacing}x", color = Color.White)
                        Slider(
                            value = lineSpacing,
                            onValueChange = {
                                lineSpacing = (Math.round(it * 10) / 10.0).toFloat()
                                sharedPrefs.edit().putFloat("line_spacing", lineSpacing).apply()
                            },
                            valueRange = 1.0f..2.5f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF4dd0e1), activeTrackColor = Color(0xFF4dd0e1))
                        )
                    }
                    Column {
                        Text("Font", color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                "Serif", "Sans-Serif", "Monospace",
                                "Alegreya", "Arial", "Arimo", "Georgia",
                                "Libre Baskerville", "Merriweather", "Times New Roman",
                                "Frank Ruhl Libre", "Heebo", "Rubik"
                            ).forEach { fontOption ->
                                Button(
                                    onClick = {
                                        selectedFont = fontOption
                                        sharedPrefs.edit().putString("font", selectedFont).apply()
                                    },
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
                    Column {
                        Text("Theme", color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("Dark", "Light", "Sepia").forEach { themeOption ->
                                Button(
                                    onClick = {
                                        theme = themeOption
                                        sharedPrefs.edit().putString("theme", theme).apply()
                                    },
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Volume Button Paging", color = Color.White)
                        Switch(
                            checked = volumePagingEnabled,
                            onCheckedChange = {
                                volumePagingEnabled = it
                                sharedPrefs.edit().putBoolean("volume_paging", volumePagingEnabled).apply()
                            },
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
                TextButton(onClick = { showSettingsDialog = false }) { Text("Close", color = Color(0xFF4dd0e1)) }
            },
            containerColor = Color(0xFF2b2b2b)
        )
    }

    if (showChapterDialog) {
        AlertDialog(
            onDismissRequest = { showChapterDialog = false },
            title = {
                Text("Select Chapter", color = Color(0xFF4dd0e1), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(totalChapters) { index ->
                        TextButton(
                            onClick = { currentChapterIndex = index; showChapterDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Chapter ${index + 1}", color = if (index == currentChapterIndex) Color(0xFF4dd0e1) else Color.White, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChapterDialog = false }) { Text("Close", color = Color.Gray) }
            },
            containerColor = Color(0xFF2b2b2b)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.displayCutoutPadding(),
                title = { Text(chapterTitle.ifEmpty { "Chapter ${currentChapterIndex + 1}" }, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("◀ Back", color = Color(0xFF4dd0e1), fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToHighlights(bookId) }) { Text("📝", fontSize = 20.sp) }
                    IconButton(onClick = { showSettingsDialog = true }) { Text("⚙️", fontSize = 20.sp) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E), titleContentColor = Color.White)
            )
        },
        bottomBar = {
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
                            onClick = { if (currentChapterIndex > 0) currentChapterIndex-- },
                            enabled = currentChapterIndex > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF442694))
                        ) { Text("◀ Prev") }
                        TextButton(onClick = { showChapterDialog = true }) {
                            Text("Ch. ${currentChapterIndex + 1} / ${if (totalChapters == 0) "?" else totalChapters} ▾", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Button(
                            onClick = { if (currentChapterIndex < totalChapters - 1) currentChapterIndex++ },
                            enabled = currentChapterIndex < totalChapters - 1,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF442694))
                        ) { Text("Next ▶") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("[$currentTime]  [$batteryStatus]  ($timeRemaining)  [Words: $wordCount]", color = Color.LightGray, fontSize = 12.sp, fontFamily = FontFamily.Serif)
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(Color(0xFF2b2b2b))) {
            AndroidView(
                factory = { ctx ->
                    object : SmartWebView(ctx) {
                        override fun startActionMode(callback: android.view.ActionMode.Callback?, type: Int): android.view.ActionMode? {
                            val wrapper = object : android.view.ActionMode.Callback2() {
                                override fun onCreateActionMode(
                                    mode: android.view.ActionMode?,
                                    menu: android.view.Menu?
                                ): Boolean {
                                    val result = callback?.onCreateActionMode(mode, menu) ?: false
                                    menu?.add(0, 1, 2, "📚 Dictionary")
                                    menu?.add(0, 2, 1, "🌍 Translate (HE)")
                                    menu?.add(0, 3, 3, "🏛 Wikipedia")
                                    menu?.add(0, 4, 0, "🖍 Highlight")
                                    return result || true
                                }

                                override fun onPrepareActionMode(
                                    mode: android.view.ActionMode?,
                                    menu: android.view.Menu?
                                ): Boolean = callback?.onPrepareActionMode(mode, menu) ?: false

                                override fun onActionItemClicked(
                                    mode: android.view.ActionMode?,
                                    item: android.view.MenuItem?
                                ): Boolean {
                                    if (item?.itemId in 1..4) {
                                        val clickedItemId = item!!.itemId

                                        if (clickedItemId == 4) {
                                            evaluateJavascript(
                                                """
                                                (function(){
                                                    var sel = window.getSelection();
                                                    if(sel.rangeCount > 0) {
                                                        window.savedHighlightRange = sel.getRangeAt(0);
                                                        return sel.toString();
                                                    }
                                                    return '';
                                                })();
                                            """.trimIndent()
                                            ) { selection ->
                                                val cleanText = selection?.trim('"')?.trim() ?: ""
                                                if (cleanText.isNotBlank()) {
                                                    pendingHighlightText = cleanText
                                                    showColorPicker = true
                                                }
                                                mode?.finish()
                                            }
                                        } else {
                                            evaluateJavascript("(function(){return window.getSelection().toString();})()") { selection ->
                                                val cleanText = selection?.trim('"')?.trim() ?: ""
                                                if (cleanText.isNotBlank()) fetchSmartTool(clickedItemId, cleanText)
                                                mode?.finish()
                                            }
                                        }
                                        return true
                                    }
                                    return callback?.onActionItemClicked(mode, item) ?: false
                                }

                                override fun onDestroyActionMode(mode: android.view.ActionMode?) {
                                    callback?.onDestroyActionMode(mode)
                                }

                                override fun onGetContentRect(
                                    mode: android.view.ActionMode?,
                                    view: android.view.View?,
                                    outRect: android.graphics.Rect?
                                ) {
                                    if (callback is android.view.ActionMode.Callback2) callback.onGetContentRect(
                                        mode,
                                        view,
                                        outRect
                                    )
                                    else super.onGetContentRect(mode, view, outRect)
                                }
                            }
                            return super.startActionMode(wrapper, type)
                        }
                    }.apply {
                        webViewRef = this
                        settings.javaScriptEnabled = true
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        settings.allowFileAccess = true
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                val url = request?.url?.toString() ?: return null

                                if (url.startsWith("https://appassets.local/fonts/")) {
                                    val fontFileName = url.substringAfterLast("/")
                                    val cleanFileName = java.net.URLDecoder.decode(fontFileName, "UTF-8")
                                    try {
                                        val inputStream = ctx.assets.open("fonts/$cleanFileName")
                                        val response =
                                            android.webkit.WebResourceResponse("font/ttf", "UTF-8", inputStream)
                                        response.responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
                                        return response
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val bgColor = when (theme) {
                                    "Light" -> "#ffffff"; "Sepia" -> "#f4ecd8"; else -> "#2b2b2b"
                                }
                                val textColor = when (theme) {
                                    "Light" -> "#000000"; "Sepia" -> "#5b4636"; else -> "#d4d4d4"
                                }

                                val js = """
    document.body.style.fontSize = '${fontSize}px';
    document.body.style.lineHeight = '${lineSpacing}';
    document.body.style.backgroundColor = '$bgColor';
    document.body.style.color = '$textColor';
    document.body.style.fontFamily = "'${selectedFont}', serif";
""".trimIndent()
                                view?.evaluateJavascript(js, null)
                                if (currentScrollPercent > 0f) {
                                    val scrollJs = """
                setTimeout(function() {
                    var scrollHeight = document.body.scrollHeight;
                    var clientHeight = document.documentElement.clientHeight;
                    window.scrollTo(0, $currentScrollPercent * (scrollHeight - clientHeight));
                }, 100); 
            """.trimIndent()
                                    view?.evaluateJavascript(scrollJs, null)
                                }
                            }
                        }

                        setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                            val contentHeight = this.contentHeight.toFloat()
                            val webViewHeight = this.height.toFloat() / context.resources.displayMetrics.density

                            if (contentHeight > webViewHeight) {
                                val maxScroll = contentHeight - webViewHeight
                                val currentScroll = scrollY.toFloat() / context.resources.displayMetrics.density
                                currentScrollPercent = (currentScroll / maxScroll).coerceIn(0f, 1f)
                            }
                        }

                        val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                            private val SWIPE_THRESHOLD = 120
                            private val SWIPE_VELOCITY_THRESHOLD = 150
                            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                                if (e1 == null) return false
                                val diffX = e2.x - e1.x
                                val diffY = e2.y - e1.y
                                if (abs(diffX) > abs(diffY)) {
                                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                                        if (diffX > 0) { if (currentChapterIndex > 0) currentChapterIndex-- }
                                        else { if (currentChapterIndex < totalChapters - 1) currentChapterIndex++ }
                                        return true
                                    }
                                }
                                return false
                            }
                        })

                        setOnTouchListener { view, event ->
                            val handled = gestureDetector.onTouchEvent(event)
                            if (handled) view.performClick()
                            false
                        }
                    }
                },
                update = { webView ->
                    if (webView.tag != chapterHtml) {
                        webView.loadDataWithBaseURL("file:///android_asset/", chapterHtml, "text/html", "UTF-8", null)
                        webView.tag = chapterHtml
                    }

                    val bgColor = when (theme) { "Light" -> "#ffffff"; "Sepia" -> "#f4ecd8"; else -> "#2b2b2b" }
                    val textColor = when (theme) { "Light" -> "#000000"; "Sepia" -> "#5b4636"; else -> "#d4d4d4" }

                    val js = """
    document.body.style.fontSize = '${fontSize}px';
    document.body.style.lineHeight = '${lineSpacing}';
    document.body.style.backgroundColor = '$bgColor';
    document.body.style.color = '$textColor';
    document.body.style.fontFamily = "'${selectedFont}', serif";
""".trimIndent()
                    webView.evaluateJavascript(js, null)

                    webView.setOnKeyListener { _, keyCode, event ->
                        if (volumePagingEnabled && event.action == android.view.KeyEvent.ACTION_DOWN) {
                            when (keyCode) {
                                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                                    if (currentChapterIndex < totalChapters - 1) currentChapterIndex++
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                                    if (currentChapterIndex > 0) currentChapterIndex--
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

fun safeHtmlReplace(html: String, target: String, replacement: String): String {
    val parts = html.split(Regex("(?=<)|(?<=>)"))
    return parts.joinToString("") { part ->
        if (part.startsWith("<")) {
            part
        } else {
            part.replace(target, replacement)
        }
    }
}