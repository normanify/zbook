package com.hexin.zbook;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SettingsViewModel extends AndroidViewModel {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Event<String>> mTestResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mIsTesting = new MutableLiveData<>(false);

    public SettingsViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<Event<String>> getTestResult() {
        return mTestResult;
    }

    public LiveData<Boolean> isTesting() {
        return mIsTesting;
    }

    public void testZoteroConnection(String baseUrl, String apiKey, String userId) {
        if (mIsTesting.getValue() != null && mIsTesting.getValue()) return;

        if (baseUrl.isEmpty() || apiKey.isEmpty() || userId.isEmpty()) {
            mTestResult.postValue(new Event<>("Zotero URL, API Key and User ID cannot be empty"));
            return;
        }

        mIsTesting.setValue(true);
        executor.execute(() -> {
            String finalBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            String userUrl = finalBaseUrl + "users/" + userId + "/";
            try {
                ZoteroApi testApi = ApiClient.getTestClient(userUrl).create(ZoteroApi.class);
                retrofit2.Response<Void> response = testApi.checkCollections(apiKey, 1).execute();
                if (response.isSuccessful()) {
                    mTestResult.postValue(new Event<>("Zotero API connection successful!"));
                } else {
                    mTestResult.postValue(new Event<>("Zotero API connection failed: " + response.code()));
                }
            } catch (Exception e) {
                mTestResult.postValue(new Event<>("Zotero API connection failed: " + e.getMessage()));
            }
            mIsTesting.postValue(false);
        });
    }

    public void testWebDavConnection(String url, String username, String password) {
        if (mIsTesting.getValue() != null && mIsTesting.getValue()) return;

        if (url.isEmpty()) {
            mTestResult.postValue(new Event<>("WebDAV URL cannot be empty"));
            return;
        }

        mIsTesting.setValue(true);

        // --- 修改开始：构建一个跳过 SSL 校验的 OkHttpClient ---
        OkHttpClient client;
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    }
            };

            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            client = new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true) // 解决域名不匹配问题
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // 解决之前遇到的超时问题
                    .build();
        } catch (Exception e) {
            // 如果配置失败，回退到默认（通常不会失败）
            client = new OkHttpClient();
        }
        // --- 修改结束 ---

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .method("PROPFIND", RequestBody.create(null, new byte[0]));

        if (!username.isEmpty()) {
            requestBuilder.addHeader("Authorization", Credentials.basic(username, password));
        }

        client.newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mTestResult.postValue(new Event<>("WebDAV connection failed: " + e.getMessage()));
                mIsTesting.postValue(false);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful() || response.code() == 207) {
                    mTestResult.postValue(new Event<>("WebDAV connection successful!"));
                } else {
                    mTestResult.postValue(new Event<>("WebDAV connection failed: " + response.code() + " " + response.message()));
                }
                response.close();
                mIsTesting.postValue(false);
            }
        });
    }
}
