package com.poorMenKindle.android.ui.screens

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.core.graphics.scale
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.epub.EpubReader
import org.jsoup.Jsoup
import kotlin.math.ceil

/** Builds the reader HTML for a single EPUB spine entry (image rewrite + sanitize + template). */
fun buildLocalChapterHtml(book: Book, idx: Int, settings: ReaderSettingsState): String {
    val spineRef = book.spine.spineReferences[idx]
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
        // Normalize SVG <image> to <img> so it survives sanitization
        img.tagName("img")
        img.attr("src", "local://$safeHref")
    }

    // EPUB files are untrusted input; strip scripts/handlers before the WebView
    val safeBody = sanitizeChapterHtml(doc.body().html())
    return buildReaderHtml(safeBody, settings.selectedFont)
}

/** Serves an EPUB-embedded image (referenced as local://…) to a reader WebView, downscaled. */
fun serveLocalEpubImage(book: Book?, url: String?): WebResourceResponse? {
    if (url == null || !url.startsWith("local://")) return null
    val encodedPath = url.removePrefix("local://")
    val imagePath = java.net.URLDecoder.decode(encodedPath, "UTF-8")
    val imageResource = book?.resources?.getByHref(imagePath) ?: return null

    val mimeType = imageResource.mediaType?.name ?: "image/jpeg"
    val originalBitmap = android.graphics.BitmapFactory.decodeByteArray(imageResource.data, 0, imageResource.data.size)
        ?: return null

    val maxWidth = 1080f
    val scale = if (originalBitmap.width > maxWidth) maxWidth / originalBitmap.width else 1f
    val finalBitmap = if (scale < 1f) {
        originalBitmap.scale((originalBitmap.width * scale).toInt(), (originalBitmap.height * scale).toInt())
    } else {
        originalBitmap
    }

    val outputStream = java.io.ByteArrayOutputStream()
    finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, outputStream)
    val inputStream = java.io.ByteArrayInputStream(outputStream.toByteArray())

    originalBitmap.recycle()
    if (scale < 1f) finalBitmap.recycle()

    return WebResourceResponse(mimeType, "UTF-8", inputStream)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalReadScreen(
    fileUriString: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    ReaderImmersiveEffect()

    val readerSettings = rememberReaderSettings()
    val smartTool = rememberSmartToolState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showChapterDialog by remember { mutableStateOf(false) }

    val fileUri = fileUriString.toUri()

    var epubBook by remember { mutableStateOf<Book?>(null) }
    var currentChapterIndex by remember { mutableIntStateOf(0) }
    var totalChapters by remember { mutableIntStateOf(0) }

    var chapterHtml by remember { mutableStateOf("Loading book...") }
    var chapterTitle by remember { mutableStateOf("Opening...") }
    var errorMessage by remember { mutableStateOf("") }

    // Shared components
    val (currentTime, batteryStatus) = rememberBatteryAndTime(context)

    var wordCount by remember { mutableIntStateOf(0) }
    var timeRemaining by remember { mutableStateOf("< 1 min left") }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var actionModeActive by remember { mutableStateOf(false) }
    var contentVersion by remember { mutableIntStateOf(0) }
    var currentScrollPercent by remember { mutableFloatStateOf(0f) }
    var isJumping by remember { mutableStateOf(false) }
    // Forward-return slot: written only when swiping BACK, read only when swiping FORWARD.
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

    // restore=true for swipes, false for buttons/TOC (always top). Every swipe records the chapter
    // being left + its scroll (single slot), so swiping back to it — in either direction — returns
    // you to that exact spot. Buttons never touch the slot.
    fun goToLocalChapter(index: Int, restore: Boolean) {
        if (index < 0 || index > totalChapters - 1) return
        val restored = if (restore && index == lastLeftChapter) lastLeftScroll else 0f
        if (restore) {
            lastLeftChapter = currentChapterIndex
            lastLeftScroll = currentScrollPercent
        }
        currentScrollPercent = restored
        isJumping = true
        currentChapterIndex = index
    }

    fun resetLocalScrollToTop() {
        currentScrollPercent = 0f
        webViewRef?.evaluateJavascript("window.scrollTo(0, 0);", null)
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
            val html = withContext(Dispatchers.Default) {
                buildLocalChapterHtml(book, currentChapterIndex, readerSettings)
            }
            val plainText = Jsoup.parse(html).text()
            wordCount = plainText.split("\\s+".toRegex()).size
            val minutes = ceil(wordCount / 225.0).toInt()
            timeRemaining = if (minutes > 1) "$minutes mins left" else "< 1 min left"
            chapterHtml = html
        } catch (e: Exception) {
            chapterHtml = "<div style='color: #ff6b6b;'>Error loading chapter: ${e.message}</div>"
        }
    }

    // Smart tool dialog
    SmartToolDialog(smartTool)

    // Settings dialog
    if (showSettingsDialog) {
        ReaderSettingsDialog(settings = readerSettings, onDismiss = { showSettingsDialog = false })
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
                                goToLocalChapter(index, restore = false)
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
                    },
                    onFindPrev = { webViewRef?.findNext(false) },
                    onFindNext = { webViewRef?.findNext(true) }
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
            ReaderBottomBar(
                currentChapterIndex = currentChapterIndex,
                totalChapters = totalChapters,
                statusText = "[$currentTime]  [$batteryStatus]  ($timeRemaining)  [Words: $wordCount]",
                onPrevChapter = { goToLocalChapter(currentChapterIndex - 1, restore = false) },
                onNextChapter = { goToLocalChapter(currentChapterIndex + 1, restore = false) },
                onSelectChapter = { showChapterDialog = true }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(Color(0xFF2b2b2b))) {
            if (errorMessage.isNotEmpty()) {
                Box(modifier = Modifier.align(Alignment.Center).padding(20.dp)) {
                    Text(errorMessage, color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                PagedReaderContainer(
                    currentChapterIndex = currentChapterIndex,
                    contentVersion = contentVersion,
                    swipeEnabled = { readerSettings.swipeToChangeChaptersEnabled },
                    actionModeActive = { actionModeActive },
                    canGoBack = { currentChapterIndex > 0 },
                    canGoForward = { currentChapterIndex < totalChapters - 1 },
                    themeBgColor = composeReaderBgColor(readerSettings.theme),
                    neighborInterceptRequest = { url -> serveReaderFont(context, url) ?: serveLocalEpubImage(epubBook, url) },
                    readerStyleJs = readerStyleJs(readerSettings),
                    buildNeighborHtml = { idx -> epubBook?.let { buildLocalChapterHtml(it, idx, readerSettings) } },
                    onCommitBack = { goToLocalChapter(currentChapterIndex - 1, restore = true) },
                    onCommitForward = { goToLocalChapter(currentChapterIndex + 1, restore = true) },
                    floatingButton = {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isScrolling,
                            enter = androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.fadeOut(),
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                        ) {
                            ReaderScrollTopButton(onClick = { resetLocalScrollToTop() })
                        }
                    }
                ) { register ->
                    AndroidView(
                        factory = { ctx ->
                            SmartWebView(ctx).apply {
                                webViewRef = this
                                register(this)
                                settings.javaScriptEnabled = true
                                settings.allowFileAccess = false
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                                actionModeCallback = object : ActionMode.Callback {
                                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                                        actionModeActive = true
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
                                                smartTool.fetch(id, selection ?: "")
                                            }
                                            mode?.finish()
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onDestroyActionMode(mode: ActionMode?) {
                                        actionModeActive = false
                                    }
                                }

                                webViewClient = object : android.webkit.WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: android.webkit.WebResourceRequest?
                                    ): android.webkit.WebResourceResponse? {
                                        val url = request?.url?.toString()
                                        return serveReaderFont(ctx, url)
                                            ?: serveLocalEpubImage(epubBook, url)
                                            ?: super.shouldInterceptRequest(view, request)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.evaluateJavascript(readerStyleJs(readerSettings), null)

                                        val scrollJs = """
                                            (function() {
                                                var applyScroll = function() {
                                                    var maxScroll = document.body.scrollHeight - document.documentElement.clientHeight;
                                                    if (maxScroll > 0) window.scrollTo(0, $currentScrollPercent * maxScroll);
                                                };
                                                applyScroll();
                                                setTimeout(applyScroll, 50);
                                                setTimeout(applyScroll, 200);
                                            })();
                                        """.trimIndent()
                                        view?.evaluateJavascript(scrollJs, null)
                                        view?.postDelayed({ isJumping = false }, 300)
                                        contentVersion++
                                    }
                                }

                                setOnScrollChangeListener { _, _, scrollY, _, _ ->
                                    if (isJumping) return@setOnScrollChangeListener
                                    scrollSignal++
                                    val contentHeight = this.contentHeight.toFloat()
                                    val webViewHeight = this.height.toFloat() / context.resources.displayMetrics.density
                                    currentScrollPercent = if (contentHeight > webViewHeight && contentHeight > 0) {
                                        val maxScroll = contentHeight - webViewHeight
                                        val scroll = scrollY.toFloat() / context.resources.displayMetrics.density
                                        (scroll / maxScroll).coerceIn(0f, 1f)
                                    } else 0f
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
                                    onPageBack = { goToLocalChapter(currentChapterIndex - 1, restore = false) },
                                    onPageForward = { goToLocalChapter(currentChapterIndex + 1, restore = false) }
                                )
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
