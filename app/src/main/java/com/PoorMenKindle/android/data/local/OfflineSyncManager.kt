package com.PoorMenKindle.android.data.local

import android.content.Context
import com.PoorMenKindle.android.network.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OfflineSyncManager {

    suspend fun downloadBookFull(
        context: Context,
        bookId: Int,
        title: String,
        author: String,
        totalChapters: Int,
        onProgress: (Int, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val dao = db.bookDao()

            val coverResponse = NetworkManager.api.getCover(bookId)
            val coverBase64 = if (coverResponse.isSuccessful) coverResponse.body()?.cover_image else null

            val localBook = LocalBook(bookId, title, author, totalChapters, coverBase64)
            dao.insertBook(localBook)

            for (i in 0 until totalChapters) {
                val chapResponse = NetworkManager.api.getChapter(bookId, i)
                if (chapResponse.isSuccessful) {
                    val chapData = chapResponse.body()
                    if (chapData != null) {
                        val localChapter = LocalChapter(
                            bookId = bookId,
                            chapterIndex = i,
                            chapterTitle = chapData.chapter_title,
                            contentHtml = chapData.text
                        )
                        dao.insertChapters(listOf(localChapter))
                    }
                }
                withContext(Dispatchers.Main) { onProgress(i + 1, totalChapters) }
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun removeLocalBook(context: Context, bookId: Int) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.bookDao().deleteBook(bookId)
            db.bookDao().deleteChapters(bookId)
        }
    }
}