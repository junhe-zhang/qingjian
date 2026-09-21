package io.github.junhezhang.qingjian;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.json.*;

public final class SyncSettings {
    public String url="https://dav.jianguoyun.com/dav/",user="",password="";
    public boolean enabled;
    static final String ALIAS="qingjian-webdav";
    public String binding()throws GeneralSecurityException{return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest((url.replaceAll("/+$","")+"\n"+user).getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}
    public void validate(){URI u=URI.create(url);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null)throw new IllegalArgumentException("请填写不含账号和查询参数的 HTTPS 目录地址。");if(user.trim().isEmpty()||user.contains(":")||user.contains("\n")||user.contains("\r")||password.isEmpty())throw new IllegalArgumentException("请填写网盘账号和应用密码。");}
    public static SyncSettings load(Context c)throws Exception {
        String encoded=c.getSharedPreferences("sync-secure",0).getString("credential",null);if(encoded==null)return new SyncSettings();
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);Key key=store.getKey(ALIAS,null);if(key==null)throw new GeneralSecurityException("同步凭据无法解密，请重新填写应用密码。");
        JSONObject envelope=new JSONObject(encoded);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(envelope.getString("iv"),Base64.NO_WRAP)));
        JSONObject o=new JSONObject(new String(cipher.doFinal(Base64.decode(envelope.getString("data"),Base64.NO_WRAP)),StandardCharsets.UTF_8));SyncSettings result=new SyncSettings();result.url=o.getString("url");result.user=o.getString("user");result.password=o.getString("password");result.enabled=o.getBoolean("enabled");return result;
    }
    public void save(Context c)throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();}
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,store.getKey(ALIAS,null));byte[] plain=new JSONObject().put("url",url).put("user",user).put("password",password).put("enabled",enabled).toString().getBytes(StandardCharsets.UTF_8);
        String value=new JSONObject().put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).put("data",Base64.encodeToString(cipher.doFinal(plain),Base64.NO_WRAP)).toString();
        if(!c.getSharedPreferences("sync-secure",0).edit().putString("credential",value).commit())throw new java.io.IOException("同步设置未能保存。");
    }
}
