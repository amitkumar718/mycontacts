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
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.mycontacts.R;
import com.ldsa.mycontacts.contacts.ContactsHelper;
import com.ldsa.mycontacts.db.ArchivedContact;
import com.ldsa.mycontacts.db.ContactDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
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

        Button btnCall    = (Button) findViewById(R.id.btnCall);
        Button btnRestore = (Button) findViewById(R.id.btnRestore);
        Button btnDelete  = (Button) findViewById(R.id.btnDelete);
        btnCall.setOnClickListener(new CallClickListener(this));
        btnRestore.setOnClickListener(new RestoreClickListener(this));
        btnDelete.setOnClickListener(new DeleteClickListener(this));

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

        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            .format(new Date(mContact.archivedAt));
        TextView tvArchivedOn = (TextView) findViewById(R.id.tvArchivedOn);
        tvArchivedOn.setText(getString(R.string.archived_on) + " " + dateStr);
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
}
