import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Run against a disposable user directory with the files exported by PersonalDictManagerTest
 * (CYIME_PHRASE_PROBE_DIR). Arguments: host library directory, disposable user directory. */
public final class SharedShortcutProbe {
  private static final String[][] CASES = {
      {"oof", "❤️", "🟠", "🟡", "🟢"}, {"oog", "----------------", "/"},
      {"oov", "——", "-"}, {"ow", "?"}, {"os", "!"}, {"qzs", "短语乙", "短语甲"}
  };
  private static int failures;
  private static void require(boolean condition, String detail) {
    if (!condition) throw new AssertionError(detail);
  }
  private static void type(RimeEngine e, String keys, boolean t9) throws Exception {
    if (t9) LayoutSyncProbe.type(e, keys);
    else for (char c : keys.toCharArray()) require(e.nativeProcessKey(c, 0), "unhandled " + c);
  }
  private static void settle(RimeEngine e, boolean t9) throws Exception {
    if (t9) {
      long end = System.nanoTime() + 5_000_000_000L;
      while (e.nativeT9RefinementState() == 1 && System.nanoTime() < end) Thread.sleep(10);
      int state = e.nativeT9RefinementState();
      require(state != 1, "timed out waiting for the paused candidate list");
      if (state == 2) e.nativeRefineT9Sentences();
    }
    e.nativeRefineOmission();
  }
  private static void test(RimeEngine e, String schema, boolean t9, boolean mergedTap) throws Exception {
    LayoutSyncProbe.select(e, schema);
    for (String[] row : CASES) {
      String keys = t9 ? LayoutSyncProbe.t9Code(row[0]) : mergedTap ? LayoutSyncProbe.k14Code(row[0]) : row[0];
      List<String> expected = Arrays.asList(row).subList(1, row.length);
      for (String want : expected) try {
        LayoutSyncProbe.clear(e, t9);
        type(e, keys, t9);
        String[][] candidates = e.nativeInspectCandidates(18);
        List<String> words = Arrays.stream(candidates).map(r -> r[0]).collect(Collectors.toList());
        int index = words.indexOf(want);
        require(index >= 0 && index < expected.size() && index == words.lastIndexOf(want),
            schema + " " + keys + " expected " + want + ": " + words);
        if (row[0].equals("qzs"))
          require(words.get(0).equals("短语乙"), "weight order changed: " + words);
        settle(e, t9);
        candidates = e.nativeInspectCandidates(expected.size());
        require(candidates.length == expected.size() && want.equals(candidates[index][0]), "changed after pause: " + want);
        if (t9) {
          // Android commits the text it displayed when the T9 controller consumes the whole code.
          require(e.nativeT9SelectCandidate(candidates[index][1], want, want.length()), "partial T9 commit: " + want);
          e.nativeT9FlushRimeInput();
          require(e.nativeT9GetRemainingDigits().isEmpty(), "digits left after selecting " + want);
        } else {
          require(e.nativeSelectCandidateByGlobalIndex(index), "selection rejected: " + want);
          require(want.equals(e.nativeCommit()), "committed text changed: " + want);
        }
        System.out.println(schema + " " + keys + " #" + (index + 1) + " " + want + " OK");
      } catch (AssertionError e1) {
        ++failures;
        System.err.println("FAIL " + schema + " " + keys + " " + want + ": " + e1.getMessage());
      }
    }
    // Remove f from oof, replace it with g: the prefix has no oof group and the new code offers '/'.
    LayoutSyncProbe.clear(e, t9);
    String from = t9 ? "663" : mergedTap ? "OOD" : "oof";
    type(e, from, t9);
    require(e.nativeProcessKey(0xff08, 0), "backspace rejected");
    if (t9) e.nativeT9FlushRimeInput();
    require(e.nativeGetInput().equals(from.substring(0, 2)), "backspace did not retain the prefix");
    require(Arrays.stream(e.nativeInspectCandidates(8)).noneMatch(r -> r[0].equals("🟢")), "stale oof candidate");
    type(e, t9 ? "4" : mergedTap ? "G" : "g", t9);
    require(Arrays.stream(e.nativeInspectCandidates(2)).anyMatch(r -> r[0].equals("/")), "edited code lost '/'");
    LayoutSyncProbe.clear(e, t9);
  }

  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    RimeEngine engine = new RimeEngine();
    engine.nativeInitialize(args[1], args[1]);
    try {
      engine.nativeEnsureT9SchemaPatches("t9_pinyin");
      LayoutSyncProbe.maintain(engine, true);
      require(engine.nativeCreateSession(), "session unavailable");
      for (String schema : new String[]{"rime_ice", "double_pinyin_flypy", "t9_pinyin", "double_pinyin_flypy_t9",
          "pinyin_14jian", "double_pinyin_flypy_14jian"}) {
        boolean t9 = schema.equals("t9_pinyin") || schema.endsWith("_t9");
        boolean merged = schema.endsWith("_14jian");
        test(engine, schema, t9, merged);
        if (merged) test(engine, schema, false, false); // individual letter swipes
      }
      LayoutSyncProbe.select(engine, "double_pinyin_flypy");
      type(engine, "qqw", false);
      require(Arrays.stream(engine.nativeInspectCandidates(8)).anyMatch(r -> r[0].equals("专用短语")), "private table lost");
      LayoutSyncProbe.select(engine, "pinyin_14jian");
      type(engine, "JJQ", false);
      require(Arrays.stream(engine.nativeInspectCandidates(8)).anyMatch(r -> r[0].equals("布局短语")), "private 14-key table lost");
      require(failures == 0, failures + " candidate/selection failures");
      System.out.println("PASS");
    } finally { engine.nativeDestroy(); }
  }
}
