package com.instagramclone.backend.notification;

public class NotificationCountResponse {
    private long count;

    public NotificationCountResponse(long count) {
        this.count = count;
    }

    public long getCount() {
        return count;
    }
}
