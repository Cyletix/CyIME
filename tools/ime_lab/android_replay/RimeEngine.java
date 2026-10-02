package com.kingzcheung.xime.rime;

/** Minimal ABI declarations for the installed library; no application classes are loaded. */
public final class RimeEngine {
    public native void nativeInitialize(String userDataDir, String sharedDataDir);
    public native boolean nativeStartMaintenance(boolean full);
    public native boolean nativeIsMaintaining();
    public native boolean nativeCreateSession();
    public native boolean nativeSwitchSchema(String schema);
    public native boolean nativeEnsureT9SchemaPatches(String schema);
    public native String nativeGetCurrentSchema();
    public native String nativeGetSchemaString(String schema, String key);
    public native void nativeSetOption(String option, boolean value);
    public native boolean nativeProcessKey(int keycode, int mask);
    public native void nativeT9FlushRimeInput();
    public native void nativeT9ClearComposition(int mode);
    public native boolean nativeT9SelectPinyinDirect(String pinyin, int digitLength);
    public native int nativeT9RefinementState();
    public native boolean nativeRefineT9Sentences();
    public native String nativeT9ScoringStatus();
    public native String nativeGetInput();
    public native String nativeT9GetRemainingDigits();
    public native String[][] nativeInspectCandidates(int limit);
    public native void nativeDestroy();
}
