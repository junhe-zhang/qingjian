using System;
using System.Runtime.InteropServices;

namespace DesktopMemo
{
    // Shell pinning contracts are undocumented. Keep this optional integration
    // separate from task storage, and surface COM failures in the desktop window.
    // Interface reference: https://github.com/MScholtes/VirtualDesktop
    internal static class VirtualDesktopPin
    {
        [ComImport, Guid("6D5140C1-7436-11CE-8034-00AA006009FA"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        interface IServiceProvider
        {
            [return: MarshalAs(UnmanagedType.IUnknown)]
            object QueryService(ref Guid service, ref Guid iid);
        }

        [ComImport, Guid("1841C6D7-4F9D-42C0-AF41-8747538F10E5"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        interface IApplicationViewCollection
        {
            [PreserveSig] int GetViews(out IntPtr views);
            [PreserveSig] int GetViewsByZOrder(out IntPtr views);
            [PreserveSig] int GetViewsByAppUserModelId([MarshalAs(UnmanagedType.LPWStr)] string id, out IntPtr views);
            [PreserveSig] int GetViewForHwnd(IntPtr window, out IntPtr view);
        }

        [ComImport, Guid("4CE81583-1E4C-4632-A621-07A53543148F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        interface IVirtualDesktopPinnedApps
        {
            [return: MarshalAs(UnmanagedType.Bool)] bool IsAppIdPinned([MarshalAs(UnmanagedType.LPWStr)] string id);
            void PinAppID([MarshalAs(UnmanagedType.LPWStr)] string id);
            void UnpinAppID([MarshalAs(UnmanagedType.LPWStr)] string id);
            [return: MarshalAs(UnmanagedType.Bool)] bool IsViewPinned(IntPtr view);
            void PinView(IntPtr view);
            void UnpinView(IntPtr view);
        }

        internal static bool EnsurePinned(IntPtr window)
        {
            var shell = (IServiceProvider)Activator.CreateInstance(Type.GetTypeFromCLSID(new Guid("C2F03A33-21F5-47FA-B4BB-156362A2F239")));
            try
            {
                Guid collectionId = typeof(IApplicationViewCollection).GUID;
                var views = (IApplicationViewCollection)shell.QueryService(ref collectionId, ref collectionId);
                try
                {
                    IntPtr view;
                    Marshal.ThrowExceptionForHR(views.GetViewForHwnd(window, out view));
                    try
                    {
                        Guid service = new Guid("B5A399E7-1C87-46B8-88E9-FC5747B171BD"), iid = typeof(IVirtualDesktopPinnedApps).GUID;
                        var pins = (IVirtualDesktopPinnedApps)shell.QueryService(ref service, ref iid);
                        try
                        {
                            if (!pins.IsViewPinned(view)) pins.PinView(view);
                            return pins.IsViewPinned(view);
                        }
                        finally { Marshal.ReleaseComObject(pins); }
                    }
                    finally { Marshal.Release(view); }
                }
                finally { Marshal.ReleaseComObject(views); }
            }
            finally { Marshal.ReleaseComObject(shell); }
        }
    }
}
