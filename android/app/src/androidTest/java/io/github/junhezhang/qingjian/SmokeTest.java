package io.github.junhezhang.qingjian;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Runs only against the debug package on a dedicated emulator; never use on personal task data. */
public class SmokeTest extends Instrumentation {
    MainActivity main;
    void check(boolean ok,String detail){if(!ok)throw new AssertionError(detail);}
    void onMain(Runnable action){Throwable[] failure={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){failure[0]=t;}});waitForIdleSync();if(failure[0]!=null)throw new AssertionError(failure[0]);}
    void sendAction(Context c,String action,String id)throws Exception{java.util.concurrent.CountDownLatch finished=new java.util.concurrent.CountDownLatch(1);Reminders.action(c,action,id).send(c,0,null,(pending,intent,code,data,extras)->finished.countDown(),new Handler(Looper.getMainLooper()));check(finished.await(10,java.util.concurrent.TimeUnit.SECONDS),"Broadcast action finished");waitForIdleSync();}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Context context=getTargetContext();check(context.getPackageName().endsWith(".debug"),"Only debug package may be tested");
            if(Build.VERSION.SDK_INT>=33){try(ParcelFileDescriptor p=getUiAutomation().executeShellCommand("pm grant "+context.getPackageName()+" android.permission.POST_NOTIFICATIONS");InputStream in=new FileInputStream(p.getFileDescriptor())){while(in.read()!=-1){}}}
            try(Store s=new Store(context)){s.getWritableDatabase().delete("tasks",null,null);}
            Intent launch=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            main=(MainActivity)startActivitySync(launch);waitForIdleSync();
            onMain(()->main.showEditor(null,null));
            onMain(()->{
                main.editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();check(main.editor.isShowing()&&main.editTitle.getError()!=null,"Blank title validation");
                main.editTitle.setText("中文界面测试");main.editNotes.setText("备注与换行\n第二行");main.editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();check(!main.editor.isShowing(),"Create dialog closes");
            });
            Todo task;try(Store s=new Store(context)){check(s.all().size()==1,"Created exactly one task");task=s.all().get(0);check(task.notes.contains("第二行")&&task.due>0,"Fields persisted");}
            String id=task.id;
            onMain(()->{View complete=findDescription(main.root,"标记完成");check(complete!=null,"Completion checkbox exists");complete.performClick();});
            try(Store s=new Store(context)){check(s.find(id).done,"Checkbox completed task");}
            onMain(()->{View undo=findDescription(main.root,"恢复待办");undo.performClick();try(Store s=new Store(context)){main.showEditor(s.find(id),null);}});
            onMain(()->{main.editHasDue.setChecked(false);main.editNotes.setText("已更新");main.editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();main.search.setText("不存在的搜索词");check(hasText(main.list,"这里暂时没有待办。"),"Search empty state");main.search.setText("");});
            try(Store s=new Store(context)){check(!s.find(id).done&&s.find(id).due==0&&s.find(id).notes.equals("已更新"),"Undo and edit persisted");}
            onMain(()->{main.showEditor(null,null);main.editTitle.setText("旋转后保留的草稿");});
            ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
            onMain(()->main.recreate());
            MainActivity recreated=(MainActivity)monitor.waitForActivityWithTimeout(5000);removeMonitor(monitor);check(recreated!=null,"Activity recreated");main=recreated;waitForIdleSync();
            onMain(()->{check(main.editor!=null&&main.editor.isShowing()&&main.editTitle.getText().toString().equals("旋转后保留的草稿"),"Draft survives activity recreation");main.editor.dismiss();});
            Todo alarm=new Todo();alarm.title="截止提醒测试";alarm.due=System.currentTimeMillis()-1000;alarm.leadMinutes=0;
            try(Store s=new Store(context)){s.save(alarm);}
            onMain(()->Reminders.process(context));
            try(Store s=new Store(context)){check(s.find(alarm.id).notifiedStage==2,"Alarm delivery persisted");}
            check(Arrays.stream(context.getSystemService(NotificationManager.class).getActiveNotifications()).anyMatch(n->alarm.id.equals(n.getTag())),"Notification posted");
            sendAction(context,"snooze",alarm.id);
            try(Store s=new Store(context)){check(s.find(alarm.id).snoozedUntil>System.currentTimeMillis()+590_000,"Snooze action persisted");}
            sendAction(context,"done",alarm.id);
            try(Store s=new Store(context)){check(s.find(alarm.id).done,"Notification completion action");}
            onMain(()->{View widget=TodoWidget.build(context,360).apply(context,new android.widget.FrameLayout(context));check(widget.findViewById(R.id.widget_title)!=null,"Widget renders selected tasks");});
            onMain(()->{
                try(Store s=new Store(context)){s.getWritableDatabase().delete("tasks",null,null);long now=System.currentTimeMillis();String[] titles={"整理本周实验记录","准备组会汇报","阅读并归档参考文献"};for(int i=0;i<titles.length;i++){Todo t=new Todo();t.title=titles[i];t.notes=i==0?"核对数据与图表，补全实验备注。":"";t.due=now+(i+1)*3_600_000L;t.notifiedStage=1;t.done=i==2;s.save(t);}}
                main.filter=0;main.refreshList();
            });
            screenshot(context,"android-main.png");
            onMain(()->main.showEditor(null,null));screenshot(context,"android-editor.png");onMain(()->main.editor.dismiss());
            result.putString("stream","QingJian smoke PASS: SQLite create/edit, validation, completion/undo, search, draft recreation, notification delivery, snooze/done actions, widget rendering.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){StringWriter text=new StringWriter();error.printStackTrace(new PrintWriter(text));result.putString("stream","QingJian smoke FAIL\n"+text);finish(Activity.RESULT_CANCELED,result);}
    }
    void screenshot(Context c,String name)throws IOException{waitForIdleSync();Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Screenshot available");try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    View findDescription(View v,String label){if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    boolean hasText(View v,String value){if(v instanceof TextView&&((TextView)v).getText().toString().contains(value))return true;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)if(hasText(group.getChildAt(i),value))return true;}return false;}
}
