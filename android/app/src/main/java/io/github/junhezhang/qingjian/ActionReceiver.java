package io.github.junhezhang.qingjian;
import android.content.*;
public class ActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String id=intent.getStringExtra("id"); if(id==null) return;
        try(Store store=new Store(c)) {
            Todo t=store.find(id); if(t==null) return;
            switch(String.valueOf(intent.getAction())) {
                case "toggle": t.setDone(!t.done); break;
                case "done": t.setDone(true); break;
                case "snooze": if(t.done || t.due==0 || t.leadMinutes<0) return; t.snooze(System.currentTimeMillis()); break;
                default: return;
            }
            store.save(t); Reminders.cancel(c,id); Reminders.process(c);
        } catch(android.database.SQLException ex) { Reminders.reportFailure(c,ex); }
    }
}
