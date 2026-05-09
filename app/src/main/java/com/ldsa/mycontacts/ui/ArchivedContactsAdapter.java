package com.ldsa.mycontacts.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ldsa.mycontacts.R;
import com.ldsa.mycontacts.db.ArchivedContact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ArchivedContactsAdapter extends BaseAdapter {

    static final int TYPE_HEADER  = 0;
    static final int TYPE_CONTACT = 1;

    static final int MODE_ALPHA = 0;
    static final int MODE_LABEL = 1;

    static final int[] AVATAR_COLORS = {
        0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A,
        0xFF00838F, 0xFFAD1457, 0xFF4527A0
    };

    private final Context mCtx;
    private int mMode;
    private List<Object> mItems; // String = section header, ArchivedContact = row

    boolean mSelectionMode = false;
    HashSet<Long> mSelected = new HashSet<Long>();

    public ArchivedContactsAdapter(Context ctx, List<ArchivedContact> contacts, int mode) {
        mCtx   = ctx;
        mMode  = mode;
        mItems = buildList(contacts);
    }

    public ArchivedContactsAdapter(Context ctx, List<ArchivedContact> contacts) {
        this(ctx, contacts, MODE_ALPHA);
    }

    public void setMode(int mode) {
        mMode = mode;
    }

    public void setItems(List<ArchivedContact> contacts) {
        mItems = buildList(contacts);
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean on) {
        mSelectionMode = on;
        if (!on) mSelected.clear();
        notifyDataSetChanged();
    }

    public void toggleSelected(long id) {
        if (mSelected.contains(id)) mSelected.remove(id);
        else mSelected.add(id);
        notifyDataSetChanged();
    }

    public Set<Long> getSelectedIds() {
        return new HashSet<Long>(mSelected);
    }

    public int getSelectedCount() {
        return mSelected.size();
    }

    public void clearSelection() {
        mSelected.clear();
        notifyDataSetChanged();
    }

    @Override public int getCount() { return mItems.size(); }
    @Override public Object getItem(int pos) { return mItems.get(pos); }
    @Override public long getItemId(int pos) {
        Object item = mItems.get(pos);
        if (item instanceof ArchivedContact) return ((ArchivedContact) item).id;
        return -1;
    }

    @Override public int getViewTypeCount() { return 2; }
    @Override public int getItemViewType(int pos) {
        return mItems.get(pos) instanceof String ? TYPE_HEADER : TYPE_CONTACT;
    }

    @Override public boolean areAllItemsEnabled() { return false; }
    @Override public boolean isEnabled(int pos) {
        return mItems.get(pos) instanceof ArchivedContact;
    }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        if (getItemViewType(pos) == TYPE_HEADER) {
            TextView tv;
            if (convertView == null) {
                tv = (TextView) LayoutInflater.from(mCtx)
                    .inflate(R.layout.item_section_header, parent, false);
            } else {
                tv = (TextView) convertView;
            }
            tv.setText((String) mItems.get(pos));
            return tv;
        }

        ContactViewHolder vh;
        if (convertView == null) {
            convertView = LayoutInflater.from(mCtx)
                .inflate(R.layout.item_contact_archived, parent, false);
            vh = new ContactViewHolder(convertView);
            convertView.setTag(vh);
        } else {
            vh = (ContactViewHolder) convertView.getTag();
        }

        ArchivedContact c = (ArchivedContact) mItems.get(pos);
        vh.bind(c);

        if (mSelectionMode && mSelected.contains(c.id)) {
            convertView.setBackgroundColor(0xFFBBDEFB);   // light blue — selected
        } else if (mSelectionMode) {
            convertView.setBackgroundColor(0xFFFFFFFF);    // plain white — unselected
        } else {
            convertView.setBackgroundResource(R.drawable.ripple_list_item);
        }

        return convertView;
    }

    private List<Object> buildList(List<ArchivedContact> contacts) {
        return mMode == MODE_LABEL ? buildLabeledList(contacts) : buildSectionedList(contacts);
    }

    private List<Object> buildSectionedList(List<ArchivedContact> contacts) {
        List<Object> items = new ArrayList<Object>();
        String currentSection = "";
        for (ArchivedContact c : contacts) {
            String first;
            if (c.displayName.isEmpty()) {
                first = "#";
            } else {
                char ch = c.displayName.charAt(0);
                first = (ch >= '0' && ch <= '9') ? "#"
                    : String.valueOf(ch).toUpperCase(Locale.US);
            }
            if (!first.equals(currentSection)) {
                items.add(first);
                currentSection = first;
            }
            items.add(c);
        }
        return items;
    }

    private List<Object> buildLabeledList(List<ArchivedContact> contacts) {
        LinkedHashMap<String, List<ArchivedContact>> byLabel =
            new LinkedHashMap<String, List<ArchivedContact>>();
        List<ArchivedContact> noLabel = new ArrayList<ArchivedContact>();

        for (ArchivedContact c : contacts) {
            List<String> labels = c.getLabels();
            if (labels.isEmpty()) {
                noLabel.add(c);
            } else {
                for (String label : labels) {
                    if (!byLabel.containsKey(label)) byLabel.put(label, new ArrayList<ArchivedContact>());
                    byLabel.get(label).add(c);
                }
            }
        }

        List<String> sortedLabels = new ArrayList<String>(byLabel.keySet());
        Collections.sort(sortedLabels, String.CASE_INSENSITIVE_ORDER);

        List<Object> items = new ArrayList<Object>();
        for (String label : sortedLabels) {
            items.add(label);
            for (ArchivedContact c : byLabel.get(label)) items.add(c);
        }
        if (!noLabel.isEmpty()) {
            items.add("No Label");
            for (ArchivedContact c : noLabel) items.add(c);
        }
        return items;
    }

    static class ContactViewHolder {
        final TextView tvAvatar;
        final TextView tvName;
        final TextView tvPhone;
        final TextView tvEmail;

        ContactViewHolder(View v) {
            tvAvatar = (TextView) v.findViewById(R.id.tvAvatar);
            tvName   = (TextView) v.findViewById(R.id.tvName);
            tvPhone  = (TextView) v.findViewById(R.id.tvPhone);
            tvEmail  = (TextView) v.findViewById(R.id.tvEmail);
        }

        void bind(ArchivedContact c) {
            tvName.setText(c.displayName);

            String phone = c.getPrimaryPhone();
            tvPhone.setText(phone);
            tvPhone.setVisibility(phone.isEmpty() ? View.GONE : View.VISIBLE);

            String email = c.getPrimaryEmail();
            tvEmail.setText(email);
            tvEmail.setVisibility(email.isEmpty() ? View.GONE : View.VISIBLE);

            String initial = c.displayName.isEmpty() ? "?"
                : String.valueOf(c.displayName.charAt(0)).toUpperCase(Locale.US);
            tvAvatar.setText(initial);

            int idx = c.displayName.isEmpty() ? 0
                : (c.displayName.charAt(0) % AVATAR_COLORS.length);
            if (idx < 0) idx = 0;
            tvAvatar.setBackgroundColor(AVATAR_COLORS[idx]);
        }
    }
}
