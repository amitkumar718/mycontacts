package com.ldsa.mycontacts.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.ldsa.mycontacts.R;
import com.ldsa.mycontacts.db.ContactDatabase;
import com.ldsa.mycontacts.db.LabelInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LabelsActivity extends Activity {

    static final String EXTRA_LABEL = "label_name";

    LabelsAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_labels);

        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        ListView listView = (ListView) findViewById(R.id.listLabels);
        TextView tvEmpty  = (TextView) findViewById(R.id.tvLabelsEmpty);

        List<LabelInfo> labels = loadLabels();
        mAdapter = new LabelsAdapter(this, labels);
        listView.setAdapter(mAdapter);

        if (labels.isEmpty()) {
            listView.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        }

        listView.setOnItemClickListener(new LabelClickListener(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<LabelInfo> labels = loadLabels();
        mAdapter.setItems(labels);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private List<LabelInfo> loadLabels() {
        LinkedHashMap<String, Integer> counts = ContactDatabase.getInstance(this).getAllLabelCounts();
        List<LabelInfo> result = new ArrayList<LabelInfo>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            result.add(new LabelInfo(e.getKey(), e.getValue()));
        }
        return result;
    }

    void openLabelContacts(String label) {
        Intent i = new Intent(this, LabelContactsActivity.class);
        i.putExtra(EXTRA_LABEL, label);
        startActivity(i);
    }

    static class LabelClickListener implements AdapterView.OnItemClickListener {
        private final LabelsActivity mAct;
        LabelClickListener(LabelsActivity act) { mAct = act; }
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            LabelInfo info = (LabelInfo) mAct.mAdapter.getItem(pos);
            mAct.openLabelContacts(info.name);
        }
    }
}
