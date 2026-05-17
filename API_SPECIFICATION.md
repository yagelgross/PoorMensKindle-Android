# BookWormHole - Server API Specification

This document outlines the REST API architecture for the BookWormHole backend. All protected routes require a JWT Bearer token passed in the Authorization header.

## 1. Authentication

### POST `/login`
Authenticates a user and provisions a JWT token.

- **Access**: Public
- **Payload (Form Data)**:
  - `username` (string)
  - `password` (string)
  - `grant_type` (string)
- **Response (200 OK)**:
  ```json
  {
    "access_token": "jwt_string_here",
    "token_type": "bearer",
    "is_admin": true
  }
  ```

### POST `/logout`
Invalidates the current JWT token on the server side.

- **Access**: Protected (User)
- **Response (200 OK)**: Success status message.

## 2. Library & Books

### GET `/books`
Retrieves the master list of all available books.

- **Access**: Protected (User)
- **Response (200 OK)**: Array of Book objects (`id`, `title`, `author`, `total_chapters`, `series_name`, `series_number`, `date_added`).

### GET `/books/{book_id}`
Retrieves detailed metadata for a specific book.

- **Access**: Protected (User)
- **Response (200 OK)**: Detailed Book object including the summary.

### GET `/books/{book_id}/chapters/{chapter_index}`
Retrieves the raw text content for a specific chapter. Returns cached data if available.

- **Access**: Protected (User)
- **Response (200 OK)**:
  ```json
  {
    "book_id": 1,
    "chapter_title": "Chapter 1: The Beginning",
    "chapter_index": 0,
    "text": "..."
  }
  ```

### GET `/books/{book_id}/toc`
Retrieves a lightweight Table of Contents for navigation.

- **Access**: Protected (User)
- **Response (200 OK)**: Array of objects containing `chapter_index` and `chapter_title`.

### GET `/books/{book_id}/cover`
Retrieves the raw binary image data for a book's cover.

- **Access**: Protected (User)
- **Response (200 OK)**: `image/jpeg` binary stream.

## 3. Reading Progress & Highlights

### POST `/progress/{book_id}`
Updates the user's current reading location.

- **Access**: Protected (User)
- **Payload (JSON)**:
  - `chapter_index` (integer)
  - `scroll_progress` (float)
- **Response (200 OK)**: Success status message.

### GET `/progress/{book_id}`
Retrieves the user's reading progress for a specific book.

- **Access**: Protected (User)
- **Response (200 OK)**:
  ```json
  {
    "chapter_index": 0,
    "scroll_progress": 0.5
  }
  ```

### GET `/last-read`
Fetches the most recently accessed book for the "Continue Reading" widget.

- **Access**: Protected (User)
- **Response (200 OK)**: Returns book metadata combined with the saved `chapter_index` and `scroll_progress`.

### POST `/highlights/{book_id}`
Saves a new user highlight and optional note.

- **Access**: Protected (User)
- **Payload (JSON)**: Chapter index, highlighted text, note, color, and scroll percentage.
- **Response (200 OK)**: Success status message.

### GET `/highlights/{book_id}`
Retrieves all highlights made by the current user for a specific book.

- **Access**: Protected (User)
- **Response (200 OK)**: Array of Highlight objects.

### DELETE `/highlights/{highlight_id}`
Deletes a specific highlight.

- **Access**: Protected (User)
- **Response (200 OK)**: Success status message.

## 4. External Integrations

### GET `/api/search?q={query}`
Proxies a search request to the external Open Library API.

- **Access**: Public / Protected
- **Response (200 OK)**: Array of standardized search results (`title`, `author`, `cover_url`, `publish_year`).

### POST `/api/requests`
Submits a user request for a new book to be added to the server.

- **Access**: Protected (User)
- **Payload (JSON)**: Details of the requested book (`title`, `author`, `open_library_id`).
- **Response (200 OK)**: Success message and generated `ticket_id`.

### GET `/api/translate?text={text}`
Translates a highlighted string from English to Hebrew using external translation services.

- **Access**: Protected (User)
- **Response (200 OK)**:
  ```json
  { "translated_text": "טקסט מתורגם" }
  ```

## 5. Admin Dashboard
Note: All routes in this section strictly require the `is_admin` flag in the JWT payload.

### User Management
- **GET `/admin/users`**: Returns a list of all registered users and login statistics.
- **POST `/admin/users/add/`**: Creates a new user account.
- **DELETE `/admin/users/delete/{user_id}`**: Permanently deletes a user.
- **PUT `/admin/users/reset-password`**: Force-resets a user's password.
- **PUT `/admin/users/promote/{user_id}`**: Grants admin privileges to a user.
- **PUT `/admin/users/demote/{user_id}`**: Revokes admin privileges.
- **GET `/admin/database/{search_term}/type/{search_type}`**: A global search route that filters either the Users or Books tables based on the requested search type (e.g., "book title", "user last login"). This is a base interface of the server.

### Book & Request Management
- **POST `/admin/books/upload`**: Accepts a Multipart Form containing:
  - `title` (string)
  - `author` (string)
  - `series_name` (string, optional)
  - `series_number` (string, optional)
  - `file` (binary .epub file)
  It parses the file and ingests it into the database.
- **POST `/admin/books/add`**: Directly adds book metadata to the database (JSON payload).
- **DELETE `/admin/books/delete/{book_id}`**: Deletes a book and purges all associated chapters from the database.
- **GET `/admin/requests`**: Retrieves all pending and resolved book requests submitted by users.
- **PUT `/admin/requests/{request_id}/status?new_status={status}`**: Updates a user request to 'approved' or 'rejected'.
