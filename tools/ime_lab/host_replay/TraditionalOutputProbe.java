import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;

/**
 * 简/繁 output (2026-10-30). Before this, the APK carried no OpenCC tables, so turning traditionalization on
 * changed nothing. Arguments: the built library directory and a fresh test user directory listing rime_ice,
 * double_pinyin_flypy, t9_pinyin and pinyin_14jian, with assets/rime_chinese/opencc copied in (never an
 * installed app's data). For each layout the same word must come out simplified with the option off and in
 * Taiwan everyday forms (台灣, 計畫, 了解 — assets/rime_chinese/opencc/cyime_s2tw.json) with it on.
 */
public final class TraditionalOutputProbe {
  static final String T9_DIGITS = "22233344455566677778889999";
  static final String MERGED14_KEYS = "ABCDEDGGUJJlmBOOQEATUCQZTZ";
  /** {pinyin, flypy double pinyin, simplified, Taiwan form} */
  static final String[][] WORDS = {
    {"taiwan", "tdwj", "台湾", "台灣"}, {"jihua", "jihx", "计划", "計畫"}, {"liaojie", "lnjp", "了解", "了解"},
    {"taifeng", "tdfg", "台风", "颱風"}, {"toufa", "tzfa", "头发", "頭髮"}, {"guitai", "gvtd", "柜台", "櫃檯"},
  };
  static int failures = 0;

  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    File user = new File(args[1]);
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(user.getPath(), user.getPath());
    try {
      e.nativeEnsureT9SchemaPatches("t9_pinyin");
      if (!new File(user, "build/t9_pinyin.schema.yaml").isFile()) maintain(e);
      if (!e.nativeCreateSession()) throw new Exception("session unavailable");
      for (String schema : new String[] {"rime_ice", "double_pinyin_flypy", "t9_pinyin", "pinyin_14jian"}) {
        boolean t9 = schema.equals("t9_pinyin");
        for (String[] w : WORDS) {
          String code = switch (schema) {
            case "t9_pinyin" -> map(w[0], T9_DIGITS);
            case "pinyin_14jian" -> map(w[0], MERGED14_KEYS);
            case "double_pinyin_flypy" -> w[1];
            default -> w[0];
          };
          for (boolean traditional : new boolean[] {false, true}) {
            if (!e.nativeSwitchSchema(schema)) throw new Exception("schema unavailable: " + schema);
            e.nativeSetOption("ascii_mode", false);
            e.nativeSetOption("traditionalization", traditional);
            clear(e, t9);
            for (char c : code.toCharArray()) {
              if (!e.nativeProcessKey(c, 0)) throw new Exception("unhandled key " + c);
              if (t9) e.nativeT9FlushRimeInput();
            }
            String[][] rows = e.nativeInspectCandidates(8);
            String want = traditional ? w[3] : w[2];
            boolean found = false;
            StringBuilder head = new StringBuilder();
            for (int i = 0; i < Math.min(rows.length, 5); i++) {
              head.append(rows[i][0]).append(' ');
              if (rows[i][0].equals(want)) found = true;
            }
            check(schema + " " + code + (traditional ? " 繁 " : " 简 ") + "→ " + want + " in top 5: " + head, found);
            clear(e, t9);
          }
        }
      }
    } finally { e.nativeDestroy(); }
    System.out.println(failures == 0 ? "PASS" : "FAIL " + failures);
    if (failures > 0) System.exit(1);
  }

  static String map(String letters, String keys) {
    StringBuilder out = new StringBuilder();
    for (char c : letters.toCharArray()) out.append(keys.charAt(c - 'a'));
    return out.toString();
  }

  static void clear(RimeEngine e, boolean t9) {
    if (t9) e.nativeT9ClearComposition(1);
    e.nativeSetInput("");
    if (t9) e.nativeT9FlushRimeInput();
  }

  static void check(String what, boolean ok) {
    if (!ok) failures++;
    System.out.println((ok ? "  ok   " : "  FAIL ") + what);
  }

  static void maintain(RimeEngine e) throws Exception {
    e.nativeStartMaintenance(true);
    long until = System.currentTimeMillis() + 1200000;
    while (e.nativeIsMaintaining()) {
      if (System.currentTimeMillis() > until) throw new Exception("maintenance timeout");
      Thread.sleep(100);
    }
  }
}
