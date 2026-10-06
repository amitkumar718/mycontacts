package com.ldsa.mycontacts.contacts;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
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

        // Structured name — first/last/prefix/suffix
        Cursor nameCur = cr.query(Data.CONTENT_URI,
            new String[]{StructuredName.GIVEN_NAME, StructuredName.FAMILY_NAME,
                         StructuredName.PREFIX, StructuredName.SUFFIX},
            Data.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
            new String[]{String.valueOf(contactId), StructuredName.CONTENT_ITEM_TYPE}, null);
        if (nameCur != null) {
            try {
                if (nameCur.moveToFirst()) {
                    ac.firstName  = nameCur.getString(0);
                    ac.lastName   = nameCur.getString(1);
                    ac.namePrefix = nameCur.getString(2);
                    ac.nameSuffix = nameCur.getString(3);
                }
            } finally { nameCur.close(); }
        }

        // Nickname
        Cursor nickCur = cr.query(Data.CONTENT_URI, new String[]{Nickname.NAME},
            Data.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
            new String[]{String.valueOf(contactId), Nickname.CONTENT_ITEM_TYPE}, null);
        if (nickCur != null) {
            try { if (nickCur.moveToFirst()) ac.nickname = nickCur.getString(0); }
            finally { nickCur.close(); }
        }

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

        // Websites
        JSONArray websites = new JSONArray();
        Cursor wc = cr.query(Data.CONTENT_URI, new String[]{Website.URL, Website.TYPE},
            Data.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
            new String[]{String.valueOf(contactId), Website.CONTENT_ITEM_TYPE}, null);
        if (wc != null) {
            try {
                while (wc.moveToNext()) {
                    try {
                        JSONObject o = new JSONObject();
                        o.put("url",  wc.getString(0));
                        o.put("type", wc.getInt(1));
                        websites.put(o);
                    } catch (Exception e) { /* skip */ }
                }
            } finally { wc.close(); }
        }
        ac.websitesJson = websites.toString();

        // Postal addresses
        JSONArray addresses = new JSONArray();
        Cursor addrCur = cr.query(Data.CONTENT_URI,
            new String[]{StructuredPostal.STREET, StructuredPostal.CITY,
                         StructuredPostal.REGION, StructuredPostal.POSTCODE,
                         StructuredPostal.COUNTRY, StructuredPostal.TYPE},
            Data.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
            new String[]{String.valueOf(contactId), StructuredPostal.CONTENT_ITEM_TYPE}, null);
        if (addrCur != null) {
            try {
                while (addrCur.moveToNext()) {
                    try {
                        JSONObject o = new JSONObject();
                        o.put("street",   addrCur.getString(0) != null ? addrCur.getString(0) : "");
                        o.put("city",     addrCur.getString(1) != null ? addrCur.getString(1) : "");
                        o.put("region",   addrCur.getString(2) != null ? addrCur.getString(2) : "");
                        o.put("postcode", addrCur.getString(3) != null ? addrCur.getString(3) : "");
                        o.put("country",  addrCur.getString(4) != null ? addrCur.getString(4) : "");
                        o.put("type",     addrCur.getInt(5));
                        addresses.put(o);
                    } catch (Exception e) { /* skip */ }
                }
            } finally { addrCur.close(); }
        }
        ac.addressesJson = addresses.toString();

        // Events — birthdays, anniversaries, other
        JSONArray events = new JSONArray();
        Cursor evCur = cr.query(Data.CONTENT_URI,
            new String[]{Event.START_DATE, Event.TYPE, Event.LABEL},
            Data.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
            new String[]{String.valueOf(contactId), Event.CONTENT_ITEM_TYPE}, null);
        if (evCur != null) {
            try {
                while (evCur.moveToNext()) {
                    try {
                        JSONObject o = new JSONObject();
                        o.put("date",  evCur.getString(0) != null ? evCur.getString(0) : "");
                        o.put("type",  evCur.getInt(1));
                        o.put("label", evCur.getString(2) != null ? evCur.getString(2) : "");
                        events.put(o);
                    } catch (Exception e) { /* skip */ }
                }
            } finally { evCur.close(); }
        }
        ac.eventsJson = events.toString();

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

        // Structured name — use first/last/prefix/suffix if we have them, else fall back to DISPLAY_NAME
        ContentProviderOperation.Builder nameOp = ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
            .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, ac.displayName);
        if (ac.firstName  != null && !ac.firstName.isEmpty())  nameOp.withValue(StructuredName.GIVEN_NAME,  ac.firstName);
        if (ac.lastName   != null && !ac.lastName.isEmpty())   nameOp.withValue(StructuredName.FAMILY_NAME, ac.lastName);
        if (ac.namePrefix != null && !ac.namePrefix.isEmpty()) nameOp.withValue(StructuredName.PREFIX,      ac.namePrefix);
        if (ac.nameSuffix != null && !ac.nameSuffix.isEmpty()) nameOp.withValue(StructuredName.SUFFIX,      ac.nameSuffix);
        ops.add(nameOp.build());

        // Nickname
        if (ac.nickname != null && !ac.nickname.isEmpty()) {
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE)
                .withValue(Nickname.NAME, ac.nickname)
                .build());
        }

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

        for (ArchivedContact.Website w : ac.getWebsites()) {
            if (w.url == null || w.url.isEmpty()) continue;
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Website.CONTENT_ITEM_TYPE)
                .withValue(Website.URL, w.url)
                .withValue(Website.TYPE, w.type)
                .build());
        }

        for (ArchivedContact.Address a : ac.getAddresses()) {
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
                .withValue(StructuredPostal.STREET,   a.street)
                .withValue(StructuredPostal.CITY,     a.city)
                .withValue(StructuredPostal.REGION,   a.region)
                .withValue(StructuredPostal.POSTCODE, a.postcode)
                .withValue(StructuredPostal.COUNTRY,  a.country)
                .withValue(StructuredPostal.TYPE,     a.type)
                .build());
        }

        for (ArchivedContact.Event ev : ac.getEvents()) {
            if (ev.date == null || ev.date.isEmpty()) continue;
            ContentProviderOperation.Builder evOp = ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE)
                .withValue(Event.START_DATE, ev.date)
                .withValue(Event.TYPE, ev.type);
            if (ev.label != null && !ev.label.isEmpty()) evOp.withValue(Event.LABEL, ev.label);
            ops.add(evOp.build());
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
