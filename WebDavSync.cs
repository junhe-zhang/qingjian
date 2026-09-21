using System;
using System.IO;
using System.Net;
using System.Text;
using System.Runtime.Serialization;
using System.Runtime.Serialization.Json;
using System.Security.Cryptography;

namespace DesktopMemo
{
    [DataContract]
    public class SyncSettings
    {
        [DataMember] public string Url="https://dav.jianguoyun.com/dav/";
        [DataMember] public string User="";
        [DataMember] public string Password="";
        [DataMember] public bool Enabled;
        public string Binding() { using(var hash=SHA256.Create())return Convert.ToBase64String(hash.ComputeHash(Encoding.UTF8.GetBytes(Url.TrimEnd('/')+"\n"+User))); }
        public void Validate()
        {
            Uri uri;
            if(!Uri.TryCreate(Url,UriKind.Absolute,out uri)||uri.Scheme!="https"||uri.UserInfo!=""||uri.Query!=""||uri.Fragment!="")throw new InvalidDataException("服务器地址必须是 HTTPS 目录地址，不含账号、查询参数或片段。");
            if(string.IsNullOrWhiteSpace(User)||User.Contains(":")||User.Contains("\r")||User.Contains("\n")||string.IsNullOrEmpty(Password))throw new InvalidDataException("请填写网盘账号和应用密码。");
        }
        public static SyncSettings Load(string path)
        {
            if(!File.Exists(path))return new SyncSettings();
            byte[] clear=ProtectedData.Unprotect(File.ReadAllBytes(path),null,DataProtectionScope.CurrentUser);
            using(var input=new MemoryStream(clear))return (SyncSettings)new DataContractJsonSerializer(typeof(SyncSettings)).ReadObject(input);
        }
        public void Save(string path)
        {
            using(var output=new MemoryStream())
            {
                new DataContractJsonSerializer(typeof(SyncSettings)).WriteObject(output,this);
                byte[] encrypted=ProtectedData.Protect(output.ToArray(),null,DataProtectionScope.CurrentUser);
                Directory.CreateDirectory(Path.GetDirectoryName(path));string temp=path+".tmp";File.WriteAllBytes(temp,encrypted);
                if(File.Exists(path))File.Replace(temp,path,null);else File.Move(temp,path);
            }
        }
    }
    public sealed class WebDavSync
    {
        readonly Uri folder;
        readonly string authorization;
        public sealed class Response
        {
            public int Status; public string Etag; public byte[] Body;
        }
        public WebDavSync(SyncSettings settings) : this(settings,false) { }
        internal WebDavSync(SyncSettings settings,bool loopbackTest)
        {
            var uri=new Uri(settings.Url.TrimEnd('/')+"/");
            if(!loopbackTest || !uri.IsLoopback)settings.Validate();
            folder=uri;
            authorization="Basic "+Convert.ToBase64String(Encoding.UTF8.GetBytes(settings.User+":"+settings.Password));
        }
        Response Request(string method,string name,byte[] body=null,string match=null,bool create=false)
        {
            var request=(HttpWebRequest)WebRequest.Create(new Uri(folder,name));request.Method=method;request.AllowAutoRedirect=false;request.Timeout=30000;request.ReadWriteTimeout=30000;
            request.Headers[HttpRequestHeader.Authorization]=authorization;request.UserAgent="QingJian/1.3";
            request.CachePolicy=new System.Net.Cache.RequestCachePolicy(System.Net.Cache.RequestCacheLevel.NoCacheNoStore);
            if(match!=null)request.Headers[HttpRequestHeader.IfMatch]=match;
            if(create)request.Headers[HttpRequestHeader.IfNoneMatch]="*";
            if(body!=null){request.ContentType="application/json; charset=utf-8";request.ContentLength=body.Length;using(var stream=request.GetRequestStream())stream.Write(body,0,body.Length);}
            HttpWebResponse response;
            try{response=(HttpWebResponse)request.GetResponse();}
            catch(WebException ex){if(ex.Response==null)throw new IOException("无法连接同步服务器，请检查网络后重试。",ex);response=(HttpWebResponse)ex.Response;}
            using(response)
            using(var stream=response.GetResponseStream())
            using(var output=new MemoryStream())
            {
                var buffer=new byte[8192];int count;
                while((count=stream.Read(buffer,0,buffer.Length))>0){if(output.Length+count>SyncModel.MaxBytes)throw new InvalidDataException("服务器响应超过 4 MB，已停止同步。");output.Write(buffer,0,count);}
                return new Response {Status=(int)response.StatusCode,Etag=response.Headers[HttpResponseHeader.ETag],Body=output.ToArray()};
            }
        }
        static void Require(Response r,params int[] allowed)
        {
            if(Array.IndexOf(allowed,r.Status)>=0)return;
            if(r.Status==401||r.Status==403)throw new IOException("网盘授权失败，请检查账号和应用密码（HTTP "+r.Status+"）。");
            if(r.Status==429)throw new IOException("网盘请求过于频繁，请稍后同步。");
            throw new IOException("同步服务器返回 HTTP "+r.Status+"，本机待办未被替换。");
        }
        static string StrongTag(Response r)
        {
            string tag=r.Etag;
            if(string.IsNullOrEmpty(tag)||tag.Length<2||!tag.StartsWith("\"",StringComparison.Ordinal)||!tag.EndsWith("\"",StringComparison.Ordinal)||tag.Contains("\r")||tag.Contains("\n"))throw new IOException("该 WebDAV 服务未提供强 ETag，无法安全处理同时修改。请更换服务或联系服务商。");
            return tag;
        }
        public void CheckConnection()
        {
            string name=".qingjian-probe-"+Guid.NewGuid().ToString("N")+".json";byte[] body=Encoding.UTF8.GetBytes("{\"probe\":1}");
            Require(Request("PUT",name,body,null,true),200,201,204);
            try
            {
                RequireConditionalRejection(Request("PUT",name,body,null,true));
                RequireConditionalRejection(Request("PUT",name,body,"\"qingjian-must-not-match\""));
                var read=Request("GET",name);Require(read,200);string tag=StrongTag(read);
                Require(Request("PUT",name,Encoding.UTF8.GetBytes("{\"probe\":2}"),tag),200,201,204);
            }
            finally { var cleanup=Request("DELETE",name);Require(cleanup,200,204,404); }
        }
        static void RequireConditionalRejection(Response response)
        {
            if(response.Status>=200&&response.Status<300)throw new IOException("该网盘未执行条件写入，不能安全同步。请联系服务商。");
            Require(response,412);
        }
        static void Backup(string folder,SyncDocument local,SyncDocument remote)
        {
            Directory.CreateDirectory(folder);
            byte[] left=SyncModel.Encode(local),right=SyncModel.Encode(remote);
            string stamp;
            using(var hash=SHA256.Create())stamp=BitConverter.ToString(hash.ComputeHash(left)).Replace("-","")+BitConverter.ToString(hash.ComputeHash(right)).Replace("-","");
            string path=Path.Combine(folder,stamp);
            if(!File.Exists(path+"-local.json"))File.WriteAllBytes(path+"-local.json",left);
            if(!File.Exists(path+"-remote.json"))File.WriteAllBytes(path+"-remote.json",right);
        }
        public SyncDocument Exchange(SyncDocument baseline,SyncDocument local,string backupFolder,SyncResolution resolution=null)
        {
            CheckConnection();
            for(int attempt=0;attempt<3;attempt++)
            {
                var read=Request("GET","qingjian-tasks-v1.json");Require(read,200,404);
                var remote=read.Status==404?new SyncDocument():SyncModel.Decode(read.Body);
                string tag=read.Status==404?null:StrongTag(read);
                // Save both sides before merging, including conflicts, without credentials.
                if(!SyncModel.Equal(local,remote))
                {
                    Backup(backupFolder,local,remote);
                }
                var merged=SyncModel.Merge(baseline,local,remote,resolution);
                if(read.Status==200&&SyncModel.Equal(merged,remote))return merged;
                var write=Request("PUT","qingjian-tasks-v1.json",SyncModel.Encode(merged),tag,tag==null);
                if(write.Status==412)continue;
                Require(write,200,201,204);return merged;
            }
            throw new IOException("另一台设备正在修改云端清单，本机修改已保留，请稍后重试。");
        }
    }
}
