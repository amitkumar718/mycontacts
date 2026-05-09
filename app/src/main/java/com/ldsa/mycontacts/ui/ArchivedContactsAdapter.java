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
import java.util.List;
import java.util.Locale;

public class ArchivedContactsAdapter extends BaseAdapter {

    static final int TYPE_HEADER  = 0;
    static final int TYPE_CONTACT = 1;

    static final int[] AVATAR_COLORS = {
        0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A,
        0xFF00838F, 0xFFAD1457, 0xFF4527A0
    };

    private final Context mCtx;
    private List<Object> mItems; // String = section header, ArchivedContact = row

    public ArchivedContactsAdapter(Context ctx, List<ArchivedContact> contacts) {
        mCtx   = ctx;
        mItems = buildSectionedList(contacts);
    }

    public void setItems(List<ArchivedContact> contacts) {
        mItems = buildSectionedList(contacts);
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
        convertView.setBackgroundResource(R.drawable.ripple_list_item);
        return convertView;
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
