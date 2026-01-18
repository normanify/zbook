# ZBook

ZBook is an Android application designed to synchronize and manage your Zotero library on the go. It allows you to browse your collections, search for items, and download attachments for offline reading.

## Features

*   **Zotero Sync**: Seamlessly sync your Zotero library (collections and items) using your API Key and User ID.
*   **Browse Collections**: Navigate through your Zotero collections hierarchy.
*   **View Items**: View details of your library items, including metadata.
*   **Search**: Quickly find items by title or Pinyin (for Chinese titles).
*   **Sorting**: Sort items by Name, Author, Publish Date, Date Added, or File Size in ascending or descending order.
*   **Download Attachments**: Download PDF attachments and other files to your device for offline access.
*   **Open Files**: Open downloaded files directly with your preferred external applications.
*   **Offline Access**: Local caching (using Room database) allows you to browse your library even without an internet connection.
*   **Recent Items**: Quickly access your recently opened files.

## Getting Started

1.  **Settings**: Open the app and go to Settings.
2.  **Credentials**: Enter your Zotero User ID and API Key.
3.  **Sync**: Tap the Sync button to fetch your library data.

## Technologies Used

*   **Language**: Java
*   **Architecture**: MVVM (Model-View-ViewModel)
*   **Networking**: Retrofit 2 + OkHttp
*   **Database**: Android Room
*   **Utilities**: Pinyin4j (for Chinese Pinyin support)

## Building

This project is built using Gradle.
Required SDK:
*   Compile SDK: 36
*   Min SDK: 23
*   Target SDK: 34
