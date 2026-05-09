package com.ldsa.mycontacts.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ldsa.mycontacts.R;
import com.ldsa.mycontacts.db.LabelInfo;

import java.util.List;

public class LabelsAdapter extends BaseAdapter {

    private final Context mCtx;
    private List<LabelInfo> mItems;

    public LabelsAdapter(Context ctx, List<LabelInfo> items) {
        mCtx = ctx;
        mItems = items;
    }

    public void setItems(List<LabelInfo> items) {
        mItems = items;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return mItems.size(); }
    @Override public Object getItem(int pos) { return mItems.get(pos); }
    @Override public long getItemId(int pos) { return pos; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        ViewHolder vh;
        if (convertView == null) {
            convertView = LayoutInflater.from(mCtx)
                .inflate(R.layout.item_label, parent, false);
            vh = new ViewHolder(convertView);
            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }
        LabelInfo info = mItems.get(pos);
        vh.tvName.setText(info.name);
        vh.tvCount.setText(String.valueOf(info.count));
        return convertView;
    }

    static class ViewHolder {
        final TextView tvName;
        final TextView tvCount;

        ViewHolder(View v) {
            tvName  = (TextView) v.findViewById(R.id.tvLabelName);
            tvCount = (TextView) v.findViewById(R.id.tvLabelCount);
        }
    }
}
