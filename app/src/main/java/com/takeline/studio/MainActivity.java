package com.takeline.studio;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class MainActivity extends Activity {
    private static final int AUDIO_REQUEST = 501;
    private static final int FILE_REQUEST = 502;
    private static final int SAVE_REQUEST = 503;
    private static final String HOME = "https://appassets.androidplatform.net/assets/index.html";
    private WebView view;
    private PermissionRequest pendingMic;
    private ValueCallback<Uri[]> fileCallback;
    private OutputStream exportStream;
    private boolean exporting = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff05070d);
        getWindow().setNavigationBarColor(0xff05070d);
        view = new WebView(this);
        setContentView(view);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        // Keep microphone and IndexedDB under a trustworthy HTTPS-like local origin.
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        view.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                return !("appassets.androidplatform.net".equals(u.getHost()) && "https".equals(u.getScheme()));
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (!HOME.equals(view.getUrl()) || !Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        request.deny(); return;
                    }
                    // Only grant audio capture, never grant unexpected camera or other resources.
                    pendingMic = request;
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        grantMic();
                    } else requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, AUDIO_REQUEST);
                });
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest req) {
                if (pendingMic == req) pendingMic = null;
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                        "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain", "text/markdown", "application/octet-stream"
                    });
                    startActivityForResult(intent, FILE_REQUEST);
                    return true;
                } catch (Exception e) { fileCallback = null; callback.onReceiveValue(null); return false; }
            }
        });
        // Only packaged content is allowed to navigate; do not load untrusted pages into this JS bridge.
        view.addJavascriptInterface(new SaveBridge(), "AndroidHost");
        view.loadUrl(HOME);
    }

    private void grantMic() {
        PermissionRequest request = pendingMic;
        pendingMic = null;
        if (request != null) request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == AUDIO_REQUEST && pendingMic != null) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) grantMic();
            else { pendingMic.deny(); pendingMic = null; }
        }
    }
    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == FILE_REQUEST && fileCallback != null) {
            Uri uri = result == RESULT_OK && data != null ? data.getData() : null;
            fileCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
            fileCallback = null;
        } else if (code == SAVE_REQUEST) {
            boolean ready = false;
            if (result == RESULT_OK && data != null && data.getData() != null) {
                try { exportStream = getContentResolver().openOutputStream(data.getData(), "w"); ready = exportStream != null; }
                catch (Exception ignored) { exportStream = null; }
            }
            exporting = ready;
            view.evaluateJavascript("window.TakelineAndroid && window.TakelineAndroid.onExportReady(" + ready + ")", null);
        }
    }
    private synchronized void closeExport() {
        if (exportStream != null) try { exportStream.close(); } catch (IOException ignored) {}
        exportStream = null;
        exporting = false;
    }
    public final class SaveBridge {
        @JavascriptInterface public void requestSave(String filename, String mime) {
            runOnUiThread(() -> {
                if (exporting) { view.evaluateJavascript("window.TakelineAndroid.onExportReady(false)", null); return; }
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(mime != null && mime.startsWith("audio/") ? mime : "audio/wav");
                intent.putExtra(Intent.EXTRA_TITLE, filename == null ? "takeline-audio.wav" : filename.replaceAll("[^a-zA-Z0-9._-]", "_"));
                try { startActivityForResult(intent, SAVE_REQUEST); }
                catch (Exception e) { view.evaluateJavascript("window.TakelineAndroid.onExportReady(false)", null); }
            });
        }
        @JavascriptInterface public synchronized boolean appendChunk(String b64) {
            if (!exporting || exportStream == null) return false;
            try { exportStream.write(Base64.decode(b64, Base64.DEFAULT)); return true; }
            catch (Exception e) { closeExport(); return false; }
        }
        @JavascriptInterface public synchronized void finishSave() { closeExport(); }
        @JavascriptInterface public synchronized void cancelSave() { closeExport(); }
    }
    @Override public void onBackPressed() {
        // Preserve the audio editor; don't inadvertently navigate away and discard an active session.
        moveTaskToBack(true);
    }
    @Override protected void onDestroy() {
        closeExport();
        if (pendingMic != null) pendingMic.deny();
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        view.destroy();
        super.onDestroy();
    }
}
