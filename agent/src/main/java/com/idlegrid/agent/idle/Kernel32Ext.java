package com.idlegrid.agent.idle;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;

/**
 * JNA binding for the single {@code Kernel32} function needed by idle detection:
 * {@code GetTickCount}, which returns the number of milliseconds elapsed since
 * the system last started.
 *
 * <p>We use the 32-bit variant (not {@code GetTickCount64}) because
 * {@code LASTINPUTINFO.dwTime} is also a 32-bit DWORD, so both values wrap at
 * the same boundary (~49.7 days of uptime).  The caller handles wrap-around
 * with unsigned arithmetic.
 *
 * <h3>Win32 reference</h3>
 * <pre>
 *   DWORD GetTickCount();   // kernel32.dll
 * </pre>
 *
 * @see <a href="https://learn.microsoft.com/en-us/windows/win32/api/sysinfoapi/nf-sysinfoapi-gettickcount">
 *      MSDN GetTickCount</a>
 */
public interface Kernel32Ext extends StdCallLibrary {

    /** Singleton JNA proxy. */
    Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class);

    /**
     * Returns the number of milliseconds that have elapsed since the system was started.
     * Wraps back to zero after approximately 49.7 days.
     *
     * <p>The return type is Java {@code int} but represents a Windows DWORD (unsigned).
     * Use {@link Integer#toUnsignedLong(int)} for arithmetic to avoid negative values
     * near the 32-bit boundary.
     */
    int GetTickCount();
}
