using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Runtime.Serialization;
using System.Runtime.Serialization.Json;
using System.Text;

namespace DesktopMemo
{
    public static class SyncTests
    {
        [DataContract] class Cases { [DataMember(Name="cases")] public List<Case> Values; }
        [DataContract] class Case
        {
            [DataMember(Name="name")] public string Name;
            [DataMember(Name="baseline")] public SyncDocument Baseline;
            [DataMember(Name="local")] public SyncDocument Local;
            [DataMember(Name="remote")] public SyncDocument Remote;
            [DataMember(Name="expected")] public SyncDocument Expected;
            [DataMember(Name="outcome")] public string Outcome;
        }
        static void Check(bool yes,string message){if(!yes)throw new Exception(message);}
        static SyncSettings Settings(string path){return new SyncSettings {Url="http://127.0.0.1:18766/"+path+"/",User="test-user",Password="test-password"};}
        public static int Run(bool verify)
        {
            string root=AppDomain.CurrentDomain.BaseDirectory,output=Path.Combine(root,"verification","sync-tests");Directory.CreateDirectory(output);
            try
            {
                var client=new WebDavSync(Settings("shared"),true);string saved=Path.Combine(output,"windows-baseline.json");
                if(verify)
                {
                    var baseline=SyncModel.Decode(File.ReadAllBytes(saved));var merged=client.Exchange(baseline,baseline,output);
                    Check(merged.Items.Single(t=>t.Id==new string('1',32)).Done,"Android completion reaches Windows");
                    var android=merged.Items.Single(t=>t.Id==new string('2',32));Check(android.Title=="安卓新增 🌿"&&android.Due==1790000000456L,"Android text and milliseconds reach Windows");
                    var local=SyncModel.Decode(SyncModel.Encode(merged));local.Items.RemoveAll(t=>t.Id==android.Id);local.Items.Add(SyncItem.Tombstone(android.Id));
                    client.Exchange(merged,local,output);File.WriteAllText(Path.Combine(output,"verify-result.txt"),"PASS: Android -> Windows completion/create; Windows -> Android delete published.");return 0;
                }
                Cases cases;using(var stream=File.OpenRead(Path.Combine(root,"android","app","src","androidTest","assets","cases.json")))cases=(Cases)new DataContractJsonSerializer(typeof(Cases)).ReadObject(stream);
                foreach(var test in cases.Values)
                {
                    try{var result=SyncModel.Merge(test.Baseline,test.Local,test.Remote);Check(test.Outcome=="ok"&&SyncModel.Equal(result,test.Expected),test.Name);Check(SyncModel.Equal(result,SyncModel.Decode(SyncModel.Encode(result))),"Wire roundtrip");}
                    catch(SyncConflict conflict){Check(test.Outcome=="conflict",test.Name);Check(conflict.Local.Count==1,"Conflict contents preserved");var chosen=SyncModel.Merge(test.Baseline,test.Local,test.Remote,new SyncResolution {Conflict=conflict,UseLocal=true});Check(SyncItem.Equal(chosen.Items.Single(),test.Local.Items.Single()),"Explicit conflict choice");}
                    catch(InvalidDataException){Check(test.Outcome=="error",test.Name);}
                }
                var settings=new SyncSettings {User="test-user",Password="test-password"};string config=Path.Combine(output,"settings.bin");settings.Save(config);Check(SyncSettings.Load(config).Password==settings.Password,"DPAPI credential roundtrip");Check(!Encoding.UTF8.GetString(File.ReadAllBytes(config)).Contains(settings.Password),"No plaintext password");
                foreach(string path in new[]{"unauthorized","redirect","no-etag","no-cas"}){bool refused=false;try{new WebDavSync(Settings(path),true).CheckConnection();}catch(IOException){refused=true;}Check(refused,"Reject unsafe server: "+path);}
                var localDoc=new SyncDocument();localDoc.Items.Add(new SyncItem {Id=new string('1',32),Title="Windows 新建 🌿",Notes="中文\n换行",Due=1790000000123L,Created=1789990000123L,Lead=60});
                var seeded=client.Exchange(new SyncDocument(),localDoc,output);File.WriteAllBytes(saved,SyncModel.Encode(seeded));
                var racing=new WebDavSync(Settings("race-windows"),true);var raceBase=racing.Exchange(new SyncDocument(),localDoc,output);var raceLocal=SyncModel.Decode(SyncModel.Encode(raceBase));raceLocal.Items[0].Done=true;var raceMerged=racing.Exchange(raceBase,raceLocal,output);Check(raceMerged.Items.Count==2&&raceMerged.Items.Single(t=>t.Id==new string('1',32)).Done,"412 retry merges concurrent addition");
                var state=new MemoState();state.Tasks.Add(new Todo {Id=new string('1',32),Title="旧标题",Desktop=false,NotifiedStage=2});var applied=SyncModel.Apply(state,seeded);Check(!applied.Tasks.Single().Desktop&&applied.Tasks.Single().NotifiedStage==0,"Local display retained, changed deadline rearmed");
                var unacknowledged=new MemoState();unacknowledged.SyncDeleted.Add(new string('1',32));Check(SyncModel.Snapshot(unacknowledged).Items.Single().Deleted,"Deletion survives missing upload acknowledgement");
                File.WriteAllText(Path.Combine(output,"result.txt"),"PASS: 12 shared merge cases; explicit conflicts; DPAPI; authentication, redirects, missing ETag and missing CAS rejected; Windows -> mock WebDAV seeded.");return 0;
            }
            catch(Exception ex){File.WriteAllText(Path.Combine(output,verify?"verify-result.txt":"result.txt"),ex.ToString());return 1;}
        }
    }
}
