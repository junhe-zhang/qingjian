using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Runtime.Serialization;
using System.Runtime.Serialization.Json;
using System.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Markup;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using Forms = System.Windows.Forms;

namespace DesktopMemo
{
    [DataContract]
    public class Todo
    {
        [DataMember] public string Id = Guid.NewGuid().ToString("N");
        [DataMember] public string Title = "";
        [DataMember] public string Notes = "";
        [DataMember] public DateTime? Due;
        [DataMember] public bool Done;
        [DataMember] public bool Desktop = true;
        [DataMember] public int LeadMinutes = 1440;
        [DataMember] public int NotifiedStage;
        [DataMember] public DateTime? SnoozedUntil;
        [DataMember] public DateTime Created = DateTime.Now;
        [DataMember] public DateTime? CompletedAt;

        public int ReminderStage(DateTime now)
        {
            if (Done || !Due.HasValue || LeadMinutes < 0) return 0;
            if (SnoozedUntil.HasValue && now < SnoozedUntil.Value) return 0;
            int stage = now >= Due.Value ? 2 : (now >= Due.Value.AddMinutes(-LeadMinutes) ? 1 : 0);
            return stage > NotifiedStage ? stage : 0;
        }
        public bool Soon(DateTime now) { return !Done && Due.HasValue && Due.Value > now && Due.Value <= now.AddHours(24); }
        public bool Overdue(DateTime now) { return !Done && Due.HasValue && Due.Value <= now; }
    }

    [DataContract]
    public class MemoState
    {
        [DataMember] public int Version = 1;
        [DataMember] public List<Todo> Tasks = new List<Todo>();
        [DataMember] public bool DesktopVisible = true;
        [DataMember] public bool AlwaysOnTop = true;
        [DataMember] public double DesktopLeft = -1;
        [DataMember] public double DesktopTop = 100;
    }

    public class Store
    {
        public readonly string PathName;
        public Store(string folder) { PathName = Path.Combine(folder, "tasks.json"); }
        public static byte[] Encode(MemoState state)
        {
            using (var stream = new MemoryStream())
            { new DataContractJsonSerializer(typeof(MemoState)).WriteObject(stream, state); return stream.ToArray(); }
        }
        public static MemoState Decode(byte[] bytes)
        {
            MemoState state;
            using (var stream = new MemoryStream(bytes))
                state = (MemoState)new DataContractJsonSerializer(typeof(MemoState)).ReadObject(stream);
            if (state == null || state.Version != 1 || state.Tasks == null || state.Tasks.Any(t => t == null || string.IsNullOrWhiteSpace(t.Id) || string.IsNullOrWhiteSpace(t.Title) || t.LeadMinutes < -1 || t.LeadMinutes > 525600 || t.NotifiedStage < 0 || t.NotifiedStage > 2) || state.Tasks.Select(t => t.Id).Distinct().Count() != state.Tasks.Count || double.IsNaN(state.DesktopLeft) || double.IsInfinity(state.DesktopLeft) || double.IsNaN(state.DesktopTop) || double.IsInfinity(state.DesktopTop))
                throw new InvalidDataException("待办数据格式不正确，原文件未被修改。");
            return state;
        }
        public MemoState Load() { return File.Exists(PathName) ? Decode(File.ReadAllBytes(PathName)) : new MemoState(); }
        public void Save(MemoState state)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(PathName));
            string temp = PathName + ".tmp";
            byte[] bytes = Encode(state);
            using (var stream = new FileStream(temp, FileMode.Create, FileAccess.Write, FileShare.None))
            { stream.Write(bytes, 0, bytes.Length); stream.Flush(true); }
            if (File.Exists(PathName)) File.Replace(temp, PathName, PathName + ".bak");
            else File.Move(temp, PathName);
        }
    }

    public static class UI
    {
        public static readonly Brush Ink = Brush("#243C36"), Muted = Brush("#78867D"), Green = Brush("#22735B"), Paper = Brush("#F6F7F2"), Red = Brush("#B74E43"), Amber = Brush("#AD771C");
        public static SolidColorBrush Brush(string hex) { return (SolidColorBrush)new BrushConverter().ConvertFromString(hex); }
        public static TextBlock Text(string value, double size, Brush color)
        { return new TextBlock { Text = value, FontSize = size, Foreground = color, TextWrapping = TextWrapping.Wrap, VerticalAlignment = VerticalAlignment.Center }; }
        public static Button Button(string label, Action action, bool primary = false)
        {
            var button = new Button { Content = label, Padding = new Thickness(14, 8, 14, 8), Margin = new Thickness(0, 0, 8, 0), Cursor = Cursors.Hand, Background = primary ? Green : Brushes.White, Foreground = primary ? Brushes.White : Ink, BorderBrush = Brush("#DEE5DD"), BorderThickness = new Thickness(primary ? 0 : 1), FontSize = 13, MinHeight = 34 };
            button.Click += (s, e) => action(); return button;
        }
        public static Border Card(UIElement child, Brush background = null)
        { return new Border { Background = background ?? Brushes.White, CornerRadius = new CornerRadius(12), Padding = new Thickness(18), Child = child, Margin = new Thickness(0, 0, 0, 10), BorderBrush = Brush("#E4E8DF"), BorderThickness = new Thickness(1) }; }
        public static void Init(Window window)
        { window.FontFamily = new FontFamily("Microsoft YaHei UI"); window.Foreground = Ink; window.Background = Paper; window.FontSize = 13; window.UseLayoutRounding = true; }
        public static string Deadline(Todo task, DateTime now)
        {
            if (!task.Due.HasValue) return "未设置截止日期";
            string date = task.Due.Value.ToString("MM月dd日 HH:mm");
            if (task.Due.Value.Year != now.Year) date = task.Due.Value.ToString("yyyy年") + date;
            if (task.Done) return date + " 截止";
            TimeSpan delta = task.Due.Value - now;
            if (delta.TotalSeconds <= 0) return date + " · 已逾期";
            if (delta.TotalHours < 1) return date + " · 剩 " + Math.Max(1, (int)Math.Ceiling(delta.TotalMinutes)) + " 分钟";
            if (delta.TotalHours < 24) return date + " · 剩 " + (int)Math.Ceiling(delta.TotalHours) + " 小时";
            return date + " · 剩 " + (int)Math.Ceiling(delta.TotalDays) + " 天";
        }
    }

    public class MemoApp : Application
    {
        public MemoState State;
        public Store Store;
        public MainView Main;
        public DesktopView Desktop;
        public ReminderView Reminder;
        Forms.NotifyIcon tray;
        DispatcherTimer timer;
        public bool Exiting;
        public bool TestMode;
        bool checking;
        public string Filter = "全部事项";

        public bool Change(Action<MemoState> change)
        {
            MemoState next = DesktopMemo.Store.Decode(DesktopMemo.Store.Encode(State));
            change(next);
            try { Store.Save(next); }
            catch (Exception ex)
            { if (!(ex is IOException) && !(ex is UnauthorizedAccessException)) throw; MessageBox.Show("保存失败，本次修改未生效。\n" + ex.Message, "无法保存", MessageBoxButton.OK, MessageBoxImage.Error); Refresh(); return false; }
            State = next; Refresh(); return true;
        }
        public void Refresh()
        {
            if (Main != null) Main.Refresh();
            if (Desktop != null)
            {
                Desktop.Topmost = State.AlwaysOnTop;
                if (State.DesktopVisible) { Desktop.Refresh(); if (!TestMode) Desktop.Show(); }
                else Desktop.Hide();
            }
            if (Reminder != null) Reminder.Refresh();
        }
        public void SetDone(string id, bool done)
        {
            Change(state => { var task = state.Tasks.Single(t => t.Id == id); task.Done = done; task.CompletedAt = done ? (DateTime?)DateTime.Now : null; if (!done) { task.NotifiedStage = 0; task.SnoozedUntil = null; } });
        }
        public void ShowMain() { Main.Show(); Main.WindowState = WindowState.Normal; Main.Activate(); }
        public void Edit(Todo task) { var dialog = new Editor(this, task) { Owner = Main.IsVisible ? (Window)Main : Desktop }; if (dialog.ShowDialog() == true) CheckReminders(); }
        public void CheckReminders()
        {
            if (checking || Exiting) return;
            checking = true;
            try
            {
                DateTime now = DateTime.Now;
                var alerts = State.Tasks.Where(t => t.ReminderStage(now) > 0).Select(t => t.Id).ToList();
                if (alerts.Count == 0) return;
                if (!Change(state => { foreach (var task in state.Tasks.Where(t => alerts.Contains(t.Id))) { task.NotifiedStage = task.ReminderStage(now); task.SnoozedUntil = null; } })) return;
                if (Reminder == null) Reminder = new ReminderView(this);
                Reminder.Add(alerts);
                Reminder.Show();
                if (tray != null)
                {
                    tray.BalloonTipTitle = "待办截止提醒";
                    tray.BalloonTipText = alerts.Count == 1 ? State.Tasks.Single(t => t.Id == alerts[0]).Title : "有 " + alerts.Count + " 项待办临近截止或已到期，点击查看。";
                    tray.ShowBalloonTip(8000);
                    System.Media.SystemSounds.Asterisk.Play();
                }
            }
            finally { checking = false; }
        }
        public void Start()
        {
            ShutdownMode = ShutdownMode.OnExplicitShutdown;
            Main = new MainView(this);
            MainWindow = Main;
            Desktop = new DesktopView(this);
            tray = new Forms.NotifyIcon { Icon = System.Drawing.Icon.ExtractAssociatedIcon(System.Reflection.Assembly.GetExecutingAssembly().Location), Text = "青笺 · 正在提醒", Visible = true };
            var menu = new Forms.ContextMenuStrip();
            menu.Items.Add("打开备忘录", null, (s, e) => ShowMain());
            menu.Items.Add("显示 / 隐藏桌面小窗", null, (s, e) => Change(state => state.DesktopVisible = !state.DesktopVisible));
            menu.Items.Add("退出（停止提醒）", null, (s, e) => Quit());
            tray.ContextMenuStrip = menu;
            tray.DoubleClick += (s, e) => ShowMain();
            tray.BalloonTipClicked += (s, e) => { if (Reminder != null && Reminder.HasItems) { Reminder.Show(); Reminder.Activate(); } else ShowMain(); };
            timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(15) };
            timer.Tick += (s, e) => { Refresh(); CheckReminders(); };
            timer.Start(); Refresh(); Main.Show();
            Dispatcher.BeginInvoke(new Action(CheckReminders), DispatcherPriority.ApplicationIdle);
        }
        public void Quit()
        {
            Exiting = true;
            if (timer != null) timer.Stop();
            if (tray != null) { tray.Visible = false; tray.Dispose(); }
            Shutdown();
        }
    }

    public class MainView : Window
    {
        readonly MemoApp app;
        readonly StackPanel tasks = new StackPanel();
        readonly TextBlock count = UI.Text("", 13, UI.Muted);
        readonly TextBlock heading = UI.Text("全部事项", 23, UI.Ink);
        readonly TextBlock stats = UI.Text("", 16, UI.Ink);
        readonly TextBlock day = UI.Text("", 13, UI.Muted);
        readonly CheckBox desktopToggle = new CheckBox { Content = "在桌面显示小窗", Margin = new Thickness(0, 8, 0, 12) };
        readonly CheckBox topToggle = new CheckBox { Content = "桌面小窗置顶", Margin = new Thickness(0, 0, 0, 12) };
        readonly TextBox search = new TextBox { Height = 36, Padding = new Thickness(10, 7, 10, 5), Width = 210, ToolTip = "搜索标题和备注", VerticalContentAlignment = VerticalAlignment.Center };
        readonly Dictionary<string, Button> nav = new Dictionary<string, Button>();
        bool rendering;
        public MainView(MemoApp app)
        {
            this.app = app; UI.Init(this); Title = "青笺 QingJian"; Width = 1080; Height = 760; MinWidth = 880; MinHeight = 580; WindowStartupLocation = WindowStartupLocation.CenterScreen;
            var root = new Grid(); root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(228) }); root.ColumnDefinitions.Add(new ColumnDefinition()); Content = root;
            var side = new DockPanel { Background = UI.Brush("#EAF0E6"), Margin = new Thickness(0) };
            root.Children.Add(side);
            var bottom = new StackPanel { Margin = new Thickness(24) };
            bottom.Children.Add(UI.Text("桌面与提醒", 12, UI.Muted)); bottom.Children.Add(desktopToggle); bottom.Children.Add(topToggle);
            bottom.Children.Add(UI.Text("关闭主窗口后，托盘继续提醒。\n完全退出后将停止提醒。", 11, UI.Muted));
            var exit = UI.Button("退出程序", () => app.Quit()); exit.Margin = new Thickness(0, 18, 0, 0); bottom.Children.Add(exit);
            DockPanel.SetDock(bottom, Dock.Bottom); side.Children.Add(bottom);
            var sideTop = new StackPanel { Margin = new Thickness(24, 32, 20, 0) }; side.Children.Add(sideTop);
            sideTop.Children.Add(UI.Text("◷  青笺", 25, UI.Green));
            var subtitle = UI.Text("留一点空间，专注当下。", 12, UI.Muted); subtitle.Margin = new Thickness(0, 10, 0, 40); sideTop.Children.Add(subtitle);
            sideTop.Children.Add(UI.Text("我的清单", 11, UI.Muted));
            foreach (string label in new[] { "全部事项", "未完成", "临近截止", "已逾期", "已完成" })
            {
                string filter = label;
                var button = UI.Button(label, () => { app.Filter = filter; Refresh(); }); button.HorizontalContentAlignment = HorizontalAlignment.Left; button.Margin = new Thickness(0, 10, 0, 0); button.Padding = new Thickness(14, 12, 0, 12); nav[label] = button; sideTop.Children.Add(button);
            }
            var body = new DockPanel { Margin = new Thickness(32, 30, 32, 22) }; Grid.SetColumn(body, 1); root.Children.Add(body);
            var header = new StackPanel(); DockPanel.SetDock(header, Dock.Top); body.Children.Add(header);
            header.Children.Add(day);
            var titleRow = new DockPanel { Margin = new Thickness(0, 16, 0, 18) };
            var add = UI.Button("＋ 新建待办", () => app.Edit(null), true); DockPanel.SetDock(add, Dock.Right); add.Margin = new Thickness(10, 0, 0, 0); titleRow.Children.Add(add);
            titleRow.Children.Add(UI.Text("把重要的事，记在这里", 27, UI.Ink)); header.Children.Add(titleRow);
            header.Children.Add(UI.Card(stats, UI.Brush("#EDF3E9")));
            var listHeader = new DockPanel { Margin = new Thickness(0, 18, 0, 16) };
            var searchBox = new StackPanel { Orientation = Orientation.Horizontal }; searchBox.Children.Add(UI.Text("搜索  ", 12, UI.Muted)); searchBox.Children.Add(search); DockPanel.SetDock(searchBox, Dock.Right); listHeader.Children.Add(searchBox); listHeader.Children.Add(heading); header.Children.Add(listHeader);
            var footer = new DockPanel { Margin = new Thickness(0, 12, 0, 0) }; DockPanel.SetDock(footer, Dock.Bottom); body.Children.Add(footer);
            var tip = UI.Text("数据自动保存在本机", 11, UI.Muted); DockPanel.SetDock(tip, Dock.Right); footer.Children.Add(tip); footer.Children.Add(count);
            body.Children.Add(new ScrollViewer { Content = tasks, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled, Padding = new Thickness(0, 0, 8, 0) });
            search.TextChanged += (s, e) => Refresh();
            desktopToggle.Click += (s, e) => { if (!rendering) app.Change(state => state.DesktopVisible = desktopToggle.IsChecked == true); };
            topToggle.Click += (s, e) => { if (!rendering) app.Change(state => state.AlwaysOnTop = topToggle.IsChecked == true); };
            Closing += (s, e) => { if (!app.Exiting) { e.Cancel = true; Hide(); } };
        }
        public void Refresh()
        {
            rendering = true;
            DateTime now = DateTime.Now;
            day.Text = now.ToString("yyyy年M月d日  dddd", CultureInfo.GetCultureInfo("zh-CN"));
            desktopToggle.IsChecked = app.State.DesktopVisible; topToggle.IsChecked = app.State.AlwaysOnTop;
            int total = app.State.Tasks.Count, done = app.State.Tasks.Count(t => t.Done), soon = app.State.Tasks.Count(t => t.Soon(now)), late = app.State.Tasks.Count(t => t.Overdue(now));
            stats.Text = "待完成  " + (total - done) + "     ·     24h 内截止  " + soon + "     ·     已逾期  " + late + "     ·     已完成  " + done;
            heading.Text = app.Filter;
            foreach (var item in nav) { item.Value.Background = item.Key == app.Filter ? UI.Green : Brushes.Transparent; item.Value.Foreground = item.Key == app.Filter ? Brushes.White : UI.Ink; item.Value.BorderThickness = new Thickness(0); }
            IEnumerable<Todo> list = app.State.Tasks;
            if (app.Filter == "未完成") list = list.Where(t => !t.Done);
            if (app.Filter == "临近截止") list = list.Where(t => t.Soon(now));
            if (app.Filter == "已逾期") list = list.Where(t => t.Overdue(now));
            if (app.Filter == "已完成") list = list.Where(t => t.Done);
            string query = search.Text.Trim();
            if (query.Length > 0) list = list.Where(t => t.Title.IndexOf(query, StringComparison.CurrentCultureIgnoreCase) >= 0 || (t.Notes ?? "").IndexOf(query, StringComparison.CurrentCultureIgnoreCase) >= 0);
            var visible = list.OrderBy(t => t.Done).ThenBy(t => t.Due ?? DateTime.MaxValue).ThenByDescending(t => t.Created).ToList();
            tasks.Children.Clear();
            foreach (Todo task in visible) tasks.Children.Add(TaskCard(task, now));
            if (visible.Count == 0)
            {
                var empty = new StackPanel { Margin = new Thickness(28, 50, 28, 50), HorizontalAlignment = HorizontalAlignment.Center };
                empty.Children.Add(UI.Text(total == 0 ? "清单空空，心里有数。" : "这里暂时没有待办", 22, UI.Green));
                var help = UI.Text(total == 0 ? "点击「新建待办」，记下第一件重要的事。\n设置 DDL 后，我会在临近截止时提醒你。" : "换个分类，或试试其他搜索词。", 13, UI.Muted); help.Margin = new Thickness(0, 18, 0, 0); empty.Children.Add(help); tasks.Children.Add(UI.Card(empty));
            }
            count.Text = "显示 " + visible.Count + " 项 / 共 " + total + " 项";
            rendering = false;
        }
        Border TaskCard(Todo task, DateTime now)
        {
            var row = new DockPanel();
            var check = new CheckBox { IsChecked = task.Done, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, 5, 14, 0), ToolTip = "标记完成 / 恢复待办", LayoutTransform = new ScaleTransform(1.25, 1.25) };
            check.Click += (s, e) => app.SetDone(task.Id, check.IsChecked == true); DockPanel.SetDock(check, Dock.Left); row.Children.Add(check);
            var actions = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Top };
            var edit = UI.Button("编辑", () => app.Edit(task)); edit.Margin = new Thickness(4, 0, 0, 0); actions.Children.Add(edit);
            DockPanel.SetDock(actions, Dock.Right); row.Children.Add(actions);
            var content = new StackPanel(); row.Children.Add(content);
            var title = UI.Text(task.Title, 17, task.Done ? UI.Muted : UI.Ink); title.FontWeight = FontWeights.SemiBold; if (task.Done) title.TextDecorations = TextDecorations.Strikethrough; content.Children.Add(title);
            if (!string.IsNullOrWhiteSpace(task.Notes)) { var notes = UI.Text(task.Notes, 12, UI.Muted); notes.Margin = new Thickness(0, 7, 10, 0); content.Children.Add(notes); }
            var deadline = UI.Text(UI.Deadline(task, now), 12, task.Overdue(now) ? UI.Red : (task.Soon(now) ? UI.Amber : UI.Muted)); deadline.Margin = new Thickness(0, 10, 0, 10); content.Children.Add(deadline);
            var meta = new StackPanel { Orientation = Orientation.Horizontal };
            var pin = new CheckBox { Content = "在桌面显示", IsChecked = task.Desktop, Foreground = UI.Muted, FontSize = 11, Margin = new Thickness(0, 0, 16, 0) };
            pin.Click += (s, e) => app.Change(state => state.Tasks.Single(t => t.Id == task.Id).Desktop = pin.IsChecked == true); meta.Children.Add(pin);
            meta.Children.Add(UI.Text(task.Done ? "✓ 已完成" : (task.LeadMinutes < 0 || !task.Due.HasValue ? "提醒已关闭" : "◷ " + Editor.LeadLabel(task.LeadMinutes)), 11, task.Done ? UI.Green : UI.Muted)); content.Children.Add(meta);
            return UI.Card(row, task.Done ? UI.Brush("#F0F3ED") : Brushes.White);
        }
    }

    public class Editor : Window
    {
        public static readonly int[] LeadValues = { 0, 10, 30, 60, 180, 1440, 2880, 10080, -1 };
        public static string LeadLabel(int minutes)
        { if (minutes < 0) return "不提醒"; if (minutes == 0) return "截止时提醒"; if (minutes % 1440 == 0) return "提前 " + minutes / 1440 + " 天提醒"; if (minutes % 60 == 0) return "提前 " + minutes / 60 + " 小时提醒"; return "提前 " + minutes + " 分钟提醒"; }
        public Editor(MemoApp app, Todo task)
        {
            UI.Init(this); Title = task == null ? "新建待办" : "编辑待办"; Width = 520; Height = 650; ResizeMode = ResizeMode.NoResize; WindowStartupLocation = WindowStartupLocation.CenterOwner; ShowInTaskbar = false;
            var content = new StackPanel { Margin = new Thickness(30) }; Content = content;
            content.Children.Add(UI.Text(Title, 24, UI.Ink));
            var title = new TextBox { Text = task == null ? "" : task.Title, MaxLength = 200, Padding = new Thickness(10), FontSize = 15 };
            Label(content, "事项名称 *"); content.Children.Add(title);
            var notes = new TextBox { Text = task == null ? "" : task.Notes, MaxLength = 4000, Height = 95, Padding = new Thickness(10), AcceptsReturn = true, TextWrapping = TextWrapping.Wrap, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
            Label(content, "备注"); content.Children.Add(notes);
            var hasDue = new CheckBox { Content = "设置截止时间（DDL）", IsChecked = task == null || task.Due.HasValue, Margin = new Thickness(0, 20, 0, 10) }; content.Children.Add(hasDue);
            DateTime initial = task != null && task.Due.HasValue ? task.Due.Value : DateTime.Today.AddDays(1).AddHours(18);
            var dueRow = new StackPanel { Orientation = Orientation.Horizontal };
            var date = new DatePicker { SelectedDate = initial.Date, Width = 235, Height = 34, Language = XmlLanguage.GetLanguage("zh-CN") };
            var time = new TextBox { Text = initial.ToString("HH:mm"), Width = 100, Padding = new Thickness(8), Margin = new Thickness(12, 0, 0, 0), MaxLength = 5, ToolTip = "24 小时制，例如 18:30" };
            dueRow.Children.Add(date); dueRow.Children.Add(time); content.Children.Add(dueRow);
            Label(content, "提醒时间");
            var lead = new ComboBox { Height = 34, Padding = new Thickness(8, 4, 8, 4) };
            foreach (int minutes in LeadValues) lead.Items.Add(new ComboBoxItem { Content = LeadLabel(minutes), Tag = minutes });
            int selected = Array.IndexOf(LeadValues, task == null ? 1440 : task.LeadMinutes); lead.SelectedIndex = selected < 0 ? 5 : selected; content.Children.Add(lead);
            Action update = () => { dueRow.IsEnabled = hasDue.IsChecked == true; lead.IsEnabled = hasDue.IsChecked == true; }; hasDue.Click += (s, e) => update(); update();
            var desktop = new CheckBox { Content = "在桌面小窗显示这条待办", IsChecked = task == null || task.Desktop, Margin = new Thickness(0, 20, 0, 0) }; content.Children.Add(desktop);
            var error = UI.Text("", 12, UI.Red); error.Height = 36; error.Margin = new Thickness(0, 8, 0, 0); content.Children.Add(error);
            var buttons = new DockPanel { LastChildFill = false }; content.Children.Add(buttons);
            Action save = () =>
            {
                if (string.IsNullOrWhiteSpace(title.Text)) { error.Text = "请填写事项名称。"; title.Focus(); return; }
                DateTime? due = null;
                if (hasDue.IsChecked == true)
                {
                    DateTime parsed;
                    if (!date.SelectedDate.HasValue || !DateTime.TryParseExact(time.Text.Trim(), "HH:mm", CultureInfo.InvariantCulture, DateTimeStyles.None, out parsed)) { error.Text = "请选择日期，时间请使用 24 小时制，如 18:30。"; return; }
                    due = date.SelectedDate.Value.Date.AddHours(parsed.Hour).AddMinutes(parsed.Minute);
                }
                int leadMinutes = (int)((ComboBoxItem)lead.SelectedItem).Tag;
                bool ok = app.Change(state =>
                {
                    Todo value = task == null ? new Todo() : state.Tasks.Single(t => t.Id == task.Id);
                    if (value.Due != due || value.LeadMinutes != leadMinutes) { value.NotifiedStage = 0; value.SnoozedUntil = null; }
                    value.Title = title.Text.Trim(); value.Notes = notes.Text.Trim(); value.Due = due; value.LeadMinutes = leadMinutes; value.Desktop = desktop.IsChecked == true;
                    if (task == null) state.Tasks.Add(value);
                });
                if (ok) DialogResult = true;
            };
            var saveButton = UI.Button("保存待办", save, true); saveButton.IsDefault = true; DockPanel.SetDock(saveButton, Dock.Right); buttons.Children.Add(saveButton);
            var cancel = UI.Button("取消", () => Close()); cancel.IsCancel = true; DockPanel.SetDock(cancel, Dock.Right); buttons.Children.Add(cancel);
            if (task != null)
            {
                var delete = UI.Button("删除", () => { if (MessageBox.Show(this, "删除「" + task.Title + "」？", "删除待办", MessageBoxButton.YesNo, MessageBoxImage.Question) == MessageBoxResult.Yes && app.Change(state => state.Tasks.RemoveAll(t => t.Id == task.Id))) DialogResult = true; }); delete.Foreground = UI.Red; buttons.Children.Add(delete);
            }
            Loaded += (s, e) => title.Focus();
        }
        static void Label(Panel content, string label) { var text = UI.Text(label, 12, UI.Muted); text.Margin = new Thickness(0, 18, 0, 8); content.Children.Add(text); }
    }

    public class DesktopView : Window
    {
        readonly MemoApp app;
        readonly StackPanel list = new StackPanel();
        readonly TextBlock summary = UI.Text("", 12, UI.Muted);
        public DesktopView(MemoApp app)
        {
            this.app = app; UI.Init(this); Title = "桌面待办"; Width = 350; Height = 440; MinWidth = 300; MinHeight = 230; WindowStyle = WindowStyle.None; ResizeMode = ResizeMode.CanResizeWithGrip; ShowInTaskbar = false; ShowActivated = false;
            Rect area = SystemParameters.WorkArea;
            Left = app.State.DesktopLeft < 0 ? area.Right - Width - 20 : Math.Max(area.Left, Math.Min(app.State.DesktopLeft, area.Right - Width));
            Top = Math.Max(area.Top, Math.Min(app.State.DesktopTop, area.Bottom - Height));
            var frame = UI.Card(null, UI.Brush("#F8FAF5")); frame.Margin = new Thickness(0); frame.CornerRadius = new CornerRadius(0); frame.BorderBrush = UI.Brush("#CBD7C7"); Content = frame;
            var root = new DockPanel(); frame.Child = root;
            var header = new DockPanel { Margin = new Thickness(0, 0, 0, 14), Background = Brushes.Transparent, Cursor = Cursors.SizeAll, ToolTip = "拖动这里移动小窗" };
            var hide = UI.Button("—", () => app.Change(state => state.DesktopVisible = false)); hide.ToolTip = "隐藏桌面小窗，提醒仍继续"; hide.Padding = new Thickness(10, 2, 10, 2); hide.Margin = new Thickness(0); DockPanel.SetDock(hide, Dock.Right); header.Children.Add(hide); header.Children.Add(UI.Text("◷  我的待办", 18, UI.Green));
            header.MouseLeftButtonDown += (s, e) => { if (!(e.OriginalSource is TextBlock) && e.OriginalSource != header) return; DragMove(); app.Change(state => { state.DesktopLeft = Left; state.DesktopTop = Top; }); };
            DockPanel.SetDock(header, Dock.Top); root.Children.Add(header);
            var footer = new DockPanel { Margin = new Thickness(0, 12, 0, 0) }; var open = UI.Button("打开清单 ↗", () => app.ShowMain()); open.Margin = new Thickness(0); DockPanel.SetDock(open, Dock.Right); footer.Children.Add(open); footer.Children.Add(summary); DockPanel.SetDock(footer, Dock.Bottom); root.Children.Add(footer);
            root.Children.Add(new ScrollViewer { Content = list, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled });
            Closing += (s, e) => { if (!app.Exiting) { e.Cancel = true; app.Change(state => state.DesktopVisible = false); } };
        }
        public void Refresh()
        {
            list.Children.Clear(); DateTime now = DateTime.Now;
            var visible = app.State.Tasks.Where(t => t.Desktop).OrderBy(t => t.Done).ThenBy(t => t.Due ?? DateTime.MaxValue).ToList();
            summary.Text = visible.Count(t => !t.Done) + " 项待完成";
            foreach (var task in visible)
            {
                var row = new DockPanel();
                var check = new CheckBox { IsChecked = task.Done, Margin = new Thickness(0, 4, 10, 0), VerticalAlignment = VerticalAlignment.Top }; check.Click += (s, e) => app.SetDone(task.Id, check.IsChecked == true); DockPanel.SetDock(check, Dock.Left); row.Children.Add(check);
                var text = new StackPanel(); var title = UI.Text(task.Title, 14, task.Done ? UI.Muted : UI.Ink); if (task.Done) title.TextDecorations = TextDecorations.Strikethrough; text.Children.Add(title);
                var due = UI.Text(task.Done ? "已完成" : UI.Deadline(task, now), 11, task.Overdue(now) ? UI.Red : task.Soon(now) ? UI.Amber : UI.Muted); due.Margin = new Thickness(0, 6, 0, 0); text.Children.Add(due); row.Children.Add(text);
                var card = UI.Card(row); card.Padding = new Thickness(12); list.Children.Add(card);
            }
            if (visible.Count == 0) { var empty = UI.Text("暂时没有桌面待办。\n在清单中勾选「在桌面显示」。", 13, UI.Muted); empty.Margin = new Thickness(8, 24, 8, 0); list.Children.Add(empty); }
        }
    }

    public class ReminderView : Window
    {
        readonly MemoApp app;
        readonly HashSet<string> ids = new HashSet<string>();
        readonly StackPanel list = new StackPanel();
        public bool HasItems { get { return ids.Count > 0; } }
        public ReminderView(MemoApp app)
        {
            this.app = app; UI.Init(this); Title = "待办截止提醒"; Width = 420; Height = 380; Topmost = true; ShowActivated = false; ResizeMode = ResizeMode.CanResize; MinWidth = 360; MinHeight = 250;
            var area = SystemParameters.WorkArea; Left = area.Right - Width - 24; Top = area.Bottom - Height - 24;
            var root = new DockPanel { Margin = new Thickness(22) }; Content = root;
            var title = UI.Text("有事项需要你留意", 22, UI.Green); title.Margin = new Thickness(0, 0, 0, 18); DockPanel.SetDock(title, Dock.Top); root.Children.Add(title);
            var buttons = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 12, 0, 0) };
            buttons.Children.Add(UI.Button("10 分钟后提醒", () => { if (app.Change(state => { foreach (var task in state.Tasks.Where(t => ids.Contains(t.Id) && !t.Done)) { task.SnoozedUntil = DateTime.Now.AddMinutes(10); task.NotifiedStage = 0; } })) { ids.Clear(); Hide(); } }));
            buttons.Children.Add(UI.Button("知道了", () => { ids.Clear(); Hide(); }, true)); DockPanel.SetDock(buttons, Dock.Bottom); root.Children.Add(buttons);
            root.Children.Add(new ScrollViewer { Content = list, VerticalScrollBarVisibility = ScrollBarVisibility.Auto });
            Closing += (s, e) => { if (!app.Exiting) { e.Cancel = true; ids.Clear(); Hide(); } };
        }
        public void Add(IEnumerable<string> values) { foreach (string id in values) ids.Add(id); Refresh(); }
        public void Refresh()
        {
            ids.RemoveWhere(id => !app.State.Tasks.Any(t => t.Id == id && !t.Done && t.Due.HasValue && t.LeadMinutes >= 0 && t.Due.Value.AddMinutes(-t.LeadMinutes) <= DateTime.Now));
            list.Children.Clear();
            foreach (var task in app.State.Tasks.Where(t => ids.Contains(t.Id)).OrderBy(t => t.Due))
            {
                var item = new StackPanel(); item.Children.Add(UI.Text(task.Title, 16, UI.Ink)); item.Children.Add(UI.Text(UI.Deadline(task, DateTime.Now), 12, task.Overdue(DateTime.Now) ? UI.Red : UI.Amber));
                var done = UI.Button("✓ 标记完成", () => app.SetDone(task.Id, true)); done.Margin = new Thickness(0, 10, 0, 0); done.HorizontalAlignment = HorizontalAlignment.Left; item.Children.Add(done); list.Children.Add(UI.Card(item));
            }
            if (ids.Count == 0) Hide();
        }
    }

    public static class Program
    {
        [STAThread]
        public static int Main(string[] args)
        {
            if (args.Contains("--self-test")) return SelfTest.Run();
            string folder = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "data");
            bool created;
            using (var mutex = new Mutex(true, "Local\\DesktopMemo_" + StableHash(folder), out created))
            {
                if (!created) { MessageBox.Show("青笺已在运行。请双击右下角托盘中的图标打开。", "青笺 QingJian"); return 0; }
                var app = new MemoApp { Store = new Store(folder) };
                try { app.State = app.Store.Load(); }
                catch (Exception ex)
                { MessageBox.Show("无法读取待办数据，原文件未被覆盖。\n" + ex.Message + "\n\n数据位置：" + app.Store.PathName + "\n如需恢复，可先保留原文件，再将 tasks.json.bak 复制为 tasks.json。", "无法启动", MessageBoxButton.OK, MessageBoxImage.Error); return 1; }
                app.Start(); app.Run();
            }
            return 0;
        }
        static string StableHash(string value)
        { using (var sha = System.Security.Cryptography.SHA256.Create()) return BitConverter.ToString(sha.ComputeHash(System.Text.Encoding.UTF8.GetBytes(value.ToUpperInvariant()))).Replace("-", "").Substring(0, 24); }
    }

    public static class SelfTest
    {
        static void Assert(bool condition, string message) { if (!condition) throw new Exception(message); }
        public static int Run()
        {
            string output = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "verification"); Directory.CreateDirectory(output);
            try
            {
                DateTime now = new DateTime(2026, 9, 20, 12, 0, 0);
                var t = new Todo { Title = "检查提醒", Due = now.AddHours(25) };
                Assert(t.ReminderStage(now) == 0, "Too early"); t.Due = now.AddHours(24); Assert(t.ReminderStage(now) == 1, "Boundary");
                t.NotifiedStage = 1; Assert(t.ReminderStage(now) == 0, "No repeat"); t.Due = now; Assert(t.ReminderStage(now) == 2, "Deadline");
                t.NotifiedStage = 2; Assert(t.ReminderStage(now.AddHours(1)) == 0, "No overdue repeat");
                t.NotifiedStage = 0; t.Done = true; Assert(t.ReminderStage(now) == 0, "Completed suppressed"); t.Done = false;
                t.SnoozedUntil = now.AddMinutes(10); Assert(t.ReminderStage(now.AddMinutes(9)) == 0, "Snoozed"); Assert(t.ReminderStage(now.AddMinutes(10)) == 2, "Snooze elapsed");
                t.SnoozedUntil = null; t.LeadMinutes = -1; Assert(t.ReminderStage(now) == 0, "Disabled"); t.LeadMinutes = 0; t.Due = null; Assert(t.ReminderStage(now) == 0, "No deadline");
                var state = new MemoState(); state.Tasks.Add(new Todo { Title = "中文、引号 \" 与换行", Notes = "第一行\n第二行", Due = now, Done = true, Desktop = false, NotifiedStage = 2, SnoozedUntil = now.AddMinutes(10) });
                var store = new Store(Path.Combine(output, "test-data")); store.Save(state); var read = store.Load(); Assert(read.Tasks[0].Title == state.Tasks[0].Title && read.Tasks[0].Due.Value == now && read.Tasks[0].Done && !read.Tasks[0].Desktop && read.Tasks[0].NotifiedStage == 2 && read.Tasks[0].SnoozedUntil == now.AddMinutes(10), "Round trip");
                state.Tasks[0].Title = "更新"; store.Save(state); Assert(store.Load().Tasks[0].Title == "更新", "Atomic update"); Assert(Store.Decode(File.ReadAllBytes(store.PathName + ".bak")).Tasks[0].Title == read.Tasks[0].Title, "Backup");
                bool corrupt = false; try { Store.Decode(System.Text.Encoding.UTF8.GetBytes("broken")); } catch (SerializationException) { corrupt = true; } Assert(corrupt, "Corruption surfaced");
                var app = new MemoApp { TestMode = true, Store = store, State = new MemoState(), ShutdownMode = ShutdownMode.OnExplicitShutdown };
                DateTime clock = DateTime.Now;
                app.State.Tasks.Add(new Todo { Title = "整理本周实验记录", Notes = "核对数据与图表，补全实验备注。", Due = clock.AddHours(3) });
                app.State.Tasks.Add(new Todo { Title = "准备组会汇报", Notes = "整理研究进展和下一步计划。", Due = clock.AddDays(2) });
                app.State.Tasks.Add(new Todo { Title = "确认项目材料", Due = clock.AddHours(-2), Desktop = false });
                app.State.Tasks.Add(new Todo { Title = "阅读并归档参考文献", Due = clock.AddHours(-5), Done = true });
                app.Main = new MainView(app); app.Desktop = new DesktopView(app); app.Refresh();
                Render(app.Main, Path.Combine(output, "main.png")); Render(app.Desktop, Path.Combine(output, "desktop.png"));
                var editor = new Editor(app, null); Render(editor, Path.Combine(output, "editor.png"));
                var reminder = new ReminderView(app); reminder.Add(new[] { app.State.Tasks[0].Id, app.State.Tasks[2].Id }); Render(reminder, Path.Combine(output, "reminder.png"));
                string doneId = app.State.Tasks[0].Id; app.SetDone(doneId, true); Assert(app.State.Tasks.Single(x => x.Id == doneId).Done && store.Load().Tasks.Single(x => x.Id == doneId).Done, "Completion persisted");
                app.SetDone(doneId, false); Assert(!app.State.Tasks.Single(x => x.Id == doneId).Done && app.State.Tasks.Single(x => x.Id == doneId).NotifiedStage == 0, "Undo completion");
                app.Change(s => s.DesktopVisible = false); Assert(!store.Load().DesktopVisible, "Visibility persisted");
                var create = new Editor(app, null);
                create.Loaded += (s, e) => create.Dispatcher.BeginInvoke(new Action(() =>
                {
                    var fields = Children<TextBox>(create).ToList();
                    var titleBox = fields.Single(x => x.MaxLength == 200);
                    var timeBox = fields.Single(x => x.MaxLength == 5);
                    var save = Children<Button>(create).Single(x => (string)x.Content == "保存待办");
                    save.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
                    Assert(Children<TextBlock>(create).Any(x => x.Text == "请填写事项名称。"), "Empty title rejected");
                    titleBox.Text = "通过界面新建的待办"; timeBox.Text = "25:70"; save.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
                    Assert(Children<TextBlock>(create).Any(x => x.Text.Contains("时间请使用")), "Invalid time rejected");
                    timeBox.Text = "18:30"; save.RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
                }));
                Assert(create.ShowDialog() == true, "Editor saved");
                var added = app.State.Tasks.Single(x => x.Title == "通过界面新建的待办");
                Assert(added.Due.Value.Hour == 18 && added.Due.Value.Minute == 30 && added.Desktop, "Editor values");
                var modify = new Editor(app, added);
                modify.Loaded += (s, e) => modify.Dispatcher.BeginInvoke(new Action(() =>
                {
                    Children<TextBox>(modify).Single(x => x.MaxLength == 200).Text = "通过界面修改";
                    Children<CheckBox>(modify).Single(x => (string)x.Content == "设置截止时间（DDL）").IsChecked = false;
                    Children<Button>(modify).Single(x => (string)x.Content == "保存待办").RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
                }));
                Assert(modify.ShowDialog() == true && app.State.Tasks.Single(x => x.Id == added.Id).Due == null, "Edit removes deadline");
                app.Filter = "已完成"; app.Main.Refresh(); Assert(Children<TextBlock>(app.Main).Any(x => x.Text == "阅读并归档参考文献") && !Children<TextBlock>(app.Main).Any(x => x.Text == "准备组会汇报"), "Completed filter");
                app.Filter = "全部事项"; Children<TextBox>(app.Main).Single().Text = "参考文献";
                Assert(Children<TextBlock>(app.Main).Any(x => x.Text == "阅读并归档参考文献") && !Children<TextBlock>(app.Main).Any(x => x.Text == "准备组会汇报"), "Search");
                Children<TextBox>(app.Main).Single().Text = "";
                app.CheckReminders();
                Assert(app.Reminder != null && app.Reminder.IsVisible && app.Reminder.HasItems, "Reminder window delivered");
                Assert(store.Load().Tasks.Single(x => x.Title == "确认项目材料").NotifiedStage == 2, "Delivered reminder persisted");
                app.Reminder.Hide(); app.CheckReminders(); Assert(!app.Reminder.IsVisible, "Delivered reminder not repeated");
                app.Reminder.Show(); app.Reminder.UpdateLayout();
                Children<Button>(app.Reminder).Single(x => (string)x.Content == "10 分钟后提醒").RaiseEvent(new RoutedEventArgs(Button.ClickEvent));
                Assert(!app.Reminder.IsVisible && app.State.Tasks.Single(x => x.Title == "确认项目材料").SnoozedUntil > DateTime.Now.AddMinutes(9), "Snooze button persisted");
                app.Main.Width = 880; app.Main.Height = 580; Render(app.Main, Path.Combine(output, "compact.png"));
                app.Main.Width = 1080; app.Main.Height = 760;
                app.State = new MemoState(); app.Refresh(); Render(app.Main, Path.Combine(output, "empty.png"));
                app.Exiting = true; editor.Close(); reminder.Close(); app.Reminder.Close(); app.Main.Close(); app.Desktop.Close();
                File.WriteAllText(Path.Combine(output, "result.txt"), "PASS: reminder boundaries, duplicate suppression, completion, snooze, disabled/no deadline, JSON round trip, atomic replacement/backup, corrupt input, completion undo, desktop visibility, editor validation/create/edit, completed filter, search, reminder delivery/persistence/snooze button, WPF rendering (main, compact, empty, desktop, editor, reminder).\r\n", System.Text.Encoding.UTF8);
                return 0;
            }
            catch (Exception ex) { File.WriteAllText(Path.Combine(output, "result.txt"), ex.ToString()); return 1; }
        }
        static IEnumerable<T> Children<T>(DependencyObject root) where T : DependencyObject
        {
            for (int i = 0; i < VisualTreeHelper.GetChildrenCount(root); i++)
            {
                var child = VisualTreeHelper.GetChild(root, i);
                if (child is T) yield return (T)child;
                foreach (T nested in Children<T>(child)) yield return nested;
            }
        }
        static void Render(Window window, string path)
        {
            window.Show(); window.UpdateLayout();
            var bitmap = new RenderTargetBitmap((int)window.ActualWidth, (int)window.ActualHeight, 96, 96, PixelFormats.Pbgra32); bitmap.Render(window);
            var encoder = new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(bitmap)); using (var stream = File.Create(path)) encoder.Save(stream); window.Hide();
        }
    }
}
