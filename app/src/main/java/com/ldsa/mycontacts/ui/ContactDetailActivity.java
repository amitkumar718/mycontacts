package com.ldsa.mycontacts.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.mycontacts.R;
import com.ldsa.mycontacts.contacts.ContactsHelper;
import com.ldsa.mycontacts.db.ArchivedContact;
import com.ldsa.mycontacts.db.ContactDatabase;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class ContactDetailActivity extends Activity {

    private ArchivedContact mContact;
    private ContactDatabase mDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_detail);

        mDb = ContactDatabase.getInstance(this);
        long id = getIntent().getLongExtra(MainActivity.EXTRA_CONTACT_ID, -1);
        if (id < 0) { finish(); return; }

        mContact = mDb.getById(id);
        if (mContact == null) { finish(); return; }

        bindViews();

        Button btnCall       = (Button) findViewById(R.id.btnCall);
        Button btnRestore    = (Button) findViewById(R.id.btnRestore);
        Button btnDelete     = (Button) findViewById(R.id.btnDelete);
        Button btnEditLabels = (Button) findViewById(R.id.btnEditLabels);
        btnCall.setOnClickListener(new CallClickListener(this));
        btnRestore.setOnClickListener(new RestoreClickListener(this));
        btnDelete.setOnClickListener(new DeleteClickListener(this));
        btnEditLabels.setOnClickListener(new EditLabelsClickListener(this));

        if (mContact.getPhones().isEmpty()) btnCall.setEnabled(false);
    }

    private void bindViews() {
        TextView tvAvatar = (TextView) findViewById(R.id.tvAvatar);
        TextView tvName   = (TextView) findViewById(R.id.tvName);

        String initial = mContact.displayName.isEmpty() ? "?" :
            String.valueOf(mContact.displayName.charAt(0)).toUpperCase();
        tvAvatar.setText(initial);
        tvName.setText(mContact.displayName);

        List<ArchivedContact.Phone> phones = mContact.getPhones();
        if (!phones.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ArchivedContact.Phone p : phones) sb.append(p.number).append("\n");
            showField(R.id.labelPhones, R.id.tvPhones, sb.toString().trim());
        }

        List<ArchivedContact.Email> emails = mContact.getEmails();
        if (!emails.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ArchivedContact.Email e : emails) sb.append(e.address).append("\n");
            showField(R.id.labelEmails, R.id.tvEmails, sb.toString().trim());
        }

        if (mContact.organization != null && !mContact.organization.isEmpty()) {
            String org = mContact.organization;
            if (mContact.jobTitle != null && !mContact.jobTitle.isEmpty()) {
                org = mContact.jobTitle + " · " + org;
            }
            showField(R.id.labelOrg, R.id.tvOrg, org);
        }

        if (mContact.notes != null && !mContact.notes.isEmpty()) {
            showField(R.id.labelNotes, R.id.tvNotes, mContact.notes);
        }

        // Full name — show only if first/last/prefix/suffix provide more than displayName already does
        String full = buildFullName();
        if (!full.isEmpty() && !full.equalsIgnoreCase(mContact.displayName)) {
            showField(R.id.labelFullName, R.id.tvFullName, full);
        }

        if (mContact.nickname != null && !mContact.nickname.isEmpty()) {
            showField(R.id.labelNickname, R.id.tvNickname, mContact.nickname);
        }

        List<ArchivedContact.Address> addrs = mContact.getAddresses();
        if (!addrs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < addrs.size(); i++) {
                if (i > 0) sb.append("\n\n");
                sb.append(formatAddress(addrs.get(i)));
            }
            showField(R.id.labelAddresses, R.id.tvAddresses, sb.toString());
        }

        List<ArchivedContact.Website> webs = mContact.getWebsites();
        if (!webs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ArchivedContact.Website w : webs) sb.append(w.url).append("\n");
            showField(R.id.labelWebsites, R.id.tvWebsites, sb.toString().trim());
        }

        List<ArchivedContact.Event> events = mContact.getEvents();
        if (!events.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < events.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(formatEvent(events.get(i)));
            }
            showField(R.id.labelEvents, R.id.tvEvents, sb.toString());
        }

        refreshLabelsView();

        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            .format(new Date(mContact.archivedAt));
        ((TextView) findViewById(R.id.tvArchivedOn))
            .setText(getString(R.string.archived_on) + " " + dateStr);
    }

    private void refreshLabelsView() {
        List<String> labels = mContact.getLabels();
        if (labels.isEmpty()) {
            findViewById(R.id.labelLabels).setVisibility(View.GONE);
            findViewById(R.id.tvLabels).setVisibility(View.GONE);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < labels.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(labels.get(i));
            }
            showField(R.id.labelLabels, R.id.tvLabels, sb.toString());
        }
    }

    private String buildFullName() {
        StringBuilder sb = new StringBuilder();
        if (mContact.namePrefix != null && !mContact.namePrefix.isEmpty()) sb.append(mContact.namePrefix).append(" ");
        if (mContact.firstName  != null && !mContact.firstName.isEmpty())  sb.append(mContact.firstName).append(" ");
        if (mContact.lastName   != null && !mContact.lastName.isEmpty())   sb.append(mContact.lastName).append(" ");
        if (mContact.nameSuffix != null && !mContact.nameSuffix.isEmpty()) sb.append(mContact.nameSuffix);
        return sb.toString().trim();
    }

    private String formatAddress(ArchivedContact.Address a) {
        StringBuilder sb = new StringBuilder();
        if (a.street   != null && !a.street.isEmpty())   sb.append(a.street).append("\n");
        StringBuilder line2 = new StringBuilder();
        if (a.city     != null && !a.city.isEmpty())     line2.append(a.city);
        if (a.region   != null && !a.region.isEmpty())   { if (line2.length() > 0) line2.append(", "); line2.append(a.region); }
        if (a.postcode != null && !a.postcode.isEmpty()) { if (line2.length() > 0) line2.append(" ");  line2.append(a.postcode); }
        if (line2.length() > 0) sb.append(line2.toString()).append("\n");
        if (a.country  != null && !a.country.isEmpty())  sb.append(a.country);
        return sb.toString().trim();
    }

    private String formatEvent(ArchivedContact.Event ev) {
        String label;
        switch (ev.type) {
            case 3: label = "Birthday"; break;
            case 1: label = "Anniversary"; break;
            case 2: label = "Other"; break;
            case 0: label = (ev.label != null && !ev.label.isEmpty()) ? ev.label : "Custom"; break;
            default: label = "Event";
        }
        return label + ": " + ev.date;
    }

    private void showField(int labelId, int valueId, String text) {
        ((TextView) findViewById(labelId)).setVisibility(View.VISIBLE);
        TextView value = (TextView) findViewById(valueId);
        value.setVisibility(View.VISIBLE);
        value.setText(text);
    }

    void initiateCall() {
        List<ArchivedContact.Phone> phones = mContact.getPhones();
        if (phones.isEmpty()) return;
        if (phones.size() == 1) { dialNumber(phones.get(0).number); return; }
        String[] numbers = new String[phones.size()];
        for (int i = 0; i < phones.size(); i++) numbers[i] = phones.get(i).number;
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Call " + mContact.displayName);
        b.setItems(numbers, new PhonePickerListener(this, numbers));
        b.show();
    }

    void dialNumber(String number) {
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))));
    }

    void confirmRestore() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setMessage(R.string.confirm_restore);
        b.setPositiveButton(R.string.yes, new RestoreConfirmListener(this));
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    void doRestore() {
        new RestoreThread(this, mContact, new Handler(Looper.getMainLooper())).start();
    }

    void onRestoreDone(String error) {
        if (error != null) {
            Toast.makeText(this, "Restore failed: " + error, Toast.LENGTH_LONG).show();
        } else {
            mDb.delete(mContact.id);
            Toast.makeText(this, mContact.displayName + " restored", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    void confirmDelete() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setMessage(R.string.confirm_delete);
        b.setPositiveButton(R.string.yes, new DeleteConfirmListener(this));
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    void doDelete() {
        mDb.delete(mContact.id);
        Toast.makeText(this, mContact.displayName + " deleted", Toast.LENGTH_SHORT).show();
        finish();
    }

    void showEditLabelsDialog() {
        LinkedHashMap<String, Integer> allCounts = mDb.getAllLabelCounts();
        List<String> currentLabels = mContact.getLabels();
        LinkedHashMap<String, Boolean> allLabels = new LinkedHashMap<String, Boolean>();
        for (String l : currentLabels) allLabels.put(l, Boolean.TRUE);
        for (String l : allCounts.keySet()) {
            if (!allLabels.containsKey(l)) allLabels.put(l, Boolean.FALSE);
        }

        List<String> nameList = new ArrayList<String>(allLabels.keySet());
        final String[] names  = nameList.toArray(new String[0]);
        final boolean[] checked = new boolean[names.length];
        for (int i = 0; i < names.length; i++) checked[i] = Boolean.TRUE.equals(allLabels.get(names[i]));

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.edit_labels);
        b.setMultiChoiceItems(names, checked, new LabelCheckListener(checked));
        b.setPositiveButton(R.string.save, new LabelSaveListener(this, names, checked));
        b.setNeutralButton(R.string.new_label, new NewLabelClickListener(this));
        b.show();
    }

    void saveLabels(String[] names, boolean[] checked) {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < names.length; i++) {
                if (checked[i]) arr.put(names[i]);
            }
            String json = arr.toString();
            mDb.updateLabels(mContact.id, json);
            mContact.labelsJson = json;
            refreshLabelsView();
            Toast.makeText(this, R.string.labels_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save labels", Toast.LENGTH_SHORT).show();
        }
    }

    void showNewLabelDialog() {
        EditText et = new EditText(this);
        et.setHint(R.string.label_name_hint);
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.new_label);
        b.setView(et);
        b.setPositiveButton(R.string.add, new NewLabelConfirmListener(this, et));
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    void addNewLabel(String name) {
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();
        List<String> current = mContact.getLabels();
        if (!current.contains(name)) {
            current.add(name);
            try {
                JSONArray arr = new JSONArray();
                for (String l : current) arr.put(l);
                String json = arr.toString();
                mDb.updateLabels(mContact.id, json);
                mContact.labelsJson = json;
                refreshLabelsView();
            } catch (Exception e) { /* ignore */ }
        }
        Toast.makeText(this, "\"" + name + "\" added", Toast.LENGTH_SHORT).show();
    }

    // --- Static threads ---

    static class RestoreThread extends Thread {
        private final ContactDetailActivity mAct;
        private final ArchivedContact mContact;
        private final Handler mHandler;
        RestoreThread(ContactDetailActivity act, ArchivedContact contact, Handler handler) {
            mAct = act; mContact = contact; mHandler = handler;
        }
        public void run() {
            String error = null;
            try { ContactsHelper.restoreContact(mAct, mContact); } catch (Exception e) { error = e.getMessage(); }
            mHandler.post(new RestoreResultRunnable(mAct, error));
        }
    }

    static class RestoreResultRunnable implements Runnable {
        private final ContactDetailActivity mAct;
        private final String mError;
        RestoreResultRunnable(ContactDetailActivity act, String error) { mAct = act; mError = error; }
        public void run() { mAct.onRestoreDone(mError); }
    }

    // --- Static listeners ---

    static class CallClickListener implements View.OnClickListener {
        private final ContactDetailActivity mAct;
        CallClickListener(ContactDetailActivity act) { mAct = act; }
        public void onClick(View v) { mAct.initiateCall(); }
    }

    static class PhonePickerListener implements DialogInterface.OnClickListener {
        private final ContactDetailActivity mAct;
        private final String[] mNumbers;
        PhonePickerListener(ContactDetailActivity act, String[] numbers) { mAct = act; mNumbers = numbers; }
        public void onClick(DialogInterface dialog, int which) { mAct.dialNumber(mNumbers[which]); }
    }

    static class RestoreClickListener implements View.OnClickListener {
        private final ContactDetailActivity mAct;
        RestoreClickListener(ContactDetailActivity act) { mAct = act; }
        public void onClick(View v) { mAct.confirmRestore(); }
    }

    static class DeleteClickListener implements View.OnClickListener {
        private final ContactDetailActivity mAct;
        DeleteClickListener(ContactDetailActivity act) { mAct = act; }
        public void onClick(View v) { mAct.confirmDelete(); }
    }

    static class RestoreConfirmListener implements DialogInterface.OnClickListener {
        private final ContactDetailActivity mAct;
        RestoreConfirmListener(ContactDetailActivity act) { mAct = act; }
        public void onClick(DialogInterface dialog, int which) { mAct.doRestore(); }
    }

    static class DeleteConfirmListener implements DialogInterface.OnClickListener {
        private final ContactDetailActivity mAct;
        DeleteConfirmListener(ContactDetailActivity act) { mAct = act; }
        public void onClick(DialogInterface dialog, int which) { mAct.doDelete(); }
    }

    static class EditLabelsClickListener implements View.OnClickListener {
        private final ContactDetailActivity mAct;
        EditLabelsClickListener(ContactDetailActivity act) { mAct = act; }
        public void onClick(View v) { mAct.showEditLabelsDialog(); }
    }

    static class LabelCheckListener implements DialogInterface.OnMultiChoiceClickListener {
        private final boolean[] mChecked;
        LabelCheckListener(boolean[] checked) { mChecked = checked; }
        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
            mChecked[which] = isChecked;
        }
    }

    static class LabelSaveListener implements DialogInterface.OnClickListener {
        private final ContactDetailActivity mAct;
        private final String[] mNames;
        private final boolean[] mChecked;
        LabelSaveListener(ContactDetailActivity act, String[] names, boolean[] checked) {
            mAct = act; mNames = names; mChecked = checked;
        }
        public void onClick(DialogInterface dialog, int which) { mAct.saveLabels(mNames, mChecked); }
    }

    static class NewLabelClickListener implements DialogInterface.OnClickListener {
        private final ContactDetailActivity mAct;
        NewLabelClickListener(ContactDetailActivity act) { mAct = act; }
        public void onClick(DialogInterface dialog, int which) { mAct.showNewLabelDialog(); }
    }

    static class NewLabelConfirmListener implements DialogInterface.OnClickListener {
        private final ContactDetailActivity mAct;
        private final EditText mEt;
        NewLabelConfirmListener(ContactDetailActivity act, EditText et) { mAct = act; mEt = et; }
        public void onClick(DialogInterface dialog, int which) {
            mAct.addNewLabel(mEt.getText().toString());
        }
    }
}
