package com.ldsa.mycontacts.contacts;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.ldsa.mycontacts.db.ArchivedContact;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BackupHelper {

    public static String buildCsvContent(List<ArchivedContact> contacts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name,Phones,Emails,Organization,Job Title,Notes,Labels,Archived On,"
                + "First Name,Last Name,Name Prefix,Name Suffix,Nickname,Websites,Addresses,Events\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        for (ArchivedContact c : contacts) {
            StringBuilder phones = new StringBuilder();
            for (ArchivedContact.Phone p : c.getPhones()) {
                if (phones.length() > 0) phones.append("; ");
                phones.append(p.number);
            }
            StringBuilder emails = new StringBuilder();
            for (ArchivedContact.Email e : c.getEmails()) {
                if (emails.length() > 0) emails.append("; ");
                emails.append(e.address);
            }
            StringBuilder labels = new StringBuilder();
            for (String l : c.getLabels()) {
                if (labels.length() > 0) labels.append("; ");
                labels.append(l);
            }
            // Websites: urls separated by "; "
            StringBuilder websites = new StringBuilder();
            for (ArchivedContact.Website w : c.getWebsites()) {
                if (websites.length() > 0) websites.append("; ");
                websites.append(w.url);
            }
            // Addresses: each address as "street|city|region|postcode|country", separated by "; "
            StringBuilder addresses = new StringBuilder();
            for (ArchivedContact.Address a : c.getAddresses()) {
                if (addresses.length() > 0) addresses.append("; ");
                addresses.append(scrub(a.street)).append("|")
                         .append(scrub(a.city)).append("|")
                         .append(scrub(a.region)).append("|")
                         .append(scrub(a.postcode)).append("|")
                         .append(scrub(a.country));
            }
            // Events: each as "type:date" (type in {birthday,anniversary,other,custom:<label>}), separated by "; "
            StringBuilder events = new StringBuilder();
            for (ArchivedContact.Event ev : c.getEvents()) {
                if (events.length() > 0) events.append("; ");
                events.append(eventTypeName(ev)).append(":").append(scrub(ev.date));
            }

            sb.append(csvCell(c.displayName)).append(",");
            sb.append(csvCell(phones.toString())).append(",");
            sb.append(csvCell(emails.toString())).append(",");
            sb.append(csvCell(c.organization)).append(",");
            sb.append(csvCell(c.jobTitle)).append(",");
            sb.append(csvCell(c.notes)).append(",");
            sb.append(csvCell(labels.toString())).append(",");
            sb.append(csvCell(sdf.format(new Date(c.archivedAt)))).append(",");
            sb.append(csvCell(c.firstName)).append(",");
            sb.append(csvCell(c.lastName)).append(",");
            sb.append(csvCell(c.namePrefix)).append(",");
            sb.append(csvCell(c.nameSuffix)).append(",");
            sb.append(csvCell(c.nickname)).append(",");
            sb.append(csvCell(websites.toString())).append(",");
            sb.append(csvCell(addresses.toString())).append(",");
            sb.append(csvCell(events.toString())).append("\n");
        }
        return sb.toString();
    }

    private static String scrub(String s) {
        if (s == null) return "";
        // Strip our internal separators from field values so they can't break parsing
        return s.replace("|", " ").replace(";", ",");
    }

    private static String eventTypeName(ArchivedContact.Event ev) {
        // ContactsContract.CommonDataKinds.Event: 0=custom, 1=anniversary, 2=other, 3=birthday
        switch (ev.type) {
            case 3: return "birthday";
            case 1: return "anniversary";
            case 2: return "other";
            case 0: return "custom:" + (ev.label != null ? scrub(ev.label) : "");
            default: return "other";
        }
    }

    public static String writeCsv(Context ctx, String content) throws IOException {
        String filename = "contacts_backup_" +
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".csv";
        File file = new File(ctx.getFilesDir(), filename);
        FileOutputStream fos = new FileOutputStream(file);
        try {
            fos.write(content.getBytes("UTF-8"));
        } finally {
            fos.close();
        }
        return filename;
    }

    public static Intent buildShareIntent(String filename) {
        Uri uri = Uri.parse("content://" + CsvFileProvider.AUTHORITY + "/" + filename);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, "MyContacts Backup");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(intent, "Share backup via");
    }

    static String csvCell(String val) {
        if (val == null || val.isEmpty()) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
