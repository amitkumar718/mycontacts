package com.ldsa.mycontacts.contacts;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;

import com.ldsa.mycontacts.db.ArchivedContact;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ContactsHelper {

    public static class DeviceContact {
        public long contactId;
        public String displayName;
        public String primaryPhone;
        public String primaryEmail;
    }

    public static List<DeviceContact> loadDeviceContacts(Context ctx) {
        List<DeviceContact> list = new ArrayList<DeviceContact>();
        ContentResolver cr = ctx.getContentResolver();
        Cursor c = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            new String[]{
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            },
            null, null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " COLLATE NOCASE ASC"
        );
        if (c == null) return list;
        try {
            int idIdx   = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID);
            int nameIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY);
            while (c.moveToNext()) {
                DeviceContact dc = new DeviceContact();
                dc.contactId   = c.getLong(idIdx);
                dc.displayName = c.getString(nameIdx);
                if (dc.displayName == null) dc.displayName = "";
                list.add(dc);
            }
        } finally {
            c.close();
        }
        for (DeviceContact dc : list) {
            dc.primaryPhone = getPrimaryPhone(cr, dc.contactId);
            dc.primaryEmail = getPrimaryEmail(cr, dc.contactId);
        }
        return list;
    }

    private static String getPrimaryPhone(ContentResolver cr, long contactId) {
        Cursor c = cr.query(Phone.CONTENT_URI, new String[]{Phone.NUMBER},
            Phone.CONTACT_ID + "=?", new String[]{String.valueOf(contactId)}, null);
        if (c == null) return "";
        try { if (c.moveToFirst()) return c.getString(0); } finally { c.close(); }
        return "";
    }

    private static String getPrimaryEmail(ContentResolver cr, long contactId) {
        Cursor c = cr.query(Email.CONTENT_URI, new String[]{Email.ADDRESS},
            Email.CONTACT_ID + "=?", new String[]{String.valueOf(contactId)}, null);
        if (c == null) return "";
        try { if (c.moveToFirst()) return c.getString(0); } finally { c.close(); }
        return "";
    }

    public static ArchivedContact readFullContact(Context ctx, long contactId) {
        ContentResolver cr = ctx.getContentResolver();
        ArchivedContact ac = new ArchivedContact();
        ac.archivedAt = System.currentTimeMillis();

        // Display name
        Cursor c = cr.query(ContactsContract.Contacts.CONTENT_URI,
            new String[]{ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
            ContactsContract.Contacts._ID + "=?", new String[]{String.valueOf(contactId)}, null);
        if (c != null) {
            try { if (c.moveToFirst()) ac.displayName = c.getString(0); } finally { c.close(); }
        }
        if (ac.displayName == null) ac.displayName = "";

        // Phones
        JSONArray phones = new JSONArray();
        Cursor pc = cr.query(Phone.CONTENT_URI, new String[]{Phone.NUMBER, Phone.TYPE},
            Phone.CONTACT_ID + "=?", new String[]{String.valueOf(contactId)}, null);
        if (pc != null) {
            try {
                while (pc.moveToNext()) {
                    try {
                        JSONObject o = new JSONObject();
                        o.put("number", pc.getString(0));
                        o.put("type",   pc.getInt(1));
                        phones.put(o);
                    } catch (Exception e) { /* skip */ }
                }
            } finally { pc.close(); }
        }
        ac.phonesJson = phones.toString();

        // Emails
        JSONArray emails = new JSONArray();
        Cursor ec = cr.query(Email.CONTENT_URI, new String[]{Email.ADDRESS, Email.TYPE},
            Email.CONTACT_ID + "=?", new String[]{String.valueOf(contactId)}, null);
        if (ec != null) {
            try {
                while (ec.moveToNext()) {
                    try {
                        JSONObject o = new JSONObject();
                        o.put("address", ec.getString(0));
                        o.put("type",    ec.getInt(1));
                        emails.put(o);
                    } catch (Exception e) { /* skip */ }
                }
            } finally { ec.close(); }
        }
        ac.emailsJson = emails.toString();

        // Organization + job title
        Cursor oc = cr.query(Data.CONTENT_URI,
            new String[]{Organization.COMPANY, Organization.TITLE},
            Data.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
            new String[]{String.valueOf(contactId), Organization.CONTENT_ITEM_TYPE}, null);
        if (oc != null) {
            try {
                if (oc.moveToFirst()) {
                    ac.organization = oc.getString(0);
                    ac.jobTitle     = oc.getString(1);
                }
            } finally { oc.close(); }
        }

        // Notes
        Cursor nc = cr.query(Data.CONTENT_URI, new String[]{Note.NOTE},
            Data.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
            new String[]{String.valueOf(contactId), Note.CONTENT_ITEM_TYPE}, null);
        if (nc != null) {
            try { if (nc.moveToFirst()) ac.notes = nc.getString(0); } finally { nc.close(); }
        }

        // Group memberships → labels
        JSONArray labels = new JSONArray();
        Cursor gc = cr.query(Data.CONTENT_URI,
            new String[]{ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID},
            Data.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
            new String[]{String.valueOf(contactId),
                ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE},
            null);
        if (gc != null) {
            try {
                while (gc.moveToNext()) {
                    long groupId = gc.getLong(0);
                    String title = getGroupTitle(cr, groupId);
                    if (title != null && !title.isEmpty()) labels.put(title);
                }
            } finally { gc.close(); }
        }
        ac.labelsJson = labels.toString();

        return ac;
    }

    private static String getGroupTitle(ContentResolver cr, long groupId) {
        Cursor c = cr.query(ContactsContract.Groups.CONTENT_URI,
            new String[]{ContactsContract.Groups.TITLE},
            ContactsContract.Groups._ID + "=?", new String[]{String.valueOf(groupId)}, null);
        if (c == null) return null;
        try { if (c.moveToFirst()) return c.getString(0); } finally { c.close(); }
        return null;
    }

    public static void deleteDeviceContact(Context ctx, long contactId) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        Cursor rc = cr.query(RawContacts.CONTENT_URI, new String[]{RawContacts._ID},
            RawContacts.CONTACT_ID + "=?", new String[]{String.valueOf(contactId)}, null);
        if (rc == null) return;
        List<Long> rawIds = new ArrayList<Long>();
        try { while (rc.moveToNext()) rawIds.add(rc.getLong(0)); } finally { rc.close(); }

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        for (long rawId : rawIds) {
            Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId)
                .buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
            ops.add(ContentProviderOperation.newDelete(uri).build());
        }
        if (!ops.isEmpty()) cr.applyBatch(ContactsContract.AUTHORITY, ops);
    }

    public static void restoreContact(Context ctx, ArchivedContact ac) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

        android.accounts.Account[] googleAccounts =
            android.accounts.AccountManager.get(ctx).getAccountsByType("com.google");
        String accountType = googleAccounts.length > 0 ? "com.google" : null;
        String accountName = googleAccounts.length > 0 ? googleAccounts[0].name : null;

        ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, accountType)
            .withValue(RawContacts.ACCOUNT_NAME, accountName)
            .build());

        ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
            .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, ac.displayName)
            .build());

        for (ArchivedContact.Phone p : ac.getPhones()) {
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, p.number)
                .withValue(Phone.TYPE, p.type)
                .build());
        }

        for (ArchivedContact.Email e : ac.getEmails()) {
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                .withValue(Email.ADDRESS, e.address)
                .withValue(Email.TYPE, e.type)
                .build());
        }

        if (ac.organization != null && !ac.organization.isEmpty()) {
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE)
                .withValue(Organization.COMPANY, ac.organization)
                .withValue(Organization.TITLE, ac.jobTitle != null ? ac.jobTitle : "")
                .build());
        }

        if (ac.notes != null && !ac.notes.isEmpty()) {
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE)
                .withValue(Note.NOTE, ac.notes)
                .build());
        }

        cr.applyBatch(ContactsContract.AUTHORITY, ops);

        // Restore labels as group memberships (must happen after batch so raw contact exists)
        for (String label : ac.getLabels()) {
            long groupId = getOrCreateGroup(cr, label, accountType, accountName);
            if (groupId > 0) {
                Cursor rawCur = cr.query(RawContacts.CONTENT_URI,
                    new String[]{RawContacts._ID},
                    RawContacts.ACCOUNT_TYPE + "=? AND " + RawContacts.ACCOUNT_NAME + "=?",
                    new String[]{accountType != null ? accountType : "",
                        accountName != null ? accountName : ""},
                    RawContacts._ID + " DESC");
                if (rawCur != null) {
                    try {
                        if (rawCur.moveToFirst()) {
                            long rawId = rawCur.getLong(0);
                            android.content.ContentValues cv = new android.content.ContentValues();
                            cv.put(Data.RAW_CONTACT_ID, rawId);
                            cv.put(Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE);
                            cv.put(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId);
                            cr.insert(Data.CONTENT_URI, cv);
                        }
                    } finally { rawCur.close(); }
                }
            }
        }
    }

    private static long getOrCreateGroup(ContentResolver cr, String title,
            String accountType, String accountName) {
        String sel = ContactsContract.Groups.TITLE + "=?";
        String[] args = new String[]{title};
        if (accountType != null) {
            sel += " AND " + ContactsContract.Groups.ACCOUNT_TYPE + "=?";
            args = new String[]{title, accountType};
        }
        Cursor c = cr.query(ContactsContract.Groups.CONTENT_URI,
            new String[]{ContactsContract.Groups._ID}, sel, args, null);
        if (c != null) {
            try { if (c.moveToFirst()) return c.getLong(0); } finally { c.close(); }
        }
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put(ContactsContract.Groups.TITLE, title);
        cv.put(ContactsContract.Groups.ACCOUNT_TYPE, accountType);
        cv.put(ContactsContract.Groups.ACCOUNT_NAME, accountName);
        android.net.Uri uri = cr.insert(ContactsContract.Groups.CONTENT_URI, cv);
        if (uri == null) return -1;
        return ContentUris.parseId(uri);
    }
}
