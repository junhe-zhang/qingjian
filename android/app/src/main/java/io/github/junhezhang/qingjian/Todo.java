package io.github.junhezhang.qingjian;

import java.util.UUID;

public class Todo {
    public String id = UUID.randomUUID().toString();
    public String title = "", notes = "";
    public long due, snoozedUntil, created = System.currentTimeMillis();
    public boolean done, desktop = true;
    public int leadMinutes = 1440, notifiedStage;

    public int reminderStage(long now) {
        if (done || due == 0 || leadMinutes < 0 || snoozedUntil > now) return 0;
        int stage = now >= due ? 2 : (now >= due - leadMinutes * 60_000L ? 1 : 0);
        return stage > notifiedStage ? stage : 0;
    }

    public long nextWake(long now) {
        if (done || due == 0 || leadMinutes < 0 || notifiedStage >= 2) return Long.MAX_VALUE;
        if (snoozedUntil > now) return snoozedUntil;
        long next = notifiedStage == 0 ? due - leadMinutes * 60_000L : due;
        return Math.max(now + 1000, next);
    }

    public void setDone(boolean value) { done = value; if (!value) resetReminder(); }
    public void resetReminder() { notifiedStage = 0; snoozedUntil = 0; }
    public void snooze(long now) { notifiedStage = 0; snoozedUntil = now + 600_000; }
}
