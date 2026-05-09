package com.ldsa.mycontacts.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.mycontacts.R;
import com.ldsa.mycontacts.db.ArchivedContact;
import com.ldsa.mycontacts.db.ContactDatabase;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class LabelContactsActivity extends Activity {

    private ArchivedContactsAdapter mAdapter;
    private String mLabel;
    private ListView mListView;
    private TextView mTvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_label_contacts);

        mLabel = getIntent().getStringExtra(LabelsActivity.EXTRA_LABEL);
        if (mLabel == null) { finish(); return; }

        setTitle(mLabel);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        mListView = (ListView) findViewById(R.id.listLabelContacts);
        mTvEmpty  = (TextView) findViewById(R.id.tvLabelContactsEmpty);
        Button fabAdd = (Button) findViewById(R.id.fabAddContacts);

        List<ArchivedContact> contacts = ContactDatabase.getInstance(this).getByLabel(mLabel);
        mAdapter = new ArchivedContactsAdapter(this, contacts);
        mListView.setAdapter(mAdapter);
        updateEmptyState(contacts.size());

        mListView.setOnItemClickListener(new ItemClickListener(this));
        fabAdd.setOnClickListener(new AddContactsClickListener(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void reload() {
        List<ArchivedContact> contacts = ContactDatabase.getInstance(this).getByLabel(mLabel);
        mAdapter.setItems(contacts);
        updateEmptyState(contacts.size());
    }

    private void updateEmptyState(int count) {
        if (count == 0) {
            mListView.setVisibility(View.GONE);
            mTvEmpty.setVisibility(View.VISIBLE);
        } else {
            mListView.setVisibility(View.VISIBLE);
            mTvEmpty.setVisibility(View.GONE);
        }
    }

    void openDetail(long id) {
        Intent i = new Intent(this, ContactDetailActivity.class);
        i.putExtra(MainActivity.EXTRA_CONTACT_ID, id);
        startActivity(i);
    }

    void showAddContactsDialog(boolean hideLabeled) {
        ContactDatabase db = ContactDatabase.getInstance(this);
        List<ArchivedContact> all = db.getAll();

        // Apply filter: when hideLabeled is true, keep only contacts with no labels at all
        List<ArchivedContact> visible = new ArrayList<ArchivedContact>();
        for (ArchivedContact c : all) {
            if (hideLabeled && !c.getLabels().isEmpty()) continue;
            visible.add(c);
        }

        if (visible.isEmpty()) {
            int msg = hideLabeled ? R.string.no_unlabeled_contacts : R.string.empty_archived;
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] names    = new String[visible.size()];
        final boolean[] checked  = new boolean[visible.size()];
        final long[] ids         = new long[visible.size()];

        for (int i = 0; i < visible.size(); i++) {
            ArchivedContact c = visible.get(i);
            names[i]   = c.displayName.isEmpty() ? getString(R.string.no_name) : c.displayName;
            checked[i] = c.getLabels().contains(mLabel);
            ids[i]     = c.id;
        }

        String filterBtn = getString(hideLabeled ? R.string.show_all_contacts : R.string.hide_labeled_contacts);

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(getString(R.string.add_contacts_to_label) + " \"" + mLabel + "\"");
        b.setMultiChoiceItems(names, checked, new ContactCheckListener(checked));
        b.setPositiveButton(R.string.save, new ContactAddSaveListener(this, ids, checked));
        b.setNeutralButton(filterBtn, new ToggleFilterListener(this, !hideLabeled));
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    void applyContactSelections(long[] ids, boolean[] checked) {
        ContactDatabase db = ContactDatabase.getInstance(this);
        List<ArchivedContact> all = db.getAll();

        java.util.HashMap<Long, ArchivedContact> map = new java.util.HashMap<Long, ArchivedContact>();
        for (ArchivedContact c : all) map.put(c.id, c);

        int changed = 0;
        for (int i = 0; i < ids.length; i++) {
            ArchivedContact c = map.get(ids[i]);
            if (c == null) continue;
            List<String> labels = c.getLabels();
            boolean hadLabel = labels.contains(mLabel);
            if (checked[i] && !hadLabel) {
                labels.add(mLabel);
                saveLabels(db, c.id, labels);
                changed++;
            } else if (!checked[i] && hadLabel) {
                labels.remove(mLabel);
                saveLabels(db, c.id, labels);
                changed++;
            }
        }

        reload();
        if (changed > 0) {
            Toast.makeText(this, changed + " " + getString(R.string.contacts_updated), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveLabels(ContactDatabase db, long id, List<String> labels) {
        try {
            JSONArray arr = new JSONArray();
            for (String l : labels) arr.put(l);
            db.updateLabels(id, arr.toString());
        } catch (Exception e) { /* ignore */ }
    }

    // --- Static listeners ---

    static class ItemClickListener implements AdapterView.OnItemClickListener {
        private final LabelContactsActivity mAct;
        ItemClickListener(LabelContactsActivity act) { mAct = act; }
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            if (id < 0) return;
            mAct.openDetail(id);
        }
    }

    static class AddContactsClickListener implements View.OnClickListener {
        private final LabelContactsActivity mAct;
        AddContactsClickListener(LabelContactsActivity act) { mAct = act; }
        public void onClick(View v) { mAct.showAddContactsDialog(false); }
    }

    static class ContactCheckListener implements DialogInterface.OnMultiChoiceClickListener {
        private final boolean[] mChecked;
        ContactCheckListener(boolean[] checked) { mChecked = checked; }
        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
            mChecked[which] = isChecked;
        }
    }

    static class ToggleFilterListener implements DialogInterface.OnClickListener {
        private final LabelContactsActivity mAct;
        private final boolean mHideLabeled;
        ToggleFilterListener(LabelContactsActivity act, boolean hideLabeled) {
            mAct = act; mHideLabeled = hideLabeled;
        }
        public void onClick(DialogInterface dialog, int which) {
            mAct.showAddContactsDialog(mHideLabeled);
        }
    }

    static class ContactAddSaveListener implements DialogInterface.OnClickListener {
        private final LabelContactsActivity mAct;
        private final long[] mIds;
        private final boolean[] mChecked;
        ContactAddSaveListener(LabelContactsActivity act, long[] ids, boolean[] checked) {
            mAct = act; mIds = ids; mChecked = checked;
        }
        public void onClick(DialogInterface dialog, int which) {
            mAct.applyContactSelections(mIds, mChecked);
        }
    }
}
