package io.github.junhezhang.qingjian;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class Store extends SQLiteOpenHelper {
    public Store(Context context) { super(context, "qingjian.db", null, 1, db -> { throw new android.database.sqlite.SQLiteDatabaseCorruptException("待办数据库损坏，文件已保留。"); }); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (id TEXT PRIMARY KEY, title TEXT NOT NULL, notes TEXT NOT NULL, due INTEGER NOT NULL, done INTEGER NOT NULL, desktop INTEGER NOT NULL, lead INTEGER NOT NULL, stage INTEGER NOT NULL, snooze INTEGER NOT NULL, created INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("数据库版本不受支持，数据未被修改。");
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
        if (t.title.trim().isEmpty() || t.title.length() > 200 || t.notes.length() > 4000 || t.leadMinutes < -1 || t.leadMinutes > 10080) throw new IllegalArgumentException("待办内容不符合要求。");
        ContentValues v = new ContentValues(); v.put("id",t.id); v.put("title",t.title.trim()); v.put("notes",t.notes); v.put("due",t.due); v.put("done",t.done?1:0); v.put("desktop",t.desktop?1:0); v.put("lead",t.leadMinutes); v.put("stage",t.notifiedStage); v.put("snooze",t.snoozedUntil); v.put("created",t.created);
        if (getWritableDatabase().insertWithOnConflict("tasks",null,v,SQLiteDatabase.CONFLICT_REPLACE) == -1) throw new android.database.SQLException("保存失败，本次修改未生效。");
    }
    public void delete(String id) { getWritableDatabase().delete("tasks","id=?",new String[]{id}); }
}
