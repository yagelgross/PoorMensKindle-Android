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
import com.poorMenKindle.android.data.local.AppDatabase
import com.poorMenKindle.android.data.repository.DEFAULT_HIGHLIGHT_COLOR
import com.poorMenKindle.android.data.repository.HighlightRepository
import com.poorMenKindle.android.network.ChapterData
import com.poorMenKindle.android.network.HighlightItem
import com.poorMenKindle.android.network.NetworkManager
import com.poorMenKindle.android.network.ProgressUpdateRequest
import com.poorMenKindle.android.network.TocItem
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

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
    var currentScrollPercent by remember { mutableFloatStateOf(initialScrollProgress) }
    // Forward-return slot: written only when swiping BACK (the chapter being left + its scroll),
    // read only when swiping FORWARD, so returning to the chapter you came from restores position.
    var lastLeftChapter by remember { mutableIntStateOf(-1) }
    var lastLeftScroll by remember { mutableFloatStateOf(0f) }
    // Drives auto-hiding the floating "scroll to top" button while the user is actively scrolling.
    var scrollSignal by remember { mutableIntStateOf(0) }
    var isScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(scrollSignal) {
        if (scrollSignal == 0) return@LaunchedEffect
        isScrolling = true
        kotlinx.coroutines.delay(700)
        isScrolling = false
    }

    ReaderImmersiveEffect()

    val coroutineScope = rememberCoroutineScope()
    val readerSettings = rememberReaderSettings()
    val highlightRepository = remember { HighlightRepository.get(context) }
    val smartTool = rememberSmartToolState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showChapterDialog by remember { mutableStateOf(false) }

    var currentChapterIndex by remember { mutableIntStateOf(initialChapter) }
    var isJumping by remember { mutableStateOf(false) }
    var chapterHtml by remember { mutableStateOf("Loading...") }
    var chapterTitle by remember { mutableStateOf("") }
    var actionModeActive by remember { mutableStateOf(false) }
    var contentVersion by remember { mutableIntStateOf(0) }

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

    // Back position handling
    var returnPosition by remember {
        mutableStateOf<Pair<Int, Float>?>(
            if (returnChapterArg != -1) returnChapterArg to returnScrollArg else null
        )
    }

    fun saveProgressAsync(chapterIndex: Int, scroll: Float) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                NetworkManager.api.saveProgress(bookId, ProgressUpdateRequest(chapterIndex, scroll))
            } catch (_: Exception) {
            }
        }
    }

    // restore=true for swipes, false for buttons/TOC (always top). Every swipe records the chapter
    // being left + its scroll (single slot), so swiping back to it — in either direction — returns
    // you to that exact spot. Buttons never touch the slot.
    fun navigateToChapter(index: Int, restore: Boolean) {
        if (index < 0 || (totalChapters > 0 && index > totalChapters - 1)) return
        returnPosition = null
        val restored = if (restore && index == lastLeftChapter) lastLeftScroll else 0f
        if (restore) {
            lastLeftChapter = currentChapterIndex
            lastLeftScroll = currentScrollPercent
        }
        currentScrollPercent = restored
        isJumping = true
        currentChapterIndex = index
        saveProgressAsync(index, restored)
    }

    fun goToChapter(index: Int) = navigateToChapter(index, restore = false)
    fun goToChapterRestoring(index: Int) = navigateToChapter(index, restore = true)

    // Reset button: scroll the current chapter to the top.
    fun resetScrollToTop() {
        currentScrollPercent = 0f
        webViewRef?.evaluateJavascript("window.scrollTo(0, 0);", null)
        saveProgressAsync(currentChapterIndex, 0f)
    }

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

    // Shared components
    val (currentTime, batteryStatus) = rememberBatteryAndTime(context)

    // Load local highlights first, then reconcile with the server; then fetch the TOC
    LaunchedEffect(bookId) {
        highlightsFetched = false
        bookHighlights = highlightRepository.getLocalHighlights(bookId)
        highlightsFetched = true
        bookHighlights = highlightRepository.syncFromServer(bookId)

        coroutineScope.launch {
            val dao = AppDatabase.getDatabase(context).bookDao()
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
            } catch (_: Exception) {
            }
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

    // Smart tool dialog
    SmartToolDialog(smartTool)

    // Highlight Color Picker (Kept local because it interacts heavily with WebView selection)
    var pendingHighlightColor by remember { mutableStateOf("") }
    var showNoteDialog by remember { mutableStateOf(false) }

    // Highlight color picker
    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("Select Highlight Color", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val colorOptions = listOf(
                        Color(0xFFFFF59D) to DEFAULT_HIGHLIGHT_COLOR,
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

    // Note dialog
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

            coroutineScope.launch {
                val newHighlight = highlightRepository.addHighlight(
                    bookId = bookId,
                    chapterIndex = currentChapterIndex,
                    text = pendingHighlightText,
                    note = note,
                    color = pendingHighlightColor,
                    scrollPercentage = currentScrollPercent
                )
                bookHighlights = bookHighlights + newHighlight
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
            val cached = chapterCache[currentChapterIndex]
            val data: ChapterData = if (cached != null) {
                cached
            } else {
                val db = AppDatabase.getDatabase(context)
                val localChapter = withContext(Dispatchers.IO) {
                    db.bookDao().getChapter(bookId, currentChapterIndex)
                }

                if (localChapter != null) {
                    ChapterData(bookId, localChapter.chapterTitle, localChapter.chapterIndex, localChapter.contentHtml)
                        .also { chapterCache[currentChapterIndex] = it }
                } else {
                    val response = withContext(Dispatchers.IO) {
                        NetworkManager.api.getChapter(bookId, currentChapterIndex)
                    }
                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        chapterCache[currentChapterIndex] = body
                        body
                    } else {
                        chapterHtml = "<div style='color: #ff6b6b;'>Error loading chapter: ${response.code()}</div>"
                        return@LaunchedEffect
                    }
                }
            }

            chapterTitle = data.chapter_title
            val chapterHighlights = bookHighlights.filter { it.chapter_index == currentChapterIndex }
            val html = buildNetworkChapterHtml(data, chapterHighlights, readerSettings.selectedFont)

            val plainText = Jsoup.parse(html).text()
            fullChapterWordCount = plainText.split("\\s+".toRegex()).size
            val wordsRead = (currentScrollPercent * fullChapterWordCount).toInt()
            wordCount = fullChapterWordCount - wordsRead
            val minutes = ceil(wordCount / 225.0).toInt()
            timeRemaining = if (minutes > 1) "$minutes mins left" else if (minutes == 1) "1 min left" else "< 1 min left"

            chapterHtml = html

            coroutineScope.launch(Dispatchers.IO) {
                val windowStart = max(0, currentChapterIndex - 3)
                val windowEnd = min(totalChapters - 1, currentChapterIndex + 10)
                val keysToRemove = chapterCache.keys.filter { it !in windowStart..windowEnd }
                keysToRemove.forEach { chapterCache.remove(it) }

                val dao = AppDatabase.getDatabase(context).bookDao()
                for (i in windowStart..windowEnd) {
                    if (i != currentChapterIndex && !chapterCache.containsKey(i)) {
                        try {
                            val local = dao.getChapter(bookId, i)
                            if (local != null) {
                                chapterCache[i] = ChapterData(bookId, local.chapterTitle, local.chapterIndex, local.contentHtml)
                            } else {
                                val response = NetworkManager.api.getChapter(bookId, i)
                                if (response.isSuccessful) {
                                    response.body()?.let { chapterCache[i] = it }
                                }
                            }
                        } catch (_: Exception) {
                        }
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

    // Settings dialog
    if (showSettingsDialog) {
        ReaderSettingsDialog(settings = readerSettings, onDismiss = { showSettingsDialog = false })
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
                                            goToChapter(index)
                                            showChapterDialog = false
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
                                            goToChapter(index)
                                            showChapterDialog = false
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
                ReaderSearchTopBar(
                    searchQuery = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        webViewRef?.findAllAsync(it)
                    },
                    onClose = {
                        isSearching = false
                        searchQuery = ""
                        webViewRef?.clearMatches()
                        activeMatchOrdinal = 0
                        numberOfMatches = 0
                    },
                    onFindPrev = { webViewRef?.findNext(false) },
                    onFindNext = { webViewRef?.findNext(true) },
                    matchCounter = when {
                        numberOfMatches > 0 -> "${activeMatchOrdinal + 1}/$numberOfMatches"
                        searchQuery.isNotEmpty() -> "0/0"
                        else -> null
                    }
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
                            onNavigateToHighlights(bookId, currentChapterIndex, currentScrollPercent)
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
            ReaderBottomBar(
                currentChapterIndex = currentChapterIndex,
                totalChapters = totalChapters,
                statusText = "[$currentTime]  [$batteryStatus]  ($timeRemaining)  [Words: $wordCount]",
                onPrevChapter = { goToChapter(currentChapterIndex - 1) },
                onNextChapter = { goToChapter(currentChapterIndex + 1) },
                onSelectChapter = {
                    returnPosition = null
                    showChapterDialog = true
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(Color(0xFF2b2b2b))) {
            PagedReaderContainer(
                modifier = Modifier.fillMaxSize(),
                currentChapterIndex = currentChapterIndex,
                contentVersion = contentVersion,
                swipeEnabled = { readerSettings.swipeToChangeChaptersEnabled },
                actionModeActive = { actionModeActive },
                canGoBack = { currentChapterIndex > 0 },
                canGoForward = { totalChapters > 0 && currentChapterIndex < totalChapters - 1 },
                themeBgColor = composeReaderBgColor(readerSettings.theme),
                neighborInterceptRequest = { url -> serveReaderFont(context, url) },
                readerStyleJs = readerStyleJs(readerSettings),
                buildNeighborHtml = { idx ->
                    chapterCache[idx]?.let {
                        buildNetworkChapterHtml(
                            it,
                            bookHighlights.filter { h -> h.chapter_index == idx },
                            readerSettings.selectedFont
                        )
                    }
                },
                onCommitBack = { goToChapterRestoring(currentChapterIndex - 1) },
                onCommitForward = { goToChapterRestoring(currentChapterIndex + 1) },
                floatingButton = {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isScrolling,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                    ) {
                        ReaderScrollTopButton(onClick = { resetScrollToTop() })
                    }
                }
            ) { register ->
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
                                    actionModeActive = true
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
                                                if (cleanText.isNotBlank()) smartTool.fetch(clickedItemId, cleanText)
                                                mode?.finish()
                                            }
                                        }
                                        return true
                                    }
                                    return callback?.onActionItemClicked(mode, item) ?: false
                                }

                                override fun onDestroyActionMode(mode: android.view.ActionMode?) {
                                    actionModeActive = false
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
                        register(this)
                        setFindListener { activeMatch, count, isDoneCounting ->
                            if (isDoneCounting) {
                                activeMatchOrdinal = activeMatch
                                numberOfMatches = count
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                val url = request?.url?.toString()
                                return serveReaderFont(ctx, url) ?: super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // Page has painted: let the slide overlay lift right away.
                                contentVersion++
                                view?.evaluateJavascript(readerStyleJs(readerSettings), null)

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

                        setOnScrollChangeListener { _, _, scrollY, _, _ ->
                            if (isJumping) return@setOnScrollChangeListener
                            scrollSignal++

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

                    }
                },
                update = { webView ->
                    if (webView.tag != chapterHtml) {
                        webView.loadDataWithBaseURL(READER_BASE_URL, chapterHtml, "text/html", "UTF-8", null)
                        webView.tag = chapterHtml
                    }

                    webView.evaluateJavascript(readerStyleJs(readerSettings), null)

                    webView.setOnKeyListener(
                        volumePagingKeyListener(
                            isEnabled = { readerSettings.volumePagingEnabled },
                            onPageBack = { goToChapter(currentChapterIndex - 1) },
                            onPageForward = { goToChapter(currentChapterIndex + 1) }
                        )
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
            }
        }
    }
}

/** Builds reader HTML for a network chapter: sanitize, bake in highlights, wrap in the template. */
fun buildNetworkChapterHtml(
    data: ChapterData,
    highlights: List<HighlightItem>,
    font: String
): String {
    // Strip scripts/handlers from server-supplied HTML before the JS-enabled WebView sees it
    var htmlContent = sanitizeChapterHtml(data.text)
    highlights.forEach { highlight ->
        val bgColor = highlight.color ?: DEFAULT_HIGHLIGHT_COLOR
        val replacement = "<mark style='background-color: $bgColor; color: inherit; border-radius: 3px; padding: 0 2px;'>${highlight.highlighted_text}</mark>"
        htmlContent = safeHtmlReplace(htmlContent, highlight.highlighted_text, replacement)
    }
    return buildReaderHtml(
        htmlContent.replace("\n\n", "</p><p>").replace("\n", "<br>"),
        font
    )
}

fun safeHtmlReplace(html: String, target: String, replacement: String): String {
    return try {
        val parts = html.split(Regex("(?=<)|(?<=>)"))
        parts.joinToString("") { part ->
            if (part.startsWith("<")) {
                part
            } else {
                // Literal replace is safer for general text selection.
                part.replace(target, replacement)
            }
        }
    } catch (e: Exception) {
        html
    }
}
