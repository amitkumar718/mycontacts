package com.ldsa.mycontacts.contacts;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.ldsa.mycontacts.db.ArchivedContact;
import com.ldsa.mycontacts.db.ContactDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ImportHelper {

    public interface Callback {
        void onDone(int imported, int skipped);
        void onError(String message);
    }

    public static void importFromUri(Context ctx, Uri uri, Callback callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        new ImportThread(ctx, uri, callback, handler).start();
    }

    static class ImportThread extends Thread {
        private final Context mCtx;
        private final Uri mUri;
        private final Callback mCallback;
        private final Handler mHandler;

        ImportThread(Context ctx, Uri uri, Callback callback, Handler handler) {
            mCtx = ctx; mUri = uri; mCallback = callback; mHandler = handler;
        }

        public void run() {
            try {
                ContentResolver cr = mCtx.getContentResolver();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(cr.openInputStream(mUri), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int n;
                while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
                reader.close();

                List<List<String>> rows = parseCsv(sb.toString());
                if (rows.isEmpty()) { postError("Empty file"); return; }

                int startRow = 0;
                if (!rows.get(0).isEmpty() && rows.get(0).get(0).equalsIgnoreCase("Name")) {
                    startRow = 1;
                }

                ContactDatabase db = ContactDatabase.getInstance(mCtx);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                int imported = 0, skipped = 0;

                for (int i = startRow; i < rows.size(); i++) {
                    List<String> row = rows.get(i);
                    if (row.isEmpty() || (row.size() == 1 && row.get(0).isEmpty())) continue;
                    try {
                        ArchivedContact c = new ArchivedContact();
                        c.displayName = get(row, 0);

                        String phonesStr = get(row, 1);
                        JSONArray phonesArr = new JSONArray();
                        if (!phonesStr.isEmpty()) {
                            for (String phone : phonesStr.split(";")) {
                                phone = phone.trim();
                                if (!phone.isEmpty()) {
                                    JSONObject obj = new JSONObject();
                                    obj.put("type", 2);
                                    obj.put("number", phone);
                                    phonesArr.put(obj);
                                }
                            }
                        }
                        c.phonesJson = phonesArr.length() > 0 ? phonesArr.toString() : "";

                        String emailsStr = get(row, 2);
                        JSONArray emailsArr = new JSONArray();
                        if (!emailsStr.isEmpty()) {
                            for (String email : emailsStr.split(";")) {
                                email = email.trim();
                                if (!email.isEmpty()) {
                                    JSONObject obj = new JSONObject();
                                    obj.put("type", 1);
                                    obj.put("address", email);
                                    emailsArr.put(obj);
                                }
                            }
                        }
                        c.emailsJson = emailsArr.length() > 0 ? emailsArr.toString() : "";

                        c.organization = get(row, 3);
                        c.jobTitle = get(row, 4);
                        c.notes = get(row, 5);

                        String labelsStr = get(row, 6);
                        JSONArray labelsArr = new JSONArray();
                        if (!labelsStr.isEmpty()) {
                            for (String label : labelsStr.split(";")) {
                                label = label.trim();
                                if (!label.isEmpty()) labelsArr.put(label);
                            }
                        }
                        c.labelsJson = labelsArr.length() > 0 ? labelsArr.toString() : "";

                        String dateStr = get(row, 7);
                        long ts = System.currentTimeMillis();
                        if (!dateStr.isEmpty()) {
                            try { ts = sdf.parse(dateStr).getTime(); } catch (Exception ignored) {}
                        }
                        c.archivedAt = ts;

                        db.insert(c);
                        imported++;
                    } catch (Exception e) {
                        skipped++;
                    }
                }

                final int imp = imported, skip = skipped;
                mHandler.post(new ImportResultRunnable(mCallback, imp, skip));

            } catch (IOException e) {
                postError(e.getMessage() != null ? e.getMessage() : "Failed to read file");
            }
        }

        private void postError(String msg) {
            mHandler.post(new ImportErrorRunnable(mCallback, msg));
        }

        private String get(List<String> row, int idx) {
            return idx < row.size() ? row.get(idx) : "";
        }

        private List<List<String>> parseCsv(String content) {
            List<List<String>> rows = new ArrayList<List<String>>();
            List<String> current = new ArrayList<String>();
            StringBuilder field = new StringBuilder();
            boolean inQuotes = false;

            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (inQuotes) {
                    if (c == '"') {
                        if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                            field.append('"');
                            i++;
                        } else {
                            inQuotes = false;
                        }
                    } else {
                        field.append(c);
                    }
                } else {
                    if (c == '"') {
                        inQuotes = true;
                    } else if (c == ',') {
                        current.add(field.toString());
                        field.setLength(0);
                    } else if (c == '\n') {
                        current.add(field.toString());
                        field.setLength(0);
                        rows.add(current);
                        current = new ArrayList<String>();
                    } else if (c != '\r') {
                        field.append(c);
                    }
                }
            }
            if (field.length() > 0 || !current.isEmpty()) {
                current.add(field.toString());
                rows.add(current);
            }
            return rows;
        }
    }

    static class ImportResultRunnable implements Runnable {
        private final Callback mCallback;
        private final int mImported;
        private final int mSkipped;
        ImportResultRunnable(Callback cb, int imported, int skipped) {
            mCallback = cb; mImported = imported; mSkipped = skipped;
        }
        public void run() { mCallback.onDone(mImported, mSkipped); }
    }

    static class ImportErrorRunnable implements Runnable {
        private final Callback mCallback;
        private final String mMsg;
        ImportErrorRunnable(Callback cb, String msg) { mCallback = cb; mMsg = msg; }
        public void run() { mCallback.onError(mMsg); }
    }
}
