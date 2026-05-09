package com.ldsa.mycontacts.contacts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.ldsa.mycontacts.contacts.BackupHelper;
import com.ldsa.mycontacts.db.ArchivedContact;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExportHelper {

    public interface Callback {
        void onSuccess(String fileUrl);
        void onError(String message);
    }

    private static final String DRIVE_UPLOAD_URL =
        "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart";
    private static final String OAUTH_SCOPE =
        "oauth2:https://www.googleapis.com/auth/drive.file";

    public static void exportToDrive(final Activity activity,
                                     final List<ArchivedContact> contacts,
                                     final Callback callback) {
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        new ExportThread(activity, contacts, callback, mainHandler).start();
    }

    static class ExportThread extends Thread {
        private final Activity mActivity;
        private final List<ArchivedContact> mContacts;
        private final Callback mCallback;
        private final Handler mMain;

        ExportThread(Activity activity, List<ArchivedContact> contacts,
                     Callback callback, Handler main) {
            mActivity = activity;
            mContacts = contacts;
            mCallback = callback;
            mMain = main;
        }

        @Override
        public void run() {
            try {
                AccountManager am = AccountManager.get(mActivity);
                Account[] accounts = am.getAccountsByType("com.google");
                if (accounts.length == 0) { postError("No Google account found on device"); return; }
                Account account = accounts[0];
                android.accounts.AccountManagerFuture<android.os.Bundle> future =
                    am.getAuthToken(account, OAUTH_SCOPE, null, mActivity, null, null);
                android.os.Bundle bundle = future.getResult();
                final String token = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                if (token == null) { postError("Could not get Google auth token"); return; }

                byte[] csvBytes = BackupHelper.buildCsvContent(mContacts).getBytes(Charset.forName("UTF-8"));
                String filename = "contacts_archive_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

                String fileUrl = uploadToDrive(token, filename, csvBytes);
                mMain.post(new SuccessRunnable(mCallback, fileUrl));

            } catch (Exception e) {
                postError(e.getMessage() != null ? e.getMessage() : "Export failed");
            }
        }

        private void postError(final String msg) {
            mMain.post(new ErrorRunnable(mCallback, msg));
        }

        private String uploadToDrive(String token, String filename, byte[] csv) throws Exception {
            String boundary = "boundary_mycontacts_" + System.currentTimeMillis();
            String metaJson = "{\"name\":\"" + filename + "\",\"mimeType\":\"application/vnd.google-apps.spreadsheet\"}";
            String CRLF = "\r\n";

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            String partHeaders =
                "--" + boundary + CRLF +
                "Content-Type: application/json; charset=UTF-8" + CRLF + CRLF +
                metaJson + CRLF +
                "--" + boundary + CRLF +
                "Content-Type: text/csv" + CRLF + CRLF;
            body.write(partHeaders.getBytes("UTF-8"));
            body.write(csv);
            body.write(("\r\n--" + boundary + "--").getBytes("UTF-8"));
            byte[] bodyBytes = body.toByteArray();

            URL url = new URL(DRIVE_UPLOAD_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            OutputStream os = conn.getOutputStream();
            os.write(bodyBytes);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            if (code == 401) {
                AccountManager.get(mActivity).invalidateAuthToken("com.google", token);
                throw new Exception("Auth token expired, please retry");
            }
            if (code < 200 || code >= 300) throw new Exception("Drive upload failed: HTTP " + code);

            BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder resp = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) resp.append(line);
            br.close();

            String fileId = extractJsonString(resp.toString(), "id");
            if (fileId != null && !fileId.isEmpty()) {
                return "https://docs.google.com/spreadsheets/d/" + fileId + "/edit";
            }
            return "https://docs.google.com";
        }

        private String extractJsonString(String json, String key) {
            String search = "\"" + key + "\":\"";
            int start = json.indexOf(search);
            if (start < 0) return null;
            start += search.length();
            int end = json.indexOf("\"", start);
            if (end < 0) return null;
            return json.substring(start, end);
        }
    }

    static class SuccessRunnable implements Runnable {
        private final Callback mCallback;
        private final String mUrl;
        SuccessRunnable(Callback cb, String url) { mCallback = cb; mUrl = url; }
        public void run() { mCallback.onSuccess(mUrl); }
    }

    static class ErrorRunnable implements Runnable {
        private final Callback mCallback;
        private final String mMsg;
        ErrorRunnable(Callback cb, String msg) { mCallback = cb; mMsg = msg; }
        public void run() { mCallback.onError(mMsg); }
    }
}
