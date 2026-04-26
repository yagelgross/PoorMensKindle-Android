package com.PoorMenKindle.android.ui.screens

import android.view.ActionMode
import androidx.core.net.toUri
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.epub.EpubReader
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlin.math.ceil
import androidx.core.graphics.scale
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalReadScreen(
    fileUriString: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val view = androidx.compose.ui.platform.LocalView.current

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
    var swipeToChangeChaptersEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("swipe_to_change_chapters", true)) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showChapterDialog by remember { mutableStateOf(false) }

    val fileUri = fileUriString.toUri()

    var epubBook by remember { mutableStateOf<Book?>(null) }
    var currentChapterIndex by remember { mutableIntStateOf(0) }
    var totalChapters by remember { mutableIntStateOf(0) }

    var chapterHtml by remember { mutableStateOf("Loading book...") }
    var chapterTitle by remember { mutableStateOf("Opening...") }
    var errorMessage by remember { mutableStateOf("") }

    // --- SHARED COMPONENTS IN ACTION ---
    val (currentTime, batteryStatus) = rememberBatteryAndTime(context)

    var wordCount by remember { mutableIntStateOf(0) }
    var timeRemaining by remember { mutableStateOf("< 1 min left") }

    var toolDialogTitle by remember { mutableStateOf("") }
    var toolDialogContent by remember { mutableStateOf("") }
    var isLoadingTool by remember { mutableStateOf(false) }
    val publicApiClient = remember { OkHttpClient() }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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

    LaunchedEffect(fileUri) {
        try {
            withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    errorMessage = "Could not open file stream."
                    return@withContext
                }
                val book = EpubReader().readEpub(inputStream)
                epubBook = book
                totalChapters = book.spine.size()
                chapterTitle = book.title ?: "Unknown Title"
            }
        } catch (e: Throwable) {
            errorMessage = "EPUB Parse Crash: ${e::class.java.simpleName} - ${e.message}"
        }
    }

    LaunchedEffect(epubBook, currentChapterIndex) {
        val book = epubBook ?: return@LaunchedEffect
        try {
            val spineRef = book.spine.spineReferences[currentChapterIndex]
            val resource = spineRef.resource
            val rawHtml = String(resource.data)
            val doc = Jsoup.parse(rawHtml)
            val chapterHref = resource.href

            val images = doc.select("img, image")
            for (img in images) {
                var src = img.attr("src").ifEmpty { img.attr("xlink:href") }
                src = java.net.URLDecoder.decode(src, "UTF-8")

                var resolvedHref = src
                val chapterDir = chapterHref.substringBeforeLast("/", "")

                if (src.startsWith("../")) {
                    val parentDir = chapterDir.substringBeforeLast("/", "")
                    val cleanSrc = src.replaceFirst("../", "")
                    resolvedHref = if (parentDir.isEmpty()) cleanSrc else "$parentDir/$cleanSrc"
                } else if (chapterDir.isNotEmpty() && !src.startsWith("/")) {
                    resolvedHref = "$chapterDir/$src"
                }

                val safeHref = java.net.URLEncoder.encode(resolvedHref, "UTF-8")
                img.attr("src", "local://$safeHref")
            }

            val plainText = doc.text()
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

    // --- USE SHARED TOOL DIALOG ---
    SmartToolDialog(
        title = toolDialogTitle,
        content = toolDialogContent,
        isLoading = isLoadingTool,
        onDismiss = { toolDialogTitle = "" }
    )

    // --- USE SHARED SETTINGS DIALOG ---
    if (showSettingsDialog) {
        ReaderSettingsDialog(
            fontSize = fontSize,
            onFontSizeChange = {
                fontSize = it
                sharedPrefs.edit().putInt("font_size", it).apply()
            },
            lineSpacing = lineSpacing,
            onLineSpacingChange = {
                lineSpacing = it
                sharedPrefs.edit().putFloat("line_spacing", it).apply()
            },
            selectedFont = selectedFont,
            onFontChange = {
                selectedFont = it
                sharedPrefs.edit().putString("font", it).apply()
            },
            theme = theme,
            onThemeChange = {
                theme = it
                sharedPrefs.edit().putString("theme", it).apply()
            },
            volumePagingEnabled = volumePagingEnabled,
            onVolumePagingChange = {
                volumePagingEnabled = it
                sharedPrefs.edit().putBoolean("volume_paging", it).apply()
            },
            swipeToChangeChaptersEnabled = swipeToChangeChaptersEnabled,
            onSwipeToChangeChaptersChange = {
                swipeToChangeChaptersEnabled = it
                sharedPrefs.edit().putBoolean("swipe_to_change_chapters", it).apply()
            },
            onDismiss = { showSettingsDialog = false }
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
                                // LocalReadScreen doesn't seem to have currentScrollPercent or isJumping state
                                // but if it did, we would reset it here.
                                showChapterDialog = false 
                            },
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
                TextButton(onClick = { showChapterDialog = false }) { Text("Close", color = Color.Gray) }
            },
            containerColor = Color(0xFF2b2b2b)
        )
    }

    Scaffold(
        topBar = {
            if (isSearching) {
                TopAppBar(
                    modifier = Modifier.displayCutoutPadding(),
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                webViewRef?.findAllAsync(it)
                            },
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
                        IconButton(onClick = {
                            isSearching = false
                            searchQuery = ""
                            webViewRef?.clearMatches()
                        }) {
                            Text("✕", color = Color.White, fontSize = 20.sp)
                        }
                    },
                    actions = {
                        IconButton(onClick = { webViewRef?.findNext(false) }) {
                            Text("▲", color = Color.White, fontSize = 20.sp)
                        }
                        IconButton(onClick = { webViewRef?.findNext(true) }) {
                            Text("▼", color = Color.White, fontSize = 20.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
                )
            } else {
                TopAppBar(
                    modifier = Modifier.displayCutoutPadding(),
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
                    actions = {
                        IconButton(onClick = { isSearching = true }) { Text("🔍", fontSize = 20.sp) }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Text("⚙️", fontSize = 20.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E1E1E),
                        titleContentColor = Color.White
                    )
                )
            }
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
                            onClick = { 
                                if (currentChapterIndex > 0) {
                                    currentChapterIndex--
                                }
                            },
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
                            onClick = { 
                                if (currentChapterIndex < totalChapters - 1) {
                                    currentChapterIndex++
                                }
                            },
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
                            webViewRef = this
                            settings.javaScriptEnabled = true
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                            settings.allowFileAccess = true

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
                                            val response = android.webkit.WebResourceResponse("font/ttf", "UTF-8", inputStream)
                                            response.responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
                                            return response
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }

                                    if (url.startsWith("local://")) {
                                        val encodedPath = url.replace("local://", "")
                                        val imagePath = java.net.URLDecoder.decode(encodedPath, "UTF-8")
                                        val imageResource = epubBook?.resources?.getByHref(imagePath)

                                        if (imageResource != null) {
                                            val mimeType = imageResource.mediaType?.name ?: "image/jpeg"
                                            val originalBitmap = android.graphics.BitmapFactory.decodeByteArray(imageResource.data, 0, imageResource.data.size)

                                            if (originalBitmap != null) {
                                                val maxWidth = 1080f
                                                val scale = if (originalBitmap.width > maxWidth) maxWidth / originalBitmap.width else 1f

                                                val finalBitmap = if (scale < 1f) {
                                                    originalBitmap.scale(
                                                        (originalBitmap.width * scale).toInt(),
                                                        (originalBitmap.height * scale).toInt()
                                                    )
                                                } else {
                                                    originalBitmap
                                                }

                                                val outputStream = java.io.ByteArrayOutputStream()
                                                finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, outputStream)
                                                val inputStream = java.io.ByteArrayInputStream(outputStream.toByteArray())

                                                originalBitmap.recycle()
                                                if (scale < 1f) finalBitmap.recycle()

                                                return android.webkit.WebResourceResponse(mimeType, "UTF-8", inputStream)
                                            }
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val bgColor = when (theme) { "Light" -> "#ffffff"; "Sepia" -> "#f4ecd8"; else -> "#2b2b2b" }
                                    val textColor = when (theme) { "Light" -> "#000000"; "Sepia" -> "#5b4636"; else -> "#d4d4d4" }

                                    val js = """
    document.body.style.fontSize = '${fontSize}px';
    document.body.style.lineHeight = '${lineSpacing}';
    document.body.style.backgroundColor = '$bgColor';
    document.body.style.color = '$textColor';
    document.body.style.fontFamily = "'${selectedFont}', serif";
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
                                        if (swipeToChangeChaptersEnabled && Math.abs(diffX) > Math.abs(diffY)) {
                                            if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                                                if (diffX > 0) {
                                                    if (currentChapterIndex > 0) {
                                                        currentChapterIndex--
                                                    }
                                                } else {
                                                    if (currentChapterIndex < totalChapters - 1) {
                                                        currentChapterIndex++
                                                    }
                                                }
                                                return true
                                            }
                                        }
                                        return false
                                    }
                                })
                            setOnTouchListener { view, event ->
                                val handled = gestureDetector.onTouchEvent(event); if (handled) view.performClick(); false
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
}