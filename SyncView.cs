using System;
using System.IO;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;

namespace DesktopMemo
{
    public sealed class SyncController
    {
        readonly MemoApp app;
        public SyncSettings Settings=new SyncSettings();
        public string Status="尚未开启跨设备同步";
        public bool Busy { get; private set; }
        public event Action Changed;
        DateTime lastAttempt=DateTime.MinValue;
        string Folder { get { return Path.GetDirectoryName(app.Store.PathName); } }
        string ConfigPath { get { return Path.Combine(Folder,"sync-settings.bin"); } }
        public SyncController(MemoApp app) { this.app=app; }
        void Notify() { if(Changed!=null)Changed(); }
        public void Initialize()
        {
            try { Settings=SyncSettings.Load(ConfigPath);Status=Settings.Enabled?"同步已开启，等待连接":"尚未开启跨设备同步"; }
            catch(Exception ex)
            {
                if(!(ex is IOException)&&!(ex is UnauthorizedAccessException)&&!(ex is System.Security.Cryptography.CryptographicException)&&!(ex is System.Runtime.Serialization.SerializationException))throw;
                Status="同步配置无法读取，请重新填写账号和应用密码。";
            }
            Notify();
        }
        public void Configure(SyncSettings settings)
        {
            if(Busy)throw new InvalidOperationException("正在同步，请稍后修改设置。");
            settings.Validate();
            if(!string.IsNullOrEmpty(app.State.SyncAccount)&&settings.Binding()!=app.State.SyncAccount)throw new InvalidOperationException("已有同步记录，本版请继续使用原账号和目录。可以更新应用密码或停用同步。");
            settings.Save(ConfigPath);Settings=settings;Status="设置已保存，尚未验证连接";Notify();
        }
        public void Disable()
        {
            if(Busy)throw new InvalidOperationException("正在同步，请稍后停用。");
            Settings.Enabled=false;Settings.Save(ConfigPath);Status="已停用同步，本机与网盘数据均保留";Notify();
        }
        public void Tick() { if(!app.TestMode&&Settings.Enabled&&!app.EditorOpen&&!Busy&&DateTime.Now-lastAttempt>TimeSpan.FromMinutes(2))Run(false,null); }
        public async void Run(bool interactive,SyncResolution resolution)
        {
            if(Busy)return;
            if(!Settings.Enabled){Status="请先保存并开启同步";Notify();return;}
            if(app.EditorOpen){Status="请先保存或关闭正在编辑的待办";Notify();return;}
            Busy=true;lastAttempt=DateTime.Now;Status="正在同步…";Notify();
            SyncConflict conflict=null;
            try
            {
                Settings.Validate();
                var local=SyncModel.Snapshot(app.State);var baseline=new SyncDocument {Items=app.State.SyncBase};
                string backupFolder=Path.Combine(Folder,"sync-backups");var client=new WebDavSync(Settings);
                var merged=await Task.Run(()=>client.Exchange(baseline,local,backupFolder,resolution));
                if(!SyncModel.Equal(local,SyncModel.Snapshot(app.State)))throw new IOException("同步期间本机有新修改，已保留。请再同步一次；部分修改可能已上传云端。");
                var next=SyncModel.Apply(app.State,merged);next.SyncAccount=Settings.Binding();app.Store.Save(next);app.State=next;app.Refresh();app.CheckReminders();
                Status="同步成功 · "+DateTime.Now.ToString("MM-dd HH:mm:ss");
            }
            catch(SyncConflict ex){conflict=ex;Status="有 "+ex.Local.Count+" 项冲突 · 点击立即同步处理";}
            catch(Exception ex)
            {
                if(!(ex is IOException)&&!(ex is UnauthorizedAccessException)&&!(ex is System.Runtime.Serialization.SerializationException)&&!(ex is System.Net.WebException)&&!(ex is ArgumentException))throw;
                Status="同步失败："+ex.Message;
            }
            finally { Busy=false;Notify(); }
            if(interactive&&conflict!=null)
            {
                bool? choice=SyncView.Choose(app.Main,conflict);
                if(choice.HasValue)Run(true,new SyncResolution {Conflict=conflict,UseLocal=choice.Value});
            }
        }
    }
    public sealed class SyncView : Window
    {
        public SyncView(MemoApp app)
        {
            var sync=app.Sync;UI.Init(this);Title="青笺 · 跨设备同步";Width=520;Height=610;MinWidth=430;MinHeight=500;WindowStartupLocation=WindowStartupLocation.CenterOwner;Owner=app.Main;
            var panel=new StackPanel {Margin=new Thickness(24)};Content=new ScrollViewer {Content=panel,VerticalScrollBarVisibility=ScrollBarVisibility.Auto};
            panel.Children.Add(UI.Text("连接你的网盘",24,UI.Green));
            panel.Children.Add(UI.Text("两端填写相同账号与服务器目录。开启后，待办内容、DDL 和完成状态将上传为网盘中的青笺专用同步文件。",13,UI.Muted));
            panel.Children.Add(UI.Text("服务器目录（HTTPS）",13,UI.Ink));var url=new TextBox {Text=sync.Settings.Url,Margin=new Thickness(0,8,0,16),Padding=new Thickness(8)};panel.Children.Add(url);
            panel.Children.Add(UI.Text("网盘账号",13,UI.Ink));var user=new TextBox {Text=sync.Settings.User,Margin=new Thickness(0,8,0,16),Padding=new Thickness(8)};panel.Children.Add(user);
            panel.Children.Add(UI.Text("应用专用密码",13,UI.Ink));var password=new PasswordBox {Password=sync.Settings.Password,Margin=new Thickness(0,8,0,16),Padding=new Thickness(8)};panel.Children.Add(password);
            var enabled=new CheckBox {Content="开启自动同步（运行期间约每 2 分钟）",IsChecked=sync.Settings.Enabled,Margin=new Thickness(0,0,0,16)};panel.Children.Add(enabled);
            var status=UI.Text(sync.Status,12,UI.Green);status.Margin=new Thickness(0,12,0,12);
            var buttons=new WrapPanel();
            buttons.Children.Add(UI.Button("保存并同步",()=>{
                try{sync.Configure(new SyncSettings {Url=url.Text.Trim(),User=user.Text.Trim(),Password=password.Password,Enabled=enabled.IsChecked==true});sync.Run(true,null);}
                catch(Exception ex){if(!(ex is IOException)&&!(ex is InvalidOperationException)&&!(ex is UnauthorizedAccessException)&&!(ex is System.Security.Cryptography.CryptographicException))throw;status.Text=ex.Message;}
            },true));
            buttons.Children.Add(UI.Button("立即同步",()=>sync.Run(true,null)));
            buttons.Children.Add(UI.Button("停用",()=>{try{sync.Disable();enabled.IsChecked=false;}catch(Exception ex){if(!(ex is IOException)&&!(ex is InvalidOperationException)&&!(ex is UnauthorizedAccessException)&&!(ex is System.Security.Cryptography.CryptographicException))throw;status.Text=ex.Message;}}));
            panel.Children.Add(buttons);panel.Children.Add(status);
            panel.Children.Add(UI.Text("首次连接会合并双方清单。冲突需要你选择版本；删除也会同步。小窗位置、折叠状态和本机提醒记录不上传。\n\n使用 HTTPS 传输；同步文件不是端到端加密，网盘服务商可以访问其内容。账号和应用密码在本机使用 Windows 加密保护。同步前的双方副本保存在数据目录的 sync-backups 中。",12,UI.Muted));
            Action update=()=>{status.Text=sync.Status;buttons.IsEnabled=!sync.Busy;};sync.Changed+=update;Closed+=(s,e)=>sync.Changed-=update;update();
        }
        public static bool? Choose(Window owner,SyncConflict conflict)
        {
            bool? choice=null;var window=new Window {Owner=owner,Title="同步冲突 · 选择版本",Width=660,Height=620,WindowStartupLocation=WindowStartupLocation.CenterOwner};UI.Init(window);
            var panel=new DockPanel {Margin=new Thickness(20)};window.Content=panel;
            var intro=UI.Text("以下事项在两端都有修改。选择应用于下面全部冲突；其他事项仍正常合并。双方副本已备份。",14,UI.Ink);DockPanel.SetDock(intro,Dock.Top);panel.Children.Add(intro);
            var buttons=new WrapPanel {Margin=new Thickness(0,12,0,0)};DockPanel.SetDock(buttons,Dock.Bottom);panel.Children.Add(buttons);
            buttons.Children.Add(UI.Button("保留本机冲突版本",()=>{choice=true;window.Close();}));buttons.Children.Add(UI.Button("使用云端冲突版本",()=>{choice=false;window.Close();}));buttons.Children.Add(UI.Button("取消",()=>window.Close()));
            panel.Children.Add(new TextBox {Text=conflict.Details(),IsReadOnly=true,TextWrapping=TextWrapping.Wrap,VerticalScrollBarVisibility=ScrollBarVisibility.Auto,Margin=new Thickness(0,12,0,0)});window.ShowDialog();return choice;
        }
    }
}
