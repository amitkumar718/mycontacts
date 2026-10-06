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

    // Parse CSV text into ArchivedContact rows (no DB insert). Used by Dropbox sync.
    public static List<ArchivedContact> parseContacts(String csv) {
        List<ArchivedContact> out = new ArrayList<ArchivedContact>();
        List<List<String>> rows = parseCsvRows(csv);
        if (rows.isEmpty()) return out;
        int startRow = 0;
        if (!rows.get(0).isEmpty() && rows.get(0).get(0).equalsIgnoreCase("Name")) startRow = 1;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        for (int i = startRow; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.isEmpty() || (row.size() == 1 && row.get(0).isEmpty())) continue;
            try {
                out.add(rowToContact(row, sdf));
            } catch (Exception e) { /* skip malformed */ }
        }
        return out;
    }

    private static ArchivedContact rowToContact(List<String> row, SimpleDateFormat sdf)
            throws Exception {
        ArchivedContact c = new ArchivedContact();
        c.displayName = cellAt(row, 0);

        JSONArray phonesArr = new JSONArray();
        String phonesStr = cellAt(row, 1);
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

        JSONArray emailsArr = new JSONArray();
        String emailsStr = cellAt(row, 2);
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

        c.organization = cellAt(row, 3);
        c.jobTitle = cellAt(row, 4);
        c.notes = cellAt(row, 5);

        JSONArray labelsArr = new JSONArray();
        String labelsStr = cellAt(row, 6);
        if (!labelsStr.isEmpty()) {
            for (String label : labelsStr.split(";")) {
                label = label.trim();
                if (!label.isEmpty()) labelsArr.put(label);
            }
        }
        c.labelsJson = labelsArr.length() > 0 ? labelsArr.toString() : "";

        String dateStr = cellAt(row, 7);
        long ts = System.currentTimeMillis();
        if (!dateStr.isEmpty()) {
            try { ts = sdf.parse(dateStr).getTime(); } catch (Exception ignored) {}
        }
        c.archivedAt = ts;

        // v3 columns (optional — older CSVs won't have them)
        String firstName = cellAt(row, 8);  if (!firstName.isEmpty())  c.firstName  = firstName;
        String lastName  = cellAt(row, 9);  if (!lastName.isEmpty())   c.lastName   = lastName;
        String prefix    = cellAt(row, 10); if (!prefix.isEmpty())     c.namePrefix = prefix;
        String suffix    = cellAt(row, 11); if (!suffix.isEmpty())     c.nameSuffix = suffix;
        String nickname  = cellAt(row, 12); if (!nickname.isEmpty())   c.nickname   = nickname;

        String websitesStr = cellAt(row, 13);
        if (!websitesStr.isEmpty()) {
            JSONArray arr = new JSONArray();
            for (String url : websitesStr.split(";")) {
                url = url.trim();
                if (url.isEmpty()) continue;
                try {
                    JSONObject o = new JSONObject();
                    o.put("url", url);
                    o.put("type", 7); // homepage
                    arr.put(o);
                } catch (Exception e) { /* skip */ }
            }
            if (arr.length() > 0) c.websitesJson = arr.toString();
        }

        String addressesStr = cellAt(row, 14);
        if (!addressesStr.isEmpty()) {
            JSONArray arr = new JSONArray();
            for (String a : addressesStr.split(";")) {
                a = a.trim();
                if (a.isEmpty()) continue;
                String[] parts = a.split("\\|", -1);
                try {
                    JSONObject o = new JSONObject();
                    o.put("street",   parts.length > 0 ? parts[0].trim() : "");
                    o.put("city",     parts.length > 1 ? parts[1].trim() : "");
                    o.put("region",   parts.length > 2 ? parts[2].trim() : "");
                    o.put("postcode", parts.length > 3 ? parts[3].trim() : "");
                    o.put("country",  parts.length > 4 ? parts[4].trim() : "");
                    o.put("type", 1); // home
                    arr.put(o);
                } catch (Exception e) { /* skip */ }
            }
            if (arr.length() > 0) c.addressesJson = arr.toString();
        }

        String eventsStr = cellAt(row, 15);
        if (!eventsStr.isEmpty()) {
            JSONArray arr = new JSONArray();
            for (String e : eventsStr.split(";")) {
                e = e.trim();
                if (e.isEmpty()) continue;
                int colon = e.indexOf(':');
                if (colon < 0) continue;
                String typeStr = e.substring(0, colon).trim();
                String date    = e.substring(colon + 1).trim();
                try {
                    JSONObject o = new JSONObject();
                    o.put("date", date);
                    o.put("label", "");
                    if (typeStr.startsWith("custom")) {
                        o.put("type", 0);
                        int c2 = typeStr.indexOf(':');
                        if (c2 >= 0) o.put("label", typeStr.substring(c2 + 1).trim());
                    } else if (typeStr.equalsIgnoreCase("anniversary")) {
                        o.put("type", 1);
                    } else if (typeStr.equalsIgnoreCase("other")) {
                        o.put("type", 2);
                    } else {
                        o.put("type", 3); // birthday (default)
                    }
                    arr.put(o);
                } catch (Exception ex) { /* skip */ }
            }
            if (arr.length() > 0) c.eventsJson = arr.toString();
        }

        return c;
    }

    private static String cellAt(List<String> row, int idx) {
        return idx < row.size() ? row.get(idx) : "";
    }

    public static List<List<String>> parseCsvRows(String content) {
        List<List<String>> rows = new ArrayList<List<String>>();
        List<String> current = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"'); i++;
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
                    current.add(field.toString()); field.setLength(0);
                } else if (c == '\n') {
                    current.add(field.toString()); field.setLength(0);
                    rows.add(current); current = new ArrayList<String>();
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

                List<List<String>> rows = parseCsvRows(sb.toString());
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
