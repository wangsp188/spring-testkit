package com.testkit.util;

public class Container<T> {

    private T data;


    public static <T> Container<T> of(T data) {
        Container<T> container = new Container<>();
        container.data = data;
        return container;
    }

    public T get() {
        return data;
    }

    public void set(T data) {
        this.data = data;
    }


    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
