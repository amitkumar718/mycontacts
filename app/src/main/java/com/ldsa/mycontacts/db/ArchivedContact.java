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

    // v3 fields — extended contact details
    public String firstName;
    public String lastName;
    public String namePrefix;
    public String nameSuffix;
    public String nickname;
    public String websitesJson;   // JSON array of {type,url}
    public String addressesJson;  // JSON array of {type,street,city,region,postcode,country}
    public String eventsJson;     // JSON array of {type,date,label}  (type: birthday|anniversary|other|custom)

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

    public static class Website {
        public int type;
        public String url;
        public Website(int type, String url) {
            this.type = type;
            this.url = url;
        }
    }

    public static class Address {
        public int type;
        public String street, city, region, postcode, country;
    }

    public static class Event {
        public int type;      // ContactsContract.CommonDataKinds.Event.TYPE_* (0=custom, 1=anniversary, 2=other, 3=birthday)
        public String date;   // typically yyyy-MM-dd, but may be --MM-dd for year-less
        public String label;  // used when type == custom
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

    public List<Website> getWebsites() {
        List<Website> list = new ArrayList<Website>();
        if (websitesJson == null || websitesJson.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(websitesJson);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                list.add(new Website(o.optInt("type", 7), o.optString("url", "")));
            }
        } catch (JSONException e) { /* ignore */ }
        return list;
    }

    public List<Address> getAddresses() {
        List<Address> list = new ArrayList<Address>();
        if (addressesJson == null || addressesJson.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(addressesJson);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                Address a = new Address();
                a.type     = o.optInt("type", 1);
                a.street   = o.optString("street", "");
                a.city     = o.optString("city", "");
                a.region   = o.optString("region", "");
                a.postcode = o.optString("postcode", "");
                a.country  = o.optString("country", "");
                list.add(a);
            }
        } catch (JSONException e) { /* ignore */ }
        return list;
    }

    public List<Event> getEvents() {
        List<Event> list = new ArrayList<Event>();
        if (eventsJson == null || eventsJson.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(eventsJson);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                Event ev = new Event();
                ev.type  = o.optInt("type", 3);
                ev.date  = o.optString("date", "");
                ev.label = o.optString("label", "");
                list.add(ev);
            }
        } catch (JSONException e) { /* ignore */ }
        return list;
    }
}
