package io.github.junhezhang.qingjian;
import android.app.Instrumentation;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

final class SyncSmoke {
    static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
    static SyncSettings settings(String path){SyncSettings s=new SyncSettings();s.url="http://10.0.2.2:18766/"+path+"/";s.user="test-user";s.password="test-password";return s;}
    static byte[] read(InputStream in)throws IOException{try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=source.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}}
    static List<SyncModel.Item> items(JSONObject test,String name)throws Exception{return SyncModel.decode(test.getJSONObject(name).toString().getBytes(StandardCharsets.UTF_8));}
    static void apply(Store s,List<SyncModel.Item> result)throws Exception{SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{s.applySync(result,"test-binding");db.setTransactionSuccessful();}finally{db.endTransaction();}}
    static void run(Instrumentation runner,boolean verify)throws Exception{
        Context c=runner.getTargetContext();check(c.getPackageName().endsWith(".debug"),"Debug tests only");WebDavSync client=new WebDavSync(settings("shared"),true);File backups=new File(c.getExternalFilesDir(null),"test-sync-backups");
        if(verify){try(Store s=new Store(c,"sync-integration-test.db")){List<SyncModel.Item> base=s.baseline();List<SyncModel.Item> merged=client.exchange(base,SyncModel.snapshot(s.all(),base),backups,null);apply(s,merged);check(s.find("22222222222222222222222222222222")==null,"Windows deletion reaches Android");check(s.all().size()==1&&s.all().get(0).done&&!s.all().get(0).desktop,"Completion retained; local desktop setting retained");}return;}
        JSONArray cases=new JSONObject(new String(read(runner.getContext().getAssets().open("cases.json")),StandardCharsets.UTF_8)).getJSONArray("cases");
        for(int i=0;i<cases.length();i++){JSONObject test=cases.getJSONObject(i);String outcome=test.getString("outcome"),name=test.getString("name");
            try{List<SyncModel.Item> result=SyncModel.merge(items(test,"baseline"),items(test,"local"),items(test,"remote"),null);check(outcome.equals("ok")&&SyncModel.same(result,items(test,"expected")),name);check(SyncModel.same(result,SyncModel.decode(SyncModel.encode(result))),"Wire roundtrip");}
            catch(SyncModel.Conflict conflict){check(outcome.equals("conflict"),name);List<SyncModel.Item> result=SyncModel.merge(items(test,"baseline"),items(test,"local"),items(test,"remote"),new SyncModel.Resolution(conflict,true));check(SyncModel.same(result,items(test,"local")),"Explicit choice");}
            catch(IllegalArgumentException ex){check(outcome.equals("error"),name);}
        }
        for(String path:new String[]{"unauthorized","redirect","no-etag","no-cas"}){boolean refused=false;try{new WebDavSync(settings(path),true).checkConnection();}catch(IOException ex){refused=true;}check(refused,"Unsafe server refused: "+path);}
        SyncSettings secret=new SyncSettings();secret.user="test-user";secret.password="test-password";secret.save(c);check(SyncSettings.load(c).password.equals(secret.password),"Keystore roundtrip");check(!c.getSharedPreferences("sync-secure",0).getString("credential","").contains(secret.password),"No plaintext credential");c.getSharedPreferences("sync-secure",0).edit().clear().commit();
        // Build a genuine schema-1 database and verify that migration preserves its rows.
        String dbName="sync-migration-test.db";c.deleteDatabase(dbName);try(SQLiteDatabase db=c.openOrCreateDatabase(dbName,0,null)){
            db.execSQL("CREATE TABLE tasks (id TEXT PRIMARY KEY,title TEXT NOT NULL,notes TEXT NOT NULL,due INTEGER NOT NULL,done INTEGER NOT NULL,desktop INTEGER NOT NULL,lead INTEGER NOT NULL,stage INTEGER NOT NULL,snooze INTEGER NOT NULL,created INTEGER NOT NULL)");
            db.execSQL("INSERT INTO tasks VALUES ('33333333-3333-3333-3333-333333333333','旧版待办','保留',1790000000123,0,0,60,1,0,1789990000123)");db.setVersion(1);
        }
        try(Store s=new Store(c,dbName)){check(s.all().size()==1&&s.all().get(0).notes.equals("保留")&&s.baseline().isEmpty(),"Schema-1 migration preserves data");s.delete(s.all().get(0).id);check(s.snapshot(s.all(),s.baseline()).get(0).deleted,"Deletion survives missing upload acknowledgement");}
        c.deleteDatabase("sync-integration-test.db");try(Store s=new Store(c,"sync-integration-test.db")){
            List<SyncModel.Item> remote=client.exchange(new ArrayList<>(),new ArrayList<>(),backups,null);check(remote.size()==1&&remote.get(0).title.equals("Windows 新建 🌿")&&remote.get(0).due==1790000000123L,"Windows -> Android Unicode and milliseconds");apply(s,remote);
            WebDavSync racing=new WebDavSync(settings("race-android"),true);List<SyncModel.Item> raceBase=racing.exchange(new ArrayList<>(),remote,backups,null);List<SyncModel.Item> raceLocal=SyncModel.decode(SyncModel.encode(raceBase));raceLocal.get(0).done=true;List<SyncModel.Item> raceMerged=racing.exchange(raceBase,raceLocal,backups,null);check(raceMerged.size()==2&&raceMerged.get(0).done,"412 retry merges concurrent addition");
            Todo first=s.all().get(0);first.setDone(true);first.desktop=false;s.save(first);Todo added=new Todo();added.id="22222222222222222222222222222222";added.title="安卓新增 🌿";added.due=1790000000456L;s.save(added);
            List<SyncModel.Item> merged=client.exchange(s.baseline(),SyncModel.snapshot(s.all(),s.baseline()),backups,null);apply(s,merged);check(s.all().size()==2&&!s.find(first.id).desktop,"Import retains local display");
            List<SyncModel.Item> base=s.baseline();SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{s.delete(added.id);}finally{db.endTransaction();}check(s.all().size()==2&&SyncModel.same(base,s.baseline()),"SQLite rollback preserves tasks and baseline");
        }
    }
}
