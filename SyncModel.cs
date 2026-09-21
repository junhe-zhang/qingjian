using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Runtime.Serialization;
using System.Runtime.Serialization.Json;
using System.Text;

namespace DesktopMemo
{
    [DataContract]
    public class SyncItem
    {
        [DataMember(Name="id", IsRequired=true)] public string Id;
        [DataMember(Name="title", IsRequired=true)] public string Title = "";
        [DataMember(Name="notes", IsRequired=true)] public string Notes = "";
        [DataMember(Name="due", IsRequired=true)] public long Due;
        [DataMember(Name="created", IsRequired=true)] public long Created;
        [DataMember(Name="done", IsRequired=true)] public bool Done;
        [DataMember(Name="lead", IsRequired=true)] public int Lead;
        [DataMember(Name="deleted", IsRequired=true)] public bool Deleted;
        public static string Key(string id) { return Guid.Parse(id).ToString("N"); }
        public static SyncItem Tombstone(string id) { return new SyncItem { Id=id, Deleted=true }; }
        public static bool Equal(SyncItem a, SyncItem b)
        {
            if (a == null || b == null) return a == b;
            return a.Id == b.Id && a.Deleted == b.Deleted && (a.Deleted ||
                (a.Title == b.Title && a.Notes == b.Notes && a.Due == b.Due && a.Created == b.Created && a.Done == b.Done && a.Lead == b.Lead));
        }
        public string Describe() { return Deleted ? "已删除" : Title + "\n" + (Done ? "已完成" : "未完成") + " · " + (Due == 0 ? "无截止时间" : SyncModel.Date(Due).ToString("yyyy-MM-dd HH:mm")) + "\n" + Editor.LeadLabel(Lead) + "\n" + Notes; }
    }
    [DataContract]
    public class SyncDocument
    {
        [DataMember(Name="schema", IsRequired=true)] public int Schema=1;
        [DataMember(Name="items", IsRequired=true)] public List<SyncItem> Items=new List<SyncItem>();
    }
    public class SyncConflict : Exception
    {
        public readonly List<SyncItem> Local=new List<SyncItem>(), Remote=new List<SyncItem>();
        public SyncConflict() : base("同一事项在两端发生了不同修改，需要选择要保留的版本。") { }
        public string Details() { return string.Join("\n\n────────\n\n", Local.Select((item,i)=>"本机：\n"+item.Describe()+"\n\n云端：\n"+Remote[i].Describe())); }
    }
    public class SyncResolution
    {
        public SyncConflict Conflict;
        public bool UseLocal;
        public SyncItem Resolve(SyncItem local, SyncItem remote)
        {
            for (int i=0; i<Conflict.Local.Count; i++)
                if (SyncItem.Equal(local,Conflict.Local[i]) && SyncItem.Equal(remote,Conflict.Remote[i])) return UseLocal ? local : remote;
            return null;
        }
    }
    public static class SyncModel
    {
        public const int MaxBytes=4*1024*1024;
        static readonly DateTime Epoch=new DateTime(1970,1,1,0,0,0,DateTimeKind.Utc);
        public static long Millis(DateTime time) { return (long)(time.ToUniversalTime()-Epoch).TotalMilliseconds; }
        public static DateTime Date(long time) { return Epoch.AddMilliseconds(time).ToLocalTime(); }
        public static byte[] Encode(SyncDocument doc)
        {
            Validate(doc); using(var stream=new MemoryStream()) { new DataContractJsonSerializer(typeof(SyncDocument)).WriteObject(stream,doc); if(stream.Length>MaxBytes)throw new InvalidDataException("同步数据超过 4 MB，请先整理清单。"); return stream.ToArray(); }
        }
        public static SyncDocument Decode(byte[] bytes)
        {
            if(bytes.Length>MaxBytes)throw new InvalidDataException("同步数据超过 4 MB。");
            SyncDocument doc; using(var stream=new MemoryStream(bytes))doc=(SyncDocument)new DataContractJsonSerializer(typeof(SyncDocument)).ReadObject(stream);
            Validate(doc);return doc;
        }
        public static void Validate(SyncDocument doc)
        {
            if(doc==null||doc.Schema!=1||doc.Items==null||doc.Items.Count>10000)throw new InvalidDataException("云端同步格式或版本不受支持，未覆盖本机数据。");
            var ids=new HashSet<string>();
            foreach(var t in doc.Items)
            {
                Guid id;
                if(t==null||!Guid.TryParseExact(t.Id,"N",out id)||t.Id!=t.Id.ToLowerInvariant()||!ids.Add(t.Id)||t.Title==null||t.Notes==null||(!t.Deleted&&string.IsNullOrWhiteSpace(t.Title))||t.Title.Length>200||t.Notes.Length>4000||t.Lead < -1||t.Lead>525600||t.Due<0||t.Created<0||t.Due>253402300799000L||t.Created>253402300799000L)
                    throw new InvalidDataException("云端待办内容不符合格式要求，未覆盖本机数据。");
            }
        }
        public static SyncDocument Snapshot(MemoState state)
        {
            var doc=new SyncDocument();
            foreach(var t in state.Tasks)doc.Items.Add(new SyncItem {Id=SyncItem.Key(t.Id),Title=t.Title,Notes=t.Notes,Due=t.Due.HasValue?Millis(t.Due.Value):0,Created=Millis(t.Created),Done=t.Done,Lead=t.LeadMinutes});
            var ids=new HashSet<string>(doc.Items.Select(t=>t.Id));
            foreach(string id in state.SyncBase.Select(t=>t.Id).Concat(state.SyncDeleted.Select(SyncItem.Key)))if(ids.Add(id))doc.Items.Add(SyncItem.Tombstone(id));
            doc.Items=doc.Items.OrderBy(t=>t.Id,StringComparer.Ordinal).ToList();Validate(doc);return doc;
        }
        public static bool Equal(SyncDocument a,SyncDocument b)
        {
            var map=b.Items.ToDictionary(t=>t.Id);return a.Items.Count==map.Count && a.Items.All(t=>map.ContainsKey(t.Id)&&SyncItem.Equal(t,map[t.Id]));
        }
        public static SyncDocument Merge(SyncDocument baseline,SyncDocument local,SyncDocument remote,SyncResolution resolution=null)
        {
            Validate(baseline);Validate(local);Validate(remote);
            var b=baseline.Items.ToDictionary(t=>t.Id);var l=local.Items.ToDictionary(t=>t.Id);var r=remote.Items.ToDictionary(t=>t.Id);
            if(b.Keys.Any(id=>!r.ContainsKey(id)))throw new InvalidDataException("云端同步文件缺少已同步记录，可能被删除或回退。请恢复网盘中的文件版本后重试。");
            var result=new SyncDocument();var conflict=new SyncConflict();
            foreach(string id in l.Keys.Union(r.Keys).OrderBy(x=>x,StringComparer.Ordinal))
            {
                SyncItem left,right,old,chosen; l.TryGetValue(id,out left);r.TryGetValue(id,out right);b.TryGetValue(id,out old);
                if(SyncItem.Equal(left,right))chosen=left;
                else if(SyncItem.Equal(left,old))chosen=right;
                else if(SyncItem.Equal(right,old))chosen=left;
                else { chosen=resolution==null?null:resolution.Resolve(left,right); if(chosen==null){conflict.Local.Add(left);conflict.Remote.Add(right);continue;} }
                if(chosen!=null)result.Items.Add(chosen);
            }
            if(conflict.Local.Count>0)throw conflict;return result;
        }
        public static MemoState Apply(MemoState state,SyncDocument merged)
        {
            var next=Store.Decode(Store.Encode(state));var old=next.Tasks.ToDictionary(t=>SyncItem.Key(t.Id));var tasks=new List<Todo>();
            foreach(var item in merged.Items.Where(t=>!t.Deleted))
            {
                Todo task;if(!old.TryGetValue(item.Id,out task))task=new Todo {Id=item.Id};
                long priorDue=task.Due.HasValue?Millis(task.Due.Value):0;
                if(priorDue!=item.Due||task.LeadMinutes!=item.Lead||(task.Done&&!item.Done)){task.NotifiedStage=0;task.SnoozedUntil=null;}
                task.Title=item.Title;task.Notes=item.Notes;task.Due=item.Due==0?(DateTime?)null:Date(item.Due);task.Created=Date(item.Created);task.Done=item.Done;task.LeadMinutes=item.Lead;
                if(!task.Done)task.CompletedAt=null;
                tasks.Add(task);
            }
            next.Tasks=tasks;next.SyncBase=merged.Items;next.SyncDeleted=merged.Items.Where(t=>t.Deleted).Select(t=>t.Id).ToList();return next;
        }
    }
}
