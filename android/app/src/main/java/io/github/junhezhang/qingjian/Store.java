package io.github.junhezhang.qingjian;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class Store extends SQLiteOpenHelper {
    public Store(Context context) { this(context,"qingjian.db"); }
    Store(Context context,String name) { super(context, name, null, 3, db -> { throw new android.database.sqlite.SQLiteDatabaseCorruptException("待办数据库损坏，文件已保留。"); }); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (id TEXT PRIMARY KEY, title TEXT NOT NULL, notes TEXT NOT NULL, due INTEGER NOT NULL, done INTEGER NOT NULL, desktop INTEGER NOT NULL, lead INTEGER NOT NULL, stage INTEGER NOT NULL, snooze INTEGER NOT NULL, created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE sync_meta (id INTEGER PRIMARY KEY, baseline BLOB NOT NULL, account TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE sync_deleted (id TEXT PRIMARY KEY)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<1||oldVersion>2||newVersion!=3)throw new IllegalStateException("数据库版本不受支持，数据未被修改。");
        if(oldVersion==1)db.execSQL("CREATE TABLE sync_meta (id INTEGER PRIMARY KEY, baseline BLOB NOT NULL, account TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE sync_deleted (id TEXT PRIMARY KEY)");
    }
    public List<Todo> all() {
        List<Todo> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT id,title,notes,due,done,desktop,lead,stage,snooze,created FROM tasks ORDER BY done, CASE WHEN due=0 THEN 9223372036854775807 ELSE due END, created DESC", null)) {
            while (cursor.moveToNext()) result.add(read(cursor));
        }
        return result;
    }
    public Todo find(String id) {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT id,title,notes,due,done,desktop,lead,stage,snooze,created FROM tasks WHERE id=?", new String[]{id})) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }
    private Todo read(Cursor c) {
        Todo t = new Todo(); t.id = c.getString(0); t.title = c.getString(1); t.notes = c.getString(2); t.due = c.getLong(3); t.done = c.getInt(4) != 0; t.desktop = c.getInt(5) != 0; t.leadMinutes = c.getInt(6); t.notifiedStage = c.getInt(7); t.snoozedUntil = c.getLong(8); t.created = c.getLong(9); return t;
    }
    public void save(Todo t) {
        if (t.title.trim().isEmpty() || t.title.length() > 200 || t.notes.length() > 4000 || t.leadMinutes < -1 || t.leadMinutes > 525600) throw new IllegalArgumentException("待办内容不符合要求。");
        ContentValues v = new ContentValues(); v.put("id",t.id); v.put("title",t.title.trim()); v.put("notes",t.notes); v.put("due",t.due); v.put("done",t.done?1:0); v.put("desktop",t.desktop?1:0); v.put("lead",t.leadMinutes); v.put("stage",t.notifiedStage); v.put("snooze",t.snoozedUntil); v.put("created",t.created);
        SQLiteDatabase db=getWritableDatabase();boolean owns=!db.inTransaction();if(owns)db.beginTransaction();try{
            if (db.insertWithOnConflict("tasks",null,v,SQLiteDatabase.CONFLICT_REPLACE) == -1) throw new android.database.SQLException("保存失败，本次修改未生效。");
            db.delete("sync_deleted","id=?",new String[]{SyncModel.key(t.id)});if(owns)db.setTransactionSuccessful();
        }finally{if(owns)db.endTransaction();}
    }
    public void delete(String id) { SQLiteDatabase db=getWritableDatabase();boolean owns=!db.inTransaction();if(owns)db.beginTransaction();try{
        db.delete("tasks","id=?",new String[]{id});ContentValues tombstone=new ContentValues();tombstone.put("id",SyncModel.key(id));if(db.insertWithOnConflict("sync_deleted",null,tombstone,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new android.database.SQLException("删除记录未能保存。");if(owns)db.setTransactionSuccessful();
    }finally{if(owns)db.endTransaction();} }
    public List<SyncModel.Item> snapshot(List<Todo> tasks,List<SyncModel.Item> base){List<SyncModel.Item> result=SyncModel.snapshot(tasks,base);java.util.Set<String> ids=new java.util.HashSet<>();for(SyncModel.Item item:result)ids.add(item.id);try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM sync_deleted",null)){while(c.moveToNext()){String id=c.getString(0);if(ids.add(id))result.add(SyncModel.tombstone(id));}}SyncModel.validate(result);return result;}
    public List<SyncModel.Item> baseline()throws org.json.JSONException {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT baseline FROM sync_meta WHERE id=1",null)){return c.moveToFirst()?SyncModel.decode(c.getBlob(0)):new ArrayList<>();}
    }
    // Caller owns the SQLite transaction: task changes and baseline commit together.
    public String syncAccount(){try(Cursor c=getReadableDatabase().rawQuery("SELECT account FROM sync_meta WHERE id=1",null)){return c.moveToFirst()?c.getString(0):"";}}
    public void applySync(List<SyncModel.Item> merged,String account)throws org.json.JSONException {
        java.util.Map<String,Todo> old=new java.util.HashMap<>();for(Todo t:all())old.put(SyncModel.key(t.id),t);
        for(SyncModel.Item item:merged){Todo t=old.get(item.id);if(item.deleted){if(t!=null)delete(t.id);continue;}if(t==null){t=new Todo();t.id=item.id;}
            if(t.due!=item.due||t.leadMinutes!=item.lead||(t.done&&!item.done))t.resetReminder();t.title=item.title;t.notes=item.notes;t.due=item.due;t.created=item.created;t.done=item.done;t.leadMinutes=item.lead;save(t);
        }
        ContentValues values=new ContentValues();values.put("id",1);values.put("account",account);values.put("baseline",SyncModel.encode(merged));if(getWritableDatabase().insertWithOnConflict("sync_meta",null,values,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new android.database.SQLException("同步记录保存失败。");
    }
}
