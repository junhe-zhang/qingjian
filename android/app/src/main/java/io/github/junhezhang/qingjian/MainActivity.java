package io.github.junhezhang.qingjian;

import android.Manifest;
import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    static final int GREEN=Color.rgb(34,115,91), INK=Color.rgb(36,60,54), MUTED=Color.rgb(113,130,118), PAPER=Color.rgb(246,247,242), RED=Color.rgb(183,78,67);
    static final int[] LEADS={0,10,30,60,180,1440,2880,10080,-1};
    static final String[] FILTERS={"全部","未完成","临近截止","已逾期","已完成"};
    LinearLayout list,filterRow,root;
    TextView stats,permissionStatus;
    EditText search;
    int filter;
    AlertDialog editor;
    EditText editTitle,editNotes;
    CheckBox editHasDue,editDesktop;
    Spinner editLead;
    Calendar editDate;
    String editId;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Reminders.init(this);
        filter=saved==null?0:saved.getInt("filter");
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(PAPER); root.setPadding(dp(20),dp(14),dp(20),dp(12));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int left,top,right,bottom;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());left=bars.left;top=bars.top;right=bars.right;bottom=bars.bottom;}
            else {left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();}
            v.setPadding(dp(20)+left,dp(14)+top,dp(20)+right,dp(12)+bottom); return insets;
        });
        setContentView(root);
        LinearLayout heading=row();
        TextView name=text("青笺",30,GREEN); name.setTypeface(null,Typeface.BOLD); heading.addView(name,new LinearLayout.LayoutParams(0,dp(52),1));
        Button add=button("＋ 新建",()->showEditor(null,null),true); add.setContentDescription("新建待办"); heading.addView(add); root.addView(heading);
        root.addView(text("留一点空间，专注当下。",13,MUTED));
        stats=text("",14,INK); stats.setPadding(dp(14),dp(14),dp(14),dp(14)); stats.setBackground(background(Color.rgb(233,241,229),14)); LinearLayout.LayoutParams sp=matchWrap();sp.setMargins(0,dp(18),0,dp(12));root.addView(stats,sp);
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setHint("搜索标题和备注");search.setContentDescription("搜索待办");search.setPadding(dp(14),dp(8),dp(14),dp(8));search.setBackground(background(Color.WHITE,12));root.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
        HorizontalScrollView filters=new HorizontalScrollView(this);filters.setHorizontalScrollBarEnabled(false);filterRow=row();filters.addView(filterRow);root.addView(filters);
        for(int i=0;i<FILTERS.length;i++){final int selected=i;Button chip=button(FILTERS[i],()->{filter=selected;refreshList();},false);chip.setTextSize(12);filterRow.addView(chip);}
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        permissionStatus=text("",11,MUTED);permissionStatus.setPadding(0,dp(8),0,dp(4));root.addView(permissionStatus);
        LinearLayout tools=row();
        tools.addView(button("提醒设置",this::showReminderSettings,false),new LinearLayout.LayoutParams(0,dp(48),1));
        tools.addView(button("桌面组件",this::addWidget,false),new LinearLayout.LayoutParams(0,dp(48),1));
        tools.addView(button("隐私说明",this::showPrivacy,false),new LinearLayout.LayoutParams(0,dp(48),1));root.addView(tools);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){} public void onTextChanged(CharSequence s,int a,int b,int c){refreshList();} public void afterTextChanged(Editable e){} });
        if(saved!=null) search.setText(saved.getString("query",""));
        if(BuildConfig.DEBUG && getIntent().getBooleanExtra("demo",false)) createDemo();
        if(saved!=null && saved.containsKey("draftTitle")) root.post(()->{
            try(Store store=new Store(this)){String id=saved.getString("draftId");Todo original=id==null?null:store.find(id);if(id==null||original!=null)showEditor(original,saved);}catch(android.database.SQLException ex){showError(ex);}
        });
    }
    @Override protected void onResume(){super.onResume();try{Reminders.process(this);}catch(android.database.SQLException ex){showError(ex);}refreshList();}
    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);out.putInt("filter",filter);out.putString("query",search.getText().toString());
        if(editor!=null && editor.isShowing()){
            out.putString("draftId",editId);out.putString("draftTitle",editTitle.getText().toString());out.putString("draftNotes",editNotes.getText().toString());out.putLong("draftDate",editDate.getTimeInMillis());out.putBoolean("draftDue",editHasDue.isChecked());out.putBoolean("draftDesktop",editDesktop.isChecked());out.putInt("draftLead",editLead.getSelectedItemPosition());
        }
    }
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(-1,-2);}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    Button button(String label,Runnable action,boolean primary){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(48));b.setTextColor(primary?Color.WHITE:GREEN);b.setBackgroundTintList(ColorStateList.valueOf(primary?GREEN:Color.rgb(234,240,230)));b.setOnClickListener(v->action.run());return b;}
    void showError(Exception ex){new AlertDialog.Builder(this).setTitle("操作未完成").setMessage(ex.getMessage()+"\n原有数据没有被清空。请保留应用，不要通过卸载尝试修复。").setPositiveButton("知道了",null).show();}
    void change(Runnable action){try{action.run();Reminders.process(this);refreshList();}catch(android.database.SQLException|IllegalArgumentException ex){showError(ex);refreshList();}}
    static String deadline(Todo t,long now){
        if(t.due==0)return "未设置截止时间";
        String date=new SimpleDateFormat("MM月dd日 HH:mm",Locale.CHINA).format(new Date(t.due));
        if(t.done)return date+" · 已完成";
        if(t.due<=now)return date+" · 已逾期";
        long minutes=(t.due-now+59_999)/60_000;
        return date+" · 剩 "+(minutes<60?minutes+" 分钟":minutes<1440?((minutes+59)/60)+" 小时":((minutes+1439)/1440)+" 天");
    }
    void refreshList(){
        if(list==null)return;
        try(Store store=new Store(this)){
            List<Todo> all=store.all();long now=System.currentTimeMillis();int done=0,soon=0;for(Todo t:all){if(t.done)done++;if(!t.done&&t.due>now&&t.due<=now+86_400_000)soon++;}
            stats.setText(getString(R.string.stats_template,all.size()-done,soon,done));
            for(int i=0;i<filterRow.getChildCount();i++){Button chip=(Button)filterRow.getChildAt(i);chip.setTextColor(i==filter?Color.WHITE:GREEN);chip.setBackgroundTintList(ColorStateList.valueOf(i==filter?GREEN:Color.rgb(234,240,230)));}
            list.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);int shown=0;
            for(Todo t:all){
                if(filter==1&&t.done || filter==2&&(t.done||t.due<=now||t.due>now+86_400_000) || filter==3&&(t.done||t.due==0||t.due>now) || filter==4&&!t.done)continue;
                if(!q.isEmpty()&&!t.title.toLowerCase(Locale.ROOT).contains(q)&&!t.notes.toLowerCase(Locale.ROOT).contains(q))continue;
                list.addView(card(t,now));shown++;
            }
            if(shown==0){TextView empty=text(all.isEmpty()?"清单空空，心里有数。\n\n点击「新建」，记下第一件重要的事。":"这里暂时没有待办。\n换个分类或搜索词试试。",16,MUTED);empty.setPadding(dp(16),dp(40),dp(16),dp(40));list.addView(empty);}
            boolean exact=Build.VERSION.SDK_INT<31||getSystemService(AlarmManager.class).canScheduleExactAlarms();
            permissionStatus.setText(!Reminders.allowed(this)?"提醒未开启 · 点击「提醒设置」允许通知":exact?"系统提醒已开启 · 数据仅保存在本机":"通知已开启 · 未允许精确提醒，系统可能延迟");
            permissionStatus.setTextColor(Reminders.allowed(this)?MUTED:RED);
        }catch(android.database.SQLException ex){list.removeAllViews();list.addView(text("无法读取待办，请保留应用数据。",16,RED));showError(ex);}
    }
    View card(Todo task,long now){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(background(task.done?Color.rgb(237,242,232):Color.WHITE,16));LinearLayout.LayoutParams layout=matchWrap();layout.setMargins(0,dp(4),0,dp(8));card.setLayoutParams(layout);
        LinearLayout titleRow=row();CheckBox complete=new CheckBox(this);complete.setChecked(task.done);complete.setContentDescription(task.done?"恢复待办":"标记完成");titleRow.addView(complete,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=text(task.title,17,task.done?MUTED:INK);title.setTypeface(null,Typeface.BOLD);if(task.done)title.setPaintFlags(title.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG);titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button edit=button("编辑",()->showEditor(task,null),false);titleRow.addView(edit);card.addView(titleRow);
        if(!task.notes.isEmpty()){TextView notes=text(task.notes,13,MUTED);notes.setPadding(dp(12),0,dp(8),dp(8));card.addView(notes);}
        TextView due=text(deadline(task,now),12,!task.done&&task.due>0&&task.due<=now?RED:MUTED);due.setPadding(dp(12),0,0,dp(4));card.addView(due);
        CheckBox pin=new CheckBox(this);pin.setText("在桌面显示");pin.setTextSize(12);pin.setTextColor(MUTED);pin.setChecked(task.desktop);pin.setMinHeight(dp(48));card.addView(pin);
        complete.setOnClickListener(v->change(()->{try(Store s=new Store(this)){Todo current=s.find(task.id);if(current==null)return;current.setDone(complete.isChecked());s.save(current);Reminders.cancel(this,current.id);}}));
        pin.setOnClickListener(v->change(()->{try(Store s=new Store(this)){Todo current=s.find(task.id);if(current==null)return;current.desktop=pin.isChecked();s.save(current);}}));return card;
    }
    void label(LinearLayout parent,String title){TextView text=text(title,13,MUTED);text.setPadding(0,dp(14),0,dp(4));parent.addView(text);}
    void showEditor(Todo original,Bundle draft){
        editId=original==null?null:original.id;
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(4),dp(20),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(form);
        label(form,"事项名称 *");editTitle=new EditText(this);editTitle.setSingleLine(true);editTitle.setTextSize(16);editTitle.setHint("要做什么？");editTitle.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});editTitle.setText(draft!=null?draft.getString("draftTitle"):original==null?"":original.title);form.addView(editTitle);
        label(form,"备注");editNotes=new EditText(this);editNotes.setMinLines(2);editNotes.setMaxLines(4);editNotes.setGravity(Gravity.TOP);editNotes.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);editNotes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)});editNotes.setText(draft!=null?draft.getString("draftNotes"):original==null?"":original.notes);form.addView(editNotes);
        editHasDue=new CheckBox(this);editHasDue.setText(R.string.set_due);editHasDue.setChecked(draft!=null?draft.getBoolean("draftDue"):original==null||original.due>0);editHasDue.setMinHeight(dp(48));form.addView(editHasDue);
        editDate=Calendar.getInstance();if(draft!=null)editDate.setTimeInMillis(draft.getLong("draftDate"));else if(original!=null&&original.due>0)editDate.setTimeInMillis(original.due);else{editDate.add(Calendar.DAY_OF_YEAR,1);editDate.set(Calendar.HOUR_OF_DAY,18);editDate.set(Calendar.MINUTE,0);}editDate.set(Calendar.SECOND,0);editDate.set(Calendar.MILLISECOND,0);
        Button date=button("",()->{},false);Runnable dateLabel=()->date.setText(new SimpleDateFormat("yyyy年MM月dd日 HH:mm",Locale.CHINA).format(editDate.getTime()));dateLabel.run();
        date.setOnClickListener(v->new DatePickerDialog(this,(picker,y,m,d)->{editDate.set(y,m,d);new TimePickerDialog(this,(clock,h,min)->{editDate.set(Calendar.HOUR_OF_DAY,h);editDate.set(Calendar.MINUTE,min);dateLabel.run();},editDate.get(Calendar.HOUR_OF_DAY),editDate.get(Calendar.MINUTE),true).show();dateLabel.run();},editDate.get(Calendar.YEAR),editDate.get(Calendar.MONTH),editDate.get(Calendar.DAY_OF_MONTH)).show());form.addView(date);
        label(form,"提醒时间");editLead=new Spinner(this);List<String> labels=new ArrayList<>();for(int lead:LEADS)labels.add(leadLabel(lead));ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);editLead.setAdapter(adapter);int chosen=5;if(original!=null)for(int i=0;i<LEADS.length;i++)if(LEADS[i]==original.leadMinutes)chosen=i;editLead.setSelection(draft!=null?draft.getInt("draftLead"):chosen);form.addView(editLead,new LinearLayout.LayoutParams(-1,dp(48)));
        Runnable dueEnabled=()->{date.setEnabled(editHasDue.isChecked());editLead.setEnabled(editHasDue.isChecked());};editHasDue.setOnClickListener(v->dueEnabled.run());dueEnabled.run();
        editDesktop=new CheckBox(this);editDesktop.setText("在桌面小组件显示");editDesktop.setChecked(draft!=null?draft.getBoolean("draftDesktop"):original==null||original.desktop);editDesktop.setMinHeight(dp(48));form.addView(editDesktop);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(original==null?"新建待办":"编辑待办").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null);
        if(original!=null)builder.setNeutralButton("删除",(d,w)->new AlertDialog.Builder(this).setTitle("删除这条待办？").setMessage(original.title).setNegativeButton("取消",null).setPositiveButton("删除",(x,y)->change(()->{try(Store s=new Store(this)){s.delete(original.id);Reminders.cancel(this,original.id);}})).show());
        editor=builder.create();editor.setOnShowListener(d->editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=editTitle.getText().toString().trim();if(name.isEmpty()){editTitle.setError("请填写事项名称");return;}
            try(Store s=new Store(this)){
                Todo task=original==null?new Todo():s.find(original.id);if(task==null){Toast.makeText(this,"待办已被删除",Toast.LENGTH_SHORT).show();editor.dismiss();return;}
                long due=editHasDue.isChecked()?editDate.getTimeInMillis():0;int lead=LEADS[editLead.getSelectedItemPosition()];if(task.due!=due||task.leadMinutes!=lead)task.resetReminder();
                task.title=name;task.notes=editNotes.getText().toString().trim();task.due=due;task.leadMinutes=lead;task.desktop=editDesktop.isChecked();s.save(task);Reminders.cancel(this,task.id);editor.dismiss();Reminders.process(this);refreshList();
            }catch(android.database.SQLException|IllegalArgumentException ex){showError(ex);}
        }));editor.show();
    }
    static String leadLabel(int minutes){if(minutes<0)return "不提醒";if(minutes==0)return "截止时提醒";if(minutes%1440==0)return "提前 "+minutes/1440+" 天";if(minutes%60==0)return "提前 "+minutes/60+" 小时";return "提前 "+minutes+" 分钟";}
    void showReminderSettings(){
        new AlertDialog.Builder(this).setTitle("让截止提醒准时出现").setMessage("请允许青笺发送通知。Android 12 及以上还可开启「闹钟和提醒」，以便在指定时间提醒。\n\n未开启精确提醒时仍会安排系统提醒，但可能延迟。强行停止应用后，请重新打开青笺。部分手机还需在系统中允许后台运行。").setPositiveButton("通知权限",(d,w)->{
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
            else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        }).setNeutralButton("精确提醒",(d,w)->{
            if(Build.VERSION.SDK_INT>=31&&!getSystemService(AlarmManager.class).canScheduleExactAlarms())startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));
            else Toast.makeText(this,"精确提醒已可用",Toast.LENGTH_SHORT).show();
        }).setNegativeButton("稍后",null).show();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);try{Reminders.process(this);}catch(android.database.SQLException ex){showError(ex);}refreshList();}
    void addWidget(){AppWidgetManager manager=getSystemService(AppWidgetManager.class);if(manager.isRequestPinAppWidgetSupported())manager.requestPinAppWidget(new ComponentName(this,TodoWidget.class),null,null);else new AlertDialog.Builder(this).setTitle("添加桌面小组件").setMessage("长按手机桌面空白处，打开「小组件」，找到「青笺待办」并拖到桌面。\n\n清单中勾选「在桌面显示」的事项会出现在组件中。").setPositiveButton("知道了",null).show();}
    void showPrivacy(){new AlertDialog.Builder(this).setTitle("青笺 · 隐私说明").setMessage("待办标题、备注、截止时间和完成状态只保存在这台设备的应用私有数据库中。\n\n应用不联网，不含广告、统计或第三方跟踪 SDK；不读取联系人、相册或位置，也不提供跨设备云同步。\n\n通知权限用于提醒；精确闹钟权限用于安排提醒；开机广播用于重建提醒。桌面小组件只显示你选中的事项。\n\n卸载应用或清除应用数据会删除待办。安卓版目前为预览版。").setPositiveButton("知道了",null).show();}
    void createDemo(){try(Store store=new Store(this)){if(!store.all().isEmpty())return;long now=System.currentTimeMillis();String[] names={"整理本周实验记录","准备组会汇报","阅读并归档参考文献"};for(int i=0;i<3;i++){Todo t=new Todo();t.title=names[i];t.notes=i==0?"核对数据与图表，补全实验备注。":"";t.due=now+(i+1)*3_600_000L;t.done=i==2;store.save(t);}}}
}
