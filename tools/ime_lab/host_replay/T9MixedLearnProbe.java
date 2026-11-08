import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 英文词、中英混合词能不能打出来；打一遍（分段选词）后再打同样的键，这个词排第几。九键、14 键、26 键跑同一批例子。
 * Arguments: library_dir, a disposable user directory (deployed on first use), mode (recall | learn | learn2 |
 * decay | reopen | all), schemas (default t9_pinyin,pinyin_14jian,rime_ice), optional preset *.userdb.txt and,
 * for decay, how many other words to commit before checking again. Exit status 1 when any case fails.
 * Nine-key goes through the app's own path: nativeT9SelectCandidate per segment, refresh the remaining digits
 * after a partial choice, nativeT9Memorize(fullText, "") after the accepted full commit, then
 * nativeT9ClearComposition(1); 14/26 keys select by global index and commit.
 * Compile and run together with ../android_replay/RimeEngine.java.
 */
public final class T9MixedLearnProbe {
  static final String SCHEMA = "t9_pinyin";
  static final String KEYS = "22233344455566677778889999";
  static int failures = 0;

  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    File logs = new File(args[1], "logs");
    logs.mkdirs();
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(args[1], args[1]);
    try {
      e.nativeEnsureT9SchemaPatches(SCHEMA);
      if (!new File(args[1], "build/" + SCHEMA + ".schema.yaml").isFile()) e.nativeStartMaintenance(true);
      long until = System.currentTimeMillis() + 900000;
      while (e.nativeIsMaintaining()) {
        if (System.currentTimeMillis() > until) throw new Exception("deployment timeout");
        Thread.sleep(100);
      }
      if (args.length > 4) {
        long[] restored = e.nativeHostRestoreUserDict(args[4], "rime_ice");
        System.out.println("seed userdb " + (restored == null ? "FAILED" : restored[0] + " entries, tick " + restored[1]));
      }
      if (!e.nativeCreateSession()) throw new Exception("session unavailable");
      String mode = args.length > 2 ? args[2] : "all";
      String[] schemas = (args.length > 3 ? args[3] : "t9_pinyin,pinyin_14jian,rime_ice").split(",");
      for (String s : schemas) {
        schema = s;
        if (!e.nativeSwitchSchema(s)) throw new Exception("schema unavailable " + s);
        e.nativeSetOption("ascii_mode", false);
        System.out.println("=== " + s);
        if (mode.equals("all") || mode.equals("recall")) recall(e);
        if (mode.equals("all") || mode.equals("learn")) learn(e);
        if (mode.equals("reopen")) reopen(e);
        if (mode.equals("learn2")) learn2(e);
        if (mode.equals("decay")) { learn2(e); filler(e, args.length > 5 ? Integer.parseInt(args[5]) : 300); recheck(e); }
      }
      System.out.println(failures == 0 ? "PROBE PASS" : "PROBE FAIL " + failures);
    } finally { e.nativeDestroy(); }
    System.exit(failures == 0 ? 0 : 1);
  }

  static final String KEYS14 = "ABCDEDGGUJJlmBOOQEATUCQZTZ";
  static String schema = SCHEMA;
  static boolean t9() { return schema.equals("t9_pinyin"); }
  /** Letters as the layout sends them: 9 keys digits, 14 keys merged capitals (l, m exact), 26 keys letters. */
  static String digits(String letters) {
    StringBuilder b = new StringBuilder();
    for (char c : letters.toLowerCase().toCharArray()) {
      if (c < 'a' || c > 'z') continue;
      if (schema.equals("t9_pinyin")) b.append(KEYS.charAt(c - 'a'));
      else if (schema.equals("pinyin_14jian")) b.append(KEYS14.charAt(c - 'a'));
      else b.append(c);
    }
    return b.toString();
  }

  static void type(RimeEngine e, String keys) throws Exception {
    if (t9()) { e.nativeT9ClearComposition(1); e.nativeT9FlushRimeInput(); }
    else e.nativeSetInput("");
    for (char c : keys.toCharArray()) {
      if (!e.nativeProcessKey(c, 0)) throw new Exception("unhandled key " + c);
      if (t9()) e.nativeT9FlushRimeInput();
    }
  }

  static String[][] rows(RimeEngine e) { return e.nativeInspectCandidates(200); }

  static int rank(String[][] rows, String text) {
    for (int i = 0; i < rows.length; i++) if (rows[i].length > 0 && rows[i][0].equals(text)) return i + 1;
    return -1;
  }

  static String head(String[][] rows, int n) {
    List<String> out = new ArrayList<>();
    for (int i = 0; i < Math.min(n, rows.length); i++) out.add(rows[i][0]);
    return out.toString();
  }

  static void check(boolean ok, String message) {
    System.out.println((ok ? "OK   " : "FAIL ") + message);
    if (!ok) failures++;
  }

  // ── 1. 英文词、中英混合词 ──
  static void recall(RimeEngine e) throws Exception {
    String[][] cases = {
      {"fuck", digits("fuck")}, {"fucking", digits("fucking")}, {"shit", digits("shit")},
      {"B站", digits("bzhan")}, {"T恤", digits("txu")}, {"A股", digits("agu")},
      {"卡拉OK", digits("kalaok")},
      // 对照：已经能打出来的
      {"steam", digits("steam")}, {"hello", digits("hello")}, {"GitHub", digits("github")},
    };
    for (String[] c : cases) {
      type(e, c[1]);
      String[][] r = rows(e);
      int k = rank(r, c[0]);
      check(k > 0, "recall " + c[0] + " (" + c[1] + ") rank " + k + " of " + r.length + "  head " + head(r, 8));
    }
  }

  /** Picks the target by segments, as a person does when the whole phrase is not offered. */
  static boolean pickBySegments(RimeEngine e, String keys, String target) throws Exception {
    type(e, keys);
    String done = "";
    for (int step = 0; step < 8; step++) {
      String[][] r = rows(e);
      String rest = target.substring(done.length());
      int best = -1;
      for (int i = 0; i < r.length; i++) {
        String t = r[i][0];
        if (t.isEmpty() || !rest.startsWith(t)) continue;
        if (best < 0 || t.length() > r[best][0].length()) best = i;
      }
      if (best < 0) { System.out.println("     no segment for '" + rest + "' in " + head(r, 12)); return false; }
      String t = r[best][0];
      boolean full;
      if (t9()) {
        full = e.nativeT9SelectCandidate(r[best][1], t, t.codePointCount(0, t.length()));
        e.nativeT9FlushRimeInput();
      } else {
        if (!e.nativeSelectCandidateByGlobalIndex(best)) { System.out.println("     select failed"); return false; }
        String committed = e.nativeCommit();
        full = committed != null && !committed.isEmpty();
        if (full && !committed.equals(done + t)) System.out.println("     committed '" + committed + "'");
      }
      done += t;
      System.out.println("     picked '" + t + "' at " + (best + 1) + (full ? " (full)" : " (partial)"));
      if (full) break;
      if (!t9()) continue;
      String remaining = e.nativeT9GetRemainingDigits();
      if (!remaining.isEmpty() && !remaining.equals(e.nativeGetInput())) e.nativeSetInput(remaining);
      e.nativeT9FlushRimeInput();
    }
    if (!done.equals(target)) { System.out.println("     composed '" + done + "' instead of '" + target + "'"); return false; }
    boolean learned = true;
    if (t9()) {
      learned = e.nativeT9Memorize(target, "");
      e.nativeT9ClearComposition(1);
      e.nativeT9FlushRimeInput();
    }
    System.out.println("     memorize -> " + learned);
    return learned;
  }

  static String[][] learnCases() { return new String[][] {
    // 词库里没有的词：分段选
    {"折成", digits("zhecheng")},
    {"抱猫", digits("baomao")},
    {"纸船", digits("zhichuan")},
    {"李明哲", digits("limingzhe")},
    {"喵星人呢", digits("miaoxingrenne")},
    // 英文词后接中文
    {"B站", digits("bzhan")},
    {"fuck", digits("fuck")},
    {"shit", digits("shit")},
    {"T恤", digits("txu")},
  }; }

  static void learn(RimeEngine e) throws Exception {
    for (String[] c : learnCases()) {
      type(e, c[1]);
      String[][] before = rows(e);
      int b = rank(before, c[0]);
      System.out.println("LEARN " + c[0] + " (" + c[1] + ") before rank " + b + "  head " + head(before, 6));
      boolean picked = pickBySegments(e, c[1], c[0]);
      if (!picked) { check(false, "learn " + c[0] + ": could not type it once"); continue; }
      type(e, c[1]);
      String[][] after = rows(e);
      int a = rank(after, c[0]);
      check(a == 1, "learn " + c[0] + " after one use rank " + a + "  head " + head(after, 6));
    }
  }

  static String[][] hardCases() { return new String[][] {
    {"孙笑川", "sunxiaochuan", "孙笑川的粉丝", "sunxiaochuandefensi"},
    {"翟天临", "zhaitianlin", "翟天临说", "zhaitianlinshuo"},
    {"沈一默", "shenyimo", "我和沈一默", "woheshenyimo"},
    {"蚌埠住了", "bengbuzhule", "我蚌埠住了", "wobengbuzhule"},
    {"你个老六", "nigelaoliu", "你个老六吧", "nigelaoliuba"},
    {"抱猫", "baomao", "抱猫的少年", "baomaodeshaonian"},
    {"折成", "zhecheng", "用纸折成", "yongzhizhecheng"},
    {"纸船", "zhichuan", "一个纸船", "yigezhichuan"},
  }; }

  /** Harder: phrases far down the list, then the same word inside a longer input. */
  static void learn2(RimeEngine e) throws Exception {
    for (String[] c : hardCases()) {
      String keys = digits(c[1]);
      type(e, keys);
      String[][] before = rows(e);
      System.out.println("LEARN " + c[0] + " (" + keys + ") before rank " + rank(before, c[0]) + "  head " + head(before, 6));
      String longKeys = digits(c[3]);
      type(e, longKeys);
      String[][] lb = rows(e);
      System.out.println("     long '" + c[2] + "' before: top " + head(lb, 3));
      if (!pickBySegments(e, keys, c[0])) { check(false, "learn2 " + c[0] + ": could not type it once"); continue; }
      type(e, keys);
      String[][] after = rows(e);
      check(rank(after, c[0]) == 1, "learn2 " + c[0] + " after one use rank " + rank(after, c[0]) + "  head " + head(after, 6));
      type(e, longKeys);
      String[][] la = rows(e);
      check(la.length > 0 && la[0][0].contains(c[0]), "learn2 inside '" + c[2] + "' top " + head(la, 3));
    }
  }

  /** Ordinary typing afterwards: N other words committed (seed dictionary words, as a person picks them). */
  static void filler(RimeEngine e, int n) throws Exception {
    List<String[]> words = new ArrayList<>();
    for (String line : java.nio.file.Files.readAllLines(new File("/home/user/CyIME/tools/ime_lab/fixtures/user_dict_seed_20261009.userdb.txt").toPath())) {
      if (line.startsWith("#")) continue;
      String[] f = line.split("\t");
      if (f.length < 2 || f[1].codePointCount(0, f[1].length()) < 2) continue;
      words.add(new String[] {f[1], f[0].replace(" ", "")});
    }
    int done = 0;
    for (int i = 0; done < n && i < words.size(); i += 7) {
      String[] w = words.get(i);
      String keys = digits(w[1]);
      type(e, keys);
      String[][] r = rows(e);
      if (rank(r, w[0]) < 1) continue;
      if (pickQuiet(e, w[0])) done++;
    }
    System.out.println("filler: committed " + done + " other words");
  }

  static boolean pickQuiet(RimeEngine e, String text) throws Exception {
    String[][] r = rows(e);
    int k = rank(r, text) - 1;
    if (k < 0) return false;
    if (t9()) {
      boolean full = e.nativeT9SelectCandidate(r[k][1], text, text.codePointCount(0, text.length()));
      e.nativeT9FlushRimeInput();
      if (!full) { e.nativeT9ClearComposition(1); return false; }
      boolean ok = e.nativeT9Memorize(text, "");
      e.nativeT9ClearComposition(1); e.nativeT9FlushRimeInput();
      return ok;
    }
    if (!e.nativeSelectCandidateByGlobalIndex(k)) return false;
    String c = e.nativeCommit();
    e.nativeSetInput("");
    return c != null && !c.isEmpty();
  }

  static void recheck(RimeEngine e) throws Exception {
    for (String[] c : hardCases()) {
      type(e, digits(c[1]));
      String[][] r = rows(e);
      check(rank(r, c[0]) == 1, "later " + c[0] + " rank " + rank(r, c[0]) + "  head " + head(r, 5));
      type(e, digits(c[3]));
      String[][] l = rows(e);
      check(l.length > 0 && l[0][0].contains(c[0]), "later inside '" + c[2] + "' top " + head(l, 3));
    }
  }

  static void reopen(RimeEngine e) throws Exception {
    for (String[] c : learnCases()) {
      type(e, c[1]);
      String[][] r = rows(e);
      int a = rank(r, c[0]);
      check(a == 1, "reopen " + c[0] + " rank " + a + "  head " + head(r, 6));
    }
  }
}
