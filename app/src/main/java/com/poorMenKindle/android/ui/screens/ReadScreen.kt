package com.poorMenKindle.android.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.poorMenKindle.android.network.ChapterData
import com.poorMenKindle.android.network.NetworkManager
import com.poorMenKindle.android.network.ProgressUpdateRequest
import com.poorMenKindle.android.network.TocItem
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.poorMenKindle.android.network.HighlightItem
import com.poorMenKindle.android.network.HighlightRequest
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun ReadScreen(
    bookId: Int,
    totalChapters: Int,
    initialChapter: Int,
    initialScrollProgress: Float = 0f,
    returnChapterArg: Int = -1,
    returnScrollArg: Float = -1f,
    onNavigateBack: () -> Unit,
    onNavigateToHighlights: (Int, Int, Float) -> Unit
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
    var swipeToChangeChaptersEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("swipe_to_change_chapters", true)) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showChapterDialog by remember { mutableStateOf(false) }

    var currentChapterIndex by remember { mutableIntStateOf(initialChapter) }
    var isJumping by remember { mutableStateOf(false) }
    var chapterHtml by remember { mutableStateOf("Loading...") }
    var chapterTitle by remember { mutableStateOf("") }

    var bookHighlights by remember { mutableStateOf<List<HighlightItem>>(emptyList()) }
    var highlightsFetched by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var pendingHighlightText by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var tableOfContents by remember { mutableStateOf<List<TocItem>>(emptyList()) }

    var activeMatchOrdinal by remember { mutableIntStateOf(0) }
    var numberOfMatches by remember { mutableIntStateOf(0) }

    // --- Return to Previous Position ---
    var returnPosition by remember { 
        mutableStateOf<Pair<Int, Float>?>(
            if (returnChapterArg != -1) returnChapterArg to returnScrollArg else null
        ) 
    }
    var lastJumpId by remember { mutableStateOf<String?>(null) } // To avoid re-triggering on same jump

    // Trigger update if initialChapter or initialScrollProgress changes due to navigation
    LaunchedEffect(initialChapter, initialScrollProgress, returnChapterArg, returnScrollArg) {
        if (returnChapterArg != -1) {
            returnPosition = returnChapterArg to returnScrollArg
        }
        if (currentChapterIndex != initialChapter || currentScrollPercent != initialScrollProgress) {
            isJumping = true
            currentChapterIndex = initialChapter
            currentScrollPercent = initialScrollProgress
        }
    }

    // --- SHARED COMPONENTS IN ACTION ---
    val (currentTime, batteryStatus) = rememberBatteryAndTime(context)

    // Load Local First + Sync from Server
    LaunchedEffect(bookId) {
        highlightsFetched = false
        val db = com.poorMenKindle.android.data.local.AppDatabase.getDatabase(context)
        val dao = db.bookDao()

        // 1. Initial Local Load
        val localHls = withContext(Dispatchers.IO) { dao.getHighlightsForBook(bookId) }
        val localConverted = localHls.map {
            HighlightItem(it.serverHighlightId ?: -it.localId, it.chapterIndex, it.highlightedText, it.note, it.color, it.scrollPercentage, "")
        }
        withContext(Dispatchers.Main) {
            bookHighlights = localConverted
            highlightsFetched = true
        }

        // 2. Sync from Server
        try {
            val response = withContext(Dispatchers.IO) { NetworkManager.api.getHighlights(bookId) }
            if (response.isSuccessful) {
                val serverHls = response.body() ?: emptyList()
                withContext(Dispatchers.IO) {
                    serverHls.forEach { serverHl ->
                        val localMatch = localHls.find { it.highlightedText == serverHl.highlighted_text && it.chapterIndex == serverHl.chapter_index }
                        if (localMatch == null) {
                            val newLocal = com.poorMenKindle.android.data.local.LocalHighlight(
                                bookId = bookId,
                                chapterIndex = serverHl.chapter_index,
                                highlightedText = serverHl.highlighted_text,
                                color = serverHl.color ?: "rgba(255, 235, 59, 0.4)",
                                note = serverHl.note,
                                scrollPercentage = serverHl.scroll_percentage,
                                serverHighlightId = serverHl.id
                            )
                            dao.insertHighlight(newLocal)
                        } else if (localMatch.serverHighlightId == null) {
                            dao.updateHighlightServerId(localMatch.localId, serverHl.id)
                        }
                    }
                }
                val updatedLocalHls = withContext(Dispatchers.IO) { dao.getHighlightsForBook(bookId) }
                withContext(Dispatchers.Main) {
                    bookHighlights = updatedLocalHls.map {
                        HighlightItem(it.serverHighlightId ?: -it.localId, it.chapterIndex, it.highlightedText, it.note, it.color, it.scrollPercentage, "")
                    }
                }
            }
        } catch (e: Exception) { }

        // 3. Fetch TOC
        coroutineScope.launch {
            // First try local DAO
            val localToc = withContext(Dispatchers.IO) { dao.getTocForBook(bookId) }
            if (localToc.isNotEmpty()) {
                tableOfContents = localToc.map { TocItem(it.chapterIndex, it.chapterTitle) }
            }

            // Then try network
            try {
                val response = withContext(Dispatchers.IO) { NetworkManager.api.getToc(bookId) }
                if (response.isSuccessful) {
                    val serverToc = response.body() ?: emptyList()
                    if (serverToc.isNotEmpty()) {
                        tableOfContents = serverToc
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val chapterCache = remember { ConcurrentHashMap<Int, ChapterData>() }
    var wordCount by remember { mutableIntStateOf(0) }
    var timeRemaining by remember { mutableStateOf("< 1 min left") }
    var fullChapterWordCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentScrollPercent) {
        if (fullChapterWordCount > 0) {
            kotlinx.coroutines.delay(400) // Debounce
            val wordsRead = (currentScrollPercent * fullChapterWordCount).toInt()
            val wordsLeft = fullChapterWordCount - wordsRead
            wordCount = wordsLeft
            val minutes = ceil(wordsLeft / 225.0).toInt()
            timeRemaining = if (minutes > 1) "$minutes mins left" else if (minutes == 1) "1 min left" else "< 1 min left"
        }
    }

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

    // --- USE SHARED TOOL DIALOG ---
    SmartToolDialog(
        title = toolDialogTitle,
        content = toolDialogContent,
        isLoading = isLoadingTool,
        onDismiss = { toolDialogTitle = "" }
    )

    // Highlight Color Picker (Kept local because it interacts heavily with WebView selection)
    var pendingHighlightColor by remember { mutableStateOf("") }
    var showNoteDialog by remember { mutableStateOf(false) }

    // 1. Color Picker Dialog
    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("Select Highlight Color", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val colorOptions = listOf(
                        Color(0xFFFFF59D) to "rgba(255, 235, 59, 0.4)",
                        Color(0xFFA5D6A7) to "rgba(76, 175, 80, 0.4)",
                        Color(0xFF90CAF9) to "rgba(33, 150, 243, 0.4)",
                        Color(0xFFEF9A9A) to "rgba(244, 67, 54, 0.4)",
                        Color(0xFFCE93D8) to "rgba(156, 39, 176, 0.4)"
                    )
                    colorOptions.forEach { (composeColor, cssColor) ->
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(composeColor)
                                .clickable {
                                    showColorPicker = false
                                    pendingHighlightColor = cssColor
                                    showNoteDialog = true
                                }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showColorPicker = false }) { Text("Cancel", color = Color.Gray) } },
            containerColor = Color(0xFF2b2b2b)
        )
    }

    // 2. Note Dialog
    if (showNoteDialog) {
        var noteText by remember { mutableStateOf("") }

        fun finalizeHighlight(note: String?) {
            webViewRef?.evaluateJavascript("""
                (function(){
                    var sel = window.getSelection();
                    sel.removeAllRanges();
                    if(window.savedHighlightRange) {
                        sel.addRange(window.savedHighlightRange);
                        document.execCommand('BackColor', false, '$pendingHighlightColor');
                        window.savedHighlightRange = null;
                    }
                })();
            """.trimIndent(), null)

            coroutineScope.launch(Dispatchers.IO) {
                val db = com.poorMenKindle.android.data.local.AppDatabase.getDatabase(context)
                val newLocalHl = com.poorMenKindle.android.data.local.LocalHighlight(
                    bookId = bookId, chapterIndex = currentChapterIndex, highlightedText = pendingHighlightText,
                    color = pendingHighlightColor, note = note, scrollPercentage = currentScrollPercent
                )
                val generatedId = db.bookDao().insertHighlight(newLocalHl).toInt()
                val newHighlight = HighlightItem(generatedId, currentChapterIndex, pendingHighlightText, note, pendingHighlightColor, currentScrollPercent, "")

                withContext(Dispatchers.Main) { bookHighlights = bookHighlights + newHighlight }

                try {
                    val response = NetworkManager.api.addHighlight(bookId, HighlightRequest(currentChapterIndex, pendingHighlightText, note, pendingHighlightColor, currentScrollPercent))
                    if (response.isSuccessful) {
                        val serverResponse = response.body()
                        val serverId = serverResponse?.get("highlight_id")?.toIntOrNull()
                        if (serverId != null) {
                            db.bookDao().updateHighlightServerId(generatedId, serverId)
                            withContext(Dispatchers.Main) {
                                bookHighlights = bookHighlights.map {
                                    if (it.id == generatedId) it.copy(id = serverId) else it
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
            showNoteDialog = false
        }

        AlertDialog(
            onDismissRequest = { finalizeHighlight(null) },
            title = { Text("Add a Note (Optional)", color = Color(0xFF4dd0e1)) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Enter your thoughts...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = Color(0xFF4dd0e1),
                        focusedIndicatorColor = Color(0xFF4dd0e1),
                        unfocusedIndicatorColor = Color.Gray
                    )
                )
            },
            confirmButton = { TextButton(onClick = { finalizeHighlight(noteText.takeIf { it.isNotBlank() }) }) { Text("Save Note", color = Color(0xFF4dd0e1)) } },
            dismissButton = { TextButton(onClick = { finalizeHighlight(null) }) { Text("Skip", color = Color.Gray) } },
            containerColor = Color(0xFF2b2b2b)
        )
    }

    // Check Offline First
    LaunchedEffect(currentChapterIndex, highlightsFetched, bookHighlights) {
        if (!highlightsFetched) return@LaunchedEffect

        chapterHtml = "<div style='color: #888; text-align: center; padding: 50px;'>Loading chapter ${currentChapterIndex + 1}...</div>"

        try {
            var data = chapterCache[currentChapterIndex]

            if (data == null) {
                val db = com.poorMenKindle.android.data.local.AppDatabase.getDatabase(context)
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
            fullChapterWordCount = plainText.split("\\s+".toRegex()).size
            val wordsRead = (currentScrollPercent * fullChapterWordCount).toInt()
            wordCount = fullChapterWordCount - wordsRead
            val minutes = ceil(wordCount / 225.0).toInt()
            timeRemaining = if (minutes > 1) "$minutes mins left" else if (minutes == 1) "1 min left" else "< 1 min left"

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
                            val db = com.poorMenKindle.android.data.local.AppDatabase.getDatabase(context)
                            val local = db.bookDao().getChapter(bookId, i)
                            if (local != null) {
                                chapterCache[i] = ChapterData(bookId, local.chapterTitle, local.chapterIndex, local.contentHtml)
                            } else {
                                val response = NetworkManager.api.getChapter(bookId, i)
                                if (response.isSuccessful) { response.body()?.let { chapterCache[i] = it } }
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            chapterHtml = "<div style='color: #ff6b6b;'>Connection error: ${e.message}</div>"
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    try { NetworkManager.api.saveProgress(bookId, ProgressUpdateRequest(currentChapterIndex, currentScrollPercent)) } catch (_: Exception) {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            chapterCache.clear()
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try { NetworkManager.api.saveProgress(bookId, ProgressUpdateRequest(currentChapterIndex, currentScrollPercent)) } catch (_: Exception) {}
            }
        }
    }

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
        var chapterSearchQuery by remember { mutableStateOf("") }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()

        LaunchedEffect(Unit) {
            val scrollToIndex = if (tableOfContents.isNotEmpty()) {
                tableOfContents.indexOfFirst { it.chapter_index == currentChapterIndex }.coerceAtLeast(0)
            } else {
                currentChapterIndex
            }
            listState.scrollToItem(scrollToIndex)
        }

        AlertDialog(
            onDismissRequest = { showChapterDialog = false },
            title = {
                Text("Select Chapter", color = Color(0xFF4dd0e1), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            },
            text = {
                Column {
                        OutlinedTextField(
                            value = chapterSearchQuery,
                            onValueChange = { chapterSearchQuery = it },
                            placeholder = { Text("Search chapter...", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            singleLine = true,
                            leadingIcon = { Text("🔍", fontSize = 16.sp) },
                            trailingIcon = {
                                if (chapterSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { chapterSearchQuery = "" }) {
                                        Text("✕", color = Color.White)
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Color(0xFF4dd0e1),
                            focusedIndicatorColor = Color(0xFF4dd0e1),
                            unfocusedIndicatorColor = Color.Gray
                        )
                    )
                    androidx.compose.foundation.lazy.LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 300.dp).fillMaxWidth()
                    ) {
                        if (tableOfContents.isNotEmpty()) {
                            items(tableOfContents.size) { i ->
                                val item = tableOfContents[i]
                                val index = item.chapter_index
                                val title = item.chapter_title
                                val matchesSearch = chapterSearchQuery.isEmpty() ||
                                        title.contains(chapterSearchQuery, ignoreCase = true) ||
                                        (index + 1).toString() == chapterSearchQuery.trim()

                                if (matchesSearch) {
                                    TextButton(
                                        onClick = {
                                            returnPosition = null
                                            currentScrollPercent = 0f
                                            isJumping = true
                                            currentChapterIndex = index
                                            showChapterDialog = false
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try { NetworkManager.api.saveProgress(bookId, ProgressUpdateRequest(index, 0f)) } catch (_: Exception) {}
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            title,
                                            color = if (index == currentChapterIndex) Color(0xFF4dd0e1) else Color.White,
                                            fontSize = 16.sp,
                                            textAlign = TextAlign.Left
                                        )
                                    }
                                }
                            }
                        } else {
                            items(totalChapters) { index ->
                                val chapterNum = index + 1
                                val matchesSearch = chapterSearchQuery.isEmpty() ||
                                        "Chapter $chapterNum".contains(chapterSearchQuery, ignoreCase = true) ||
                                        chapterNum.toString() == chapterSearchQuery.trim()

                                if (matchesSearch) {
                                    TextButton(
                                        onClick = {
                                            returnPosition = null
                                            currentScrollPercent = 0f
                                            isJumping = true
                                            currentChapterIndex = index
                                            showChapterDialog = false
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    NetworkManager.api.saveProgress(
                                                        bookId,
                                                        ProgressUpdateRequest(index, 0f)
                                                    )
                                                } catch (_: Exception) {
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "Chapter $chapterNum",
                                            color = if (index == currentChapterIndex) Color(0xFF4dd0e1) else Color.White,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
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
                            // Reset the counters when closing search
                            activeMatchOrdinal = 0
                            numberOfMatches = 0
                        }) {
                            Text("✕", color = Color.White, fontSize = 20.sp)
                        }
                    },
                    actions = {
                        // Display match counts
                        if (numberOfMatches > 0) {
                            Text(
                                text = "${activeMatchOrdinal + 1}/$numberOfMatches",
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        } else if (searchQuery.isNotEmpty()) {
                            Text(
                                text = "0/0",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

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
                    navigationIcon = {
                        TextButton(onClick = onNavigateBack) {
                            Text("◀ Back", color = Color(0xFF4dd0e1), fontWeight = FontWeight.Bold)
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                chapterTitle.ifEmpty { "Chapter ${currentChapterIndex + 1}" },
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold
                            )
                            // Return to Previous Position Button in Top Bar
                            returnPosition?.let { (ch, scroll) ->
                                TextButton(
                                    onClick = {
                                        returnPosition = null
                                        isJumping = true
                                        currentChapterIndex = ch
                                        currentScrollPercent = scroll
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4dd0e1))
                                ) {
                                    Text("Return ↩", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = true }) { Text("🔍", fontSize = 20.sp) }
                                IconButton(onClick = { 
                                    // Capture current scroll before navigating
                                    val currentScroll = currentScrollPercent
                                    
                                    onNavigateToHighlights(bookId, currentChapterIndex, currentScroll) 
                                }) {
                            Text("📝", fontSize = 20.sp)
                        }
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
                            onClick = { 
                                if (currentChapterIndex > 0) {
                                    returnPosition = null
                                    currentScrollPercent = 0f
                                    isJumping = true
                                    currentChapterIndex-- 
                                    val nextChapter = currentChapterIndex
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try { NetworkManager.api.saveProgress(bookId, ProgressUpdateRequest(nextChapter, 0f)) } catch (_: Exception) {}
                                    }
                                }
                            },
                            enabled = currentChapterIndex > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF442694))
                        ) { Text("◀ Prev") }
                        TextButton(onClick = { 
                            returnPosition = null
                            showChapterDialog = true 
                        }) {
                            Text("Ch. ${currentChapterIndex + 1} / ${if (totalChapters == 0) "?" else totalChapters} ▾", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Button(
                            onClick = { 
                                if (currentChapterIndex < totalChapters - 1) {
                                    returnPosition = null
                                    currentScrollPercent = 0f
                                    isJumping = true
                                    currentChapterIndex++ 
                                    val nextChapter = currentChapterIndex
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try { NetworkManager.api.saveProgress(bookId, ProgressUpdateRequest(nextChapter, 0f)) } catch (_: Exception) {}
                                    }
                                }
                            },
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
                        setFindListener { activeMatch, count, isDoneCounting ->
                            if (isDoneCounting) {
                                activeMatchOrdinal = activeMatch
                                numberOfMatches = count
                            }
                        }
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
                                
                                val scrollJs = """
                (function() {
                    var applyScroll = function() {
                        var scrollHeight = document.body.scrollHeight;
                        var clientHeight = document.documentElement.clientHeight;
                        var maxScroll = scrollHeight - clientHeight;
                        if (maxScroll > 0) {
                            window.scrollTo(0, $currentScrollPercent * maxScroll);
                        }
                    };
                    applyScroll();
                    setTimeout(applyScroll, 50);
                    setTimeout(applyScroll, 150);
                    setTimeout(applyScroll, 400);
                    setTimeout(function() {
                        window.androidObj.onJumpFinished();
                    }, 500);
                })();
            """.trimIndent()
                                view?.evaluateJavascript(scrollJs, null)
                                
                                // Reset isJumping after a safety timeout if the JS interface fails to trigger
                                view?.postDelayed({
                                    isJumping = false
                                }, 1000)
                            }
                        }
                        
                        addJavascriptInterface(object {
                            @android.webkit.JavascriptInterface
                            fun onJumpFinished() {
                                isJumping = false
                            }
                        }, "androidObj")

                        setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                            if (isJumping) return@setOnScrollChangeListener
                            
                            val contentHeight = this.contentHeight.toFloat()
                            val webViewHeight = this.height.toFloat() / context.resources.displayMetrics.density

                            if (contentHeight > webViewHeight && contentHeight > 0) {
                                val maxScroll = contentHeight - webViewHeight
                                val currentScroll = scrollY.toFloat() / context.resources.displayMetrics.density
                                val newPercent = (currentScroll / maxScroll).coerceIn(0f, 1f)
                                currentScrollPercent = newPercent
                            } else {
                                currentScrollPercent = 0f
                            }
                        }

                        val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                            private val SWIPE_THRESHOLD = 120
                            private val SWIPE_VELOCITY_THRESHOLD = 150
                            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                                if (e1 == null) return false
                                val diffX = e2.x - e1.x
                                val diffY = e2.y - e1.y
                                if (swipeToChangeChaptersEnabled && abs(diffX) > abs(diffY)) {
                                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                                        if (diffX > 0) {
                                            if (currentChapterIndex > 0) {
                                                returnPosition = null
                                                currentScrollPercent = 0f
                                                isJumping = true
                                                currentChapterIndex--
                                                val nextChapter = currentChapterIndex
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try { NetworkManager.api.saveProgress(bookId, ProgressUpdateRequest(nextChapter, 0f)) } catch (_: Exception) {}
                                                }
                                            }
                                        } else {
                                            if (currentChapterIndex < totalChapters - 1) {
                                                returnPosition = null
                                                currentScrollPercent = 0f
                                                isJumping = true
                                                currentChapterIndex++
                                                val nextChapter = currentChapterIndex
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try { NetworkManager.api.saveProgress(bookId, ProgressUpdateRequest(nextChapter, 0f)) } catch (_: Exception) {}
                                                }
                                            }
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
                                    if (currentChapterIndex < totalChapters - 1) {
                                        currentScrollPercent = 0f
                                        currentChapterIndex++
                                        val nextChapter = currentChapterIndex
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try { NetworkManager.api.saveProgress(bookId, ProgressUpdateRequest(nextChapter, 0f)) } catch (_: Exception) {}
                                        }
                                    }
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                                    if (currentChapterIndex > 0) {
                                        currentScrollPercent = 0f
                                        currentChapterIndex--
                                        val nextChapter = currentChapterIndex
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try { NetworkManager.api.saveProgress(bookId, ProgressUpdateRequest(nextChapter, 0f)) } catch (_: Exception) {}
                                        }
                                    }
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
    return try {
        val parts = html.split(Regex("(?=<)|(?<=>)"))
        parts.joinToString("") { part ->
            if (part.startsWith("<")) {
                part
            } else {
                // Use a regex with word boundaries or just literal replace? 
                // Literal replace is safer for general text selection.
                part.replace(target, replacement)
            }
        }
    } catch (e: Exception) {
        html
    }
}