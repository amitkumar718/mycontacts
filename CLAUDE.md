# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build — two supported paths

The project supports two independent build pipelines against the same source. Changes must keep both working.

### Termux (hand-rolled pipeline, no Gradle)

```sh
sh build-local.sh                        # standard build
sh build-local.sh 2>&1 | tee build.log  # capture log for diagnosis
```

Pipeline: `aapt2 compile` → `aapt2 link` → `javac -source 8 -target 8` → `d8` → `aapt add` (dex) → `apksigner`. Output APK: `build/local/mycontacts-debug.apk`.

**Never run `sh build-local.sh` via a tool call.** The user runs it manually in Termux and pastes errors back or shares the log.

### WSL2 (Gradle, replicates the mynutricheck pattern)

```sh
./build-wsl.sh                     # build only; writes build.log
./build-wsl.sh --stop              # kill Gradle daemon first
./build-wsl.sh --stacktrace        # verbose Gradle output
./build-run.sh                     # build + start AVD + install + launch
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`. Scripts tee all output to `build.log` / `build-run.log` in the project root — Claude reads these for diagnosis after the user runs.

Gradle config: AGP 8.10, compileSdk 34, buildToolsVersion 36.1.0, minSdk 21, Java 11, no dependencies. See `app/build.gradle`.

### Install + logcat (either build)

```sh
adb install -r <path-to-apk>
adb shell pm grant com.ldsa.mycontacts android.permission.READ_CONTACTS
adb shell pm grant com.ldsa.mycontacts android.permission.WRITE_CONTACTS
adb shell pm grant com.ldsa.mycontacts android.permission.GET_ACCOUNTS
adb logcat -s mycontacts.*
```

## Dual-build invariants

Changes must keep **both** builds working:

- **D8 / Termux Java constraints apply to all code** (see below). AGP wouldn't force them, but Termux's d8 would crash. Any new Java must follow the static-inner-class pattern.
- **`AndroidManifest.xml` must keep `package="com.ldsa.mycontacts"`**. Termux's bare aapt2 reads the package from the manifest; without it, `aapt2 link` fails. Gradle AGP 8 accepts both `package=` in manifest and `namespace` in build.gradle as long as they match (deprecation warning only).
- **`<uses-sdk>` was removed from the manifest for AGP 8.** Termux's `build-local.sh` injects min/target SDK via `aapt2 link` CLI flags, so this is safe for both.

## D8 / Termux Java Constraints

These apply to **all** Java code in this project — D8 on Termux crashes silently otherwise:

- **No anonymous classes** — use named `static` inner classes with an explicit outer reference
- **No lambdas** — same crash pattern; use `static` classes implementing the interface
- **No non-static inner classes** — always `static`, always pass `OuterClass outer` via constructor
- **No covariant return overrides** — override `Object getItem(int pos)` and cast at call site; a `SubType` override makes javac emit a synthetic bridge method with a null param that crashes D8
- **Package-private static final arrays** — `private static final int[]` on an outer class forces a synthetic accessor when an inner class reads it; declare `static final int[]` (no `private`) instead
- **`org.json` only** — no external JSON libs; it's built into the Android platform
- **No AAR dependencies** — no AndroidX, no Material Components; use `android.app.Activity` and `@android:style/Theme.Material.Light.DarkActionBar`
- **Compile target: `-source 8 -target 8`** on the Termux path. Gradle path uses Java 11 but the same source compiles cleanly against both because no Java 9+ features are used.

Static inner class pattern:
```java
// All event listeners / callbacks / threads follow this shape
static class MyListener implements View.OnClickListener {
    private final OuterActivity mOuter;
    MyListener(OuterActivity outer) { mOuter = outer; }
    public void onClick(View v) { mOuter.doSomething(); }
}
```

Background thread pattern (no lambdas, no anonymous Runnables):
```java
static class WorkThread extends Thread {
    private final Handler mHandler;
    WorkThread(Handler h) { mHandler = h; }
    public void run() { /* work */ mHandler.post(new ResultRunnable(result)); }
}
static class ResultRunnable implements Runnable {
    private final String mResult;
    ResultRunnable(String r) { mResult = r; }
    public void run() { /* update UI on main thread */ }
}
```

## Architecture

```
app/src/main/java/com/ldsa/mycontacts/
  db/
    ArchivedContact.java      — data model (phones/emails/labels stored as JSON strings)
    ContactDatabase.java      — SQLiteOpenHelper singleton; current schema version 2
    LabelInfo.java            — lightweight label+count holder for LabelsActivity
  contacts/
    ContactsHelper.java       — ContactsContract read/write (archive, restore, delete, group memberships)
    BackupHelper.java         — builds CSV string from archived contacts; writes to internal storage
    ExportHelper.java         — uploads CSV to Google Drive as a Spreadsheet via OAuth2 + REST
    DropboxHelper.java        — Dropbox API client: upload (mode:add/update) + rev-based sync
                                (get_metadata + conditional download); logs to tag mycontacts.dropbox
    ImportHelper.java         — CSV parser (state-machine); importFromUri inserts to DB; parseContacts
                                / parseCsvRows exposed as public statics for Dropbox sync reuse
    CsvFileProvider.java      — minimal ContentProvider replacing FileProvider (no AndroidX)
  ui/
    MainActivity.java         — archived list: search, A-Z/label view toggle, long-press multi-select
    ArchivedContactsAdapter.java — ListView adapter; two modes: MODE_ALPHA (flat) / MODE_LABEL (grouped)
    DeviceContactsActivity.java  — browse live device contacts, tap to archive+delete
    DeviceContactsAdapter.java
    ContactDetailActivity.java   — read-only detail view with restore/delete actions
    LabelsActivity.java          — list all labels with contact counts; long-press to rename/delete
    LabelsAdapter.java
    LabelContactsActivity.java   — contacts filtered by a single label
    SyncActivity.java            — Sync Check: two tabs (Duplicates / Archive Only) to reconcile
                                   archive vs device contacts; selection via blue row background
    DropboxSyncActivity.java     — Dropbox Sync: two tabs (Upstream Only / Local Only) to reconcile
                                   archive vs the canonical CSV in Dropbox; same selection model
```

**Data flow for archive:** `DeviceContactsActivity` → `ContactsHelper.readFullContact()` (reads all data rows including group memberships → labels) → `ContactDatabase.insert()` → `ContactsHelper.deleteDeviceContact()`.

**Data flow for restore:** `ContactDetailActivity`, bulk action in `MainActivity`, or `SyncActivity` "Archive Only" tab → `ContactsHelper.restoreContact()` (batch `ContentProviderOperation` insert, then group membership rows) → `ContactDatabase.delete()`.

**Sync matching** (`SyncActivity` and `DropboxSyncActivity`): same matching semantics in both — normalized display name OR normalized phone number (last 10 digits, strips country-code prefix differences). The two activities differ only in what they compare against (device contacts vs the Dropbox CSV).

**Labels** are stored in `ArchivedContact.labelsJson` as a JSON array of strings (`["Work","Family"]`). `ContactDatabase.getByLabel()` uses a `LIKE "%\"label\"%"` query. `ContactDatabase.getAllLabelCounts()` iterates all contacts in memory to build the count map.

**Selection mode** in `MainActivity`, `SyncActivity`, `DropboxSyncActivity`: row background changes to light blue (`0xFFBBDEFB`) for selected rows — no checkboxes anywhere.

**Google Drive export** (`ExportHelper`): gets an OAuth token via `AccountManager.getAuthToken`, uploads a multipart POST with `mimeType: application/vnd.google-apps.spreadsheet` so Drive auto-converts the CSV to a Google Sheet.

**Dropbox sync** (`DropboxHelper` + `DropboxSyncActivity`): follows Dropbox's canonical-file + rev-based sync pattern.
- Single file at `/contacts.csv` in the App Folder scope — resolves to `/Apps/<AppName>/contacts.csv` in the actual Dropbox. No timestamped backup pile.
- Last-seen `rev` stored in `SharedPreferences("dropbox", "last_rev")`.
- Sync does `POST /files/get_metadata` first (cheap) and skips the download entirely when the server rev matches the stored rev. Three outcomes: `onEmpty` (file not yet on Dropbox), `onUnchanged` (short-circuit), `onChanged` (download → diff → show both tabs).
- Push uses `POST /files/upload` with `mode:{"update":"<rev>"}` for optimistic concurrency — Dropbox returns 409 if a concurrent modification occurred; `onConflict` triggers an auto re-sync. First-ever push uses `mode:"add"`.
- Auth: pasted long-lived access token stored in `SharedPreferences("dropbox", "access_token")`. Token must have `files.content.{read,write}` + `files.metadata.read` scopes. Dropbox returns HTTP 400 (not 401) when a token lacks a scope, so 400 responses containing `"missing_scope"` or `"required scope"` are coerced to the auth-failed path to re-prompt the user. On real auth failure, both token and rev are cleared.
- All HTTP activity logs to logcat tag `mycontacts.dropbox` with request args, response code, and error bodies — grep this tag for post-run diagnosis.

## SQLite schema

```sql
CREATE TABLE contacts (
  _id           INTEGER PRIMARY KEY AUTOINCREMENT,
  display_name  TEXT NOT NULL,
  phones_json   TEXT,   -- [{number, type}, ...]
  emails_json   TEXT,   -- [{address, type}, ...]
  organization  TEXT,
  job_title     TEXT,
  notes         TEXT,
  labels_json   TEXT,   -- ["label1", "label2"]  (added in v2 via ALTER TABLE)
  archived_at   INTEGER NOT NULL  -- epoch ms
);
```

Schema upgrades use `ALTER TABLE ADD COLUMN` — never drop and recreate.
