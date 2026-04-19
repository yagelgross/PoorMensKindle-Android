package com.PoorMenKindle.android.ui.screens

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.ActionMode
import androidx.core.net.toUri
import android.view.Menu
import android.view.MenuItem
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.epub.EpubReader
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalReadScreen(
    fileUriString: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- Settings State ---
    var fontSize by remember { mutableIntStateOf(18) }
    var lineSpacing by remember { mutableFloatStateOf(1.6f) }
    var theme by remember { mutableStateOf("Dark") }
    var selectedFont by remember { mutableStateOf("Palatino") } // <-- NEW: Font State restored

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showChapterDialog by remember { mutableStateOf(false) }

    val fileUri = fileUriString.toUri()

    // EPUB State
    var epubBook by remember { mutableStateOf<Book?>(null) }
    var currentChapterIndex by remember { mutableIntStateOf(0) }
    var totalChapters by remember { mutableIntStateOf(0) }

    // UI State
    var chapterHtml by remember { mutableStateOf("Loading book...") }
    var chapterTitle by remember { mutableStateOf("Opening...") }
    var errorMessage by remember { mutableStateOf("") }

    // Live Footer State
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
        toolDialogTitle = when (type) {
            1 -> "📚 Dictionary: $query"
            2 -> "🌍 Hebrew Translation"
            else -> "🏛 Wikipedia: $query"
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val url = when (type) {
                    1 -> "https://api.dictionaryapi.dev/api/v2/entries/en/$query"
                    2 -> "https://api.mymemory.translated.net/get?q=$query&langpair=en|he"
                    else -> "https://en.wikipedia.org/api/rest_v1/page/summary/$query"
                }
                val response = publicApiClient.newCall(Request.Builder().url(url).build()).execute()
                val responseBody = response.body?.string() ?: ""

                val result = if (response.isSuccessful && responseBody.isNotEmpty()) {
                    when (type) {
                        1 -> JSONArray(responseBody).getJSONObject(0).getJSONArray("meanings").getJSONObject(0)
                            .getJSONArray("definitions").getJSONObject(0).getString("definition")

                        2 -> JSONObject(responseBody).getJSONObject("responseData").getString("translatedText")
                        else -> JSONObject(responseBody).getString("extract")
                    }
                } else "No results found."

                withContext(Dispatchers.Main) { toolDialogContent = result; isLoadingTool = false }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toolDialogContent = "Error: ${e.localizedMessage}"; isLoadingTool = false
                }
            }
        }
    }

    // 1. Load the EPUB File initially
    LaunchedEffect(fileUri) {
        try {
            withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    errorMessage = "Could not open file stream."
                    return@withContext
                }
                // If it crashes here, it will be caught and the exact reason displayed
                val book = EpubReader().readEpub(inputStream)
                epubBook = book
                totalChapters = book.spine.size()
                chapterTitle = book.title ?: "Unknown Title"
            }
        } catch (e: Throwable) { // Catch Throwable to trap NoClassDefFoundError
            errorMessage = "EPUB Parse Crash: ${e::class.java.simpleName} - ${e.message}"
        }
    }

    // 2. Extract and render the current chapter HTML
    LaunchedEffect(epubBook, currentChapterIndex) {
        val book = epubBook ?: return@LaunchedEffect
        try {
            val spineRef = book.spine.spineReferences[currentChapterIndex]
            val resource = spineRef.resource
            val rawHtml = String(resource.data)
            val doc = Jsoup.parse(rawHtml)
            val chapterHref = resource.href

            // --- Robust Image Extraction ---
            val images = doc.select("img, image") // Catch both <img> and SVG <image>
            for (img in images) {
                var src = img.attr("src").ifEmpty { img.attr("xlink:href") }
                src = java.net.URLDecoder.decode(src, "UTF-8")

                var imageResource = book.resources.getByHref(src)

                // If direct match fails, calculate the relative path manually
                if (imageResource == null) {
                    val chapterDir = chapterHref.substringBeforeLast("/", "")

                    var resolvedHref = src
                    if (src.startsWith("../")) {
                        // Go up one directory
                        val parentDir = chapterDir.substringBeforeLast("/", "")
                        val cleanSrc = src.replaceFirst("../", "")
                        resolvedHref = if (parentDir.isEmpty()) cleanSrc else "$parentDir/$cleanSrc"
                    } else if (chapterDir.isNotEmpty() && !src.startsWith("/")) {
                        // Same directory
                        resolvedHref = "$chapterDir/$src"
                    }

                    imageResource = book.resources.getByHref(resolvedHref)
                }

                // Inject Base64
                if (imageResource != null) {
                    val imgData = imageResource.data
                    val base64Img = android.util.Base64.encodeToString(imgData, android.util.Base64.NO_WRAP)
                    val mimeType = imageResource.mediaType?.name ?: "image/jpeg"
                    img.attr("src", "data:$mimeType;base64,$base64Img")
                }
            }

            val plainText = doc.text()
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
                        img { max-width: 100%; height: auto; display: block; margin: 15px auto; border-radius: 8px; }
                        p { margin-bottom: 1em; }
                    </style>
                </head>
                <body dir="auto">
                    ${doc.body().html()}
                </body>
                </html>
            """.trimIndent()
        } catch (e: Exception) {
            chapterHtml = "<div style='color: #ff6b6b;'>Error loading chapter: ${e.message}</div>"
        }
    }

    LaunchedEffect(Unit) {
        var ticks = 0
        while (isActive) {
            currentTime = LocalTime.now(ZoneId.of("Asia/Jerusalem")).format(DateTimeFormatter.ofPattern("HH:mm"))
            if (ticks % 60 == 0) {
                val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val isCharging = intent?.getIntExtra(
                    BatteryManager.EXTRA_STATUS,
                    -1
                ) in listOf(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL)
                val pct = if (scale > 0) (level * 100) / scale else 0
                batteryStatus = if (isCharging) "⚡$pct%" else "🔋$pct%"
            }
            ticks++; delay(1000)
        }
    }

    if (toolDialogTitle.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { toolDialogTitle = "" },
            title = {
                Text(
                    toolDialogTitle,
                    color = Color(0xFF4dd0e1),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
            },
            text = {
                if (isLoadingTool) CircularProgressIndicator(color = Color(0xFF4dd0e1)) else Text(
                    toolDialogContent,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif
                )
            },
            confirmButton = {
                TextButton(onClick = { toolDialogTitle = "" }) {
                    Text(
                        "Close",
                        color = Color(0xFF4dd0e1)
                    )
                }
            },
            containerColor = Color(0xFF2b2b2b)
        )
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
                                        containerColor = if (selectedFont == fontOption) Color(
                                            0xFF4dd0e1
                                        ) else Color(0xFF444444),
                                        contentColor = if (selectedFont == fontOption) Color.Black else Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) { Text(fontOption, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    Column {
                        Text("Theme", color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("Dark", "Light", "Sepia").forEach { themeOption ->
                                Button(
                                    onClick = { theme = themeOption },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (theme == themeOption) Color(0xFF4dd0e1) else Color(
                                            0xFF444444
                                        ), contentColor = if (theme == themeOption) Color.Black else Color.White
                                    ),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text(themeOption, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
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
                            onClick = { currentChapterIndex = index; showChapterDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Chapter ${index + 1}",
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
                                "Ch. ${currentChapterIndex + 1} / ${if (totalChapters == 0) "?" else totalChapters} ▾",
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
                        "[$currentTime]  [$batteryStatus]  ($timeRemaining)  [Words: $wordCount]",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(Color(0xFF2b2b2b))) {
            if (errorMessage.isNotEmpty()) {
                Box(modifier = Modifier.align(Alignment.Center).padding(20.dp)) {
                    Text(errorMessage, color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        SmartWebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)

                            actionModeCallback = object : ActionMode.Callback {
                                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                                    menu?.add(0, 1, 0, "📚 Dictionary")
                                    menu?.add(0, 2, 0, "🌍 Translate (HE)")
                                    menu?.add(0, 3, 0, "🏛 Wikipedia")
                                    return true
                                }

                                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
                                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                                    val id = item?.itemId ?: return false
                                    if (id in 1..3) {
                                        evaluateJavascript("(function(){return window.getSelection().toString()})()") { selection ->
                                            fetchSmartTool(
                                                id,
                                                selection ?: ""
                                            )
                                        }
                                        mode?.finish()
                                        return true
                                    }
                                    return false
                                }

                                override fun onDestroyActionMode(mode: ActionMode?) {}
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
                                val handled =
                                    gestureDetector.onTouchEvent(event); if (handled) view.performClick(); false
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
}