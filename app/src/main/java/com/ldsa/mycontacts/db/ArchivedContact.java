package com.ldsa.mycontacts.db;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class ArchivedContact {

    public long id;
    public String displayName;
    public String phonesJson;    // JSON array of {type,number}
    public String emailsJson;    // JSON array of {type,address}
    public String organization;
    public String jobTitle;
    public String notes;
    public String labelsJson;    // JSON array of label name strings
    public long archivedAt;      // epoch ms

    public static class Phone {
        public int type;
        public String number;
        public Phone(int type, String number) {
            this.type = type;
            this.number = number;
        }
    }

    public static class Email {
        public int type;
        public String address;
        public Email(int type, String address) {
            this.type = type;
            this.address = address;
        }
    }

    public List<Phone> getPhones() {
        List<Phone> list = new ArrayList<Phone>();
        if (phonesJson == null || phonesJson.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(phonesJson);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                list.add(new Phone(o.optInt("type", 2), o.optString("number", "")));
            }
        } catch (JSONException e) { /* ignore */ }
        return list;
    }

    public List<Email> getEmails() {
        List<Email> list = new ArrayList<Email>();
        if (emailsJson == null || emailsJson.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(emailsJson);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                list.add(new Email(o.optInt("type", 1), o.optString("address", "")));
            }
        } catch (JSONException e) { /* ignore */ }
        return list;
    }

    public String getPrimaryPhone() {
        List<Phone> phones = getPhones();
        return phones.isEmpty() ? "" : phones.get(0).number;
    }

    public String getPrimaryEmail() {
        List<Email> emails = getEmails();
        return emails.isEmpty() ? "" : emails.get(0).address;
    }

    public List<String> getLabels() {
        List<String> list = new ArrayList<String>();
        if (labelsJson == null || labelsJson.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(labelsJson);
            for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
        } catch (JSONException e) { /* ignore */ }
        return list;
    }
}
