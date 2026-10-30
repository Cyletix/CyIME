import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Nine keys and 14 keys keeping up with 26 keys (2026-10-30). Arguments: the built library directory, a
 * fresh test user directory listing rime_ice, t9_pinyin and pinyin_14jian (never an installed app's data),
 * and a mode:
 *   all  — the nine-key flick letter joins the code and fixes that position instead of clearing it; custom
 *          phrases reach nine keys (digits) and 14 keys (pair capitals) through the copies the app writes;
 *          then the 14-key ranking list below.
 *   rank — only the 14-key ranking list and whether a removed candidate (cyime-hidden-candidates.txt) still
 *          shows, to compare a schema before and after a filter change.
 * The phrase files are written the way PersonalDictManager.ensureSchemaPackInner writes them.
 */
public final class LayoutSyncProbe {
  static final String T9 = "t9_pinyin";
  static final String K14 = "pinyin_14jian";
  static final String T9_DIGITS = "22233344455566677778889999";
  static final String MERGED14_KEYS = "ABCDEDGGUJJlmBOOQEATUCQZTZ";
  /** The ranking must-pass list, as full pinyin; 14 keys type each with pair capitals. */
  static final String[][] RANKING = {
    {"上市公司", "shangshigongsi"}, {"房地产", "fangdichan"}, {"帮我拿个快递", "bangwonagekuaidi"},
    {"他也会来的", "tayehuilaide"}, {"通话", "tonghua"}, {"慢慢地", "manmande"}, {"好好地", "haohaode"},
    {"连上了吗", "lianshanglema"}, {"测完了吗", "cewanlema"}, {"语言键", "yuyanjian"}, {"打不出来", "dabuchulai"},
  };
  static int failures = 0;

  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    File user = new File(args[1]);
    String mode = args.length > 2 ? args[2] : "all";
    if (mode.equals("all")) writePhrases(user);
    // A candidate removed by long-press on 26 keys; 14 keys must not offer it either.
    write(new File(user, "cyime-hidden-candidates.txt"), HIDDEN + "\n");
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(user.getPath(), user.getPath());
    try {
      e.nativeEnsureT9SchemaPatches(T9);
      if (!new File(user, "build/" + T9 + ".schema.yaml").isFile()) maintain(e, true);
      if (!e.nativeCreateSession()) throw new Exception("session unavailable");
      if (mode.equals("all")) {
        flick(e);
        phrases(e);
      }
      ranking(e);
      hidden(e, mode.equals("all"));
    } finally { e.nativeDestroy(); }
    System.out.println(failures == 0 ? "PASS" : "FAIL " + failures);
    if (failures > 0) System.exit(1);
  }

  // ── Nine-key flick letter (ledger 498) ──

  static void flick(RimeEngine e) throws Exception {
    select(e, T9);
    e.nativeT9ClearComposition(1);
    e.nativeT9FlushRimeInput();
    check("with no code a flick is an ordinary letter (FixLetter declines)", !e.nativeT9FixLetter("g"));
    for (String[] c : new String[][] {{"9426", "g"}, {"9426", "h"}, {"744", "i"}, {"74", "h"}}) {
      e.nativeT9ClearComposition(1);
      e.nativeT9FlushRimeInput();
      type(e, c[0]);
      boolean fixed = e.nativeT9FixLetter(c[1]);
      e.nativeT9FlushRimeInput();
      String input = e.nativeGetInput();
      char digit = T9_DIGITS.charAt(c[1].charAt(0) - 'a');
      String[][] rows = e.nativeInspectCandidates(30);
      System.out.println("T9 " + c[0] + " + flick " + c[1] + " => input '" + input + "' " + head(rows, 5));
      check(c[0] + "+" + c[1] + ": flick accepted", fixed);
      check(c[0] + "+" + c[1] + ": typed digits kept and the letter's digit added", input.equals(c[0] + digit));
      int position = c[0].length();
      check(c[0] + "+" + c[1] + ": candidates offered", rows.length > 0);
      for (int i = 0; i < Math.min(rows.length, 10); i++) {
        String letters = letters(rows[i][1]);
        if (letters.length() > position) check(c[0] + "+" + c[1] + ": " + rows[i][0] + " (" + rows[i][1] + ") has "
            + c[1] + " at " + (position + 1), letters.charAt(position) == c[1].charAt(0));
      }
      // More digits continue the same code; the fixed letter stays fixed.
      type(e, "6");
      String more = e.nativeGetInput();
      String[][] after = e.nativeInspectCandidates(10);
      System.out.println("   then 6 => input '" + more + "' " + head(after, 3));
      check(c[0] + "+" + c[1] + " then 6: one code", more.equals(c[0] + digit + "6"));
      if (after.length > 0 && letters(after[0][1]).length() > position)
        check(c[0] + "+" + c[1] + " then 6: still " + c[1], letters(after[0][1]).charAt(position) == c[1].charAt(0));
    }
    e.nativeT9ClearComposition(1);
    e.nativeT9FlushRimeInput();
  }

  // ── Custom phrases on nine and 14 keys ──

  static final String[][] PHRASES = {{"地址", "dz"}, {"我的邮箱", "wdyx"}, {"探针短语", "tzdy"}};

  static void writePhrases(File user) throws Exception {
    String header = "# Rime table\n# coding: utf-8\n#@/db_name\t%s\n#@/db_type\ttabledb\n#\n";
    StringBuilder shared = new StringBuilder(String.format(header, "custom_phrase"));
    StringBuilder t9 = new StringBuilder(String.format(header, "custom_phrase_t9"));
    StringBuilder k14 = new StringBuilder(String.format(header, "custom_phrase_k14"));
    for (String[] p : PHRASES) {
      shared.append(p[0]).append('\t').append(p[1]).append("\t1\n");
      t9.append(p[0]).append('\t').append(t9Code(p[1])).append("\t1\n");
      k14.append(p[0]).append('\t').append(k14Code(p[1])).append("\t1\n");
      if (!k14Code(p[1]).equals(p[1])) k14.append(p[0]).append('\t').append(p[1]).append("\t1\n");
    }
    write(new File(user, "custom_phrase.txt"), shared.toString());
    write(new File(user, "custom_phrase_t9.txt"), t9.toString());
    write(new File(user, "custom_phrase_k14.txt"), k14.toString());
    write(new File(user, "rime_ice.custom.yaml"), patch("custom_phrase"));
    write(new File(user, T9 + ".custom.yaml"), patch("custom_phrase_t9"));
    write(new File(user, K14 + ".custom.yaml"), patch("custom_phrase_k14"));
  }

  static String patch(String dict) {
    return "patch:\n  \"engine/translators/+\":\n    - table_translator@custom_phrase\n  \"custom_phrase\":\n"
        + "    dictionary: \"\"\n    user_dict: " + dict + "\n    db_class: stabledb\n    enable_completion: false\n"
        + "    enable_sentence: false\n    initial_quality: 99\n";
  }

  static void phrases(RimeEngine e) throws Exception {
    for (String[] p : PHRASES) {
      phrase(e, "rime_ice", p[1], p[0], false);
      phrase(e, T9, t9Code(p[1]), p[0], true);
      phrase(e, K14, k14Code(p[1]), p[0], false);
      // Swiping each letter on 14 keys types the letters themselves.
      phrase(e, K14, p[1], p[0], false);
    }
  }

  static void phrase(RimeEngine e, String schema, String code, String text, boolean t9) throws Exception {
    select(e, schema);
    if (t9) { e.nativeT9ClearComposition(1); e.nativeT9FlushRimeInput(); type(e, code); }
    else { for (char c : code.toCharArray()) if (!e.nativeProcessKey(c, 0)) throw new Exception("unhandled key " + c); }
    String[][] rows = e.nativeInspectCandidates(20);
    int rank = -1;
    for (int i = 0; i < rows.length; i++) if (rows[i][0].equals(text)) { rank = i + 1; break; }
    System.out.println("PHRASE " + schema + " " + code + " => " + text + " rank " + rank + " " + head(rows, 3));
    check(schema + " " + code + ": " + text + " first", rank == 1);
    clear(e, t9);
  }

  // ── 14-key ranking ──

  static void ranking(RimeEngine e) throws Exception {
    select(e, K14);
    for (String[] w : RANKING) {
      String code = k14Code(w[1]);
      for (char c : code.toCharArray()) if (!e.nativeProcessKey(c, 0)) throw new Exception("unhandled key " + c);
      String[][] rows = e.nativeInspectCandidates(20);
      int rank = -1;
      for (int i = 0; i < rows.length; i++) if (rows[i][0].equals(w[0])) { rank = i + 1; break; }
      System.out.println("RANK " + K14 + " " + code + " " + w[0] + " " + rank + " " + head(rows, 5));
      clear(e, false);
    }
  }

  static final String HIDDEN = "童话";

  static void hidden(RimeEngine e, boolean required) throws Exception {
    select(e, K14);
    String code = k14Code("tonghua");
    for (char c : code.toCharArray()) if (!e.nativeProcessKey(c, 0)) throw new Exception("unhandled key " + c);
    String[][] rows = e.nativeInspectCandidates(50);
    boolean shown = false;
    for (String[] row : rows) if (row[0].equals(HIDDEN)) shown = true;
    System.out.println("HIDDEN " + K14 + " " + code + " " + HIDDEN + " shown=" + shown + " " + head(rows, 5));
    if (required) check(K14 + ": a removed candidate stays removed", !shown);
    clear(e, false);
  }

  // ── helpers ──

  static String t9Code(String letters) {
    StringBuilder b = new StringBuilder();
    for (char c : letters.toCharArray()) b.append(T9_DIGITS.charAt(c - 'a'));
    return b.toString();
  }

  static String k14Code(String letters) {
    StringBuilder b = new StringBuilder();
    for (char c : letters.toCharArray()) b.append(MERGED14_KEYS.charAt(c - 'a'));
    return b.toString();
  }

  static String letters(String comment) {
    StringBuilder b = new StringBuilder();
    for (char c : comment.toLowerCase().toCharArray()) if (c >= 'a' && c <= 'z') b.append(c);
    return b.toString();
  }

  static String head(String[][] rows, int n) {
    List<String> out = new ArrayList<>();
    for (int i = 0; i < Math.min(rows.length, n); i++) out.add(rows[i][0] + "/" + rows[i][1]);
    return out.toString();
  }

  static void select(RimeEngine e, String schema) throws Exception {
    if (!e.nativeSwitchSchema(schema)) throw new Exception("schema unavailable: " + schema);
    e.nativeSetOption("ascii_mode", false);
  }

  static void clear(RimeEngine e, boolean t9) {
    if (t9) e.nativeT9ClearComposition(1);
    e.nativeSetInput("");
    if (t9) e.nativeT9FlushRimeInput();
  }

  static void type(RimeEngine e, String digits) throws Exception {
    for (char c : digits.toCharArray()) {
      if (!e.nativeProcessKey(c, 0)) throw new Exception("unhandled key " + c);
      e.nativeT9FlushRimeInput();
    }
  }

  static void check(String what, boolean ok) {
    if (!ok) failures++;
    System.out.println((ok ? "  ok   " : "  FAIL ") + what);
  }

  static void write(File file, String text) throws Exception {
    Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
  }

  static void maintain(RimeEngine e, boolean full) throws Exception {
    e.nativeStartMaintenance(full);
    long until = System.currentTimeMillis() + 1200000;
    while (e.nativeIsMaintaining()) {
      if (System.currentTimeMillis() > until) throw new Exception("maintenance timeout");
      Thread.sleep(100);
    }
  }
}
