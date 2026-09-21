package io.github.junhezhang.qingjian;

import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class SyncModel {
    public static final int MAX_BYTES=4*1024*1024;
    public static String key(String id){return UUID.fromString(id.length()==32?id.substring(0,8)+"-"+id.substring(8,12)+"-"+id.substring(12,16)+"-"+id.substring(16,20)+"-"+id.substring(20):id).toString().replace("-","");}
    public static final class Item {
        public String id,title="",notes=""; public long due,created; public int lead; public boolean done,deleted;
        JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("title",title).put("notes",notes).put("due",due).put("created",created).put("lead",lead).put("done",done).put("deleted",deleted);}
        public String describe(){if(deleted)return "已删除";return title+"\n"+(done?"已完成":"未完成")+" · "+(due==0?"无截止时间":new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(due)))+"\n"+MainActivity.leadLabel(lead)+"\n"+notes;}
    }
    public static Item tombstone(String id){Item t=new Item();t.id=id;t.deleted=true;return t;}
    public static boolean same(Item a,Item b){if(a==null||b==null)return a==b;return a.id.equals(b.id)&&a.deleted==b.deleted&&(a.deleted||(a.title.equals(b.title)&&a.notes.equals(b.notes)&&a.due==b.due&&a.created==b.created&&a.lead==b.lead&&a.done==b.done));}
    public static final class Conflict extends Exception {
        public final List<Item> local=new ArrayList<>(),remote=new ArrayList<>();
        Conflict(){super("同一事项在两端都有修改，需要选择版本。");}
        public String details(){StringBuilder s=new StringBuilder();for(int i=0;i<local.size();i++)s.append("本机：\n").append(local.get(i).describe()).append("\n\n云端：\n").append(remote.get(i).describe()).append("\n\n────────\n\n");return s.toString();}
    }
    public static final class Resolution {
        final Conflict conflict;final boolean useLocal;
        public Resolution(Conflict conflict,boolean useLocal){this.conflict=conflict;this.useLocal=useLocal;}
        Item resolve(Item a,Item b){for(int i=0;i<conflict.local.size();i++)if(same(a,conflict.local.get(i))&&same(b,conflict.remote.get(i)))return useLocal?a:b;return null;}
    }
    public static void validate(List<Item> items){
        if(items.size()>10000)throw new IllegalArgumentException("同步记录超过 10000 项。");Set<String> ids=new HashSet<>();
        for(Item t:items)if(t==null||t.id==null||!t.id.matches("[0-9a-f]{32}")||!ids.add(t.id)||t.title==null||t.notes==null||(!t.deleted&&t.title.trim().isEmpty())||t.title.length()>200||t.notes.length()>4000||t.lead< -1||t.lead>525600||t.due<0||t.created<0||t.due>253402300799000L||t.created>253402300799000L)throw new IllegalArgumentException("云端待办格式不正确，未覆盖本机数据。");
    }
    public static byte[] encode(List<Item> items)throws JSONException {
        validate(items);JSONArray array=new JSONArray();for(Item t:items)array.put(t.json());byte[] data=new JSONObject().put("schema",1).put("items",array).toString().getBytes(StandardCharsets.UTF_8);if(data.length>MAX_BYTES)throw new IllegalArgumentException("同步数据超过 4 MB。");return data;
    }
    public static List<Item> decode(byte[] data)throws JSONException {
        if(data.length>MAX_BYTES)throw new IllegalArgumentException("同步数据超过 4 MB。");JSONObject doc=new JSONObject(new String(data,StandardCharsets.UTF_8));if(doc.getInt("schema")!=1)throw new IllegalArgumentException("同步文件版本不受支持。");JSONArray array=doc.getJSONArray("items");List<Item> items=new ArrayList<>();
        for(int i=0;i<array.length();i++){JSONObject o=array.getJSONObject(i);Item t=new Item();t.id=o.getString("id");t.title=o.getString("title");t.notes=o.getString("notes");t.due=o.getLong("due");t.created=o.getLong("created");t.lead=o.getInt("lead");t.done=o.getBoolean("done");t.deleted=o.getBoolean("deleted");items.add(t);}validate(items);return items;
    }
    static Map<String,Item> map(List<Item> items){Map<String,Item> map=new TreeMap<>();for(Item t:items)map.put(t.id,t);return map;}
    public static List<Item> snapshot(List<Todo> tasks,List<Item> baseline){
        Map<String,Item> items=new TreeMap<>();for(Todo t:tasks){Item i=new Item();i.id=key(t.id);i.title=t.title;i.notes=t.notes;i.due=t.due;i.created=t.created;i.done=t.done;i.lead=t.leadMinutes;items.put(i.id,i);}
        for(Item i:baseline)if(!items.containsKey(i.id))items.put(i.id,tombstone(i.id));List<Item> result=new ArrayList<>(items.values());validate(result);return result;
    }
    public static boolean same(List<Item> a,List<Item> b){if(a.size()!=b.size())return false;Map<String,Item> right=map(b);for(Item t:a)if(!same(t,right.get(t.id)))return false;return true;}
    public static List<Item> merge(List<Item> baseline,List<Item> local,List<Item> remote,Resolution resolution)throws Conflict {
        validate(baseline);validate(local);validate(remote);Map<String,Item> b=map(baseline),l=map(local),r=map(remote);if(!r.keySet().containsAll(b.keySet()))throw new IllegalArgumentException("云端同步文件缺少已知记录，可能被删除或回退。请恢复网盘文件版本后重试。");
        Set<String> ids=new TreeSet<>(l.keySet());ids.addAll(r.keySet());List<Item> result=new ArrayList<>();Conflict conflict=new Conflict();
        for(String id:ids){Item left=l.get(id),right=r.get(id),old=b.get(id),chosen;
            if(same(left,right))chosen=left;else if(same(left,old))chosen=right;else if(same(right,old))chosen=left;
            else{chosen=resolution==null?null:resolution.resolve(left,right);if(chosen==null){conflict.local.add(left);conflict.remote.add(right);continue;}}if(chosen!=null)result.add(chosen);
        }if(!conflict.local.isEmpty())throw conflict;return result;
    }
}
