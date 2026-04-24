package com.PoorMenKindle.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.PoorMenKindle.android.network.BookInfo
import com.PoorMenKindle.android.network.NetworkManager
import com.PoorMenKindle.android.network.NewUserRequest
import com.PoorMenKindle.android.network.RequestItem
import com.PoorMenKindle.android.network.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Users", "Books", "Requests")

    var searchQuery by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    // Data States
    var users by remember { mutableStateOf<List<UserInfo>>(emptyList()) }
    var books by remember { mutableStateOf<List<BookInfo>>(emptyList()) }
    var requests by remember { mutableStateOf<List<RequestItem>>(emptyList()) }

    var showAddUserDialog by remember { mutableStateOf(false) }
    var showAddBookDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var seriesName by remember { mutableStateOf("") }
    var seriesNumber by remember { mutableStateOf("") }

    // JavaFX Peach-to-Coral Gradient
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFfcd6b5), Color(0xFFfc6969))
    )

    // Data Fetching Logic
    fun loadData() {
        statusMessage = "Loading..."
        coroutineScope.launch {
            try {
                when (selectedTabIndex) {
                    0 -> {
                        val res = withContext(Dispatchers.IO) { NetworkManager.api.getAllUsers() }
                        if (res.isSuccessful) users = res.body() ?: emptyList()
                    }
                    1 -> {
                        val res = withContext(Dispatchers.IO) { NetworkManager.api.getBooks() }
                        if (res.isSuccessful) books = res.body() ?: emptyList()
                    }
                    2 -> {
                        val res = withContext(Dispatchers.IO) { NetworkManager.api.getAllRequests() }
                        if (res.isSuccessful) requests = res.body() ?: emptyList()
                    }
                }
                statusMessage = ""
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
            }
        }
    }

    // Fetch data when tab changes
    LaunchedEffect(selectedTabIndex) { loadData() }

    // Add User Dialog
    if (showAddUserDialog) {
        var newUsername by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var isNewAdmin by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            title = { Text("Add New User") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newUsername, onValueChange = { newUsername = it }, label = { Text("Username") })
                    OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, label = { Text("Password") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isNewAdmin, onCheckedChange = { isNewAdmin = it })
                        Text("Is Admin")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddUserDialog = false
                    coroutineScope.launch {
                        try {
                            val req = NewUserRequest(newUsername, newPassword, isNewAdmin)
                            withContext(Dispatchers.IO) { NetworkManager.api.addUser(req) }
                            loadData() // Refresh list
                        } catch (e: Exception) {
                            statusMessage = "Failed to add user."
                        }
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddUserDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add Book Dialog (Hybrid: Manual Text + EPUB File)
    if (showAddBookDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newAuthor by remember { mutableStateOf("") }
        var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
        var isUploading by remember { mutableStateOf(false) }

        val epubPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            selectedFileUri = uri
        }

        AlertDialog(
            onDismissRequest = { if (!isUploading) showAddBookDialog = false },
            title = { Text("Add New Book", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newAuthor,
                        onValueChange = { newAuthor = it },
                        label = { Text("Author") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = seriesName,
                        onValueChange = { seriesName = it },
                        label = { Text("Series Name (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = seriesNumber,
                        onValueChange = { seriesNumber = it },
                        label = { Text("Series Number (e.g., 1 or 1.5)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isUploading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(color = Color(0xFF4dd0e1))
                            Text("Uploading & Extracting...", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    } else {
                        Button(
                            onClick = { epubPicker.launch(arrayOf("application/epub+zip")) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedFileUri == null) Color(0xFF000333) else Color(0xFF2ecc71)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (selectedFileUri == null) "📁 Select EPUB File" else "✔ EPUB Selected")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = selectedFileUri ?: return@TextButton
                        isUploading = true

                        coroutineScope.launch {
                            try {
                                val titleBody = newTitle.toRequestBody(okhttp3.MultipartBody.FORM)
                                val authorBody = newAuthor.toRequestBody(okhttp3.MultipartBody.FORM)

                                val seriesNameBody = seriesName.takeIf { it.isNotBlank() }
                                    ?.toRequestBody(okhttp3.MultipartBody.FORM)

                                val seriesNumBody = seriesNumber.takeIf { it.isNotBlank() }
                                    ?.toRequestBody(okhttp3.MultipartBody.FORM)

                                val inputStream = context.contentResolver.openInputStream(uri)
                                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                                val mediaType = "application/epub+zip".toMediaTypeOrNull()
                                val fileBody = bytes.toRequestBody(mediaType)

                                val multipartFile = okhttp3.MultipartBody.Part.createFormData("file", "uploaded_book.epub", fileBody)

                                val res = withContext(Dispatchers.IO) {
                                    NetworkManager.api.uploadBookFile(
                                        titleBody,
                                        authorBody,
                                        seriesNameBody,
                                        seriesNumBody,
                                        multipartFile
                                    )
                                }

                                if (res.isSuccessful) {
                                    statusMessage = res.body()?.get("message") ?: "Book added!"
                                    showAddBookDialog = false
                                    // Clear fields for the next upload
                                    newTitle = ""
                                    newAuthor = ""
                                    seriesName = ""
                                    seriesNumber = ""
                                    selectedFileUri = null

                                    loadData() // Refresh admin list
                                } else {
                                    statusMessage = "Upload failed: ${res.code()}"
                                }
                            } catch (e: Exception) {
                                statusMessage = "Error: ${e.message}"
                            } finally {
                                isUploading = false
                            }
                        }
                    },
                    // Ensure the button is only clickable if all fields are filled and a file is picked
                    enabled = newTitle.isNotBlank() && newAuthor.isNotBlank() && selectedFileUri != null && !isUploading
                ) {
                    Text("Upload", color = if (newTitle.isNotBlank() && newAuthor.isNotBlank() && selectedFileUri != null) Color(0xFF2ecc71) else Color.Gray, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddBookDialog = false },
                    enabled = !isUploading
                ) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙ Admin Dashboard", fontWeight = FontWeight.Bold, color = Color(0xFF000333)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF000333))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = { showAddUserDialog = true },
                    containerColor = Color(0xFF000333),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add User")
                }
            } else if (selectedTabIndex == 1) { // --- Add Book Button ---
                FloatingActionButton(
                    onClick = { showAddBookDialog = true },
                    containerColor = Color(0xFF000333),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Book")
                }
            }
        },
        containerColor = Color.Transparent,
        modifier = Modifier.background(backgroundBrush)
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {

            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.White.copy(alpha = 0.4f),
                contentColor = Color(0xFF000333)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search currently loaded data...") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.6f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.4f),
                    focusedBorderColor = Color(0xFF000333)
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            if (statusMessage.isNotEmpty()) {
                Text(statusMessage, modifier = Modifier.padding(horizontal = 16.dp), color = Color.DarkGray)
            }

            // Lists
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> {
                        val filteredUsers = users.filter { it.username.contains(searchQuery, ignoreCase = true) }
                        items(filteredUsers) { user ->
                            UserCard(user, coroutineScope, onRefresh = { loadData() })
                        }
                    }
                    1 -> {
                        val filteredBooks = books.filter { it.title.contains(searchQuery, ignoreCase = true) }
                        items(filteredBooks) { book ->
                            AdminBookCard(book, coroutineScope, onRefresh = { loadData() })
                        }
                    }
                    2 -> {
                        val filteredRequests = requests.filter { it.title.contains(searchQuery, ignoreCase = true) }
                        items(filteredRequests) { req ->
                            RequestCard(req, coroutineScope, onRefresh = { loadData() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserCard(user: UserInfo, scope: kotlinx.coroutines.CoroutineScope, onRefresh: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "ID: ${user.id} | ${user.username}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (user.isAdmin) {
                    Text("ADMIN", color = Color(0xFF2ecc71), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
            Text("Joined: ${user.createdAt}", fontSize = 12.sp, color = Color.DarkGray)
            Text("Last Login: ${user.lastLogin ?: "Never"}", fontSize = 12.sp, color = Color.DarkGray)

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (user.isAdmin) {
                    Button(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { NetworkManager.api.demoteUser(user.id) }
                            onRefresh()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) { Text("Demote") }
                } else {
                    Button(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { NetworkManager.api.promoteUser(user.id) }
                            onRefresh()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ecc71))) { Text("Promote") }
                }

                Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { NetworkManager.api.deleteUser(user.id) }
                        onRefresh()
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFe74c3c))) { Text("Delete") }
            }
        }
    }
}

@Composable
fun AdminBookCard(book: BookInfo, scope: kotlinx.coroutines.CoroutineScope, onRefresh: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {

                if (!book.series_name.isNullOrEmpty()) {
                    val numStr = book.series_number?.let {
                        if (it % 1.0 == 0.0) "#${it.toInt()}" else "#$it"
                    } ?: ""
                    Text(
                        text = "${book.series_name} $numStr".trim(),
                        fontSize = 12.sp,
                        color = Color(0xFFe67e22),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(text = book.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = book.author, color = Color.DarkGray, fontSize = 14.sp)
                Text(text = "ID: ${book.id} | ${book.total_chapters} Chapters", fontSize = 12.sp, color = Color.Gray)
            }
            Button(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { NetworkManager.api.deleteBook(book.id) }
                    onRefresh()
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFe74c3c))) {
                Text("Delete")
            }
        }
    }
}

@Composable
fun RequestCard(req: RequestItem, scope: kotlinx.coroutines.CoroutineScope, onRefresh: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = req.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = "By: ${req.author}", color = Color.DarkGray, fontSize = 14.sp)
            Text(text = "Requested by: ${req.requestedBy}", fontSize = 12.sp, color = Color.Gray)
            Text(text = "Status: ${req.status.uppercase()}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (req.status == "pending") Color(0xFFe67e22) else if (req.status == "approved") Color(0xFF2ecc71) else Color(0xFFe74c3c))

            if (req.status == "pending") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { NetworkManager.api.updateRequestStatus(req.id, "rejected") }
                            onRefresh()
                        }
                    }, modifier = Modifier.background(Color(0xFFe74c3c).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Download", tint = Color(0xFFe74c3c))
                    }

                    Button(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { NetworkManager.api.updateRequestStatus(req.id, "approved") }
                            onRefresh()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ecc71))) { Text("✔ Approve") }

                    Button(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { NetworkManager.api.updateRequestStatus(req.id, "rejected") }
                            onRefresh()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFe74c3c))) { Text("✖ Reject") }
                }
            }
            else if (req.status == "approved") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { NetworkManager.api.updateRequestStatus(req.id, "rejected") }
                            onRefresh()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFe74c3c))) { Text("Delete") }

                }
            }
        }
    }
}