package com.PoorMenKindle.android.ui.screens

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun ReadScreen(
    bookId: Int,
    totalChapters: Int,
    initialChapter: Int,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- Settings State ---
    var fontSize by remember { mutableIntStateOf(18) }
    var lineSpacing by remember { mutableFloatStateOf(1.6f) }
    var theme by remember { mutableStateOf("Dark") }
    var selectedFont by remember { mutableStateOf("Palatino") } // <-- NEW: Font State

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showChapterDialog by remember { mutableStateOf(false) }

    // Core Reading State
    var currentChapterIndex by remember { mutableIntStateOf(initialChapter) }
    var chapterHtml by remember { mutableStateOf("Loading...") }
    var chapterTitle by remember { mutableStateOf("") }

    // Live Footer State
    var currentTime by remember { mutableStateOf("") }
    var batteryStatus by remember { mutableStateOf("🔋--%") }
    var wordCount by remember { mutableIntStateOf(0) }
    var timeRemaining by remember { mutableStateOf("< 1 min left") }

    // Smart Tools State
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
                    Text(
                        "Close",
                        color = Color(0xFF4dd0e1),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = Color(0xFF2b2b2b),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
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
                val isCharging =
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                val pct = if (scale > 0) (level * 100) / scale else 0
                batteryStatus = if (isCharging) "⚡$pct%" else "🔋$pct%"
            }

            ticks++
            delay(1000)
        }
    }

    LaunchedEffect(currentChapterIndex) {
        chapterHtml =
            "<div style='color: #888; text-align: center; padding: 50px;'>Loading chapter ${currentChapterIndex + 1}...</div>"
        try {
            val response = withContext(Dispatchers.IO) {
                NetworkManager.api.getChapter(bookId, currentChapterIndex)
            }
            if (response.isSuccessful) {
                val data = response.body()
                if (data != null) {
                    chapterTitle = data.chapter_title

                    // Jsoup handles stripping the raw base64 HTML so it doesn't inflate word count
                    val plainText = Jsoup.parse(data.text).text()
                    wordCount = plainText.split("\\s+".toRegex()).size
                    val minutes = ceil(wordCount / 225.0).toInt()
                    timeRemaining = if (minutes > 1) "$minutes mins left" else "< 1 min left"

                    // NEW: Added dynamic font-family and responsive CSS for embedded <img> tags
                    chapterHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
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
                            ${data.text.replace("\n\n", "</p><p>").replace("\n", "<br>")}
                        </body>
                        </html>
                    """.trimIndent()
                }
            } else {
                chapterHtml = "<div style='color: #ff6b6b;'>Error loading chapter: ${response.code()}</div>"
            }
        } catch (e: Exception) {
            chapterHtml = "<div style='color: #ff6b6b;'>Connection error: ${e.message}</div>"
        }
        if (currentChapterIndex < totalChapters - 1) {
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    NetworkManager.api.getChapter(bookId, currentChapterIndex + 1)
                } catch (_: Exception) {
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    NetworkManager.api.saveProgress(bookId, ProgressUpdateRequest(currentChapterIndex))
                } catch (_: Exception) {
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    "Reading Settings",
                    color = Color(0xFF4dd0e1),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Font Size
                    Column {
                        Text("Font Size: ${fontSize}px", color = Color.White)
                        Slider(
                            value = fontSize.toFloat(),
                            onValueChange = { fontSize = it.toInt() },
                            valueRange = 12f..36f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF4dd0e1),
                                activeTrackColor = Color(0xFF4dd0e1)
                            )
                        )
                    }
                    // Line Spacing
                    Column {
                        Text("Line Spacing: ${lineSpacing}x", color = Color.White)
                        Slider(
                            value = lineSpacing,
                            onValueChange = { lineSpacing = (Math.round(it * 10) / 10.0).toFloat() },
                            valueRange = 1.0f..2.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF4dd0e1),
                                activeTrackColor = Color(0xFF4dd0e1)
                            )
                        )
                    }
                    // NEW: Font Family Selector
                    Column {
                        Text("Font", color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            listOf("Palatino", "Serif", "Sans-Serif", "Monospace").forEach { fontOption ->
                                Button(
                                    onClick = { selectedFont = fontOption },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedFont == fontOption) Color(0xFF4dd0e1) else Color(
                                            0xFF444444
                                        ),
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
                                    onClick = { theme = themeOption },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (theme == themeOption) Color(0xFF4dd0e1) else Color(
                                            0xFF444444
                                        ),
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
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(
                        "Close",
                        color = Color(0xFF4dd0e1)
                    )
                }
            },
            containerColor = Color(0xFF2b2b2b)
        )
    }

    if (showChapterDialog) {
        AlertDialog(
            onDismissRequest = { showChapterDialog = false },
            title = {
                Text(
                    "Select Chapter",
                    color = Color(0xFF4dd0e1),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
            },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(totalChapters) { index ->
                        TextButton(
                            onClick = {
                                currentChapterIndex = index
                                showChapterDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Chapter ${index + 1}",
                                color = if (index == currentChapterIndex) Color(0xFF4dd0e1) else Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChapterDialog = false }) {
                    Text(
                        "Close",
                        color = Color.Gray
                    )
                }
            },
            containerColor = Color(0xFF2b2b2b)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        chapterTitle.ifEmpty { "Chapter ${currentChapterIndex + 1}" },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(
                            "◀ Back",
                            color = Color(0xFF4dd0e1),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = { IconButton(onClick = { showSettingsDialog = true }) { Text("⚙️", fontSize = 20.sp) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(color = Color(0xFF1E1E1E), contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        TextButton(onClick = {
                            showChapterDialog = true
                        }) {
                            Text(
                                text = "Ch. ${currentChapterIndex + 1} / ${if (totalChapters == 0) "?" else totalChapters} ▾",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Button(
                            onClick = { if (currentChapterIndex < totalChapters - 1) currentChapterIndex++ },
                            enabled = currentChapterIndex < totalChapters - 1,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF442694))
                        ) { Text("Next ▶") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "[$currentTime]  [$batteryStatus]  ($timeRemaining)  [Words: $wordCount]",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(Color(0xFF2b2b2b))) {
            AndroidView(
                factory = { ctx ->
                    object : SmartWebView(ctx) {
                        override fun startActionMode(
                            callback: android.view.ActionMode.Callback?,
                            type: Int
                        ): android.view.ActionMode? {
                            val wrapper = object : android.view.ActionMode.Callback2() {
                                override fun onCreateActionMode(
                                    mode: android.view.ActionMode?,
                                    menu: android.view.Menu?
                                ): Boolean {
                                    val result = callback?.onCreateActionMode(mode, menu) ?: false
                                    menu?.add(0, 1, 0, "📚 Dictionary")
                                    menu?.add(0, 2, 1, "🌍 Translate (HE)")
                                    menu?.add(0, 3, 2, "🏛 Wikipedia")
                                    return result || true
                                }

                                override fun onPrepareActionMode(
                                    mode: android.view.ActionMode?,
                                    menu: android.view.Menu?
                                ): Boolean {
                                    return callback?.onPrepareActionMode(mode, menu) ?: false
                                }

                                override fun onActionItemClicked(
                                    mode: android.view.ActionMode?,
                                    item: android.view.MenuItem?
                                ): Boolean {
                                    if (item?.itemId in 1..3) {
                                        val clickedItemId = item!!.itemId
                                        evaluateJavascript("(function(){return window.getSelection().toString();})()") { selection ->
                                            val cleanText = selection?.trim('"')?.trim() ?: ""
                                            if (cleanText.isNotBlank()) fetchSmartTool(clickedItemId, cleanText)
                                            mode?.finish()
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
                        settings.javaScriptEnabled = true
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)

                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val bgColor = when (theme) {
                                    "Light" -> "#ffffff"
                                    "Sepia" -> "#f4ecd8"
                                    else -> "#2b2b2b"
                                }
                                val textColor = when (theme) {
                                    "Light" -> "#000000"
                                    "Sepia" -> "#5b4636"
                                    else -> "#d4d4d4"
                                }
                                // NEW: Added fontFamily injection
                                val js = """
                                        document.body.style.fontSize = '${fontSize}px';
                                        document.body.style.lineHeight = '${lineSpacing}';
                                        document.body.style.backgroundColor = '$bgColor';
                                        document.body.style.color = '$textColor';
                                        document.body.style.fontFamily = '$selectedFont';
                                    """.trimIndent()
                                view?.evaluateJavascript(js, null)
                            }
                        }

                        val gestureDetector = android.view.GestureDetector(
                            ctx,
                            object : android.view.GestureDetector.SimpleOnGestureListener() {
                                private val SWIPE_THRESHOLD = 120
                                private val SWIPE_VELOCITY_THRESHOLD = 150
                                override fun onFling(
                                    e1: android.view.MotionEvent?,
                                    e2: android.view.MotionEvent,
                                    velocityX: Float,
                                    velocityY: Float
                                ): Boolean {
                                    if (e1 == null) return false
                                    val diffX = e2.x - e1.x
                                    val diffY = e2.y - e1.y
                                    if (Math.abs(diffX) > Math.abs(diffY)) {
                                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                                            if (diffX > 0) {
                                                if (currentChapterIndex > 0) currentChapterIndex--
                                            } else {
                                                if (currentChapterIndex < totalChapters - 1) currentChapterIndex++
                                            }
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
                        webView.loadDataWithBaseURL(null, chapterHtml, "text/html", "UTF-8", null)
                        webView.tag = chapterHtml
                    }

                    val bgColor = when (theme) {
                        "Light" -> "#ffffff"
                        "Sepia" -> "#f4ecd8"
                        else -> "#2b2b2b"
                    }
                    val textColor = when (theme) {
                        "Light" -> "#000000"
                        "Sepia" -> "#5b4636"
                        else -> "#d4d4d4"
                    }

                    // NEW: Added fontFamily to dynamic injection
                    val js = """
                        document.body.style.fontSize = '${fontSize}px';
                        document.body.style.lineHeight = '${lineSpacing}';
                        document.body.style.backgroundColor = '$bgColor';
                        document.body.style.color = '$textColor';
                        document.body.style.fontFamily = '$selectedFont';
                    """.trimIndent()

                    webView.evaluateJavascript(js, null)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}