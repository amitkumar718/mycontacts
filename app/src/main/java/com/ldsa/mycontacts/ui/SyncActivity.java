package com.ldsa.mycontacts.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.mycontacts.R;
import com.ldsa.mycontacts.contacts.ContactsHelper;
import com.ldsa.mycontacts.db.ArchivedContact;
import com.ldsa.mycontacts.db.ContactDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SyncActivity extends Activity {

    static final int MODE_DUPLICATES   = 0;
    static final int MODE_ARCHIVE_ONLY = 1;

    private Button mBtnToggleDuplicates;
    private Button mBtnToggleArchiveOnly;
    private TextView mTvStatus;
    private TextView mTvEmpty;
    private TextView mTvHint;
    private ProgressBar mProgress;
    private ListView mListView;
    private LinearLayout mLayoutSelectAll;
    private CheckBox mCbSelectAll;
    private TextView mTvSelectCount;
    private Button mBtnAction;
    private SyncAdapter mAdapter;
    private ContactDatabase mDb;

    List<SyncMatch> mDuplicates  = new ArrayList<SyncMatch>();
    List<SyncMatch> mArchiveOnly = new ArrayList<SyncMatch>();
    int mCurrentMode = MODE_DUPLICATES;
    boolean mLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync);
        setTitle(R.string.sync_check);

        mDb = ContactDatabase.getInstance(this);

        mBtnToggleDuplicates  = (Button)      findViewById(R.id.btnToggleDuplicates);
        mBtnToggleArchiveOnly = (Button)      findViewById(R.id.btnToggleArchiveOnly);
        mTvStatus  = (TextView)    findViewById(R.id.tvSyncStatus);
        mTvEmpty   = (TextView)    findViewById(R.id.tvSyncEmpty);
        mTvHint    = (TextView)    findViewById(R.id.tvSyncHint);
        mProgress  = (ProgressBar) findViewById(R.id.syncProgress);
        mListView  = (ListView)    findViewById(R.id.listSync);
        mLayoutSelectAll = (LinearLayout) findViewById(R.id.layoutSyncSelectAll);
        mCbSelectAll     = (CheckBox)     findViewById(R.id.cbSyncSelectAll);
        mTvSelectCount   = (TextView)     findViewById(R.id.tvSyncSelectCount);
        mBtnAction = (Button)      findViewById(R.id.btnSyncAction);
        Button btnCancel = (Button) findViewById(R.id.btnSyncCancel);

        mBtnToggleDuplicates.setOnClickListener(new ToggleModeListener(this, MODE_DUPLICATES));
        mBtnToggleArchiveOnly.setOnClickListener(new ToggleModeListener(this, MODE_ARCHIVE_ONLY));
        mListView.setOnItemClickListener(new ItemClickListener(this));
        mCbSelectAll.setOnClickListener(new SelectAllClickListener(this));
        mBtnAction.setOnClickListener(new ActionClickListener(this));
        btnCancel.setOnClickListener(new CancelClickListener(this));

        startLoad();
    }

    void startLoad() {
        mTvStatus.setText(R.string.sync_loading);
        mTvHint.setVisibility(View.GONE);
        mProgress.setVisibility(View.VISIBLE);
        mListView.setVisibility(View.GONE);
        mTvEmpty.setVisibility(View.GONE);
        mBtnAction.setEnabled(false);
        new LoadThread(this, new Handler(Looper.getMainLooper())).start();
    }

    void onLoadDone(List<SyncMatch> duplicates, List<SyncMatch> archiveOnly) {
        mProgress.setVisibility(View.GONE);
        mDuplicates  = duplicates;
        mArchiveOnly = archiveOnly;
        mLoaded = true;

        // Update toggle button labels with counts
        mBtnToggleDuplicates.setText(
            getString(R.string.sync_tab_duplicates) + " (" + mDuplicates.size() + ")");
        mBtnToggleArchiveOnly.setText(
            getString(R.string.sync_tab_archive_only) + " (" + mArchiveOnly.size() + ")");

        switchMode(MODE_DUPLICATES);
    }

    void switchMode(int mode) {
        mCurrentMode = mode;

        // Re-select all items when entering a mode (fresh view, all selected)
        List<SyncMatch> items = mode == MODE_DUPLICATES ? mDuplicates : mArchiveOnly;
        for (int i = 0; i < items.size(); i++) items.get(i).selected = true;

        // Toggle button visuals
        if (mode == MODE_DUPLICATES) {
            mBtnToggleDuplicates.setBackgroundResource(R.drawable.bg_toggle_active);
            mBtnToggleDuplicates.setTextColor(0xFFFFFFFF);
            mBtnToggleArchiveOnly.setBackgroundResource(R.drawable.bg_toggle_inactive);
            mBtnToggleArchiveOnly.setTextColor(0xFF1565C0);
        } else {
            mBtnToggleArchiveOnly.setBackgroundResource(R.drawable.bg_toggle_active);
            mBtnToggleArchiveOnly.setTextColor(0xFFFFFFFF);
            mBtnToggleDuplicates.setBackgroundResource(R.drawable.bg_toggle_inactive);
            mBtnToggleDuplicates.setTextColor(0xFF1565C0);
        }

        if (items.isEmpty()) {
            mListView.setVisibility(View.GONE);
            mTvEmpty.setVisibility(View.VISIBLE);
            mTvEmpty.setText(mode == MODE_DUPLICATES
                ? R.string.sync_empty_duplicates : R.string.sync_empty_archive_only);
            mTvStatus.setText(mode == MODE_DUPLICATES
                ? R.string.sync_empty_duplicates : R.string.sync_empty_archive_only);
            mTvHint.setVisibility(View.GONE);
            mLayoutSelectAll.setVisibility(View.GONE);
            mBtnAction.setEnabled(false);
            // keep action button styled correctly even when disabled
            styleActionButton(mode, 0);
        } else {
            mTvEmpty.setVisibility(View.GONE);
            mListView.setVisibility(View.VISIBLE);
            mTvHint.setVisibility(View.VISIBLE);
            mTvHint.setText(mode == MODE_DUPLICATES
                ? R.string.sync_hint_duplicates : R.string.sync_hint_archive_only);
            mLayoutSelectAll.setVisibility(View.VISIBLE);
            mAdapter = new SyncAdapter(this, items);
            mListView.setAdapter(mAdapter);
            updateSelectAllRow();
            updateActionButton();
        }
    }

    /** Refresh the select-all checkbox + "N / M" count label for the current tab. */
    void updateSelectAllRow() {
        List<SyncMatch> items = mCurrentMode == MODE_DUPLICATES ? mDuplicates : mArchiveOnly;
        int sel = 0;
        for (SyncMatch m : items) if (m.selected) sel++;
        mTvSelectCount.setText(sel + " / " + items.size());
        mCbSelectAll.setChecked(sel > 0 && sel == items.size());
    }

    /** Select or deselect every item in the current tab. */
    void selectAllInCurrentTab(boolean selected) {
        List<SyncMatch> items = mCurrentMode == MODE_DUPLICATES ? mDuplicates : mArchiveOnly;
        for (SyncMatch m : items) m.selected = selected;
        if (mAdapter != null) mAdapter.notifyDataSetChanged();
        updateSelectAllRow();
        updateActionButton();
    }

    void updateActionButton() {
        if (!mLoaded || mAdapter == null) return;
        int count = mAdapter.getSelectedCount();

        List<SyncMatch> items = mCurrentMode == MODE_DUPLICATES ? mDuplicates : mArchiveOnly;
        mTvStatus.setText(items.size() + " " + getString(mCurrentMode == MODE_DUPLICATES
            ? R.string.sync_status_duplicates : R.string.sync_status_archive_only));

        styleActionButton(mCurrentMode, count);
        mBtnAction.setEnabled(count > 0);
    }

    private void styleActionButton(int mode, int count) {
        if (mode == MODE_DUPLICATES) {
            String label = count > 0
                ? getString(R.string.btn_delete) + " (" + count + ")"
                : getString(R.string.btn_delete);
            mBtnAction.setText(label);
            mBtnAction.setBackgroundResource(R.drawable.bg_button_danger);
            mBtnAction.setTextColor(0xFFFFFFFF);
        } else {
            String label = count > 0
                ? getString(R.string.btn_restore) + " (" + count + ")"
                : getString(R.string.btn_restore);
            mBtnAction.setText(label);
            mBtnAction.setBackgroundResource(R.drawable.bg_button_primary);
            mBtnAction.setTextColor(0xFFFFFFFF);
        }
    }

    void toggleItem(int pos) {
        if (mAdapter == null) return;
        mAdapter.toggleSelected(pos);
        updateSelectAllRow();
        updateActionButton();
    }

    void confirmAction() {
        if (mAdapter == null) return;
        int count = mAdapter.getSelectedCount();
        if (count == 0) return;
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        if (mCurrentMode == MODE_DUPLICATES) {
            b.setMessage(count + " " + getString(R.string.sync_confirm_delete));
            b.setPositiveButton(R.string.btn_delete, new DeleteConfirmListener(this));
        } else {
            b.setMessage(count + " " + getString(R.string.sync_confirm_restore));
            b.setPositiveButton(R.string.btn_restore, new RestoreConfirmListener(this));
        }
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    void doDelete() {
        if (mAdapter == null) return;
        List<SyncMatch> selected = mAdapter.getSelectedMatches();
        for (int i = 0; i < selected.size(); i++) {
            mDb.delete(selected.get(i).archived.id);
            mDuplicates.remove(selected.get(i));
        }
        mBtnToggleDuplicates.setText(
            getString(R.string.sync_tab_duplicates) + " (" + mDuplicates.size() + ")");
        Toast.makeText(this,
            selected.size() + " " + getString(R.string.contacts_deleted),
            Toast.LENGTH_SHORT).show();
        switchMode(MODE_DUPLICATES);
    }

    void doRestore() {
        if (mAdapter == null) return;
        List<SyncMatch> selected = mAdapter.getSelectedMatches();
        Toast.makeText(this, R.string.restoring, Toast.LENGTH_SHORT).show();
        mBtnAction.setEnabled(false);
        new RestoreThread(this, selected, new Handler(Looper.getMainLooper())).start();
    }

    void onRestoreDone(List<SyncMatch> succeeded, int failed) {
        for (int i = 0; i < succeeded.size(); i++) {
            mDb.delete(succeeded.get(i).archived.id);
            mArchiveOnly.remove(succeeded.get(i));
        }
        mBtnToggleArchiveOnly.setText(
            getString(R.string.sync_tab_archive_only) + " (" + mArchiveOnly.size() + ")");
        String msg = succeeded.size() + " " + getString(R.string.contacts_restored);
        if (failed > 0) msg += ", " + failed + " failed";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        switchMode(MODE_ARCHIVE_ONLY);
    }

    // =========================================================
    // Data model
    // =========================================================

    static class SyncMatch {
        ArchivedContact archived;
        boolean selected = true;
    }

    // =========================================================
    // Adapter
    // =========================================================

    static class SyncAdapter extends BaseAdapter {
        private final SyncActivity mOuter;
        private final List<SyncMatch> mItems;

        SyncAdapter(SyncActivity outer, List<SyncMatch> items) {
            mOuter = outer;
            mItems = items;
        }

        public int getCount() { return mItems.size(); }
        public Object getItem(int pos) { return mItems.get(pos); }
        public long getItemId(int pos) { return mItems.get(pos).archived.id; }

        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mOuter)
                    .inflate(R.layout.item_sync_contact, parent, false);
            }
            SyncMatch m = mItems.get(pos);

            TextView tvName   = (TextView) convertView.findViewById(R.id.tvName);
            TextView tvAvatar = (TextView) convertView.findViewById(R.id.tvAvatar);
            TextView tvSub    = (TextView) convertView.findViewById(R.id.tvSub);

            String name = m.archived.displayName.isEmpty()
                ? mOuter.getString(R.string.no_name) : m.archived.displayName;
            tvName.setText(name);
            tvAvatar.setText(name.substring(0, 1).toUpperCase());

            String phone = m.archived.getPrimaryPhone();
            String email = m.archived.getPrimaryEmail();
            tvSub.setText(!phone.isEmpty() ? phone : email);

            convertView.setBackgroundColor(m.selected ? 0xFFBBDEFB : 0xFFFFFFFF);
            return convertView;
        }

        void toggleSelected(int pos) {
            mItems.get(pos).selected = !mItems.get(pos).selected;
            notifyDataSetChanged();
        }

        int getSelectedCount() {
            int n = 0;
            for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).selected) n++;
            return n;
        }

        List<SyncMatch> getSelectedMatches() {
            List<SyncMatch> out = new ArrayList<SyncMatch>();
            for (int i = 0; i < mItems.size(); i++) {
                if (mItems.get(i).selected) out.add(mItems.get(i));
            }
            return out;
        }
    }

    // =========================================================
    // Background loading
    // =========================================================

    static class LoadThread extends Thread {
        private final SyncActivity mOuter;
        private final Handler mHandler;

        LoadThread(SyncActivity outer, Handler handler) {
            mOuter = outer;
            mHandler = handler;
        }

        public void run() {
            List<ArchivedContact> archived = ContactDatabase.getInstance(mOuter).getAll();
            List<ContactsHelper.DeviceContact> device =
                ContactsHelper.loadDeviceContacts(mOuter);

            HashMap<String, Boolean> deviceNames  = new HashMap<String, Boolean>();
            HashMap<String, Boolean> devicePhones = new HashMap<String, Boolean>();

            for (int i = 0; i < device.size(); i++) {
                ContactsHelper.DeviceContact dc = device.get(i);
                String name = normalizeName(dc.displayName);
                if (!name.isEmpty()) deviceNames.put(name, Boolean.TRUE);
                String phone = normalizePhone(dc.primaryPhone);
                if (!phone.isEmpty()) devicePhones.put(phone, Boolean.TRUE);
            }

            List<SyncMatch> duplicates  = new ArrayList<SyncMatch>();
            List<SyncMatch> archiveOnly = new ArrayList<SyncMatch>();

            for (int i = 0; i < archived.size(); i++) {
                ArchivedContact ac = archived.get(i);
                SyncMatch m = new SyncMatch();
                m.archived = ac;
                if (isMatch(ac, deviceNames, devicePhones)) {
                    duplicates.add(m);
                } else {
                    archiveOnly.add(m);
                }
            }

            mHandler.post(new LoadResultRunnable(mOuter, duplicates, archiveOnly));
        }

        private boolean isMatch(ArchivedContact ac,
                HashMap<String, Boolean> names,
                HashMap<String, Boolean> phones) {
            String name = normalizeName(ac.displayName);
            if (!name.isEmpty() && names.containsKey(name)) return true;
            List<ArchivedContact.Phone> acPhones = ac.getPhones();
            for (int i = 0; i < acPhones.size(); i++) {
                String p = normalizePhone(acPhones.get(i).number);
                if (!p.isEmpty() && phones.containsKey(p)) return true;
            }
            return false;
        }

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
            // keep last 10 digits to normalize country-code prefix differences
            if (d.length() > 10) d = d.substring(d.length() - 10);
            return d;
        }
    }

    static class LoadResultRunnable implements Runnable {
        private final SyncActivity mOuter;
        private final List<SyncMatch> mDups;
        private final List<SyncMatch> mOnly;
        LoadResultRunnable(SyncActivity outer, List<SyncMatch> dups, List<SyncMatch> only) {
            mOuter = outer; mDups = dups; mOnly = only;
        }
        public void run() { mOuter.onLoadDone(mDups, mOnly); }
    }

    // =========================================================
    // Background restore
    // =========================================================

    static class RestoreThread extends Thread {
        private final SyncActivity mOuter;
        private final List<SyncMatch> mItems;
        private final Handler mHandler;

        RestoreThread(SyncActivity outer, List<SyncMatch> items, Handler handler) {
            mOuter = outer; mItems = items; mHandler = handler;
        }

        public void run() {
            List<SyncMatch> succeeded = new ArrayList<SyncMatch>();
            int failed = 0;
            for (int i = 0; i < mItems.size(); i++) {
                try {
                    ContactsHelper.restoreContact(mOuter, mItems.get(i).archived);
                    succeeded.add(mItems.get(i));
                } catch (Exception e) {
                    failed++;
                }
            }
            mHandler.post(new RestoreResultRunnable(mOuter, succeeded, failed));
        }
    }

    static class RestoreResultRunnable implements Runnable {
        private final SyncActivity mOuter;
        private final List<SyncMatch> mSucceeded;
        private final int mFailed;
        RestoreResultRunnable(SyncActivity outer, List<SyncMatch> succeeded, int failed) {
            mOuter = outer; mSucceeded = succeeded; mFailed = failed;
        }
        public void run() { mOuter.onRestoreDone(mSucceeded, mFailed); }
    }

    // =========================================================
    // Listeners
    // =========================================================

    static class ToggleModeListener implements View.OnClickListener {
        private final SyncActivity mOuter;
        private final int mMode;
        ToggleModeListener(SyncActivity outer, int mode) { mOuter = outer; mMode = mode; }
        public void onClick(View v) {
            if (mOuter.mLoaded) mOuter.switchMode(mMode);
        }
    }

    static class ItemClickListener implements AdapterView.OnItemClickListener {
        private final SyncActivity mOuter;
        ItemClickListener(SyncActivity outer) { mOuter = outer; }
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            mOuter.toggleItem(pos);
        }
    }

    static class ActionClickListener implements View.OnClickListener {
        private final SyncActivity mOuter;
        ActionClickListener(SyncActivity outer) { mOuter = outer; }
        public void onClick(View v) { mOuter.confirmAction(); }
    }

    static class DeleteConfirmListener implements DialogInterface.OnClickListener {
        private final SyncActivity mOuter;
        DeleteConfirmListener(SyncActivity outer) { mOuter = outer; }
        public void onClick(DialogInterface d, int w) { mOuter.doDelete(); }
    }

    static class RestoreConfirmListener implements DialogInterface.OnClickListener {
        private final SyncActivity mOuter;
        RestoreConfirmListener(SyncActivity outer) { mOuter = outer; }
        public void onClick(DialogInterface d, int w) { mOuter.doRestore(); }
    }

    static class CancelClickListener implements View.OnClickListener {
        private final SyncActivity mOuter;
        CancelClickListener(SyncActivity outer) { mOuter = outer; }
        public void onClick(View v) { mOuter.finish(); }
    }

    static class SelectAllClickListener implements View.OnClickListener {
        private final SyncActivity mOuter;
        SelectAllClickListener(SyncActivity outer) { mOuter = outer; }
        public void onClick(View v) {
            boolean newState = ((CheckBox) v).isChecked();
            mOuter.selectAllInCurrentTab(newState);
        }
    }
}
