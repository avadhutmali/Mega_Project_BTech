package com.idlegrid.agent.idle;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;

/**
 * JNA binding for the {@code GetLastInputInfo} function from {@code user32.dll}.
 *
 * <p>This is the standard Windows API for idle detection used by screensavers,
 * power management, and distributed computing frameworks (Condor, BOINC, Folding@home).
 * It returns the system tick count at the time of the last keyboard or mouse event —
 * subtracting from the current tick count gives milliseconds of idle time.
 *
 * <h3>Win32 reference</h3>
 * <pre>
 *   BOOL GetLastInputInfo(PLASTINPUTINFO plii);
 *
 *   typedef struct tagLASTINPUTINFO {
 *     UINT  cbSize;   // must be sizeof(LASTINPUTINFO) = 8 bytes
 *     DWORD dwTime;   // tick count (ms since boot) at last input event
 *   } LASTINPUTINFO, *PLASTINPUTINFO;
 * </pre>
 *
 * @see <a href="https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getlastinputinfo">
 *      MSDN GetLastInputInfo</a>
 */
public interface User32Ext extends StdCallLibrary {

    /** Singleton JNA proxy. Never call Native.load() yourself — use this. */
    User32Ext INSTANCE = Native.load("user32", User32Ext.class);

    /**
     * Retrieves the time of the last input event.
     *
     * @param plii a {@link LASTINPUTINFO.ByReference} with {@code cbSize} pre-set to 8
     * @return {@code true} on success; {@code false} on failure (call {@code Native.getLastError()})
     */
    boolean GetLastInputInfo(LASTINPUTINFO.ByReference plii);

    /**
     * JNA mapping of {@code LASTINPUTINFO}.
     * {@code @FieldOrder} is required by JNA to determine struct field layout.
     */
    @Structure.FieldOrder({"cbSize", "dwTime"})
    class LASTINPUTINFO extends Structure {

        /**
         * Size of this structure in bytes.
         * <b>Must</b> be set to 8 before calling {@code GetLastInputInfo}.
         * JNA does not auto-initialise struct fields, so we set the default here.
         */
        public int cbSize = 8; // sizeof(LASTINPUTINFO)

        /**
         * Tick count (ms since system boot) at the time of the last input event.
         * Note: Java {@code int} is signed but this field is a Windows DWORD (unsigned 32-bit).
         * Use {@link Integer#toUnsignedLong(int)} when computing elapsed time.
         */
        public int dwTime;

        /** Pass-by-reference variant required by JNA for out-parameter structs. */
        public static class ByReference extends LASTINPUTINFO implements Structure.ByReference {}
    }
}
