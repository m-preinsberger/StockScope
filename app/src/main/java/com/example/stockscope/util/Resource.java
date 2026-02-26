package com.example.stockscope.util;

import androidx.annotation.Nullable;

public final class Resource<T> {
    public enum Status { LOADING, SUCCESS, ERROR }

    public final Status status;
    @Nullable public final T data;
    @Nullable public final String error;

    private Resource(Status status, @Nullable T data, @Nullable String error) {
        this.status = status;
        this.data = data;
        this.error = error;
    }

    public static <T> Resource<T> loading(@Nullable T data) { return new Resource<>(Status.LOADING, data, null); }
    public static <T> Resource<T> success(T data) { return new Resource<>(Status.SUCCESS, data, null); }
    public static <T> Resource<T> error(String msg, @Nullable T data) { return new Resource<>(Status.ERROR, data, msg); }
}
