package com.ldsa.mycontacts.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.mycontacts.R;
import com.ldsa.mycontacts.contacts.ContactsHelper;
import com.ldsa.mycontacts.db.ArchivedContact;
import com.ldsa.mycontacts.db.ContactDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DeviceContactsActivity extends Activity {

    private ListView mListView;
    private TextView mTvEmpty;
    private TextView mTvSelected;
    private Button mBtnArchive;
    private ProgressBar mProgress;
    private DeviceContactsAdapter mAdapter;
    private ContactDatabase mDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_contacts);
        setTitle(R.string.title_device_contacts);

        mDb = ContactDatabase.getInstance(this);
        EditText etSearch = (EditText) findViewById(R.id.etSearch);
        mListView   = (ListView) findViewById(R.id.listContacts);
        mTvEmpty    = (TextView) findViewById(R.id.tvEmpty);
        mTvSelected = (TextView) findViewById(R.id.tvSelected);
        mBtnArchive = (Button) findViewById(R.id.btnArchiveSelected);
        mProgress   = (ProgressBar) findViewById(R.id.progress);

        mAdapter = new DeviceContactsAdapter(this, new ArrayList<ContactsHelper.DeviceContact>());
        mListView.setAdapter(mAdapter);

        etSearch.addTextChangedListener(new SearchWatcher(this));
        mListView.setOnItemClickListener(new ItemClickListener(this));
        mBtnArchive.setOnClickListener(new ArchiveClickListener(this));

        loadContacts();
    }

    private void loadContacts() {
        mProgress.setVisibility(View.VISIBLE);
        mListView.setVisibility(View.GONE);
        final Handler handler = new Handler(Looper.getMainLooper());
        Thread t = new LoadThread(this, handler);
        t.start();
    }

    void onContactsLoaded(List<ContactsHelper.DeviceContact> contacts) {
        mProgress.setVisibility(View.GONE);
        mAdapter = new DeviceContactsAdapter(this, contacts);
        mListView.setAdapter(mAdapter);
        updateState();
    }

    void updateState() {
        int selCount = mAdapter.getSelected().size();
        mTvSelected.setText(selCount + " selected");
        mBtnArchive.setEnabled(selCount > 0);
        int total = mAdapter.getCount();
        if (total == 0) {
            mListView.setVisibility(View.GONE);
            mTvEmpty.setVisibility(View.VISIBLE);
        } else {
            mListView.setVisibility(View.VISIBLE);
            mTvEmpty.setVisibility(View.GONE);
        }
    }

    void onItemTap(int pos) {
        ContactsHelper.DeviceContact c = (ContactsHelper.DeviceContact) mAdapter.getItem(pos);
        mAdapter.toggleSelected(c.contactId);
        updateState();
    }

    void confirmAndArchive() {
        Set<Long> selected = mAdapter.getSelected();
        if (selected.isEmpty()) return;
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setMessage(R.string.confirm_archive);
        b.setPositiveButton(R.string.yes, new ArchiveConfirmListener(this));
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    void doArchive() {
        Set<Long> ids = mAdapter.getSelected();
        mProgress.setVisibility(View.VISIBLE);
        final Handler handler = new Handler(Looper.getMainLooper());
        Thread t = new ArchiveThread(this, ids, handler);
        t.start();
    }

    void onArchiveDone(int count, String error) {
        mProgress.setVisibility(View.GONE);
        if (error != null) {
            Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, count + " contact(s) archived", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // --- Static thread classes ---

    static class LoadThread extends Thread {
        private final DeviceContactsActivity mAct;
        private final Handler mHandler;

        LoadThread(DeviceContactsActivity act, Handler handler) {
            mAct = act;
            mHandler = handler;
        }

        public void run() {
            final List<ContactsHelper.DeviceContact> contacts =
                ContactsHelper.loadDeviceContacts(mAct);
            mHandler.post(new LoadResultRunnable(mAct, contacts));
        }
    }

    static class LoadResultRunnable implements Runnable {
        private final DeviceContactsActivity mAct;
        private final List<ContactsHelper.DeviceContact> mContacts;
        LoadResultRunnable(DeviceContactsActivity act, List<ContactsHelper.DeviceContact> c) {
            mAct = act; mContacts = c;
        }
        public void run() { mAct.onContactsLoaded(mContacts); }
    }

    static class ArchiveThread extends Thread {
        private final DeviceContactsActivity mAct;
        private final Set<Long> mIds;
        private final Handler mHandler;

        ArchiveThread(DeviceContactsActivity act, Set<Long> ids, Handler handler) {
            mAct = act; mIds = ids; mHandler = handler;
        }

        public void run() {
            int count = 0;
            String error = null;
            ContactDatabase db = ContactDatabase.getInstance(mAct);
            for (long id : mIds) {
                try {
                    ArchivedContact ac = ContactsHelper.readFullContact(mAct, id);
                    db.insert(ac);
                    ContactsHelper.deleteDeviceContact(mAct, id);
                    count++;
                } catch (Exception e) {
                    error = e.getMessage();
                }
            }
            final int finalCount = count;
            final String finalError = error;
            mHandler.post(new ArchiveResultRunnable(mAct, finalCount, finalError));
        }
    }

    static class ArchiveResultRunnable implements Runnable {
        private final DeviceContactsActivity mAct;
        private final int mCount;
        private final String mError;
        ArchiveResultRunnable(DeviceContactsActivity act, int count, String error) {
            mAct = act; mCount = count; mError = error;
        }
        public void run() { mAct.onArchiveDone(mCount, mError); }
    }

    // --- Static listener classes ---

    static class SearchWatcher implements TextWatcher {
        private final DeviceContactsActivity mAct;
        SearchWatcher(DeviceContactsActivity act) { mAct = act; }
        public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
        public void onTextChanged(CharSequence s, int st, int b, int c) {}
        public void afterTextChanged(Editable s) {
            mAct.mAdapter.filter(s.toString());
            mAct.updateState();
        }
    }

    static class ItemClickListener implements AdapterView.OnItemClickListener {
        private final DeviceContactsActivity mAct;
        ItemClickListener(DeviceContactsActivity act) { mAct = act; }
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            mAct.onItemTap(pos);
        }
    }

    static class ArchiveClickListener implements View.OnClickListener {
        private final DeviceContactsActivity mAct;
        ArchiveClickListener(DeviceContactsActivity act) { mAct = act; }
        public void onClick(View v) { mAct.confirmAndArchive(); }
    }

    static class ArchiveConfirmListener implements DialogInterface.OnClickListener {
        private final DeviceContactsActivity mAct;
        ArchiveConfirmListener(DeviceContactsActivity act) { mAct = act; }
        public void onClick(DialogInterface dialog, int which) { mAct.doArchive(); }
    }
}
