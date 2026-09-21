package io.github.junhezhang.qingjian;

import android.app.AlertDialog;
import android.text.InputType;
import android.widget.*;

public final class SyncUi {
    static EditText field(MainActivity a,LinearLayout parent,String label,String value,boolean secret){a.label(parent,label);EditText input=new EditText(a);input.setSingleLine(true);input.setTextSize(14);input.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));input.setText(value);parent.addView(input);return input;}
    public static AlertDialog show(MainActivity a){
        SyncSettings settings;
        try{settings=SyncSettings.load(a);}catch(Exception ex){settings=new SyncSettings();Toast.makeText(a,"同步配置无法读取，请重新填写原账号和应用密码。",Toast.LENGTH_LONG).show();}
        LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(a.dp(20),a.dp(8),a.dp(20),a.dp(12));ScrollView scroll=new ScrollView(a);scroll.addView(panel);
        panel.addView(a.text("开启后，待办内容、DDL 和完成状态将上传到你指定的网盘。两端填写相同账号与服务器目录。",13,MainActivity.MUTED));
        EditText url=field(a,panel,"服务器目录（HTTPS）",settings.url,false),user=field(a,panel,"网盘账号",settings.user,false),password=field(a,panel,"应用专用密码",settings.password,true);
        CheckBox enabled=new CheckBox(a);enabled.setText("开启自动同步");enabled.setChecked(settings.enabled);panel.addView(enabled);
        TextView status=a.text(SyncManager.status(a),12,MainActivity.GREEN);status.setPadding(0,a.dp(12),0,a.dp(12));panel.addView(status);
        panel.addView(a.text("打开应用及前台约每 2 分钟检查同步；后台由系统约每 15 分钟调度，可能延迟。冲突需要选择版本。\n\nHTTPS 保护传输，文件不是端到端加密，网盘服务商可访问内容。凭据使用 Android Keystore 加密保护。本机通知记录和桌面显示设置不上传。",12,MainActivity.MUTED));
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("青笺 · 云端同步").setView(scroll).setPositiveButton("保存并同步",null).setNeutralButton("立即同步",null).setNegativeButton("关闭",null).create();
        panel.addView(a.button("停用同步",()->{try{SyncManager.disable(a);enabled.setChecked(false);status.setText(SyncManager.status(a));a.refreshList();}catch(Exception ex){status.setText(ex.getMessage());}},false));
        dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{SyncSettings next=new SyncSettings();next.url=url.getText().toString().trim();next.user=user.getText().toString().trim();next.password=password.getText().toString();next.enabled=enabled.isChecked();SyncManager.configure(a,next);run(a,status,null);}catch(Exception ex){status.setText(ex.getMessage());}});dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->run(a,status,null));});dialog.show();return dialog;
    }
    static void run(MainActivity a,TextView status,SyncModel.Resolution resolution){
        boolean started=SyncManager.start(a,true,resolution,conflict->{if(a.isFinishing()||a.isDestroyed())return;status.setText(SyncManager.status(a));a.refreshList();if(conflict!=null)choose(a,status,conflict);});
        status.setText(SyncManager.status(a));if(!started&&SyncManager.busy)status.setText("正在同步，请稍候。");a.refreshList();
    }
    static void choose(MainActivity a,TextView status,SyncModel.Conflict conflict){
        TextView detail=a.text("选择应用于下面全部冲突，其他事项正常合并。双方副本已备份。\n\n"+conflict.details(),14,MainActivity.INK);detail.setPadding(a.dp(20),a.dp(12),a.dp(20),a.dp(12));detail.setTextIsSelectable(true);ScrollView scroll=new ScrollView(a);scroll.addView(detail);
        new AlertDialog.Builder(a).setTitle("同步冲突 · 选择版本").setView(scroll).setPositiveButton("保留本机",(d,w)->run(a,status,new SyncModel.Resolution(conflict,true))).setNeutralButton("使用云端",(d,w)->run(a,status,new SyncModel.Resolution(conflict,false))).setNegativeButton("取消",null).show();
    }
}
