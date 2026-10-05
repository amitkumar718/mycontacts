package com.ldsa.mycontacts.contacts;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ldsa.mycontacts.db.ArchivedContact;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Dropbox client for a single canonical CSV at {@link #FILE_PATH}, using the
 * App Folder scope. All sync is rev-based:
 *   - Upload uses mode:update with an expected rev (or mode:add on first push)
 *   - Sync does get_metadata first; downloads only when server rev differs
 */
public class DropboxHelper {

    // SharedPreferences layout — single source of truth for both menu items
    public static final String PREF_FILE  = "dropbox";
    public static final String KEY_TOKEN  = "access_token";
    public static final String KEY_REV    = "last_rev";

    // Canonical file in the Dropbox App Folder.
    // With App Folder scope, this maps to /Apps/<AppName>/contacts.csv in the real Dropbox.
    public static final String FILE_PATH = "/contacts.csv";

    private static final String UPLOAD_URL   = "https://content.dropboxapi.com/2/files/upload";
    private static final String DOWNLOAD_URL = "https://content.dropboxapi.com/2/files/download";
    private static final String METADATA_URL = "https://api.dropboxapi.com/2/files/get_metadata";

    private static final String TAG = "mycontacts.dropbox";

    // =========================================================
    // Public API
    // =========================================================

    public interface Callback {
        void onSuccess(String path, String newRev);
        void onConflict();        // server rev != expectedRev; caller should prompt user to sync first
        void onError(String message);
        void onAuthFailed();
    }

    public interface SyncCallback {
        void onEmpty();                                    // file not yet on Dropbox
        void onUnchanged();                                // server rev matches lastKnownRev
        void onChanged(String csvContent, String newRev);  // downloaded fresh content
        void onError(String message);
        void onAuthFailed();
    }

    /** Upload CSV. {@code expectedRev} null → first push (mode:add); non-null → mode:update. */
    public static void uploadToDropbox(Activity activity,
                                       List<ArchivedContact> contacts,
                                       String token,
                                       String expectedRev,
                                       Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new UploadThread(activity, contacts, token, expectedRev, callback, main).start();
    }

    /** Rev-aware sync. Does get_metadata; downloads only if server rev differs from lastKnownRev. */
    public static void syncFromDropbox(Activity activity,
                                       String token,
                                       String lastKnownRev,
                                       SyncCallback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new SyncThread(activity, token, lastKnownRev, callback, main).start();
    }

    // =========================================================
    // Internal exceptions
    // =========================================================

    static class AuthException extends Exception {}
    static class ConflictException extends Exception {}
    static class NotFoundException extends Exception {}

    // =========================================================
    // Upload
    // =========================================================

    static class UploadThread extends Thread {
        private final Activity mActivity;
        private final List<ArchivedContact> mContacts;
        private final String mToken;
        private final String mExpectedRev;
        private final Callback mCallback;
        private final Handler mMain;

        UploadThread(Activity activity, List<ArchivedContact> contacts, String token,
                     String expectedRev, Callback callback, Handler main) {
            mActivity = activity; mContacts = contacts; mToken = token;
            mExpectedRev = expectedRev; mCallback = callback; mMain = main;
        }

        public void run() {
            try {
                byte[] csv = BackupHelper.buildCsvContent(mContacts)
                    .getBytes(Charset.forName("UTF-8"));

                String arg;
                if (mExpectedRev == null || mExpectedRev.isEmpty()) {
                    arg = "{\"path\":\"" + FILE_PATH
                        + "\",\"mode\":\"add\",\"autorename\":false,\"mute\":true}";
                } else {
                    arg = "{\"path\":\"" + FILE_PATH
                        + "\",\"mode\":{\".tag\":\"update\",\"update\":\"" + mExpectedRev
                        + "\"},\"autorename\":false,\"mute\":true}";
                }
                Log.d(TAG, "Upload Dropbox-API-Arg: " + arg + " (csv size=" + csv.length + ")");

                HttpURLConnection conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", "Bearer " + mToken);
                conn.setRequestProperty("Dropbox-API-Arg", arg);
                conn.setRequestProperty("Content-Type", "application/octet-stream");
                conn.setRequestProperty("Content-Length", String.valueOf(csv.length));
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);

                OutputStream os = conn.getOutputStream();
                os.write(csv);
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                Log.d(TAG, "Upload response code: " + code);
                if (code == 401) {
                    Log.e(TAG, "Upload: auth failed (401). Body: " + readStream(conn.getErrorStream()));
                    throw new AuthException();
                }
                if (code == 409) {
                    Log.w(TAG, "Upload: conflict (409). Body: " + readStream(conn.getErrorStream()));
                    throw new ConflictException();
                }
                if (code < 200 || code >= 300) {
                    String err = readStream(conn.getErrorStream());
                    Log.e(TAG, "Upload: HTTP " + code + " body: " + err);
                    // Dropbox returns HTTP 400 (not 401) when the token lacks a required scope.
                    // Treat missing-scope as auth failure so the UI re-prompts for a new token.
                    if (code == 400 && err.contains("missing_scope")) throw new AuthException();
                    if (err.contains("required scope")) throw new AuthException();
                    throw new Exception("HTTP " + code + (err.isEmpty() ? "" : ": " + err));
                }

                String body = readStream(conn.getInputStream());
                String newRev = jsonString(body, "rev");
                String path = jsonString(body, "path_display");
                Log.d(TAG, "Upload OK: path=" + path + " rev=" + newRev);
                mMain.post(new UploadSuccessRunnable(mCallback,
                    path != null ? path : FILE_PATH, newRev));

            } catch (AuthException e) {
                mMain.post(new AuthFailedRunnable(mCallback));
            } catch (ConflictException e) {
                mMain.post(new ConflictRunnable(mCallback));
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : "Upload failed";
                Log.e(TAG, "Upload exception: " + msg, e);
                mMain.post(new ErrorRunnable(mCallback, msg));
            }
        }
    }

    // =========================================================
    // Sync (get_metadata, then conditional download)
    // =========================================================

    static class SyncThread extends Thread {
        private final Activity mActivity;
        private final String mToken;
        private final String mLastKnownRev;
        private final SyncCallback mCallback;
        private final Handler mMain;

        SyncThread(Activity activity, String token, String lastKnownRev,
                   SyncCallback callback, Handler main) {
            mActivity = activity; mToken = token; mLastKnownRev = lastKnownRev;
            mCallback = callback; mMain = main;
        }

        public void run() {
            try {
                String serverRev;
                try {
                    serverRev = getMetadataRev();
                } catch (NotFoundException e) {
                    Log.d(TAG, "Sync: file not found on Dropbox (empty)");
                    mMain.post(new SyncEmptyRunnable(mCallback));
                    return;
                }
                Log.d(TAG, "Sync: serverRev=" + serverRev + " lastKnown=" + mLastKnownRev);

                if (mLastKnownRev != null && mLastKnownRev.equals(serverRev)) {
                    mMain.post(new SyncUnchangedRunnable(mCallback));
                    return;
                }

                String content = download();
                Log.d(TAG, "Sync: downloaded " + content.length() + " chars");
                mMain.post(new SyncChangedRunnable(mCallback, content, serverRev));

            } catch (AuthException e) {
                mMain.post(new SyncAuthFailedRunnable(mCallback));
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : "Sync failed";
                Log.e(TAG, "Sync exception: " + msg, e);
                mMain.post(new SyncErrorRunnable(mCallback, msg));
            }
        }

        private String getMetadataRev() throws Exception {
            HttpURLConnection conn = (HttpURLConnection) new URL(METADATA_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + mToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            byte[] body = ("{\"path\":\"" + FILE_PATH + "\"}").getBytes("UTF-8");
            OutputStream os = conn.getOutputStream();
            os.write(body);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            Log.d(TAG, "get_metadata response code: " + code);
            if (code == 401) throw new AuthException();
            if (code == 409) {
                // Dropbox returns 409 for "not found" on get_metadata
                String err = readStream(conn.getErrorStream());
                Log.d(TAG, "get_metadata 409 body: " + err);
                if (err.contains("\"not_found\"")) throw new NotFoundException();
                throw new Exception("Metadata error: " + err);
            }
            if (code < 200 || code >= 300) {
                String err = readStream(conn.getErrorStream());
                Log.e(TAG, "get_metadata HTTP " + code + " body: " + err);
                if (code == 400 && err.contains("missing_scope")) throw new AuthException();
                if (err.contains("required scope")) throw new AuthException();
                throw new Exception("HTTP " + code + (err.isEmpty() ? "" : ": " + err));
            }
            String resp = readStream(conn.getInputStream());
            String rev = jsonString(resp, "rev");
            if (rev == null) throw new Exception("No rev in metadata response");
            return rev;
        }

        private String download() throws Exception {
            HttpURLConnection conn = (HttpURLConnection) new URL(DOWNLOAD_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + mToken);
            conn.setRequestProperty("Dropbox-API-Arg", "{\"path\":\"" + FILE_PATH + "\"}");
            // Dropbox download requires Content-Type to be empty string
            conn.setRequestProperty("Content-Type", "");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.getOutputStream().close();

            int code = conn.getResponseCode();
            if (code == 401) throw new AuthException();
            if (code < 200 || code >= 300) {
                String err = readStream(conn.getErrorStream());
                throw new Exception("HTTP " + code + (err.isEmpty() ? "" : ": " + err));
            }
            return readStream(conn.getInputStream());
        }
    }

    // =========================================================
    // Shared helpers
    // =========================================================

    static String readStream(InputStream is) {
        if (is == null) return "";
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Robust string field extraction via org.json; falls back to substring on parse failure. */
    static String jsonString(String body, String key) {
        if (body == null || body.isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(body);
            if (obj.has(key) && !obj.isNull(key)) return obj.getString(key);
        } catch (JSONException e) {
            // fall through to substring
        }
        String search = "\"" + key + "\":\"";
        int start = body.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = body.indexOf("\"", start);
        if (end < 0) return null;
        return body.substring(start, end);
    }

    // =========================================================
    // Runnables
    // =========================================================

    static class UploadSuccessRunnable implements Runnable {
        private final Callback mCallback;
        private final String mPath;
        private final String mRev;
        UploadSuccessRunnable(Callback cb, String p, String r) {
            mCallback = cb; mPath = p; mRev = r;
        }
        public void run() { mCallback.onSuccess(mPath, mRev); }
    }

    static class ErrorRunnable implements Runnable {
        private final Callback mCallback;
        private final String mMsg;
        ErrorRunnable(Callback cb, String m) { mCallback = cb; mMsg = m; }
        public void run() { mCallback.onError(mMsg); }
    }

    static class AuthFailedRunnable implements Runnable {
        private final Callback mCallback;
        AuthFailedRunnable(Callback cb) { mCallback = cb; }
        public void run() { mCallback.onAuthFailed(); }
    }

    static class ConflictRunnable implements Runnable {
        private final Callback mCallback;
        ConflictRunnable(Callback cb) { mCallback = cb; }
        public void run() { mCallback.onConflict(); }
    }

    static class SyncEmptyRunnable implements Runnable {
        private final SyncCallback mCallback;
        SyncEmptyRunnable(SyncCallback cb) { mCallback = cb; }
        public void run() { mCallback.onEmpty(); }
    }

    static class SyncUnchangedRunnable implements Runnable {
        private final SyncCallback mCallback;
        SyncUnchangedRunnable(SyncCallback cb) { mCallback = cb; }
        public void run() { mCallback.onUnchanged(); }
    }

    static class SyncChangedRunnable implements Runnable {
        private final SyncCallback mCallback;
        private final String mContent;
        private final String mRev;
        SyncChangedRunnable(SyncCallback cb, String c, String r) {
            mCallback = cb; mContent = c; mRev = r;
        }
        public void run() { mCallback.onChanged(mContent, mRev); }
    }

    static class SyncErrorRunnable implements Runnable {
        private final SyncCallback mCallback;
        private final String mMsg;
        SyncErrorRunnable(SyncCallback cb, String m) { mCallback = cb; mMsg = m; }
        public void run() { mCallback.onError(mMsg); }
    }

    static class SyncAuthFailedRunnable implements Runnable {
        private final SyncCallback mCallback;
        SyncAuthFailedRunnable(SyncCallback cb) { mCallback = cb; }
        public void run() { mCallback.onAuthFailed(); }
    }
}
