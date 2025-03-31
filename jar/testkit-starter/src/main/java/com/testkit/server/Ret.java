package com.testkit.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class Ret {
    private boolean success;
    private int cost;
    private String message;

    private Object data;

    private List<Map<String, String>> profile;

    public static Ret fail(String message,int cost) {
        Ret ret = new Ret();
        ret.success = false;
        ret.message = message;
        ret.cost = cost;
        return ret;
    }

    public static Ret fail(String message,int cost, Map<String, String> profile) {
        Ret ret = new Ret();
        ret.success = false;
        ret.message = message;
        if (profile != null && !profile.isEmpty()) {
            ret.profile = new ArrayList<>();
            ret.profile.add(profile);
        }
        return ret;
    }

    public static Ret success(Object data, int cost) {
        Ret ret = new Ret();
        ret.success = true;
        ret.data = data;
        ret.cost = cost;
        return ret;
    }

    public static Ret success(Object data, int cost, Map<String, String> profile) {
        Ret ret = new Ret();
        ret.success = true;
        ret.data = data;
        ret.cost = cost;
        if (profile != null && !profile.isEmpty()) {
            ret.profile = new ArrayList<>();
            ret.profile.add(profile);
        }
        return ret;
    }


    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public List<Map<String, String>> getProfile() {
        return profile;
    }

    public void setProfile(List<Map<String, String>> profile) {
        this.profile = profile;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }
}
