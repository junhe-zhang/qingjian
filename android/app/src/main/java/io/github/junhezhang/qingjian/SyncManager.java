package io.github.junhezhang.qingjian;

import android.content.*;
import android.app.job.*;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;

public final class SyncManager {
    static final int JOB=1301;
    public static volatile boolean busy,editing;
    static long lastAttempt;
    static final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
    public interface Callback {void finished(SyncModel.Conflict conflict);}
    public static String status(Context c){return c.getSharedPreferences("sync-status",0).getString("message","尚未开启跨设备同步");}
    static void status(Context c,String text){c.getSharedPreferences("sync-status",0).edit().putString("message",text).apply();}
    public static void configure(Context c,SyncSettings settings)throws Exception {
        if(busy)throw new IllegalStateException("正在同步，请稍后修改设置。");settings.validate();
        try(Store store=new Store(c)){if(!store.syncAccount().isEmpty()&&!settings.binding().equals(store.syncAccount()))throw new IllegalArgumentException("已有同步记录，本版请继续使用原账号和目录。可以更换应用密码或停用。");}
        settings.save(c);schedule(c,settings.enabled);status(c,settings.enabled?"设置已保存，尚未验证连接":"同步已停用");
    }
    public static void disable(Context c)throws Exception {if(busy)throw new IllegalStateException("正在同步，请稍后停用。");SyncSettings s=SyncSettings.load(c);s.enabled=false;s.save(c);schedule(c,false);status(c,"同步已停用，本机与网盘数据均保留");}
    static void schedule(Context c,boolean enabled){JobScheduler jobs=c.getSystemService(JobScheduler.class);if(!enabled){jobs.cancel(JOB);return;}if(jobs.getPendingJob(JOB)==null){int result=jobs.schedule(new JobInfo.Builder(JOB,new ComponentName(c,SyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60_000L).setPersisted(true).build());if(result!=JobScheduler.RESULT_SUCCESS)throw new IllegalStateException("系统未接受后台同步任务，仍可手动同步。");}}
    public static synchronized boolean start(Context context,boolean manual,SyncModel.Resolution resolution,Callback callback){
        if(busy||editing||(!manual&&lastAttempt!=0&&SystemClock.elapsedRealtime()-lastAttempt<120_000))return false;
        Context c=context.getApplicationContext();SyncSettings settings;
        try{settings=SyncSettings.load(c);if(!settings.enabled)return false;settings.validate();schedule(c,true);}catch(Exception ex){status(c,"同步设置错误："+ex.getMessage());return false;}
        busy=true;lastAttempt=SystemClock.elapsedRealtime();status(c,"正在同步…");
        worker.execute(()->{
            SyncModel.Conflict conflict=null;
            try{
                List<SyncModel.Item> baseline,local;
                try(Store store=new Store(c)){SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{baseline=store.baseline();local=store.snapshot(store.all(),baseline);db.setTransactionSuccessful();}finally{db.endTransaction();}}
                List<SyncModel.Item> merged=new WebDavSync(settings).exchange(baseline,local,new File(c.getFilesDir(),"sync-backups"),resolution);
                List<Todo> prior;
                try(Store store=new Store(c)){SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
                    prior=store.all();if(!SyncModel.same(local,store.snapshot(prior,store.baseline())))throw new IOException("同步期间本机有新修改，已保留。请再次同步；部分修改可能已上传。");
                    store.applySync(merged,settings.binding());db.setTransactionSuccessful();
                }finally{db.endTransaction();}}
                // Remove obsolete notifications before rebuilding reminders from the merged state.
                for(Todo t:prior)Reminders.cancel(c,t.id);Reminders.process(c);
                status(c,"同步成功 · "+new java.text.SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date()));
            }catch(SyncModel.Conflict ex){conflict=ex;status(c,"有 "+ex.local.size()+" 项冲突 · 打开云端同步处理");}
            catch(Exception ex){status(c,"同步失败："+ex.getMessage());}
            finally{busy=false;}
            SyncModel.Conflict result=conflict;if(callback!=null)new Handler(Looper.getMainLooper()).post(()->callback.finished(result));
        });return true;
    }
}
