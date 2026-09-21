package io.github.junhezhang.qingjian;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import android.util.Base64;

public final class WebDavSync {
    final String folder,authorization;
    static final class Response {int status;String etag;byte[] body;}
    public WebDavSync(SyncSettings s){this(s,false);}
    WebDavSync(SyncSettings s,boolean emulatorTest){if(!emulatorTest||!(s.url.startsWith("http://10.0.2.2:")||s.url.startsWith("http://127.0.0.1:")))s.validate();folder=s.url.replaceAll("/+$","")+"/";authorization="Basic "+Base64.encodeToString((s.user+":"+s.password).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);}
    Response request(String method,String name,byte[] body,String match,boolean create)throws IOException {
        HttpURLConnection connection=(HttpURLConnection)new URL(folder+name).openConnection();connection.setRequestMethod(method);connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(30000);connection.setReadTimeout(30000);connection.setUseCaches(false);connection.setRequestProperty("Authorization",authorization);connection.setRequestProperty("User-Agent","QingJian/0.2");
        if(match!=null)connection.setRequestProperty("If-Match",match);if(create)connection.setRequestProperty("If-None-Match","*");
        try{
            if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setFixedLengthStreamingMode(body.length);try(OutputStream out=connection.getOutputStream()){out.write(body);}}
            Response r=new Response();r.status=connection.getResponseCode();r.etag=connection.getHeaderField("ETag");InputStream input=r.status>=400?connection.getErrorStream():connection.getInputStream();
            try(InputStream stream=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){if(stream!=null){byte[] bytes=new byte[8192];int count;while((count=stream.read(bytes))!=-1){if(out.size()+count>SyncModel.MAX_BYTES)throw new IOException("服务器响应超过 4 MB。");out.write(bytes,0,count);}}r.body=out.toByteArray();}return r;
        }finally{connection.disconnect();}
    }
    static void require(Response r,int... values)throws IOException {for(int v:values)if(r.status==v)return;if(r.status==401||r.status==403)throw new IOException("网盘授权失败，请检查账号和应用密码（HTTP "+r.status+"）。");if(r.status==429)throw new IOException("网盘请求过于频繁，请稍后重试。");throw new IOException("同步服务器返回 HTTP "+r.status+"，本机待办未被替换。");}
    static String tag(Response r)throws IOException {String tag=r.etag;if(tag==null||tag.length()<2||!tag.startsWith("\"")||!tag.endsWith("\"")||tag.contains("\r")||tag.contains("\n"))throw new IOException("该 WebDAV 服务未提供强 ETag，无法安全处理同时修改。");return tag;}
    public void checkConnection()throws IOException {
        String name=".qingjian-probe-"+UUID.randomUUID()+".json";byte[] body="{\"probe\":1}".getBytes(StandardCharsets.UTF_8);require(request("PUT",name,body,null,true),200,201,204);
        try{requireConditionalRejection(request("PUT",name,body,null,true));requireConditionalRejection(request("PUT",name,body,"\"qingjian-must-not-match\"",false));Response read=request("GET",name,null,null,false);require(read,200);require(request("PUT",name,"{\"probe\":2}".getBytes(StandardCharsets.UTF_8),tag(read),false),200,201,204);}
        finally{require(request("DELETE",name,null,null,false),200,204,404);}
    }
    static void requireConditionalRejection(Response r)throws IOException {if(r.status>=200&&r.status<300)throw new IOException("该网盘未执行条件写入，不能安全同步。请联系服务商。");require(r,412);}
    static String digest(byte[] data)throws java.security.NoSuchAlgorithmException {return Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(data),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
    static void backup(File dir,String name,byte[] data)throws IOException {if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建同步备份目录。");File file=new File(dir,name);if(file.exists())return;try(FileOutputStream out=new FileOutputStream(file)){out.write(data);out.getFD().sync();}}
    public List<SyncModel.Item> exchange(List<SyncModel.Item> baseline,List<SyncModel.Item> local,File backupDir,SyncModel.Resolution resolution)throws Exception {
        checkConnection();
        for(int attempt=0;attempt<3;attempt++){
            Response read=request("GET","qingjian-tasks-v1.json",null,null,false);require(read,200,404);List<SyncModel.Item> remote=read.status==404?new ArrayList<>():SyncModel.decode(read.body);String etag=read.status==404?null:tag(read);
            if(!SyncModel.same(local,remote)){byte[] left=SyncModel.encode(local),right=SyncModel.encode(remote);String stamp=digest(left)+digest(right);backup(backupDir,stamp+"-local.json",left);backup(backupDir,stamp+"-remote.json",right);}
            List<SyncModel.Item> merged=SyncModel.merge(baseline,local,remote,resolution);if(read.status==200&&SyncModel.same(merged,remote))return merged;
            Response write=request("PUT","qingjian-tasks-v1.json",SyncModel.encode(merged),etag,etag==null);if(write.status==412)continue;require(write,200,201,204);return merged;
        }throw new IOException("另一台设备正在修改清单，本机修改已保留，请稍后重试。");
    }
}
