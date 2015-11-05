/*
Copyright (c) 2007 Thomas Boerkel, All Rights Reserved

Disclaimer:
===========
This code is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This code is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.
 */
package hudson.util.jna;

import com.sun.jna.Structure;
import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.IntByReference;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author TB
 */
public interface Advapi32  extends StdCallLibrary {
  Advapi32 INSTANCE = (Advapi32) Native.loadLibrary("Advapi32", Advapi32.class, Options.UNICODE_OPTIONS);

    /**
     * Retrieves the name of the user associated with the current thread.
     *
     * <p>
     * See http://msdn.microsoft.com/en-us/library/ms724432(VS.85).aspx
     */
    boolean GetUserName(char[] buffer, IntByReference lpnSize);

/*
BOOL WINAPI LookupAccountName(
  LPCTSTR lpSystemName,
  LPCTSTR lpAccountName,
  PSID Sid,
  LPDWORD cbSid,
  LPTSTR ReferencedDomainName,
  LPDWORD cchReferencedDomainName,
  PSID_NAME_USE peUse
);*/
  boolean LookupAccountName(String lpSystemName, String lpAccountName,
          byte[] Sid, IntByReference cbSid, char[] ReferencedDomainName,
          IntByReference cchReferencedDomainName, PointerByReference peUse);

/*
BOOL WINAPI LookupAccountSid(
  LPCTSTR lpSystemName,
  PSID lpSid,
  LPTSTR lpName,
  LPDWORD cchName,
  LPTSTR lpReferencedDomainName,
  LPDWORD cchReferencedDomainName,
  PSID_NAME_USE peUse
);*/
  boolean LookupAccountSid(String lpSystemName, byte[] Sid,
          char[] lpName, IntByReference cchName,  char[] ReferencedDomainName,
          IntByReference cchReferencedDomainName, PointerByReference peUse);

/*
BOOL ConvertSidToStringSid(
  PSID Sid,
  LPTSTR* StringSid
);*/
  boolean ConvertSidToStringSid(byte[] Sid, PointerByReference StringSid);

/*
BOOL WINAPI ConvertStringSidToSid(
  LPCTSTR StringSid,
  PSID* Sid
);*/
  boolean ConvertStringSidToSid(String StringSid, PointerByReference Sid);

/*
SC_HANDLE WINAPI OpenSCManager(
  LPCTSTR lpMachineName,
  LPCTSTR lpDatabaseName,
  DWORD dwDesiredAccess
);*/
  Pointer OpenSCManager(String lpMachineName, WString lpDatabaseName, int dwDesiredAccess);

/*
BOOL WINAPI CloseServiceHandle(
  SC_HANDLE hSCObject
);*/
  boolean CloseServiceHandle(Pointer hSCObject);

/*
SC_HANDLE WINAPI OpenService(
  SC_HANDLE hSCManager,
  LPCTSTR lpServiceName,
  DWORD dwDesiredAccess
);*/
  Pointer OpenService(Pointer hSCManager, String lpServiceName, int dwDesiredAccess);

/*
BOOL WINAPI StartService(
  SC_HANDLE hService,
  DWORD dwNumServiceArgs,
  LPCTSTR* lpServiceArgVectors
);*/
  boolean StartService(Pointer hService, int dwNumServiceArgs, char[] lpServiceArgVectors);

/*
BOOL WINAPI ControlService(
  SC_HANDLE hService,
  DWORD dwControl,
  LPSERVICE_STATUS lpServiceStatus
);*/
  boolean ControlService(Pointer hService, int dwControl, SERVICE_STATUS lpServiceStatus);

/*
BOOL WINAPI StartServiceCtrlDispatcher(
  const SERVICE_TABLE_ENTRY* lpServiceTable
);*/
  boolean StartServiceCtrlDispatcher(Structure[] lpServiceTable);

/*
SERVICE_STATUS_HANDLE WINAPI RegisterServiceCtrlHandler(
  LPCTSTR lpServiceName,
  LPHANDLER_FUNCTION lpHandlerProc
);*/
  Pointer RegisterServiceCtrlHandler(String lpServiceName, Handler lpHandlerProc);

/*
SERVICE_STATUS_HANDLE WINAPI RegisterServiceCtrlHandlerEx(
  LPCTSTR lpServiceName,
  LPHANDLER_FUNCTION_EX lpHandlerProc,
  LPVOID lpContext
);*/
  Pointer RegisterServiceCtrlHandlerEx(String lpServiceName, HandlerEx lpHandlerProc, Pointer lpContext);

/*
BOOL WINAPI SetServiceStatus(
  SERVICE_STATUS_HANDLE hServiceStatus,
  LPSERVICE_STATUS lpServiceStatus
);*/
  boolean SetServiceStatus(Pointer hServiceStatus, SERVICE_STATUS lpServiceStatus);

/*
SC_HANDLE WINAPI CreateService(
  SC_HANDLE hSCManager,
  LPCTSTR lpServiceName,
  LPCTSTR lpDisplayName,
  DWORD dwDesiredAccess,
  DWORD dwServiceType,
  DWORD dwStartType,
  DWORD dwErrorControl,
  LPCTSTR lpBinaryPathName,
  LPCTSTR lpLoadOrderGroup,
  LPDWORD lpdwTagId,
  LPCTSTR lpDependencies,
  LPCTSTR lpServiceStartName,
  LPCTSTR lpPassword
);*/
  Pointer CreateService(Pointer hSCManager, String lpServiceName, String lpDisplayName,
          int dwDesiredAccess, int dwServiceType, int dwStartType, int dwErrorControl,
          String lpBinaryPathName, String lpLoadOrderGroup, IntByReference lpdwTagId,
          String lpDependencies, String lpServiceStartName, String lpPassword);

/*
BOOL WINAPI DeleteService(
  SC_HANDLE hService
);*/
  boolean DeleteService(Pointer hService);

/*
BOOL WINAPI ChangeServiceConfig2(
  SC_HANDLE hService,
  DWORD dwInfoLevel,
  LPVOID lpInfo
);*/
  boolean ChangeServiceConfig2(Pointer hService, int dwInfoLevel, ChangeServiceConfig2Info lpInfo);

/*
LONG WINAPI RegOpenKeyEx(
  HKEY hKey,
  LPCTSTR lpSubKey,
  DWORD ulOptions,
  REGSAM samDesired,
  PHKEY phkResult
);*/
  int RegOpenKeyEx(int hKey, String lpSubKey, int ulOptions, int samDesired, IntByReference phkResult);

/*
LONG WINAPI RegQueryValueEx(
  HKEY hKey,
  LPCTSTR lpValueName,
  LPDWORD lpReserved,
  LPDWORD lpType,
  LPBYTE lpData,
  LPDWORD lpcbData
);*/
  int RegQueryValueEx(int hKey, String lpValueName, IntByReference lpReserved, IntByReference lpType, byte[] lpData, IntByReference lpcbData);

/*
LONG WINAPI RegCloseKey(
  HKEY hKey
);*/
  int RegCloseKey(int hKey);

/*
LONG WINAPI RegDeleteValue(
  HKEY hKey,
  LPCTSTR lpValueName
);*/
  int RegDeleteValue(int hKey, String lpValueName);

/*
LONG WINAPI RegSetValueEx(
  HKEY hKey,
  LPCTSTR lpValueName,
  DWORD Reserved,
  DWORD dwType,
  const BYTE* lpData,
  DWORD cbData
);*/
  int RegSetValueEx(int hKey, String lpValueName, int Reserved, int dwType, byte[] lpData, int cbData);

/*
LONG WINAPI RegCreateKeyEx(
  HKEY hKey,
  LPCTSTR lpSubKey,
  DWORD Reserved,
  LPTSTR lpClass,
  DWORD dwOptions,
  REGSAM samDesired,
  LPSECURITY_ATTRIBUTES lpSecurityAttributes,
  PHKEY phkResult,
  LPDWORD lpdwDisposition
);*/
  int RegCreateKeyEx(int hKey, String lpSubKey, int Reserved, String lpClass, int dwOptions,
          int samDesired, WINBASE.SECURITY_ATTRIBUTES lpSecurityAttributes, IntByReference phkResult,
          IntByReference lpdwDisposition);

/*
LONG WINAPI RegDeleteKey(
  HKEY hKey,
  LPCTSTR lpSubKey
);*/
  int RegDeleteKey(int hKey, String name);

/*
LONG WINAPI RegEnumKeyEx(
  HKEY hKey,
  DWORD dwIndex,
  LPTSTR lpName,
  LPDWORD lpcName,
  LPDWORD lpReserved,
  LPTSTR lpClass,
  LPDWORD lpcClass,
  PFILETIME lpftLastWriteTime
);*/
  int RegEnumKeyEx(int hKey, int dwIndex, char[] lpName, IntByReference lpcName, IntByReference reserved,
          char[] lpClass, IntByReference lpcClass, WINBASE.FILETIME lpftLastWriteTime);

/*
LONG WINAPI RegEnumValue(
  HKEY hKey,
  DWORD dwIndex,
  LPTSTR lpValueName,
  LPDWORD lpcchValueName,
  LPDWORD lpReserved,
  LPDWORD lpType,
  LPBYTE lpData,
  LPDWORD lpcbData
);*/
  int RegEnumValue(int hKey, int dwIndex, char[] lpValueName, IntByReference lpcchValueName, IntByReference reserved,
          IntByReference lpType, byte[] lpData, IntByReference lpcbData);

  interface SERVICE_MAIN_FUNCTION extends StdCallCallback {
    /*
    VOID WINAPI ServiceMain(
    DWORD dwArgc,
    LPTSTR* lpszArgv
    );*/
    void callback(int dwArgc, Pointer lpszArgv);
  }

  interface Handler extends StdCallCallback {
    /*
    VOID WINAPI Handler(
      DWORD fdwControl
    );*/
    void callback(int fdwControl);
  }

  interface HandlerEx extends StdCallCallback {
    /*
    DWORD WINAPI HandlerEx(
      DWORD dwControl,
      DWORD dwEventType,
      LPVOID lpEventData,
      LPVOID lpContext
    );*/
    int callback(int dwControl, int dwEventType, Pointer lpEventData, Pointer lpContext);
  }

/*
typedef struct _SERVICE_STATUS {
  DWORD dwServiceType;
  DWORD dwCurrentState;
  DWORD dwControlsAccepted;
  DWORD dwWin32ExitCode;
  DWORD dwServiceSpecificExitCode;
  DWORD dwCheckPoint;
  DWORD dwWaitHint;
} SERVICE_STATUS,
 *LPSERVICE_STATUS;*/
  class SERVICE_STATUS extends Structure {
    public int dwServiceType;
    public int dwCurrentState;
    public int dwControlsAccepted;
    public int dwWin32ExitCode;
    public int dwServiceSpecificExitCode;
    public int dwCheckPoint;
    public int dwWaitHint;

    @Override
    protected List getFieldOrder() {
        return Arrays.asList("dwServiceType", "dwCurrentState",
                "dwControlsAccepted", "dwWin32ExitCode",
                "dwServiceSpecificExitCode", "dwCheckPoint",
                "dwWaitHint");
    }
  }

/*
typedef struct _SERVICE_TABLE_ENTRY {
  LPTSTR lpServiceName;
  LPSERVICE_MAIN_FUNCTION lpServiceProc;
} SERVICE_TABLE_ENTRY,
 *LPSERVICE_TABLE_ENTRY;*/
  class SERVICE_TABLE_ENTRY extends Structure {
    public String lpServiceName;
    public SERVICE_MAIN_FUNCTION lpServiceProc;

    @Override
    protected List getFieldOrder() {
        return Arrays.asList(new String[] {"lpServiceName", "lpServiceProc"});
    }
  }

  class ChangeServiceConfig2Info extends Structure {

    @Override
    protected List getFieldOrder() {
        return Arrays.asList(new String[] {});
    }
  }

/*
 typedef struct _SERVICE_DESCRIPTION {
  LPTSTR lpDescription;
} SERVICE_DESCRIPTION,
 *LPSERVICE_DESCRIPTION;*/
  class SERVICE_DESCRIPTION extends ChangeServiceConfig2Info {
    public String lpDescription;
  }
}
