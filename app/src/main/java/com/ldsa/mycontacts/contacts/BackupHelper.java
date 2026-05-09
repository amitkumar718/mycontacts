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
        sb.append("Name,Phones,Emails,Organization,Job Title,Notes,Labels,Archived On\n");
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
            sb.append(csvCell(c.displayName)).append(",");
            sb.append(csvCell(phones.toString())).append(",");
            sb.append(csvCell(emails.toString())).append(",");
            sb.append(csvCell(c.organization)).append(",");
            sb.append(csvCell(c.jobTitle)).append(",");
            sb.append(csvCell(c.notes)).append(",");
            sb.append(csvCell(labels.toString())).append(",");
            sb.append(csvCell(sdf.format(new Date(c.archivedAt)))).append("\n");
        }
        return sb.toString();
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
