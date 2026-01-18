# ZBook

ZBook is an Android application designed to synchronize and manage your Zotero library on the go. It is **specifically optimized for Android-based e-book readers (E-Ink devices)** and tablets, allowing seamless integration with your preferred reading apps.

## Motivation

While the official Zotero for Android app is robust, it enforces the use of its embedded PDF reader. This can be less than ideal for specific use cases:

*   **E-Ink Devices**: Devices like **Onyx Boox (NoteP, etc.), inkBOOK, Hisense**, and others often have specialized system readers optimized for their E-Ink screens (refresh rates, contrast, scribbling). Using the official app's embedded reader bypasses these optimizations.
*   **Flexibility**: Users may prefer professional third-party PDF readers (like Moon+ Reader, Adobe Acrobat) that offer features not available in the official client.

**ZBook** was developed to bridge this gap. It synchronizes your library but allows you to open files using **your system's default viewer** or any installed app of your choice, purely acting as a library manager.

## Features

*   **Android E-Book Friendy**: Designed with E-Ink screens in mind (high contrast, simple interface).
*   **Zotero Sync**: Seamlessly sync your Zotero library (collections and items) using your API Key and User ID.
*   **Browse Collections**: Navigate through your Zotero collections hierarchy.
*   **View Items**: View details of your library items, including metadata.
*   **Search**: Quickly find items by title or Pinyin (for Chinese titles).
*   **Sorting**: Sort items by Name, Author, Publish Date, Date Added, or File Size in ascending or descending order.
*   **Download Attachments**: Download PDF attachments and other files to your device for offline access.
*   **Open Files**: Open downloaded files directly with your preferred external applications.
*   **Offline Access**: Local caching (using Room database) allows you to browse your library even without an internet connection.
*   **Recent Items**: Quickly access your recently opened files.

## Usage Instructions

### 1. Configure Zotero Access
To sync your library, you need to generate a private key for ZBook:
1.  Log in to [Zotero.org](https://www.zotero.org).
2.  Navigate to **Home -> Settings -> Security -> Applications**.
3.  Click **"Create new private key"**.
4.  Make sure to grant read access to your library.
5.  Note down your **User ID** (e.g., "Your userID for use in API calls is XXXXXXX").
6.  Copy the generated **API Key**.

### 2. Configure App Settings
Open ZBook and go to **Settings**:
1.  **Zotero Config**: Enter your **User ID** and **API Key** from the previous step.
2.  **WebDAV Config**:
    *   **Note**: Currently, ZBook only supports **WebDAV** for file storage and syncing.
    *   Enter your WebDAV **URL**, **Username**, and **Password**.
3.  Tap **"Test Connection"** (optional) to verify settings.
4.  Tap **"Save"**.

### 3. Sync
Return to the main screen and tap the **Sync** button (or select "Sync" from the menu) to fetch your library structure and items.

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

### GitHub Actions (Auto-Publish)

To enable automatic release building, configure the following **Secrets** in your GitHub repository settings:

*   `KEYSTORE_FILE`: The base64 encoded content of your `keystore.jks` file.
    *   Command to generate: `base64 -i path/to/keystore.jks | pbcopy`
*   `KEYSTORE_PASSWORD`: The password for your keystore.
*   `KEY_ALIAS`: The alias of the key.
*   `KEY_PASSWORD`: The password for the key.
