package com.poorMenKindle.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poorMenKindle.android.network.BookInfo
import com.poorMenKindle.android.network.NetworkManager
import com.poorMenKindle.android.network.NewUserRequest
import com.poorMenKindle.android.network.RequestItem
import com.poorMenKindle.android.network.UserInfo
import com.poorMenKindle.android.ui.theme.adminBackgroundBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

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

    var users by remember { mutableStateOf<List<UserInfo>>(emptyList()) }
    var books by remember { mutableStateOf<List<BookInfo>>(emptyList()) }
    var requests by remember { mutableStateOf<List<RequestItem>>(emptyList()) }

    var showAddUserDialog by remember { mutableStateOf(false) }
    var showAddBookDialog by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf<UserInfo?>(null) }
    var confirmDeleteUser by remember { mutableStateOf<UserInfo?>(null) }
    var confirmDeleteBook by remember { mutableStateOf<BookInfo?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var seriesName by remember { mutableStateOf("") }
    var seriesNumber by remember { mutableStateOf("") }

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

    /** Runs an admin API call, surfaces failures in the status line,
     *  and refreshes the list. */
    fun runAdminAction(actionLabel: String, action: suspend () -> Response<Map<String, String>>) {
        coroutineScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { action() }
                if (!res.isSuccessful) {
                    statusMessage = "$actionLabel failed: ${res.code()}"
                    return@launch
                }
            } catch (e: Exception) {
                statusMessage = "$actionLabel failed: ${e.message}"
                return@launch
            }
            loadData()
        }
    }

    LaunchedEffect(selectedTabIndex) { loadData() }

    // Confirm: delete user
    confirmDeleteUser?.let { user ->
        AlertDialog(
            onDismissRequest = { confirmDeleteUser = null },
            title = { Text("Delete User?") },
            text = { Text("This will permanently delete \"${user.username}\" and their data. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteUser = null
                    runAdminAction("Delete user") { NetworkManager.api.deleteUser(user.id) }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteUser = null }) { Text("Cancel") }
            }
        )
    }

    // Confirm: delete book
    confirmDeleteBook?.let { book ->
        AlertDialog(
            onDismissRequest = { confirmDeleteBook = null },
            title = { Text("Delete Book?") },
            text = { Text("This will permanently remove \"${book.title}\" from the library for all users. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteBook = null
                    runAdminAction("Delete book") { NetworkManager.api.deleteBook(book.id) }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteBook = null }) { Text("Cancel") }
            }
        )
    }

    if (showResetPasswordDialog != null) {
        val targetUser = showResetPasswordDialog!!
        var newPassword by remember { mutableStateOf("") }
        var isResetting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isResetting) showResetPasswordDialog = null },
            title = { Text("Reset Password for ${targetUser.username}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (isResetting) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPassword.isBlank()) return@Button
                        isResetting = true
                        coroutineScope.launch {
                            try {
                                val res = withContext(Dispatchers.IO) {
                                    NetworkManager.api.resetUserPassword(
                                        com.poorMenKindle.android.network.ResetPasswordRequest(
                                            user_id = targetUser.id,
                                            new_password = newPassword
                                        )
                                    )
                                }
                                if (res.isSuccessful) {
                                    statusMessage = "Password reset successful!"
                                    showResetPasswordDialog = null
                                } else {
                                    statusMessage = "Reset failed: ${res.code()}"
                                }
                            } catch (e: Exception) {
                                statusMessage = "Error: ${e.message}"
                            } finally {
                                isResetting = false
                            }
                        }
                    },
                    enabled = !isResetting && newPassword.isNotBlank()
                ) {
                    Text("Confirm/Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetPasswordDialog = null }, enabled = !isResetting) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add user dialog
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
                    runAdminAction("Add user") {
                        NetworkManager.api.addUser(NewUserRequest(newUsername, newPassword, isNewAdmin))
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddUserDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add book dialog (Manual Text + EPUB File)
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
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Uploading & Extracting...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = { epubPicker.launch(arrayOf("application/epub+zip")) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedFileUri == null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (selectedFileUri == null) "📁 Select EPUB File" else "✔ EPUB Selected")
                        }
                    }
                }
            },
            confirmButton = {
                val canUpload = newTitle.isNotBlank() && newAuthor.isNotBlank() && selectedFileUri != null
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
                    enabled = canUpload && !isUploading
                ) {
                    Text(
                        "Upload",
                        color = if (canUpload) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddBookDialog = false },
                    enabled = !isUploading
                ) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙ Admin Dashboard", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            when (selectedTabIndex) {
                0 -> {
                    // Show Add User button on the Users tab
                    FloatingActionButton(
                        onClick = { showAddUserDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add User")
                    }
                }
                1 -> {
                    // Show Add Book button on the Books tab
                    FloatingActionButton(
                        onClick = { showAddBookDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Book")
                    }
                }
            }
        },
        containerColor = Color.Transparent,
        modifier = Modifier.background(adminBackgroundBrush())
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {

            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.primary
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
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            if (statusMessage.isNotEmpty()) {
                val isFailure = statusMessage.contains("fail", ignoreCase = true) ||
                        statusMessage.contains("error", ignoreCase = true)
                Text(
                    statusMessage,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = if (isFailure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                            UserCard(
                                user = user,
                                onPromote = { runAdminAction("Promote") { NetworkManager.api.promoteUser(user.id) } },
                                onDemote = { runAdminAction("Demote") { NetworkManager.api.demoteUser(user.id) } },
                                onDelete = { confirmDeleteUser = user },
                                onResetPassword = { showResetPasswordDialog = it }
                            )
                        }
                    }
                    1 -> {
                        val filteredBooks = books.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                                    it.author.contains(searchQuery, ignoreCase = true) ||
                                    (it.series_name?.contains(searchQuery, ignoreCase = true) == true)
                        }

                        items(filteredBooks) { book ->
                            AdminBookCard(book = book, onDelete = { confirmDeleteBook = book })
                        }
                    }
                    2 -> {
                        val filteredRequests = requests.filter { it.title.contains(searchQuery, ignoreCase = true) }
                        items(filteredRequests) { req ->
                            RequestCard(
                                req = req,
                                onApprove = { runAdminAction("Approve") { NetworkManager.api.updateRequestStatus(req.id, "approved") } },
                                onReject = { runAdminAction("Reject") { NetworkManager.api.updateRequestStatus(req.id, "rejected") } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserCard(
    user: UserInfo,
    onPromote: () -> Unit,
    onDemote: () -> Unit,
    onDelete: () -> Unit,
    onResetPassword: (UserInfo) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "ID: ${user.id} | ${user.username}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (user.isAdmin) {
                    Text("ADMIN", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
            Text("Joined: ${user.createdAt}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Last Login: ${user.lastLogin ?: "Never"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (user.isAdmin) {
                    Button(
                        onClick = onDemote,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Text("Demote") }
                } else {
                    Button(
                        onClick = onPromote,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) { Text("Promote") }
                }

                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }

                Button(
                    onClick = { onResetPassword(user) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Reset")
                }
            }
        }
    }
}

@Composable
fun AdminBookCard(book: BookInfo, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
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
                        color = MaterialTheme.colorScheme.secondary,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(text = book.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = book.author, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Text(text = "ID: ${book.id} | ${book.total_chapters} Chapters", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        }
    }
}

@Composable
fun RequestCard(req: RequestItem, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = req.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = "By: ${req.author}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text(text = "Requested by: ${req.requestedBy}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            Text(
                text = "Status: ${req.status.uppercase()}",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = when (req.status) {
                    "pending" -> MaterialTheme.colorScheme.secondary
                    "approved" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }
            )

            if (req.status == "pending") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) { Text("✔ Approve") }

                    Button(
                        onClick = onReject,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("✖ Reject") }
                }
            } else if (req.status == "approved") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onReject,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                }
            }
        }
    }
}
