package io.github.junhezhang.qingjian;

import android.appwidget.*;
import android.content.*;
import android.graphics.Paint;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.util.*;

public class TodoWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids) { for(int id:ids) update(c,manager,id); }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager manager,int id,Bundle options) { update(c,manager,id); }
    public static void updateAll(Context c) {
        AppWidgetManager manager=AppWidgetManager.getInstance(c);
        for(int id:manager.getAppWidgetIds(new ComponentName(c,TodoWidget.class))) update(c,manager,id);
    }
    static void update(Context c,AppWidgetManager manager,int widgetId) {
        manager.updateAppWidget(widgetId,build(c,manager.getAppWidgetOptions(widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,250)));
    }
    static RemoteViews build(Context c,int height) {
        RemoteViews root=new RemoteViews(c.getPackageName(),R.layout.widget);
        root.setOnClickPendingIntent(R.id.widget_header,Reminders.open(c)); root.setOnClickPendingIntent(R.id.widget_footer,Reminders.open(c));
        root.removeAllViews(R.id.widget_items);
        try(Store store=new Store(c)) {
            List<Todo> selected=new ArrayList<>(); for(Todo t:store.all()) if(t.desktop) selected.add(t);
            int capacity=Math.max(1,Math.min(6,(height-100)/60));
            for(int i=0;i<Math.min(capacity,selected.size());i++) {
                Todo t=selected.get(i); RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_row);
                row.setTextViewText(R.id.widget_check,t.done?"✓":"○"); row.setTextViewText(R.id.widget_title,t.title);
                row.setInt(R.id.widget_title,"setPaintFlags",Paint.ANTI_ALIAS_FLAG | (t.done?Paint.STRIKE_THRU_TEXT_FLAG:0));
                row.setTextViewText(R.id.widget_due,t.done?"已完成":MainActivity.deadline(t,System.currentTimeMillis()));
                row.setOnClickPendingIntent(R.id.widget_check,Reminders.action(c,"toggle",t.id)); row.setOnClickPendingIntent(R.id.widget_row_text,Reminders.open(c)); root.addView(R.id.widget_items,row);
            }
            String footer=selected.isEmpty()?"在清单中勾选「在桌面显示」":(selected.size()>capacity?"还有 "+(selected.size()-capacity)+" 项 · 打开完整清单":"共 "+selected.size()+" 项 · 打开清单"); root.setTextViewText(R.id.widget_footer,footer);
        } catch(android.database.SQLException ex) { android.util.Log.e("QingJian","Widget data unavailable",ex); root.setTextViewText(R.id.widget_footer,"无法读取待办，请打开青笺查看"); }
        return root;
    }
}
