package io.github.junhezhang.qingjian;
import android.content.*;
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) { try { Reminders.process(c); } catch (android.database.SQLException ex) { Reminders.reportFailure(c,ex); } }
}
