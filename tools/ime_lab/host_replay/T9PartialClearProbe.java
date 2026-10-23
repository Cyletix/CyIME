import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.util.Arrays;

/**
 * T9 after a partial choice, then the composition ends (Enter, clear, cursor move...): the next key
 * must start a new code. The user's case (2026-10-23): 9447426926 (zhishaoyao), choose 至少, Enter
 * commits 至少yao, then 64 showed zhi'qian'yao'ni because T9's own digit buffer survived a Rime-only
 * clear. Arguments: the built library directory and a separate test user directory.
 */
public final class T9PartialClearProbe {
  static final String SCHEMA = "t9_pinyin";

  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    File logs = new File(args[1], "logs");
    if (!logs.isDirectory() && !logs.mkdirs()) throw new Exception("cannot create native log directory");
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(args[1], args[1]);
    try {
      e.nativeEnsureT9SchemaPatches(SCHEMA);
      // A copied replay root is already deployed; deploy only a fresh one.
      if (!new File(args[1], "build/" + SCHEMA + ".schema.yaml").isFile()) e.nativeStartMaintenance(true);
      long until = System.currentTimeMillis() + 600000;
      while (e.nativeIsMaintaining()) {
        if (System.currentTimeMillis() > until) throw new Exception("deployment timeout");
        Thread.sleep(100);
      }
      if (!e.nativeCreateSession() || !e.nativeSwitchSchema(SCHEMA)) throw new Exception("session unavailable");
      e.nativeSetOption("ascii_mode", false);
      // What the app did before the fix: Rime-only clear (setInput("") + clearComposition).
      String before = afterPartialChoice(e, false);
      // The fix: T9's own state is cleared with Rime's.
      String after = afterPartialChoice(e, true);
      System.out.println("RIME-ONLY CLEAR then 64 => input '" + before + "'");
      System.out.println("T9 CLEAR then 64 => input '" + after + "'");
      if (!after.equals("64")) throw new Exception("old digits survived the T9 clear: " + after);
      System.out.println(before.equals("64")
          ? "PASS (the Rime-only clear no longer reproduces the stale buffer on this engine)"
          : "PASS (Rime-only clear reproduces the report; the T9 clear starts a new code)");
    } finally { e.nativeDestroy(); }
  }

  static String afterPartialChoice(RimeEngine e, boolean clearT9) throws Exception {
    e.nativeT9ClearComposition(1);
    e.nativeT9FlushRimeInput();
    type(e, "9447426926");
    String[][] rows = e.nativeInspectCandidates(200);
    int index = -1;
    for (int i = 0; i < rows.length; i++) if (rows[i].length > 0 && rows[i][0].equals("至少")) { index = i; break; }
    if (index < 0) throw new Exception("至少 not offered for 9447426926: " + Arrays.deepToString(Arrays.copyOf(rows, Math.min(rows.length, 8))));
    // As the app does: T9 consumes the chosen reading itself (comment = its pinyin).
    e.nativeT9SelectCandidate(rows[index][1], "至少", 2);
    e.nativeT9FlushRimeInput();
    String remaining = e.nativeGetInput();
    if (remaining.isEmpty()) throw new Exception("choosing 至少 left no code; not a partial choice");
    if (clearT9) e.nativeT9ClearComposition(1);
    e.nativeSetInput("");
    type(e, "64");
    return e.nativeGetInput();
  }

  static void type(RimeEngine e, String digits) throws Exception {
    for (char c : digits.toCharArray()) {
      if (!e.nativeProcessKey(c, 0)) throw new Exception("unhandled key " + c);
      e.nativeT9FlushRimeInput();
    }
  }
}
