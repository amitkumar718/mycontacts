package com.ldsa.mycontacts.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.mycontacts.R;
import com.ldsa.mycontacts.contacts.BackupHelper;
import com.ldsa.mycontacts.contacts.ContactsHelper;
import com.ldsa.mycontacts.contacts.DropboxHelper;
import com.ldsa.mycontacts.contacts.ExportHelper;
import com.ldsa.mycontacts.contacts.ImportHelper;
import com.ldsa.mycontacts.db.ArchivedContact;
import com.ldsa.mycontacts.db.ContactDatabase;

import android.net.Uri;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int REQ_CONTACTS_PERM = 1;
    private static final int REQ_IMPORT_FILE   = 2;
    private static final String PREF_VIEW_MODE  = "view_mode";
    private static final String PREF_DROPBOX_FILE  = DropboxHelper.PREF_FILE;
    private static final String KEY_DROPBOX_TOKEN  = DropboxHelper.KEY_TOKEN;
    private static final String KEY_DROPBOX_REV    = DropboxHelper.KEY_REV;
    static final String EXTRA_CONTACT_ID = "contact_id";

    private EditText mEtSearch;
    private ListView mListView;
    private TextView mTvEmpty;
    private TextView mTvCount;
    private TextView mTvSelectionCount;
    private Button mBtnToggleAlpha;
    private Button mBtnToggleLabel;
    private View mLayoutSearchBar;
    private View mLayoutSelectionBar;
    private ArchivedContactsAdapter mAdapter;
    private ContactDatabase mDb;
    private List<ArchivedContact> mContacts;
    boolean mSelectionMode = false;
    int mViewMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDb               = ContactDatabase.getInstance(this);
        mEtSearch         = (EditText)  findViewById(R.id.etSearch);
        mListView         = (ListView)  findViewById(R.id.listArchived);
        mTvEmpty          = (TextView)  findViewById(R.id.tvEmpty);
        mTvCount          = (TextView)  findViewById(R.id.tvCount);
        mTvSelectionCount = (TextView)  findViewById(R.id.tvSelectionCount);
        mBtnToggleAlpha   = (Button)    findViewById(R.id.btnToggleAlpha);
        mBtnToggleLabel   = (Button)    findViewById(R.id.btnToggleLabel);
        mLayoutSearchBar  = findViewById(R.id.layoutSearchBar);
        mLayoutSelectionBar = findViewById(R.id.layoutSelectionBar);
        Button fabArchive   = (Button)  findViewById(R.id.fabArchive);
        Button btnExit      = (Button)  findViewById(R.id.btnExitSelection);
        Button btnActions   = (Button)  findViewById(R.id.btnSelectionActions);

        mViewMode = getPreferences(MODE_PRIVATE).getInt(PREF_VIEW_MODE, ArchivedContactsAdapter.MODE_ALPHA);
        mContacts = mDb.getAll();
        mAdapter  = new ArchivedContactsAdapter(this, mContacts, mViewMode);
        mListView.setAdapter(mAdapter);

        updateEmptyState();
        updateToggleButtons();

        mEtSearch.addTextChangedListener(new SearchWatcher(this));
        mListView.setOnItemClickListener(new ItemClickListener(this));
        mListView.setOnItemLongClickListener(new ItemLongClickListener(this));
        fabArchive.setOnClickListener(new ArchiveNewClickListener(this));
        mBtnToggleAlpha.setOnClickListener(new ToggleModeListener(this, ArchivedContactsAdapter.MODE_ALPHA));
        mBtnToggleLabel.setOnClickListener(new ToggleModeListener(this, ArchivedContactsAdapter.MODE_LABEL));
        btnExit.setOnClickListener(new SelectionExitClickListener(this));
        btnActions.setOnClickListener(new SelectionActionsClickListener(this));

        checkContactsPermission();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_sync) {
            startActivity(new Intent(this, SyncActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_labels) {
            startActivity(new Intent(this, LabelsActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_share_backup) {
            startBackup();
            return true;
        }
        if (item.getItemId() == R.id.action_export) {
            startExport();
            return true;
        }
        if (item.getItemId() == R.id.action_dropbox) {
            startDropboxUpload();
            return true;
        }
        if (item.getItemId() == R.id.action_dropbox_sync) {
            startActivity(new Intent(this, DropboxSyncActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_import) {
            startImport();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mSelectionMode) exitSelectionMode();
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload(mEtSearch.getText().toString());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_IMPORT_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                Toast.makeText(this, R.string.importing, Toast.LENGTH_SHORT).show();
                ImportHelper.importFromUri(this, uri, new ImportCallbackImpl(this));
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // --- Normal mode ---

    void reload(String query) {
        mContacts = mDb.search(query);
        mAdapter.setItems(mContacts);
        updateEmptyState();
    }

    void setViewMode(int mode) {
        mViewMode = mode;
        getPreferences(MODE_PRIVATE).edit().putInt(PREF_VIEW_MODE, mode).apply();
        mAdapter.setMode(mode);
        reload(mEtSearch.getText().toString());
        updateToggleButtons();
    }

    private void updateToggleButtons() {
        if (mViewMode == ArchivedContactsAdapter.MODE_ALPHA) {
            mBtnToggleAlpha.setBackgroundResource(R.drawable.bg_toggle_active);
            mBtnToggleAlpha.setTextColor(0xFFFFFFFF);
            mBtnToggleLabel.setBackgroundResource(R.drawable.bg_toggle_inactive);
            mBtnToggleLabel.setTextColor(0xFF1565C0);
        } else {
            mBtnToggleLabel.setBackgroundResource(R.drawable.bg_toggle_active);
            mBtnToggleLabel.setTextColor(0xFFFFFFFF);
            mBtnToggleAlpha.setBackgroundResource(R.drawable.bg_toggle_inactive);
            mBtnToggleAlpha.setTextColor(0xFF1565C0);
        }
    }

    private void updateEmptyState() {
        int count = mContacts.size();
        mTvCount.setText(count + " archived");
        mListView.setVisibility(count == 0 ? View.GONE   : View.VISIBLE);
        mTvEmpty .setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    void openDetail(long id) {
        Intent i = new Intent(this, ContactDetailActivity.class);
        i.putExtra(EXTRA_CONTACT_ID, id);
        startActivity(i);
    }

    void openArchiveScreen() {
        startActivity(new Intent(this, DeviceContactsActivity.class));
    }

    // --- Selection mode ---

    void enterSelectionMode(long firstId) {
        mSelectionMode = true;
        mAdapter.setSelectionMode(true);
        mAdapter.toggleSelected(firstId);
        mLayoutSearchBar.setVisibility(View.GONE);
        mLayoutSelectionBar.setVisibility(View.VISIBLE);
        updateSelectionCount();
        mListView.setOnItemClickListener(new SelectionItemClickListener(this));
    }

    void exitSelectionMode() {
        mSelectionMode = false;
        mAdapter.setSelectionMode(false);
        mLayoutSelectionBar.setVisibility(View.GONE);
        mLayoutSearchBar.setVisibility(View.VISIBLE);
        mListView.setOnItemClickListener(new ItemClickListener(this));
    }

    void toggleSelection(long id) {
        mAdapter.toggleSelected(id);
        updateSelectionCount();
        if (mAdapter.getSelectedCount() == 0) exitSelectionMode();
    }

    private void updateSelectionCount() {
        mTvSelectionCount.setText(mAdapter.getSelectedCount() + " " + getString(R.string.selected));
    }

    void showActionMenu() {
        if (mAdapter.getSelectedCount() == 0) {
            Toast.makeText(this, R.string.no_contacts_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        long[] ids = toArray(mAdapter.getSelectedIds());
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(mAdapter.getSelectedCount() + " " + getString(R.string.selected));
        b.setItems(new String[]{
            getString(R.string.add_to_label),
            getString(R.string.btn_restore),
            getString(R.string.btn_delete)
        }, new ActionMenuListener(this, ids));
        b.show();
    }

    void showLabelPickerDialog(long[] ids) {
        LinkedHashMap<String, Integer> counts = mDb.getAllLabelCounts();
        List<String> labelList = new ArrayList<String>(counts.keySet());
        String[] labels = labelList.toArray(new String[0]);
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.pick_label);
        if (labels.length > 0) b.setItems(labels, new LabelPickListener(this, labels, ids));
        b.setNeutralButton(R.string.new_label, new NewLabelForSelectionListener(this, ids));
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    void applyLabelToSelected(String label, long[] ids) {
        int count = 0;
        for (long id : ids) {
            ArchivedContact c = mDb.getById(id);
            if (c == null) continue;
            List<String> labels = c.getLabels();
            if (!labels.contains(label)) {
                labels.add(label);
                try {
                    JSONArray arr = new JSONArray();
                    for (String l : labels) arr.put(l);
                    mDb.updateLabels(c.id, arr.toString());
                    count++;
                } catch (Exception e) { /* ignore */ }
            }
        }
        exitSelectionMode();
        reload(mEtSearch.getText().toString());
        if (count > 0) {
            Toast.makeText(this,
                count + " " + getString(R.string.contacts_labeled) + " \"" + label + "\"",
                Toast.LENGTH_SHORT).show();
        }
    }

    void showNewLabelForSelectionDialog(long[] ids) {
        EditText et = new EditText(this);
        et.setHint(R.string.label_name_hint);
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.new_label);
        b.setView(et);
        b.setPositiveButton(R.string.add, new NewLabelForSelectionConfirmListener(this, et, ids));
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    void confirmBulkRestore(long[] ids) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setMessage(ids.length + " " + getString(R.string.confirm_bulk_restore));
        b.setPositiveButton(R.string.yes, new BulkRestoreConfirmListener(this, ids));
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    void onBulkRestoreDone(int restored, int failed) {
        exitSelectionMode();
        reload(mEtSearch.getText().toString());
        String msg = restored + " " + getString(R.string.contacts_restored);
        if (failed > 0) msg += ", " + failed + " failed";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    void confirmBulkDelete(long[] ids) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setMessage(ids.length + " " + getString(R.string.confirm_bulk_delete));
        b.setPositiveButton(R.string.yes, new BulkDeleteConfirmListener(this, ids));
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    void doBulkDelete(long[] ids) {
        for (long id : ids) mDb.delete(id);
        exitSelectionMode();
        reload(mEtSearch.getText().toString());
        Toast.makeText(this,
            ids.length + " " + getString(R.string.contacts_deleted),
            Toast.LENGTH_SHORT).show();
    }

    private static long[] toArray(Set<Long> set) {
        long[] arr = new long[set.size()];
        int i = 0;
        for (Long id : set) arr[i++] = id;
        return arr;
    }

    // --- Backup / Import ---

    void startBackup() {
        List<ArchivedContact> all = mDb.getAll();
        if (all.isEmpty()) {
            Toast.makeText(this, R.string.no_contacts_to_backup, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.preparing_backup, Toast.LENGTH_SHORT).show();
        new BackupThread(this, all, new Handler(Looper.getMainLooper())).start();
    }

    void onBackupReady(String filename) {
        startActivity(BackupHelper.buildShareIntent(filename));
    }

    void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT_FILE);
    }

    void onImportDone(int imported, int skipped) {
        reload(mEtSearch.getText().toString());
        String msg = imported + " " + getString(R.string.contacts_imported);
        if (skipped > 0) msg += ", " + skipped + " skipped";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // --- Permissions ---

    private void checkContactsPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    android.Manifest.permission.READ_CONTACTS,
                    android.Manifest.permission.WRITE_CONTACTS
                }, REQ_CONTACTS_PERM);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        if (req == REQ_CONTACTS_PERM) {
            if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    // --- Export ---

    void startExport() {
        List<ArchivedContact> all = mDb.getAll();
        if (all.isEmpty()) {
            Toast.makeText(this, R.string.no_contacts_to_backup, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.upload_to_drive, Toast.LENGTH_SHORT).show();
        ExportHelper.exportToDrive(this, all, new ExportCallbackImpl(this));
    }

    // --- Dropbox ---

    void startDropboxUpload() {
        List<ArchivedContact> all = mDb.getAll();
        if (all.isEmpty()) {
            Toast.makeText(this, R.string.no_contacts_to_backup, Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREF_DROPBOX_FILE, MODE_PRIVATE);
        String token = prefs.getString(KEY_DROPBOX_TOKEN, null);
        if (token == null || token.isEmpty()) {
            showDropboxTokenDialog();
        } else {
            doDropboxUpload(all, token);
        }
    }

    void showDropboxTokenDialog() {
        EditText et = new EditText(this);
        et.setHint(R.string.dropbox_token_hint);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.dropbox_token_title);
        b.setMessage(R.string.dropbox_token_message);
        b.setView(et);
        b.setPositiveButton(R.string.save, new DropboxTokenSaveListener(this, et));
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    void saveDropboxTokenAndUpload(String token) {
        getSharedPreferences(PREF_DROPBOX_FILE, MODE_PRIVATE)
            .edit().putString(KEY_DROPBOX_TOKEN, token).apply();
        doDropboxUpload(mDb.getAll(), token);
    }

    void doDropboxUpload(List<ArchivedContact> contacts, String token) {
        SharedPreferences prefs = getSharedPreferences(PREF_DROPBOX_FILE, MODE_PRIVATE);
        String rev = prefs.getString(KEY_DROPBOX_REV, null);
        Toast.makeText(this, R.string.uploading_to_dropbox, Toast.LENGTH_SHORT).show();
        DropboxHelper.uploadToDropbox(this, contacts, token, rev, new DropboxCallbackImpl(this));
    }

    void storeDropboxRev(String rev) {
        getSharedPreferences(PREF_DROPBOX_FILE, MODE_PRIVATE)
            .edit().putString(KEY_DROPBOX_REV, rev).apply();
    }

    void clearDropboxToken() {
        getSharedPreferences(PREF_DROPBOX_FILE, MODE_PRIVATE)
            .edit().remove(KEY_DROPBOX_TOKEN).remove(KEY_DROPBOX_REV).apply();
    }

    // =========================================================
    // Static listener classes (D8: no anonymous / non-static)
    // =========================================================

    static class SearchWatcher implements TextWatcher {
        private final MainActivity mMain;
        SearchWatcher(MainActivity main) { mMain = main; }
        public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
        public void onTextChanged(CharSequence s, int st, int b, int c) {}
        public void afterTextChanged(Editable s) { mMain.reload(s.toString()); }
    }

    static class ItemClickListener implements AdapterView.OnItemClickListener {
        private final MainActivity mMain;
        ItemClickListener(MainActivity main) { mMain = main; }
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            if (id < 0) return;
            mMain.openDetail(id);
        }
    }

    static class ItemLongClickListener implements AdapterView.OnItemLongClickListener {
        private final MainActivity mMain;
        ItemLongClickListener(MainActivity main) { mMain = main; }
        public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
            if (id < 0) return false;
            if (!mMain.mSelectionMode) mMain.enterSelectionMode(id);
            else mMain.toggleSelection(id);
            return true;
        }
    }

    static class SelectionItemClickListener implements AdapterView.OnItemClickListener {
        private final MainActivity mMain;
        SelectionItemClickListener(MainActivity main) { mMain = main; }
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            if (id < 0) return;
            mMain.toggleSelection(id);
        }
    }

    static class ToggleModeListener implements View.OnClickListener {
        private final MainActivity mMain;
        private final int mMode;
        ToggleModeListener(MainActivity main, int mode) { mMain = main; mMode = mode; }
        public void onClick(View v) { mMain.setViewMode(mMode); }
    }

    static class ArchiveNewClickListener implements View.OnClickListener {
        private final MainActivity mMain;
        ArchiveNewClickListener(MainActivity main) { mMain = main; }
        public void onClick(View v) { mMain.openArchiveScreen(); }
    }

    static class SelectionExitClickListener implements View.OnClickListener {
        private final MainActivity mMain;
        SelectionExitClickListener(MainActivity main) { mMain = main; }
        public void onClick(View v) { mMain.exitSelectionMode(); }
    }

    static class SelectionActionsClickListener implements View.OnClickListener {
        private final MainActivity mMain;
        SelectionActionsClickListener(MainActivity main) { mMain = main; }
        public void onClick(View v) { mMain.showActionMenu(); }
    }

    static class ActionMenuListener implements DialogInterface.OnClickListener {
        private final MainActivity mMain;
        private final long[] mIds;
        ActionMenuListener(MainActivity main, long[] ids) { mMain = main; mIds = ids; }
        public void onClick(DialogInterface dialog, int which) {
            if (which == 0) mMain.showLabelPickerDialog(mIds);
            else if (which == 1) mMain.confirmBulkRestore(mIds);
            else mMain.confirmBulkDelete(mIds);
        }
    }

    static class LabelPickListener implements DialogInterface.OnClickListener {
        private final MainActivity mMain;
        private final String[] mLabels;
        private final long[] mIds;
        LabelPickListener(MainActivity main, String[] labels, long[] ids) {
            mMain = main; mLabels = labels; mIds = ids;
        }
        public void onClick(DialogInterface dialog, int which) {
            mMain.applyLabelToSelected(mLabels[which], mIds);
        }
    }

    static class NewLabelForSelectionListener implements DialogInterface.OnClickListener {
        private final MainActivity mMain;
        private final long[] mIds;
        NewLabelForSelectionListener(MainActivity main, long[] ids) { mMain = main; mIds = ids; }
        public void onClick(DialogInterface dialog, int which) {
            mMain.showNewLabelForSelectionDialog(mIds);
        }
    }

    static class NewLabelForSelectionConfirmListener implements DialogInterface.OnClickListener {
        private final MainActivity mMain;
        private final EditText mEt;
        private final long[] mIds;
        NewLabelForSelectionConfirmListener(MainActivity main, EditText et, long[] ids) {
            mMain = main; mEt = et; mIds = ids;
        }
        public void onClick(DialogInterface dialog, int which) {
            String name = mEt.getText().toString().trim();
            if (!name.isEmpty()) mMain.applyLabelToSelected(name, mIds);
        }
    }

    static class BulkRestoreConfirmListener implements DialogInterface.OnClickListener {
        private final MainActivity mMain;
        private final long[] mIds;
        BulkRestoreConfirmListener(MainActivity main, long[] ids) { mMain = main; mIds = ids; }
        public void onClick(DialogInterface dialog, int which) {
            Toast.makeText(mMain, R.string.restoring, Toast.LENGTH_SHORT).show();
            new BulkRestoreThread(mMain, mIds, new Handler(Looper.getMainLooper())).start();
        }
    }

    static class BulkDeleteConfirmListener implements DialogInterface.OnClickListener {
        private final MainActivity mMain;
        private final long[] mIds;
        BulkDeleteConfirmListener(MainActivity main, long[] ids) { mMain = main; mIds = ids; }
        public void onClick(DialogInterface dialog, int which) { mMain.doBulkDelete(mIds); }
    }

    static class BulkRestoreThread extends Thread {
        private final MainActivity mMain;
        private final long[] mIds;
        private final Handler mHandler;
        BulkRestoreThread(MainActivity main, long[] ids, Handler handler) {
            mMain = main; mIds = ids; mHandler = handler;
        }
        public void run() {
            int restored = 0, failed = 0;
            ContactDatabase db = ContactDatabase.getInstance(mMain);
            for (long id : mIds) {
                ArchivedContact c = db.getById(id);
                if (c == null) continue;
                try {
                    ContactsHelper.restoreContact(mMain, c);
                    db.delete(id);
                    restored++;
                } catch (Exception e) { failed++; }
            }
            mHandler.post(new BulkRestoreResultRunnable(mMain, restored, failed));
        }
    }

    static class BulkRestoreResultRunnable implements Runnable {
        private final MainActivity mMain;
        private final int mRestored;
        private final int mFailed;
        BulkRestoreResultRunnable(MainActivity main, int restored, int failed) {
            mMain = main; mRestored = restored; mFailed = failed;
        }
        public void run() { mMain.onBulkRestoreDone(mRestored, mFailed); }
    }

    static class ExportCallbackImpl implements ExportHelper.Callback {
        private final MainActivity mMain;
        ExportCallbackImpl(MainActivity main) { mMain = main; }
        public void onSuccess(String fileUrl) {
            Toast.makeText(mMain,
                mMain.getString(R.string.export_success) + "\n" + fileUrl,
                Toast.LENGTH_LONG).show();
        }
        public void onError(String message) {
            Toast.makeText(mMain,
                mMain.getString(R.string.export_failed) + ": " + message,
                Toast.LENGTH_LONG).show();
        }
    }

    static class DropboxTokenSaveListener implements DialogInterface.OnClickListener {
        private final MainActivity mMain;
        private final EditText mEt;
        DropboxTokenSaveListener(MainActivity main, EditText et) { mMain = main; mEt = et; }
        public void onClick(DialogInterface d, int w) {
            String token = mEt.getText().toString().trim();
            if (token.isEmpty()) {
                Toast.makeText(mMain, R.string.dropbox_token_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            mMain.saveDropboxTokenAndUpload(token);
        }
    }

    static class DropboxCallbackImpl implements DropboxHelper.Callback {
        private final MainActivity mMain;
        DropboxCallbackImpl(MainActivity main) { mMain = main; }
        public void onSuccess(String path, String newRev) {
            mMain.storeDropboxRev(newRev);
            Toast.makeText(mMain,
                mMain.getString(R.string.dropbox_upload_success) + "\n" + path,
                Toast.LENGTH_LONG).show();
        }
        public void onConflict() {
            Toast.makeText(mMain, R.string.dropbox_push_conflict, Toast.LENGTH_LONG).show();
        }
        public void onError(String message) {
            Toast.makeText(mMain,
                mMain.getString(R.string.dropbox_upload_failed) + ": " + message,
                Toast.LENGTH_LONG).show();
        }
        public void onAuthFailed() {
            mMain.clearDropboxToken();
            Toast.makeText(mMain, R.string.dropbox_token_invalid, Toast.LENGTH_LONG).show();
        }
    }

    static class BackupThread extends Thread {
        private final MainActivity mMain;
        private final List<ArchivedContact> mContacts;
        private final Handler mHandler;
        BackupThread(MainActivity main, List<ArchivedContact> contacts, Handler handler) {
            mMain = main; mContacts = contacts; mHandler = handler;
        }
        public void run() {
            try {
                String content = BackupHelper.buildCsvContent(mContacts);
                String filename = BackupHelper.writeCsv(mMain, content);
                mHandler.post(new BackupResultRunnable(mMain, filename));
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : "Backup failed";
                mHandler.post(new BackupErrorRunnable(mMain, msg));
            }
        }
    }

    static class BackupResultRunnable implements Runnable {
        private final MainActivity mMain;
        private final String mFilename;
        BackupResultRunnable(MainActivity main, String filename) { mMain = main; mFilename = filename; }
        public void run() { mMain.onBackupReady(mFilename); }
    }

    static class BackupErrorRunnable implements Runnable {
        private final MainActivity mMain;
        private final String mMsg;
        BackupErrorRunnable(MainActivity main, String msg) { mMain = main; mMsg = msg; }
        public void run() {
            Toast.makeText(mMain, mMsg, Toast.LENGTH_LONG).show();
        }
    }

    static class ImportCallbackImpl implements ImportHelper.Callback {
        private final MainActivity mMain;
        ImportCallbackImpl(MainActivity main) { mMain = main; }
        public void onDone(int imported, int skipped) { mMain.onImportDone(imported, skipped); }
        public void onError(String message) {
            Toast.makeText(mMain,
                mMain.getString(R.string.import_failed) + ": " + message,
                Toast.LENGTH_LONG).show();
        }
    }
}
