package com.poorMenKindle.android.data.local

import android.content.Context
import com.poorMenKindle.android.network.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OfflineSyncManager {

    /**
     * Downloads a book and all of its chapters for offline reading.
     *
     * The book row is only inserted after every chapter has been stored, so a book is
     * never shown as "downloaded" with missing chapters. Each chapter gets one retry;
     * on failure all partial chapter data is removed and false is returned.
     */
    suspend fun downloadBookFull(
        context: Context,
        bookId: Int,
        title: String,
        author: String,
        totalChapters: Int,
        onProgress: (Int, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getDatabase(context).bookDao()
        try {
            val coverBase64 = try {
                val coverResponse = NetworkManager.api.getCover(bookId)
                if (coverResponse.isSuccessful) {
                    coverResponse.body()?.bytes()?.let { imageBytes ->
                        android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT)
                    }
                } else null
            } catch (e: Exception) {
                null
            }

            val fullBookDetails = try {
                NetworkManager.api.getBook(bookId).body()
            } catch (e: Exception) {
                null
            }

            for (i in 0 until totalChapters) {
                val stored = fetchAndStoreChapter(dao, bookId, i) || fetchAndStoreChapter(dao, bookId, i)
                if (!stored) {
                    dao.deleteChapters(bookId)
                    return@withContext false
                }
                withContext(Dispatchers.Main) { onProgress(i + 1, totalChapters) }
            }

            dao.insertBook(
                LocalBook(
                    bookId,
                    title,
                    author,
                    totalChapters,
                    coverBase64,
                    fullBookDetails?.summary,
                    fullBookDetails?.series_name,
                    fullBookDetails?.series_number
                )
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                dao.deleteChapters(bookId)
            } catch (_: Exception) {
            }
            false
        }
    }

    private suspend fun fetchAndStoreChapter(dao: BookDao, bookId: Int, chapterIndex: Int): Boolean {
        return try {
            val response = NetworkManager.api.getChapter(bookId, chapterIndex)
            val data = response.body()
            if (response.isSuccessful && data != null) {
                dao.insertChapters(
                    listOf(
                        LocalChapter(
                            bookId = bookId,
                            chapterIndex = chapterIndex,
                            chapterTitle = data.chapter_title,
                            contentHtml = data.text
                        )
                    )
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
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
