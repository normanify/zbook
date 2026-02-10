package com.hexin.zbook;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {

    private static volatile SettingsManager INSTANCE;
    private static final String PREFS_NAME = "ZBookSettings";

    private static final String KEY_ZOTERO_API_BASE_URL = "zoteroApiBaseUrl";
    private static final String KEY_API_KEY = "apiKey";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_WEB_DAV_URL = "webDavUrl";
    private static final String KEY_WEB_DAV_USERNAME = "webDavUsername";
    private static final String KEY_WEB_DAV_PASSWORD = "webDavPassword";
    private static final String KEY_DOWNLOAD_SIZE_THRESHOLD_MB = "downloadSizeThresholdMb";
    private static final String KEY_HIDE_ATTACHMENTS_IN_ALL_ITEMS = "hideAttachmentsInAllItems";
    private static final String KEY_SORT_FIELD = "sort_field";
    private static final String KEY_SORT_ORDER = "sort_order";
    private static final String KEY_WEB_DAV_CERTIFICATE_PATH = "webDavCertificatePath";
    private static final String KEY_TRUST_ALL_CERTIFICATES = "trustAllCertificates"; // New key for trust all setting
    private final SharedPreferences mPrefs;

    private SettingsManager(Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static SettingsManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SettingsManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SettingsManager(context);
                }
            }
        }
        return INSTANCE;
    }
    public void setSortField(String sortField) {
        mPrefs.edit().putString(KEY_SORT_FIELD, sortField).apply();
    }

    public  String getSortField(String defaultValue) {
        return mPrefs.getString(KEY_SORT_FIELD, defaultValue);
    }


    public void setSortOrder(String sortOrder) {
        mPrefs.edit().putString(KEY_SORT_ORDER, sortOrder).apply();
    }

    public String getSortOrder(String  defaultValue) {
        return mPrefs.getString(KEY_SORT_ORDER, defaultValue);
    }

    public String getZoteroApiBaseUrl() {
        return mPrefs.getString(KEY_ZOTERO_API_BASE_URL, "https://api.zotero.org/");
    }

    public void setZoteroApiBaseUrl(String baseUrl) {
        mPrefs.edit().putString(KEY_ZOTERO_API_BASE_URL, baseUrl).apply();
    }

    public String getApiKey() {
        return mPrefs.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String apiKey) {
        mPrefs.edit().putString(KEY_API_KEY, apiKey).apply();
    }

    public String getUserId() {
        return mPrefs.getString(KEY_USER_ID, "");
    }

    public void setUserId(String userId) {
        mPrefs.edit().putString(KEY_USER_ID, userId).apply();
    }

    public String getWebDavUrl() {
        return mPrefs.getString(KEY_WEB_DAV_URL, "");
    }

    public void setWebDavUrl(String url) {
        mPrefs.edit().putString(KEY_WEB_DAV_URL, url).apply();
    }

    public String getWebDavUsername() {
        return mPrefs.getString(KEY_WEB_DAV_USERNAME, "");
    }

    public void setWebDavUsername(String username) {
        mPrefs.edit().putString(KEY_WEB_DAV_USERNAME, username).apply();
    }

    public String getWebDavPassword() {
        return mPrefs.getString(KEY_WEB_DAV_PASSWORD, "");
    }

    public void setWebDavPassword(String password) {
        mPrefs.edit().putString(KEY_WEB_DAV_PASSWORD, password).apply();
    }

    public int getDownloadSizeThresholdMb() {
        return mPrefs.getInt(KEY_DOWNLOAD_SIZE_THRESHOLD_MB, -1);
    }

    public void setDownloadSizeThresholdMb(int thresholdMb) {
        mPrefs.edit().putInt(KEY_DOWNLOAD_SIZE_THRESHOLD_MB, thresholdMb).apply();
    }

    public boolean getHideAttachmentsInAllItems() {
        return mPrefs.getBoolean(KEY_HIDE_ATTACHMENTS_IN_ALL_ITEMS, true);
    }

    public void setHideAttachmentsInAllItems(boolean hide) {
        mPrefs.edit().putBoolean(KEY_HIDE_ATTACHMENTS_IN_ALL_ITEMS, hide).apply();
    }

    public String getWebDavCertificatePath() {
        return mPrefs.getString(KEY_WEB_DAV_CERTIFICATE_PATH, "");
    }

    public void setWebDavCertificatePath(String path) {
        mPrefs.edit().putString(KEY_WEB_DAV_CERTIFICATE_PATH, path).apply();
    }

    // New methods for trusting all certificates
    public boolean getTrustAllCertificates() {
        return mPrefs.getBoolean(KEY_TRUST_ALL_CERTIFICATES, true);
    }

    public void setTrustAllCertificates(boolean trustAll) {
        mPrefs.edit().putBoolean(KEY_TRUST_ALL_CERTIFICATES, trustAll).apply();
    }
}
