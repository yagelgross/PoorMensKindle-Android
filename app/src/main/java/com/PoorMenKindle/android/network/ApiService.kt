package com.PoorMenKindle.android.network

import retrofit2.Response
import retrofit2.http.*
import retrofit2.http.GET
import retrofit2.http.Query
import okhttp3.MultipartBody
import okhttp3.RequestBody
interface ApiService {

    // --- Auth ---
    @FormUrlEncoded
    @POST("/login")
    suspend fun login(
        @Field("grant_type") grantType: String = "password",
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<LoginResponse>

    // --- Books ---
    @GET("/books")
    suspend fun getBooks(): Response<List<BookInfo>>

    @GET("/books/{book_id}/chapters/{chapter_index}")
    suspend fun getChapter(
        @Path("book_id") bookId: Int,
        @Path("chapter_index") chapterIndex: Int
    ): Response<ChapterData>

    @GET("/books/{book_id}/cover")
    suspend fun getCover(@Path("book_id") bookId: Int): Response<BookInfo>

    // --- Progress ---
    @POST("/progress/{book_id}")
    suspend fun saveProgress(
        @Path("book_id") bookId: Int,
        @Body progress: ProgressUpdateRequest
    ): Response<Map<String, String>>

    @GET("/progress/{book_id}")
    suspend fun getProgress(@Path("book_id") bookId: Int): Response<ProgressData>

    @GET("/last-read")
    suspend fun getLastRead(): Response<LastReadInfo?>

    // --- Admin Users ---
    @GET("/admin/users")
    suspend fun getAllUsers(): Response<List<UserInfo>>

    @PUT("/admin/users/promote/{user_id}")
    suspend fun promoteUser(@Path("user_id") userId: Int): Response<Map<String, String>>

    @PUT("/admin/users/demote/{user_id}")
    suspend fun demoteUser(@Path("user_id") userId: Int): Response<Map<String, String>>

    @DELETE("/admin/users/delete/{user_id}")
    suspend fun deleteUser(@Path("user_id") userId: Int): Response<Map<String, String>>

    @DELETE("/admin/books/delete/{book_id}")
    suspend fun deleteBook(@Path("book_id") bookId: Int): Response<Map<String, String>>

    @POST("/admin/users/add/")
    suspend fun addUser(@Body request: NewUserRequest): Response<Map<String, String>>

    @Multipart
    @POST("/admin/books/upload")
    suspend fun uploadBookFile(
        @Part("title") title: RequestBody,
        @Part("author") author: RequestBody,
        @Part("series_name") seriesName: RequestBody?,
        @Part("series_number") seriesNumber: RequestBody?,
        @Part file: MultipartBody.Part
    ): Response<Map<String, String>>

    // --- Requests ---
    @GET("/api/search")
    suspend fun searchOpenLibrary(@Query("q") query: String): Response<List<OpenLibraryBook>>

    @POST("/api/requests")
    suspend fun submitBookRequest(@Body request: BookRequestCreate): Response<Map<String, String>>

    @GET("/admin/requests")
    suspend fun getAllRequests(): Response<List<RequestItem>>

    @POST("/admin/books/add")
    suspend fun addBook(@Body request: AdminAddBookRequest): Response<Map<String, String>>

    @GET("/books/{book_id}")
    suspend fun getBook(@Path("book_id") bookId: Int): Response<BookInfo>

    @GET("/api/translate")
    suspend fun translateText(@Query("text") text: String): retrofit2.Response<Map<String, String>>

    @PUT("/admin/requests/{request_id}/status")
    suspend fun updateRequestStatus(
        @Path("request_id") requestId: Int,
        @Query("new_status") newStatus: String
    ): Response<Map<String, String>>
}