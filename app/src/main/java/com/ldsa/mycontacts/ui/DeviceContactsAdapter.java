package com.ldsa.mycontacts.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.ldsa.mycontacts.R;
import com.ldsa.mycontacts.contacts.ContactsHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeviceContactsAdapter extends BaseAdapter {

    static final int[] AVATAR_COLORS = {
        0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A,
        0xFF00838F, 0xFFAD1457, 0xFF4527A0
    };

    private final Context mCtx;
    private List<ContactsHelper.DeviceContact> mAll;
    private List<ContactsHelper.DeviceContact> mFiltered;
    private final Set<Long> mSelected = new HashSet<Long>();

    public DeviceContactsAdapter(Context ctx, List<ContactsHelper.DeviceContact> items) {
        mCtx = ctx;
        mAll = items;
        mFiltered = new ArrayList<ContactsHelper.DeviceContact>(items);
    }

    public void filter(String query) {
        mFiltered = new ArrayList<ContactsHelper.DeviceContact>();
        if (query == null || query.trim().isEmpty()) {
            mFiltered.addAll(mAll);
        } else {
            String q = query.trim().toLowerCase();
            for (ContactsHelper.DeviceContact c : mAll) {
                if (c.displayName.toLowerCase().contains(q) ||
                    c.primaryPhone.toLowerCase().contains(q)) {
                    mFiltered.add(c);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void toggleSelected(long contactId) {
        if (mSelected.contains(contactId)) {
            mSelected.remove(contactId);
        } else {
            mSelected.add(contactId);
        }
        notifyDataSetChanged();
    }

    public Set<Long> getSelected() { return mSelected; }

    public void clearSelection() {
        mSelected.clear();
        notifyDataSetChanged();
    }

    @Override public int getCount() { return mFiltered.size(); }
    @Override public Object getItem(int pos) { return mFiltered.get(pos); }
    @Override public long getItemId(int pos) { return mFiltered.get(pos).contactId; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        ViewHolder vh;
        if (convertView == null) {
            convertView = LayoutInflater.from(mCtx)
                .inflate(R.layout.item_contact_device, parent, false);
            vh = new ViewHolder(convertView);
            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }
        ContactsHelper.DeviceContact c = mFiltered.get(pos);
        vh.bind(c, mSelected.contains(c.contactId));
        return convertView;
    }

    static class ViewHolder {
        final CheckBox cbSelect;
        final TextView tvAvatar;
        final TextView tvName;
        final TextView tvPhone;

        ViewHolder(View v) {
            cbSelect = (CheckBox) v.findViewById(R.id.cbSelect);
            tvAvatar = (TextView) v.findViewById(R.id.tvAvatar);
            tvName   = (TextView) v.findViewById(R.id.tvName);
            tvPhone  = (TextView) v.findViewById(R.id.tvPhone);
        }

        void bind(ContactsHelper.DeviceContact c, boolean selected) {
            cbSelect.setChecked(selected);
            tvName.setText(c.displayName);
            tvPhone.setText(c.primaryPhone.isEmpty() ? "" : c.primaryPhone);
            tvPhone.setVisibility(c.primaryPhone.isEmpty() ? View.GONE : View.VISIBLE);

            String initial = c.displayName.isEmpty() ? "?" :
                String.valueOf(c.displayName.charAt(0)).toUpperCase(java.util.Locale.US);
            tvAvatar.setText(initial);

            int idx = c.displayName.isEmpty() ? 0 : (c.displayName.charAt(0) % AVATAR_COLORS.length);
            if (idx < 0) idx = 0;
            tvAvatar.setBackgroundColor(AVATAR_COLORS[idx]);
        }
    }
}
