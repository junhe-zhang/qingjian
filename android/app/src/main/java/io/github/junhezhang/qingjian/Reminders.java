package io.github.junhezhang.qingjian;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import java.util.List;

public final class Reminders {
    static final String CHANNEL = "deadlines";
    public static void init(Context c) {
        NotificationChannel channel = new NotificationChannel(CHANNEL,"待办截止提醒",NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("临近截止、到期及延后提醒"); channel.enableVibration(true);
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    public static boolean allowed(Context c) {
        NotificationManager n = c.getSystemService(NotificationManager.class);
        NotificationChannel channel = n.getNotificationChannel(CHANNEL);
        return (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && n.areNotificationsEnabled() && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }
    public static PendingIntent action(Context c, String action, String id) {
        Intent intent = new Intent(c,ActionReceiver.class).setAction(action).setData(Uri.parse("qingjian://task/"+id+"/"+action)).putExtra("id",id);
        return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static PendingIntent open(Context c) {
        return PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static void cancel(Context c, String id) { c.getSystemService(NotificationManager.class).cancel(id,1); }
    public static void process(Context c) {
        init(c);
        if (allowed(c)) {
            try (Store store = new Store(c)) {
                long now = System.currentTimeMillis();
                for (Todo task : store.all()) {
                    int stage = task.reminderStage(now); if (stage == 0) continue;
                    Notification notification = new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(task.title).setContentText(MainActivity.deadline(task,now))
                        .setStyle(new Notification.BigTextStyle().bigText(MainActivity.deadline(task,now)+(task.notes.isEmpty()?"":"\n"+task.notes)))
                        .setContentIntent(open(c)).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                        .addAction(new Notification.Action.Builder(null,"标记完成",action(c,"done",task.id)).build())
                        .addAction(new Notification.Action.Builder(null,"10 分钟后",action(c,"snooze",task.id)).build()).build();
                    c.getSystemService(NotificationManager.class).notify(task.id,1,notification);
                    task.notifiedStage=stage; task.snoozedUntil=0; store.save(task);
                }
            }
        }
        schedule(c); TodoWidget.updateAll(c);
    }
    @SuppressLint("ScheduleExactAlarm")
    public static void schedule(Context c) {
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        PendingIntent pending = PendingIntent.getBroadcast(c,1,new Intent(c,AlarmReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(pending);
        if (!allowed(c)) return;
        long next=Long.MAX_VALUE, now=System.currentTimeMillis();
        try (Store store = new Store(c)) { for (Todo t : store.all()) next=Math.min(next,t.nextWake(now)); }
        if(next==Long.MAX_VALUE) return;
        if(Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pending);
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pending);
    }
    public static void reportFailure(Context c, Exception error) {
        android.util.Log.e("QingJian","Reminder update failed; user data kept",error);
        init(c);
        if(allowed(c)) c.getSystemService(NotificationManager.class).notify("storage-error",2,new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("青笺未能更新提醒").setContentText("请打开应用检查；原有数据没有被清空。").setContentIntent(open(c)).setAutoCancel(true).build());
    }
}
