package com.testkit.side_server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class Ret {
    private boolean success;

    private String message;

    private Object data;

    private List<Map<String,String>> profile;

    public static Ret fail(String message) {
        Ret ret = new Ret();
        ret.success = false;
        ret.message = message;
        return ret;
    }

    public static Ret fail(String message,Map<String,String> profile) {
        Ret ret = new Ret();
        ret.success = false;
        ret.message = message;
        if (profile!=null && !profile.isEmpty()) {
            ret.profile = new ArrayList<>();
            ret.profile.add(profile);
        }
        return ret;
    }

    public static Ret success(Object data) {
        Ret ret = new Ret();
        ret.success = true;
        ret.data = data;
        return ret;
    }

    public static Ret success(Object data, Map<String,String> profile) {
        Ret ret = new Ret();
        ret.success = true;
        ret.data = data;
        if (profile!=null && !profile.isEmpty()) {
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
}
