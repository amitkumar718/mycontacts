package com.ldsa.mycontacts.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class ContactDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "archived_contacts.db";
    private static final int DB_VERSION = 3;

    private static final String TABLE = "contacts";
    private static final String COL_ID = "_id";
    private static final String COL_NAME = "display_name";
    private static final String COL_PHONES = "phones_json";
    private static final String COL_EMAILS = "emails_json";
    private static final String COL_ORG = "organization";
    private static final String COL_TITLE = "job_title";
    private static final String COL_NOTES = "notes";
    private static final String COL_LABELS = "labels_json";
    private static final String COL_ARCHIVED_AT = "archived_at";
    // v3 columns
    private static final String COL_FIRST_NAME = "first_name";
    private static final String COL_LAST_NAME = "last_name";
    private static final String COL_NAME_PREFIX = "name_prefix";
    private static final String COL_NAME_SUFFIX = "name_suffix";
    private static final String COL_NICKNAME = "nickname";
    private static final String COL_WEBSITES = "websites_json";
    private static final String COL_ADDRESSES = "addresses_json";
    private static final String COL_EVENTS = "events_json";

    private static ContactDatabase sInstance;

    public static ContactDatabase getInstance(Context ctx) {
        if (sInstance == null) {
            sInstance = new ContactDatabase(ctx.getApplicationContext());
        }
        return sInstance;
    }

    private ContactDatabase(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE " + TABLE + " (" +
            COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            COL_NAME + " TEXT NOT NULL," +
            COL_PHONES + " TEXT," +
            COL_EMAILS + " TEXT," +
            COL_ORG + " TEXT," +
            COL_TITLE + " TEXT," +
            COL_NOTES + " TEXT," +
            COL_LABELS + " TEXT," +
            COL_ARCHIVED_AT + " INTEGER NOT NULL," +
            COL_FIRST_NAME + " TEXT," +
            COL_LAST_NAME + " TEXT," +
            COL_NAME_PREFIX + " TEXT," +
            COL_NAME_SUFFIX + " TEXT," +
            COL_NICKNAME + " TEXT," +
            COL_WEBSITES + " TEXT," +
            COL_ADDRESSES + " TEXT," +
            COL_EVENTS + " TEXT" +
            ")"
        );
        db.execSQL("CREATE INDEX idx_name ON " + TABLE + " (" + COL_NAME + " COLLATE NOCASE)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_LABELS + " TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_FIRST_NAME + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_LAST_NAME + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_NAME_PREFIX + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_NAME_SUFFIX + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_NICKNAME + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_WEBSITES + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_ADDRESSES + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_EVENTS + " TEXT");
        }
    }

    public long insert(ArchivedContact c) {
        return getWritableDatabase().insert(TABLE, null, toValues(c));
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public List<ArchivedContact> search(String query) {
        SQLiteDatabase db = getReadableDatabase();
        String sel = null;
        String[] args = null;
        if (query != null && !query.trim().isEmpty()) {
            sel = COL_NAME + " LIKE ? OR " + COL_PHONES + " LIKE ? OR " + COL_EMAILS + " LIKE ?";
            String like = "%" + query.trim() + "%";
            args = new String[]{like, like, like};
        }
        Cursor c = db.query(TABLE, null, sel, args, null, null, COL_NAME + " COLLATE NOCASE ASC");
        return cursorToList(c);
    }

    public ArchivedContact getById(long id) {
        Cursor c = getReadableDatabase().query(TABLE, null,
            COL_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        List<ArchivedContact> list = cursorToList(c);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<ArchivedContact> getAll() {
        return search(null);
    }

    public List<ArchivedContact> getByLabel(String label) {
        String like = "%\"" + label.replace("\"", "") + "\"%";
        Cursor c = getReadableDatabase().query(TABLE, null,
            COL_LABELS + " LIKE ?", new String[]{like},
            null, null, COL_NAME + " COLLATE NOCASE ASC");
        return cursorToList(c);
    }

    public LinkedHashMap<String, Integer> getAllLabelCounts() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (ArchivedContact c : getAll()) {
            for (String label : c.getLabels()) {
                Integer cnt = counts.get(label);
                counts.put(label, cnt == null ? 1 : cnt + 1);
            }
        }
        return counts;
    }

    public void updateLabels(long id, String labelsJson) {
        ContentValues cv = new ContentValues();
        cv.put(COL_LABELS, labelsJson);
        getWritableDatabase().update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    private List<ArchivedContact> cursorToList(Cursor c) {
        List<ArchivedContact> list = new ArrayList<ArchivedContact>();
        try {
            while (c.moveToNext()) list.add(fromCursor(c));
        } finally {
            c.close();
        }
        return list;
    }

    private ArchivedContact fromCursor(Cursor c) {
        ArchivedContact a = new ArchivedContact();
        a.id            = c.getLong(c.getColumnIndexOrThrow(COL_ID));
        a.displayName   = c.getString(c.getColumnIndexOrThrow(COL_NAME));
        a.phonesJson    = c.getString(c.getColumnIndexOrThrow(COL_PHONES));
        a.emailsJson    = c.getString(c.getColumnIndexOrThrow(COL_EMAILS));
        a.organization  = c.getString(c.getColumnIndexOrThrow(COL_ORG));
        a.jobTitle      = c.getString(c.getColumnIndexOrThrow(COL_TITLE));
        a.notes         = c.getString(c.getColumnIndexOrThrow(COL_NOTES));
        a.labelsJson    = c.getString(c.getColumnIndexOrThrow(COL_LABELS));
        a.archivedAt    = c.getLong(c.getColumnIndexOrThrow(COL_ARCHIVED_AT));
        a.firstName     = c.getString(c.getColumnIndexOrThrow(COL_FIRST_NAME));
        a.lastName      = c.getString(c.getColumnIndexOrThrow(COL_LAST_NAME));
        a.namePrefix    = c.getString(c.getColumnIndexOrThrow(COL_NAME_PREFIX));
        a.nameSuffix    = c.getString(c.getColumnIndexOrThrow(COL_NAME_SUFFIX));
        a.nickname      = c.getString(c.getColumnIndexOrThrow(COL_NICKNAME));
        a.websitesJson  = c.getString(c.getColumnIndexOrThrow(COL_WEBSITES));
        a.addressesJson = c.getString(c.getColumnIndexOrThrow(COL_ADDRESSES));
        a.eventsJson    = c.getString(c.getColumnIndexOrThrow(COL_EVENTS));
        return a;
    }

    private ContentValues toValues(ArchivedContact c) {
        ContentValues cv = new ContentValues();
        cv.put(COL_NAME,         c.displayName);
        cv.put(COL_PHONES,       c.phonesJson);
        cv.put(COL_EMAILS,       c.emailsJson);
        cv.put(COL_ORG,          c.organization);
        cv.put(COL_TITLE,        c.jobTitle);
        cv.put(COL_NOTES,        c.notes);
        cv.put(COL_LABELS,       c.labelsJson);
        cv.put(COL_ARCHIVED_AT,  c.archivedAt);
        cv.put(COL_FIRST_NAME,   c.firstName);
        cv.put(COL_LAST_NAME,    c.lastName);
        cv.put(COL_NAME_PREFIX,  c.namePrefix);
        cv.put(COL_NAME_SUFFIX,  c.nameSuffix);
        cv.put(COL_NICKNAME,     c.nickname);
        cv.put(COL_WEBSITES,     c.websitesJson);
        cv.put(COL_ADDRESSES,    c.addressesJson);
        cv.put(COL_EVENTS,       c.eventsJson);
        return cv;
    }
}
