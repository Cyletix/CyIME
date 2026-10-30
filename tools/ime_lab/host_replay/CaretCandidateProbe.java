import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.util.Arrays;

/**
 * Candidates follow the code caret (2026-10-30 report): xu'yao|jia'ge with the caret after yao must
 * offer 需要 first, not the whole 需要价格, and choosing it must leave jia'ge to continue.
 * Arguments: the built library directory and a copied deployed user directory listing
 * t9_pinyin and rime_ice.
 */
public final class CaretCandidateProbe {
  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    File logs = new File(args[1], "logs");
    if (!logs.isDirectory() && !logs.mkdirs()) throw new Exception("cannot create native log directory");
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(args[1], args[1]);
    int failures = 0;
    try {
      e.nativeEnsureT9SchemaPatches("t9_pinyin");
      if (!new File(args[1], "build/t9_pinyin.schema.yaml").isFile() || !new File(args[1], "build/rime_ice.schema.yaml").isFile()) e.nativeStartMaintenance(true);
      long until = System.currentTimeMillis() + 600000;
      while (e.nativeIsMaintaining()) {
        if (System.currentTimeMillis() > until) throw new Exception("deployment timeout");
        Thread.sleep(100);
      }
      if (!e.nativeCreateSession()) throw new Exception("session unavailable");
      failures += t9(e);
      failures += full(e);
    } finally { e.nativeDestroy(); }
    System.out.println(failures == 0 ? "PASS" : "FAIL " + failures);
    if (failures != 0) System.exit(1);
  }

  static int t9(RimeEngine e) throws Exception {
    if (!e.nativeSwitchSchema("t9_pinyin")) throw new Exception("t9_pinyin unavailable");
    e.nativeSetOption("ascii_mode", false);
    e.nativeT9ClearComposition(1);
    e.nativeT9FlushRimeInput();
    for (char c : "9892654243".toCharArray()) { e.nativeProcessKey(c, 0); e.nativeT9FlushRimeInput(); }
    print("t9 end", e);
    String input = e.nativeGetInput();
    e.nativeSetCaret(caretAfterKeys(input, 5));
    print("t9 caret after xuyao", e);
    String[][] rows = e.nativeInspectCandidates(20);
    int fail = 0;
    if (rows.length == 0 || !rows[0][0].equals("需要")) { System.out.println("FAIL t9: first is not 需要"); fail++; }
    int index = find(rows, "需要");
    if (index >= 0) {
      boolean full = e.nativeT9SelectCandidate(rows[index][1], "需要", 2);
      e.nativeT9FlushRimeInput();
      String remaining = e.nativeT9GetRemainingDigits();
      System.out.println("t9 select full=" + full + " remaining='" + remaining + "'");
      // As the app does after a partial choice (T9InputController.refreshRemainingInput).
      if (!remaining.isEmpty()) e.nativeSetInput(remaining);
      if (full || !remaining.equals("54243")) { System.out.println("FAIL t9: 需要 did not leave jia'ge"); fail++; }
      print("t9 after choosing 需要", e);
      String[][] after = e.nativeInspectCandidates(20);
      if (find(after, "价格") < 0) { System.out.println("FAIL t9: 价格 not offered after 需要"); fail++; }
    }
    e.nativeT9ClearComposition(1);
    e.nativeT9FlushRimeInput();
    return fail;
  }

  static int full(RimeEngine e) throws Exception {
    if (!e.nativeSwitchSchema("rime_ice")) throw new Exception("rime_ice unavailable");
    e.nativeSetOption("ascii_mode", false);
    for (char c : "xuyaojiage".toCharArray()) e.nativeProcessKey(c, 0);
    print("26 end", e);
    e.nativeSetCaret(5);
    print("26 caret after xuyao", e);
    String[][] rows = e.nativeInspectCandidates(20);
    int fail = 0;
    if (rows.length == 0 || !rows[0][0].equals("需要")) { System.out.println("FAIL 26: first is not 需要"); fail++; }
    int index = find(rows, "需要");
    if (index >= 0 && e.nativeSelectCandidateByGlobalIndex(index)) {
      print("26 after choosing 需要", e);
      String[] s = e.nativePinyinEditSnapshot();
      String[][] after = e.nativeInspectCandidates(20);
      if (!"5".equals(s[2]) || !"需要".equals(s[3]) || find(after, "价格") < 0) {
        System.out.println("FAIL 26: 需要 did not leave jiage"); fail++;
      }
    }
    e.nativeSetInput("");
    return fail;
  }

  /** Rime offset just after the first [keys] code keys (delimiters skipped). */
  static int caretAfterKeys(String input, int keys) {
    int seen = 0;
    for (int i = 0; i < input.length(); i++) {
      if (input.charAt(i) != '\'' && ++seen == keys) return i + 1;
    }
    return input.length();
  }

  static int find(String[][] rows, String text) {
    for (int i = 0; i < rows.length; i++) if (rows[i].length > 0 && rows[i][0].equals(text)) return i;
    return -1;
  }

  static void print(String label, RimeEngine e) {
    String[] s = e.nativePinyinEditSnapshot();
    String[][] rows = e.nativeInspectCandidates(6);
    StringBuilder b = new StringBuilder();
    for (String[] r : rows) b.append(r[0]).append('(').append(r.length > 1 ? r[1] : "").append(") ");
    System.out.println(label + ": input='" + e.nativeGetInput() + "' snapshot=" + Arrays.toString(s) + " -> " + b);
  }
}
