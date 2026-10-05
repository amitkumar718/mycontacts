package com.ldsa.mycontacts.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.mycontacts.R;
import com.ldsa.mycontacts.contacts.DropboxHelper;
import com.ldsa.mycontacts.contacts.ImportHelper;
import com.ldsa.mycontacts.db.ArchivedContact;
import com.ldsa.mycontacts.db.ContactDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DropboxSyncActivity extends Activity {

    private static final String PREF_FILE  = DropboxHelper.PREF_FILE;
    private static final String PREF_TOKEN = DropboxHelper.KEY_TOKEN;
    private static final String PREF_REV   = DropboxHelper.KEY_REV;

    static final int MODE_UPSTREAM_ONLY = 0;
    static final int MODE_LOCAL_ONLY    = 1;

    private Button mBtnToggleUpstream;
    private Button mBtnToggleLocal;
    private TextView mTvStatus;
    private TextView mTvEmpty;
    private TextView mTvHint;
    private ProgressBar mProgress;
    private ListView mListView;
    private Button mBtnImport;
    private Button mBtnPush;
    private DropboxSyncAdapter mAdapter;
    private ContactDatabase mDb;
    String mToken;

    List<SyncItem> mUpstreamOnly = new ArrayList<SyncItem>();
    List<SyncItem> mLocalOnly    = new ArrayList<SyncItem>();
    int mCurrentMode = MODE_UPSTREAM_ONLY;
    boolean mLoaded = false;
    boolean mInSync = false;  // true when onUnchanged (no diff needed)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dropbox_sync);
        setTitle(R.string.dropbox_sync_title);

        mDb = ContactDatabase.getInstance(this);

        mBtnToggleUpstream = (Button)      findViewById(R.id.btnToggleUpstream);
        mBtnToggleLocal    = (Button)      findViewById(R.id.btnToggleLocal);
        mTvStatus = (TextView)    findViewById(R.id.tvDbxStatus);
        mTvEmpty  = (TextView)    findViewById(R.id.tvDbxEmpty);
        mTvHint   = (TextView)    findViewById(R.id.tvDbxHint);
        mProgress = (ProgressBar) findViewById(R.id.dbxProgress);
        mListView = (ListView)    findViewById(R.id.listDbx);
        mBtnImport = (Button) findViewById(R.id.btnDbxImport);
        mBtnPush   = (Button) findViewById(R.id.btnDbxPush);
        Button btnCancel = (Button) findViewById(R.id.btnDbxCancel);

        mBtnToggleUpstream.setOnClickListener(new ToggleModeListener(this, MODE_UPSTREAM_ONLY));
        mBtnToggleLocal.setOnClickListener(new ToggleModeListener(this, MODE_LOCAL_ONLY));
        mListView.setOnItemClickListener(new ItemClickListener(this));
        mBtnImport.setOnClickListener(new ImportClickListener(this));
        mBtnPush.setOnClickListener(new PushClickListener(this));
        btnCancel.setOnClickListener(new CancelClickListener(this));

        mToken = getSharedPreferences(PREF_FILE, MODE_PRIVATE).getString(PREF_TOKEN, null);
        if (mToken == null || mToken.isEmpty()) {
            showTokenDialog();
        } else {
            startLoad();
        }
    }

    void showTokenDialog() {
        EditText et = new EditText(this);
        et.setHint(R.string.dropbox_token_hint);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.dropbox_token_title);
        b.setMessage(R.string.dropbox_token_message);
        b.setView(et);
        b.setPositiveButton(R.string.save, new TokenSaveListener(this, et));
        b.setNegativeButton(R.string.cancel, new TokenCancelListener(this));
        b.setCancelable(false);
        b.show();
    }

    void saveTokenAndLoad(String token) {
        mToken = token;
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit().putString(PREF_TOKEN, token).apply();
        startLoad();
    }

    void startLoad() {
        mTvStatus.setText(R.string.dbx_loading);
        mTvHint.setVisibility(View.GONE);
        mProgress.setVisibility(View.VISIBLE);
        mListView.setVisibility(View.GONE);
        mTvEmpty.setVisibility(View.GONE);
        mBtnImport.setEnabled(false);
        String lastRev = getSharedPreferences(PREF_FILE, MODE_PRIVATE)
            .getString(PREF_REV, null);
        DropboxHelper.syncFromDropbox(this, mToken, lastRev, new SyncCallbackImpl(this));
    }

    void onSyncChanged(String csv, String newRev) {
        storeRev(newRev);
        List<ArchivedContact> upstream = ImportHelper.parseContacts(csv);
        List<ArchivedContact> local = mDb.getAll();
        diffAndShow(upstream, local);
    }

    void onSyncUnchanged() {
        // Server rev matches our last-synced rev. Nothing new to pull.
        // We don't track incremental local changes, so Local Only is unknown.
        // Show "already in sync" state with Push available for force re-upload.
        mProgress.setVisibility(View.GONE);
        mInSync = true;
        mLoaded = true;
        mUpstreamOnly = new ArrayList<SyncItem>();
        mLocalOnly = new ArrayList<SyncItem>();
        mTvStatus.setText(R.string.dbx_already_in_sync);
        mTvEmpty.setVisibility(View.VISIBLE);
        mTvEmpty.setText(R.string.dbx_already_in_sync_hint);
        mListView.setVisibility(View.GONE);
        mTvHint.setVisibility(View.GONE);
        updateToggleLabels();
        mBtnImport.setEnabled(false);
        mBtnPush.setEnabled(true);
    }

    void onSyncEmpty() {
        mProgress.setVisibility(View.GONE);
        mTvStatus.setText(R.string.dbx_empty_folder);
        mTvEmpty.setVisibility(View.VISIBLE);
        mTvEmpty.setText(R.string.dbx_empty_folder_hint);
        mLoaded = true;
        // No upstream — all local is "Local Only"
        mUpstreamOnly = new ArrayList<SyncItem>();
        mLocalOnly    = new ArrayList<SyncItem>();
        for (ArchivedContact c : mDb.getAll()) {
            SyncItem it = new SyncItem();
            it.contact = c;
            it.selected = false; // local-only is informational
            mLocalOnly.add(it);
        }
        updateToggleLabels();
        mBtnImport.setEnabled(false);
        mBtnPush.setEnabled(!mLocalOnly.isEmpty());
    }

    void onSyncError(String msg) {
        mProgress.setVisibility(View.GONE);
        Toast.makeText(this, getString(R.string.dbx_download_failed) + ": " + msg,
            Toast.LENGTH_LONG).show();
        finish();
    }

    void storeRev(String rev) {
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit()
            .putString(PREF_REV, rev).apply();
    }

    void onAuthFailed() {
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit()
            .remove(PREF_TOKEN).remove(PREF_REV).apply();
        mToken = null;
        Toast.makeText(this, R.string.dropbox_token_invalid, Toast.LENGTH_LONG).show();
        showTokenDialog();
    }

    private void diffAndShow(List<ArchivedContact> upstream, List<ArchivedContact> local) {
        mProgress.setVisibility(View.GONE);

        HashMap<String, Boolean> upstreamNames  = new HashMap<String, Boolean>();
        HashMap<String, Boolean> upstreamPhones = new HashMap<String, Boolean>();
        for (ArchivedContact ac : upstream) {
            String n = normalizeName(ac.displayName);
            if (!n.isEmpty()) upstreamNames.put(n, Boolean.TRUE);
            for (ArchivedContact.Phone p : ac.getPhones()) {
                String ph = normalizePhone(p.number);
                if (!ph.isEmpty()) upstreamPhones.put(ph, Boolean.TRUE);
            }
        }

        HashMap<String, Boolean> localNames  = new HashMap<String, Boolean>();
        HashMap<String, Boolean> localPhones = new HashMap<String, Boolean>();
        for (ArchivedContact ac : local) {
            String n = normalizeName(ac.displayName);
            if (!n.isEmpty()) localNames.put(n, Boolean.TRUE);
            for (ArchivedContact.Phone p : ac.getPhones()) {
                String ph = normalizePhone(p.number);
                if (!ph.isEmpty()) localPhones.put(ph, Boolean.TRUE);
            }
        }

        mUpstreamOnly = new ArrayList<SyncItem>();
        for (ArchivedContact ac : upstream) {
            if (!matchedIn(ac, localNames, localPhones)) {
                SyncItem it = new SyncItem();
                it.contact = ac;
                it.selected = true;
                mUpstreamOnly.add(it);
            }
        }

        mLocalOnly = new ArrayList<SyncItem>();
        for (ArchivedContact ac : local) {
            if (!matchedIn(ac, upstreamNames, upstreamPhones)) {
                SyncItem it = new SyncItem();
                it.contact = ac;
                it.selected = false; // local-only is informational
                mLocalOnly.add(it);
            }
        }

        mLoaded = true;
        updateToggleLabels();
        mBtnPush.setEnabled(true);
        switchMode(MODE_UPSTREAM_ONLY);
    }

    private static boolean matchedIn(ArchivedContact ac,
            HashMap<String, Boolean> names, HashMap<String, Boolean> phones) {
        String n = normalizeName(ac.displayName);
        if (!n.isEmpty() && names.containsKey(n)) return true;
        for (ArchivedContact.Phone p : ac.getPhones()) {
            String ph = normalizePhone(p.number);
            if (!ph.isEmpty() && phones.containsKey(ph)) return true;
        }
        return false;
    }

    void updateToggleLabels() {
        mBtnToggleUpstream.setText(
            getString(R.string.dbx_tab_upstream_only) + " (" + mUpstreamOnly.size() + ")");
        mBtnToggleLocal.setText(
            getString(R.string.dbx_tab_local_only) + " (" + mLocalOnly.size() + ")");
    }

    void switchMode(int mode) {
        mCurrentMode = mode;
        List<SyncItem> items = mode == MODE_UPSTREAM_ONLY ? mUpstreamOnly : mLocalOnly;

        if (mode == MODE_UPSTREAM_ONLY) {
            mBtnToggleUpstream.setBackgroundResource(R.drawable.bg_toggle_active);
            mBtnToggleUpstream.setTextColor(0xFFFFFFFF);
            mBtnToggleLocal.setBackgroundResource(R.drawable.bg_toggle_inactive);
            mBtnToggleLocal.setTextColor(0xFF1565C0);
        } else {
            mBtnToggleLocal.setBackgroundResource(R.drawable.bg_toggle_active);
            mBtnToggleLocal.setTextColor(0xFFFFFFFF);
            mBtnToggleUpstream.setBackgroundResource(R.drawable.bg_toggle_inactive);
            mBtnToggleUpstream.setTextColor(0xFF1565C0);
        }

        if (items.isEmpty()) {
            mListView.setVisibility(View.GONE);
            mTvEmpty.setVisibility(View.VISIBLE);
            mTvEmpty.setText(mode == MODE_UPSTREAM_ONLY
                ? R.string.dbx_empty_upstream : R.string.dbx_empty_local);
            mTvHint.setVisibility(View.GONE);
        } else {
            mTvEmpty.setVisibility(View.GONE);
            mListView.setVisibility(View.VISIBLE);
            mTvHint.setVisibility(View.VISIBLE);
            mTvHint.setText(mode == MODE_UPSTREAM_ONLY
                ? R.string.dbx_hint_upstream : R.string.dbx_hint_local);
            mAdapter = new DropboxSyncAdapter(this, items, mode == MODE_UPSTREAM_ONLY);
            mListView.setAdapter(mAdapter);
        }

        updateStatus();
        updateImportButton();
    }

    void updateStatus() {
        if (!mLoaded) return;
        List<SyncItem> items = mCurrentMode == MODE_UPSTREAM_ONLY ? mUpstreamOnly : mLocalOnly;
        String key = mCurrentMode == MODE_UPSTREAM_ONLY
            ? getString(R.string.dbx_status_upstream)
            : getString(R.string.dbx_status_local);
        mTvStatus.setText(items.size() + " " + key);
    }

    void updateImportButton() {
        if (mCurrentMode != MODE_UPSTREAM_ONLY || mAdapter == null) {
            mBtnImport.setEnabled(false);
            mBtnImport.setText(getString(R.string.dbx_import));
            return;
        }
        int n = mAdapter.getSelectedCount();
        mBtnImport.setText(getString(R.string.dbx_import) + " (" + n + ")");
        mBtnImport.setEnabled(n > 0);
    }

    void toggleItem(int pos) {
        if (mCurrentMode != MODE_UPSTREAM_ONLY || mAdapter == null) return;
        mAdapter.toggleSelected(pos);
        updateImportButton();
    }

    void confirmImport() {
        if (mAdapter == null) return;
        int n = mAdapter.getSelectedCount();
        if (n == 0) return;
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setMessage(n + " " + getString(R.string.dbx_confirm_import));
        b.setPositiveButton(R.string.dbx_import, new ImportConfirmListener(this));
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    void doImport() {
        if (mAdapter == null) return;
        List<SyncItem> selected = mAdapter.getSelectedItems();
        for (SyncItem it : selected) {
            mDb.insert(it.contact);
            mUpstreamOnly.remove(it);
            // Imported contacts now exist locally — they become part of what would be pushed next
            SyncItem localCopy = new SyncItem();
            localCopy.contact = it.contact;
            localCopy.selected = false;
            mLocalOnly.add(localCopy);
        }
        Toast.makeText(this,
            selected.size() + " " + getString(R.string.contacts_imported),
            Toast.LENGTH_SHORT).show();
        updateToggleLabels();
        switchMode(mCurrentMode);
    }

    void confirmPush() {
        List<ArchivedContact> all = mDb.getAll();
        if (all.isEmpty()) {
            Toast.makeText(this, R.string.no_contacts_to_backup, Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setMessage(all.size() + " " + getString(R.string.dbx_confirm_push));
        b.setPositiveButton(R.string.dbx_push, new PushConfirmListener(this));
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    void doPush() {
        List<ArchivedContact> all = mDb.getAll();
        String rev = getSharedPreferences(PREF_FILE, MODE_PRIVATE).getString(PREF_REV, null);
        Toast.makeText(this, R.string.uploading_to_dropbox, Toast.LENGTH_SHORT).show();
        mBtnPush.setEnabled(false);
        mBtnImport.setEnabled(false);
        DropboxHelper.uploadToDropbox(this, all, mToken, rev, new UploadCallbackImpl(this));
    }

    void onPushSuccess(String path, String newRev) {
        storeRev(newRev);
        Toast.makeText(this,
            getString(R.string.dropbox_upload_success) + "\n" + path,
            Toast.LENGTH_LONG).show();
        finish();
    }

    void onPushConflict() {
        Toast.makeText(this, R.string.dropbox_push_conflict, Toast.LENGTH_LONG).show();
        // Re-fetch so user can see what changed
        startLoad();
    }

    void onPushError(String msg) {
        Toast.makeText(this,
            getString(R.string.dropbox_upload_failed) + ": " + msg,
            Toast.LENGTH_LONG).show();
        mBtnPush.setEnabled(true);
        updateImportButton();
    }

    void onPushAuthFailed() {
        onAuthFailed();
    }

    // =========================================================
    // Normalization — same semantics as SyncActivity
    // =========================================================

    private static String normalizeName(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase();
    }

    private static String normalizePhone(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        String d = sb.toString();
        if (d.length() > 10) d = d.substring(d.length() - 10);
        return d;
    }

    // =========================================================
    // Data model
    // =========================================================

    static class SyncItem {
        ArchivedContact contact;
        boolean selected;
    }

    // =========================================================
    // Adapter
    // =========================================================

    static class DropboxSyncAdapter extends BaseAdapter {
        private final DropboxSyncActivity mOuter;
        private final List<SyncItem> mItems;
        private final boolean mSelectable;

        DropboxSyncAdapter(DropboxSyncActivity outer, List<SyncItem> items, boolean selectable) {
            mOuter = outer; mItems = items; mSelectable = selectable;
        }

        public int getCount() { return mItems.size(); }
        public Object getItem(int pos) { return mItems.get(pos); }
        public long getItemId(int pos) { return pos; }

        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mOuter)
                    .inflate(R.layout.item_sync_contact, parent, false);
            }
            SyncItem it = mItems.get(pos);
            TextView tvName   = (TextView) convertView.findViewById(R.id.tvName);
            TextView tvAvatar = (TextView) convertView.findViewById(R.id.tvAvatar);
            TextView tvSub    = (TextView) convertView.findViewById(R.id.tvSub);

            String name = it.contact.displayName.isEmpty()
                ? mOuter.getString(R.string.no_name) : it.contact.displayName;
            tvName.setText(name);
            tvAvatar.setText(name.substring(0, 1).toUpperCase());

            String phone = it.contact.getPrimaryPhone();
            String email = it.contact.getPrimaryEmail();
            tvSub.setText(!phone.isEmpty() ? phone : email);

            if (mSelectable) {
                convertView.setBackgroundColor(it.selected ? 0xFFBBDEFB : 0xFFFFFFFF);
            } else {
                convertView.setBackgroundColor(0xFFFFFFFF);
            }
            return convertView;
        }

        void toggleSelected(int pos) {
            if (!mSelectable) return;
            mItems.get(pos).selected = !mItems.get(pos).selected;
            notifyDataSetChanged();
        }

        int getSelectedCount() {
            if (!mSelectable) return 0;
            int n = 0;
            for (SyncItem it : mItems) if (it.selected) n++;
            return n;
        }

        List<SyncItem> getSelectedItems() {
            List<SyncItem> out = new ArrayList<SyncItem>();
            if (!mSelectable) return out;
            for (SyncItem it : mItems) if (it.selected) out.add(it);
            return out;
        }
    }

    // =========================================================
    // Callback implementations
    // =========================================================

    static class SyncCallbackImpl implements DropboxHelper.SyncCallback {
        private final DropboxSyncActivity mOuter;
        SyncCallbackImpl(DropboxSyncActivity outer) { mOuter = outer; }
        public void onChanged(String csv, String rev) { mOuter.onSyncChanged(csv, rev); }
        public void onUnchanged() { mOuter.onSyncUnchanged(); }
        public void onEmpty() { mOuter.onSyncEmpty(); }
        public void onError(String msg) { mOuter.onSyncError(msg); }
        public void onAuthFailed() { mOuter.onAuthFailed(); }
    }

    static class UploadCallbackImpl implements DropboxHelper.Callback {
        private final DropboxSyncActivity mOuter;
        UploadCallbackImpl(DropboxSyncActivity outer) { mOuter = outer; }
        public void onSuccess(String path, String rev) { mOuter.onPushSuccess(path, rev); }
        public void onConflict() { mOuter.onPushConflict(); }
        public void onError(String msg) { mOuter.onPushError(msg); }
        public void onAuthFailed() { mOuter.onPushAuthFailed(); }
    }

    // =========================================================
    // Listeners
    // =========================================================

    static class ToggleModeListener implements View.OnClickListener {
        private final DropboxSyncActivity mOuter;
        private final int mMode;
        ToggleModeListener(DropboxSyncActivity outer, int mode) { mOuter = outer; mMode = mode; }
        public void onClick(View v) {
            if (mOuter.mLoaded) mOuter.switchMode(mMode);
        }
    }

    static class ItemClickListener implements AdapterView.OnItemClickListener {
        private final DropboxSyncActivity mOuter;
        ItemClickListener(DropboxSyncActivity outer) { mOuter = outer; }
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            mOuter.toggleItem(pos);
        }
    }

    static class ImportClickListener implements View.OnClickListener {
        private final DropboxSyncActivity mOuter;
        ImportClickListener(DropboxSyncActivity outer) { mOuter = outer; }
        public void onClick(View v) { mOuter.confirmImport(); }
    }

    static class ImportConfirmListener implements DialogInterface.OnClickListener {
        private final DropboxSyncActivity mOuter;
        ImportConfirmListener(DropboxSyncActivity outer) { mOuter = outer; }
        public void onClick(DialogInterface d, int w) { mOuter.doImport(); }
    }

    static class PushClickListener implements View.OnClickListener {
        private final DropboxSyncActivity mOuter;
        PushClickListener(DropboxSyncActivity outer) { mOuter = outer; }
        public void onClick(View v) { mOuter.confirmPush(); }
    }

    static class PushConfirmListener implements DialogInterface.OnClickListener {
        private final DropboxSyncActivity mOuter;
        PushConfirmListener(DropboxSyncActivity outer) { mOuter = outer; }
        public void onClick(DialogInterface d, int w) { mOuter.doPush(); }
    }

    static class CancelClickListener implements View.OnClickListener {
        private final DropboxSyncActivity mOuter;
        CancelClickListener(DropboxSyncActivity outer) { mOuter = outer; }
        public void onClick(View v) { mOuter.finish(); }
    }

    static class TokenSaveListener implements DialogInterface.OnClickListener {
        private final DropboxSyncActivity mOuter;
        private final EditText mEt;
        TokenSaveListener(DropboxSyncActivity outer, EditText et) { mOuter = outer; mEt = et; }
        public void onClick(DialogInterface d, int w) {
            String t = mEt.getText().toString().trim();
            if (t.isEmpty()) {
                Toast.makeText(mOuter, R.string.dropbox_token_empty, Toast.LENGTH_SHORT).show();
                mOuter.showTokenDialog();
                return;
            }
            mOuter.saveTokenAndLoad(t);
        }
    }

    static class TokenCancelListener implements DialogInterface.OnClickListener {
        private final DropboxSyncActivity mOuter;
        TokenCancelListener(DropboxSyncActivity outer) { mOuter = outer; }
        public void onClick(DialogInterface d, int w) { mOuter.finish(); }
    }
}
