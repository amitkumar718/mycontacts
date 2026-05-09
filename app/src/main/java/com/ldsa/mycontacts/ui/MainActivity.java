package com.ldsa.mycontacts.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
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
import com.ldsa.mycontacts.contacts.ExportHelper;
import com.ldsa.mycontacts.db.ArchivedContact;
import com.ldsa.mycontacts.db.ContactDatabase;

import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_CONTACTS_PERM = 1;
    static final String EXTRA_CONTACT_ID = "contact_id";

    private EditText mEtSearch;
    private ListView mListView;
    private TextView mTvEmpty;
    private TextView mTvCount;
    private ArchivedContactsAdapter mAdapter;
    private ContactDatabase mDb;
    private List<ArchivedContact> mContacts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDb       = ContactDatabase.getInstance(this);
        mEtSearch = (EditText)  findViewById(R.id.etSearch);
        mListView = (ListView)  findViewById(R.id.listArchived);
        mTvEmpty  = (TextView)  findViewById(R.id.tvEmpty);
        mTvCount  = (TextView)  findViewById(R.id.tvCount);
        Button fabArchive = (Button) findViewById(R.id.fabArchive);

        mContacts = mDb.getAll();
        mAdapter  = new ArchivedContactsAdapter(this, mContacts);
        mListView.setAdapter(mAdapter);

        updateEmptyState();

        mEtSearch.addTextChangedListener(new SearchWatcher(this));
        mListView.setOnItemClickListener(new ItemClickListener(this));
        fabArchive.setOnClickListener(new ArchiveNewClickListener(this));

        checkContactsPermission();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_export) {
            startExport();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload(mEtSearch.getText().toString());
    }

    void reload(String query) {
        mContacts = mDb.search(query);
        mAdapter.setItems(mContacts);
        updateEmptyState();
    }

    private void updateEmptyState() {
        int count = mContacts.size();
        mTvCount.setText(count + " archived");
        mListView.setVisibility(count == 0 ? View.GONE  : View.VISIBLE);
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
            Toast.makeText(this, "No contacts to export", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Uploading to Google Drive…", Toast.LENGTH_SHORT).show();
        ExportHelper.exportToDrive(this, all, new ExportCallbackImpl(this));
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

    static class ArchiveNewClickListener implements View.OnClickListener {
        private final MainActivity mMain;
        ArchiveNewClickListener(MainActivity main) { mMain = main; }
        public void onClick(View v) { mMain.openArchiveScreen(); }
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
}
