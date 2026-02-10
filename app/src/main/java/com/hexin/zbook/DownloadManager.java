package com.hexin.zbook;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class DownloadManager {

    private static final String TAG = "DownloadManager";
    private static volatile DownloadManager INSTANCE;
    private final Context mContext;
    private final OkHttpClient mHttpClient;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(3);
    private final SettingsManager mSettingsManager;
    private final ItemDao mItemDao;

    private final Map<String, MutableLiveData<DownloadProgress>> mProgressMap = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> mTaskMap = new ConcurrentHashMap<>();
    private final MutableLiveData<GlobalDownloadProgress> mGlobalProgress = new MutableLiveData<>();

    private final AtomicInteger mBulkTotal = new AtomicInteger(0);
    private final AtomicInteger mBulkDownloaded = new AtomicInteger(0);

    public enum DownloadState {
        NOT_DOWNLOADED, QUEUED, DOWNLOADING, DOWNLOADED, FAILED, SKIPPED, DOWNLOADED_BUT_NOT_EXISTS
    }

    public static class DownloadProgress {
        public final DownloadState state; public final long bytesDownloaded; public final long totalBytes; public final String error;
        public DownloadProgress(DownloadState s, long b, long t, String e) { state = s; bytesDownloaded = b; totalBytes = t; error = e; }
    }

    public static class GlobalDownloadProgress {
        public final boolean isBulkDownloading;
        public final int downloadedCount;
        public final int totalToDownload;
        public GlobalDownloadProgress(boolean isBulk, int downloaded, int total) {
            isBulkDownloading = isBulk; downloadedCount = downloaded; totalToDownload = total;
        }
    }
    
    private static class FileSizeExceededException extends IOException {
        private final long fileSize;
        public FileSizeExceededException(String message, long fileSize) {
            super(message);
            this.fileSize = fileSize;
        }
        public long getFileSize() {
            return fileSize;
        }
    }

    private DownloadManager(Context context, ItemDao itemDao) {
        mContext = context.getApplicationContext();
        String certPath = SettingsManager.getInstance(context).getWebDavCertificatePath();
        boolean trustAll = SettingsManager.getInstance(context).getTrustAllCertificates();
        mHttpClient = getTrustedOkHttpClient(context, certPath, trustAll);
        mSettingsManager = SettingsManager.getInstance(context);
        mItemDao = itemDao;
        mGlobalProgress.postValue(new GlobalDownloadProgress(false, 0, 0));
    }

    public static DownloadManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (DownloadManager.class) {
                if (INSTANCE == null) {
                    AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
                    INSTANCE = new DownloadManager(context.getApplicationContext(), db.itemDao());
                }
            }
        }
        return INSTANCE;
    }

    public LiveData<GlobalDownloadProgress> getGlobalDownloadProgress() { return mGlobalProgress; }

    public LiveData<DownloadProgress> getDownloadProgress(String key, String filename) {
        if (!mProgressMap.containsKey(key)) {
            MutableLiveData<DownloadProgress> liveData = new MutableLiveData<>();
            File file = getLocalFileForItem(filename, key);
            long fileSize = file.exists() ? file.length() : 0;
            DownloadState state = file.exists() ? DownloadState.DOWNLOADED : DownloadState.NOT_DOWNLOADED;
            liveData.postValue(new DownloadProgress(state, fileSize, fileSize, null));
            mProgressMap.put(key, liveData);
        }
        return mProgressMap.get(key);
    }

    public void startAllDownloads(List<Item> allAttachments) {
        if (mBulkTotal.get() > 0) return;

        List<Item> toDownload = new ArrayList<>();
        int thresholdMb = mSettingsManager.getDownloadSizeThresholdMb();

        for (Item attachment : allAttachments) {
            File file = getLocalFileForItem(attachment.filename, attachment.key);
            if (file.exists()) continue;

            // For bulk downloads, always pre-check Zotero storage items with known filesize
            if (attachment.filesize > 0 && thresholdMb != -1 && attachment.filesize > thresholdMb * 1024L * 1024L) {
                ((MutableLiveData<DownloadProgress>) getDownloadProgress(attachment.key, attachment.filename))
                    .postValue(new DownloadProgress(DownloadState.SKIPPED, 0, attachment.filesize, "File size exceeds threshold"));
                continue;
            }
            toDownload.add(attachment);
        }

        if (toDownload.isEmpty()) return;

        mBulkTotal.set(toDownload.size());
        mBulkDownloaded.set(0);
        mGlobalProgress.postValue(new GlobalDownloadProgress(true, 0, toDownload.size()));

        for (Item attachment : toDownload) {
            startDownload(attachment, true); // isBulk = true
        }
    }

    public void startDownload(Item attachment, boolean isBulk) {
        MutableLiveData<DownloadProgress> liveData = (MutableLiveData<DownloadProgress>) getDownloadProgress(attachment.key, attachment.filename);
        DownloadProgress current = liveData.getValue();
        if (current != null && (current.state == DownloadState.DOWNLOADING || current.state == DownloadState.DOWNLOADED)) return;

        try {
            // Pre-flight check for Zotero items with known filesize, only in bulk mode.
            if (isBulk && attachment.filesize > 0) {
                int threshold = mSettingsManager.getDownloadSizeThresholdMb();
                if (threshold != -1 && attachment.filesize > threshold * 1024L * 1024L) {
                    throw new FileSizeExceededException("File size exceeds threshold", attachment.filesize);
                }
            }

            liveData.postValue(new DownloadProgress(DownloadState.QUEUED, 0, 0, null));
            Runnable task = () -> {
                try {
                    if (attachment.url != null && !attachment.url.isEmpty()) {
                        downloadZoteroStorage(attachment, liveData);
                    } else {
                        downloadWebDav(attachment, liveData, isBulk);
                    }
                    if (isBulk) incrementBulkProgress();
                } catch (Exception e) {
                     handleDownloadException(e, getLocalFileForItem(attachment.filename, attachment.key), liveData, isBulk);
                } finally {
                    mTaskMap.remove(attachment.key);
                }
            };
            mTaskMap.put(attachment.key, mExecutor.submit(task));

        } catch (Exception e) {
            handleDownloadException(e, null, liveData, isBulk);
        }
    }

    private void incrementBulkProgress() {
        int downloaded = mBulkDownloaded.incrementAndGet();
        int total = mBulkTotal.get();
        mGlobalProgress.postValue(new GlobalDownloadProgress(true, downloaded, total));
        if (downloaded >= total) {
            mBulkTotal.set(0);
            mBulkDownloaded.set(0);
            mGlobalProgress.postValue(new GlobalDownloadProgress(false, 0, 0));
        }
    }

    private void downloadZoteroStorage(Item attachment, MutableLiveData<DownloadProgress> liveData) throws Exception {
        File outputFile = getLocalFileForItem(attachment.filename, attachment.key);
        executeDownload(new Request.Builder().url(attachment.url).build(), outputFile, liveData, attachment.filename);
        liveData.postValue(new DownloadProgress(DownloadState.DOWNLOADED, outputFile.length(), outputFile.length(), null));
    }

    private void downloadWebDav(Item attachment, MutableLiveData<DownloadProgress> liveData, boolean isBulk) throws Exception {
        String url = mSettingsManager.getWebDavUrl();
        if (url == null || url.isEmpty()) throw new IOException("WebDAV URL not configured");
        if (!url.endsWith("/")) url += "/";

        Request.Builder builder = new Request.Builder().url(url + attachment.key + ".zip");
        String user = mSettingsManager.getWebDavUsername();
        if (user != null && !user.isEmpty()) builder.header("Authorization", Credentials.basic(user, mSettingsManager.getWebDavPassword()));

        long fileSize;
        try (Response headResponse = mHttpClient.newCall(builder.head().build()).execute()) {
            if (!headResponse.isSuccessful()) throw new IOException("Failed to get file size: " + headResponse.code());
            String lengthHeader = headResponse.header("Content-Length");
            fileSize = lengthHeader != null ? Long.parseLong(lengthHeader) : -1;
        }

        // Update the filesize in the database as soon as we know it.
        if(fileSize > 0){
            mItemDao.updateFileSize(attachment.key, fileSize);
        }

        // Only perform the check for bulk downloads
        if (isBulk) {
            int thresholdMb = mSettingsManager.getDownloadSizeThresholdMb();
            if (thresholdMb != -1 && fileSize > thresholdMb * 1024L * 1024L) {
                throw new FileSizeExceededException("File size exceeds threshold", fileSize);
            }
        }

        File outputFile = getLocalFileForItem(attachment.filename, attachment.key);
        File tempZipFile = File.createTempFile("dl-", ".zip", mContext.getCacheDir());
        try {
            executeDownload(builder.get().build(), tempZipFile, liveData, attachment.filename != null ? attachment.filename + ".zip" : "file.zip");
            liveData.postValue(new DownloadProgress(DownloadState.DOWNLOADING, tempZipFile.length(), tempZipFile.length(), "Unzipping..."));
            unzip(tempZipFile, outputFile);
            liveData.postValue(new DownloadProgress(DownloadState.DOWNLOADED, outputFile.length(), outputFile.length(), null));
        } finally {
            if (tempZipFile != null) tempZipFile.delete();
        }
    }

    private void executeDownload(Request request, File file, MutableLiveData<DownloadProgress> liveData, String currentFileName) throws IOException, InterruptedException {
        try (Response response = mHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 404) throw new IOException("File not found on server");
                throw new IOException("Server error: " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty response body");

            long total = body.contentLength();
            long downloaded = 0;
            try (InputStream in = body.byteStream(); FileOutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Download cancelled");
                    out.write(buffer, 0, read);
                    downloaded += read;
                    liveData.postValue(new DownloadProgress(DownloadState.DOWNLOADING, downloaded, total, null));
                }
            }
        }
    }

    private void handleDownloadException(Exception e, File outputFile, MutableLiveData<DownloadProgress> liveData, boolean isBulk) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            liveData.postValue(new DownloadProgress(DownloadState.NOT_DOWNLOADED, 0, 0, "Cancelled"));
        } else if (e instanceof FileSizeExceededException) {
            FileSizeExceededException fse = (FileSizeExceededException) e;
            liveData.postValue(new DownloadProgress(DownloadState.SKIPPED, 0, fse.getFileSize(), e.getMessage()));
        } else {
            liveData.postValue(new DownloadProgress(DownloadState.FAILED, 0, 0, e.getMessage()));
        }
        if (outputFile != null && outputFile.exists()) outputFile.delete();
        if (isBulk) incrementBulkProgress();
    }
    
    private void unzip(File zip, File out) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null && entry.isDirectory()) entry = zis.getNextEntry();
            if (entry == null) throw new IOException("Empty ZIP archive");
            if(out.getParentFile() != null && !out.getParentFile().exists()) out.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buffer = new byte[4 * 1024];
                int len; while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
            }
            zis.closeEntry();
        }
    }

    public void cancelDownload(Item attachment) {
        Future<?> future = mTaskMap.get(attachment.key);
        if (future != null) future.cancel(true);
    }

    public void cancelAllDownloads() {
        for (Future<?> future : mTaskMap.values()) future.cancel(true);
        mTaskMap.clear();
        mBulkTotal.set(0);
        mBulkDownloaded.set(0);
        mGlobalProgress.postValue(new GlobalDownloadProgress(false, 0, 0));
    }

    public void deleteFile(Item attachment) {
        File file = getLocalFileForItem(attachment.filename, attachment.key);
        if (file.exists() && file.delete()) {
            ((MutableLiveData<DownloadProgress>) getDownloadProgress(attachment.key, attachment.filename)).postValue(new DownloadProgress(DownloadState.NOT_DOWNLOADED, 0, 0, null));
        }
    }

    public File getLocalFileForItem(String filename, String itemKey) {
        File downloadsDir = new File(mContext.getExternalFilesDir(null), "attachments");
        if (!downloadsDir.exists()) downloadsDir.mkdirs();
        String effectiveFilename = (filename != null && !filename.isEmpty()) ? filename : itemKey;
        return new File(downloadsDir, effectiveFilename);
    }

    // New helper method to create an OkHttpClient that trusts a custom certificate or all certificates

    private OkHttpClient getTrustedOkHttpClient(Context context, String certPath, boolean trustAll) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // 1. 解决核心问题：强制使用 IPv4 避免 IPv6 握手超时
//        builder.dns(hostname -> {
//            try {
//                List<java.net.InetAddress> addresses = okhttp3.Dns.SYSTEM.lookup(hostname);
//                List<java.net.InetAddress> ipv4Addresses = new java.util.ArrayList<>();
//                for (java.net.InetAddress addr : addresses) {
//                    if (addr instanceof java.net.Inet4Address) {
//                        ipv4Addresses.add(addr);
//                    }
//                }
//                // 如果解析出了 IPv4 则只返回 IPv4，否则返回原始列表
//                return ipv4Addresses.isEmpty() ? addresses : ipv4Addresses;
//            } catch (Exception e) {
//                return okhttp3.Dns.SYSTEM.lookup(hostname);
//            }
//        });

        // 2. 增加超时时间：给握手留出更多缓冲空间
        builder.connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS);

        // 3. 原有的 SSL 证书配置逻辑
        if (trustAll) {
            Log.w(TAG, "Trusting all certificates...");
            try {
                final TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                            @Override
                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        }
                };
                final SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
                builder.hostnameVerifier((hostname, session) -> true);
            } catch (Exception e) {
                Log.e(TAG, "Error configuring trustAll", e);
            }
        } else if (certPath != null && !certPath.isEmpty()) {
            try {
                InputStream caInput = new FileInputStream(certPath);
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                Certificate ca = certificateFactory.generateCertificate(caInput);
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, null);
                keyStore.setCertificateEntry("ca", ca);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(keyStore);
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), null);
                builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) tmf.getTrustManagers()[0]);
            } catch (Exception e) {
                Log.e(TAG, "Error configuring custom cert", e);
            }
        }

        return builder.build();
    }



}
