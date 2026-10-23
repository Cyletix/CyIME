import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeded random input on the 9-key, 26-key and 14-key schemas: typing half a word, choosing part
 * of it, deleting and adding keys, capitals/digits/punctuation inside pinyin, clearing the way the
 * app does, then typing again. Checks after every step that the engine did not throw, that a clear
 * leaves no code behind (the next keys start a new code), and that backspace can always empty it.
 * Arguments: library dir, a copy of a deployed user dir whose schema_list holds all three, seed,
 * rounds per schema.
 */
public final class ChaosInputProbe {
  static final String[] SCHEMAS = {"t9_pinyin", "rime_ice", "pinyin_14jian"};
  static final ArrayDeque<String> trace = new ArrayDeque<>();
  static final List<String> violations = new ArrayList<>();
  static int steps;

  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    File user = new File(args[1]);
    long seed = Long.parseLong(args[2]);
    int rounds = Integer.parseInt(args[3]);
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(user.getPath(), user.getPath());
    try {
      for (String s : SCHEMAS) e.nativeEnsureT9SchemaPatches(s);
      boolean deployed = true;
      for (String s : SCHEMAS) deployed &= new File(user, "build/" + s + ".schema.yaml").isFile();
      if (!deployed) {
        e.nativeStartMaintenance(true);
        long until = System.currentTimeMillis() + 900000;
        while (e.nativeIsMaintaining()) {
          if (System.currentTimeMillis() > until) throw new Exception("deployment timeout");
          Thread.sleep(100);
        }
      }
      if (!e.nativeCreateSession()) throw new Exception("session unavailable");
      Random random = new Random(seed);
      for (String schema : SCHEMAS) {
        if (!e.nativeSwitchSchema(schema)) throw new Exception("schema unavailable: " + schema);
        e.nativeSetOption("ascii_mode", false);
        int before = steps;
        for (int round = 0; round < rounds; round++) round(e, schema, random, round);
        System.out.println(schema + ": " + (steps - before) + " steps");
      }
    } finally { e.nativeDestroy(); }
    System.out.println("STEPS " + steps + " VIOLATIONS " + violations.size());
    for (String v : violations) System.out.println(v);
    if (!violations.isEmpty()) System.exit(1);
  }

  static boolean t9(String schema) { return schema.equals("t9_pinyin"); }

  static void round(RimeEngine e, String schema, Random r, int round) {
    trace.clear();
    appClear(e, schema);
    int ops = 10 + r.nextInt(30);
    for (int i = 0; i < ops; i++) {
      int pick = r.nextInt(100);
      try {
        if (pick < 45) type(e, schema, randomKey(schema, r));
        else if (pick < 58) key(e, schema, "BS", 0xff08);
        else if (pick < 70) choose(e, schema, r);
        else if (pick < 76) key(e, schema, "SEP", t9(schema) ? '1' : '\'');
        else if (pick < 82) { log("SPACE"); e.nativeProcessKey(' ', 0); flush(e, schema); }
        else if (pick < 90) { appClear(e, schema); freshAfterClear(e, schema, r, round); }
        else if (pick < 95) { log("ENTER(app: raw commit, then clear)"); appClear(e, schema); freshAfterClear(e, schema, r, round); }
        else drain(e, schema, round);
      } catch (Throwable t) {
        violation(schema, round, "threw " + t);
        appClear(e, schema);
      }
    }
    drain(e, schema, round);
  }

  static String randomKey(String schema, Random r) {
    if (t9(schema)) return String.valueOf((char) ('2' + r.nextInt(8)));
    int pick = r.nextInt(100);
    if (pick < 75) return String.valueOf((char) ('a' + r.nextInt(26)));
    if (pick < 85) return String.valueOf((char) ('A' + r.nextInt(26)));
    if (pick < 95) return String.valueOf((char) ('0' + r.nextInt(10)));
    return String.valueOf(",.;/-".charAt(r.nextInt(5)));
  }

  static void type(RimeEngine e, String schema, String k) { key(e, schema, k, k.charAt(0)); }

  static void key(RimeEngine e, String schema, String name, int code) {
    log(name);
    e.nativeProcessKey(code, 0);
    flush(e, schema);
  }

  static void flush(RimeEngine e, String schema) { if (t9(schema)) e.nativeT9FlushRimeInput(); }

  /** Choose a candidate, as the app does: T9 consumes the reading itself, others select in Rime. */
  static void choose(RimeEngine e, String schema, Random r) {
    String[][] rows = e.nativeInspectCandidates(12);
    if (rows.length == 0) { log("CHOOSE(none)"); return; }
    int index = r.nextInt(rows.length);
    String text = rows[index][0];
    log("CHOOSE " + index + " " + text);
    if (t9(schema)) {
      e.nativeT9SelectCandidate(rows[index][1], text, text.codePointCount(0, text.length()));
      e.nativeT9FlushRimeInput();
    } else e.nativeSelectCandidateByGlobalIndex(index);
  }

  /** What the app does when input ends (Enter, clear gesture, cursor move, panels): see ImeKeyRouter.endQueuedComposition. */
  static void appClear(RimeEngine e, String schema) {
    log("CLEAR");
    if (t9(schema)) { e.nativeT9ClearComposition(1); e.nativeT9FlushRimeInput(); }
    e.nativeSetInput("");
  }

  static void freshAfterClear(RimeEngine e, String schema, Random r, int round) {
    String input = e.nativeGetInput();
    if (!input.isEmpty()) violation(schema, round, "code left after clear: '" + input + "'");
    String a = randomKey(schema, r).toLowerCase(), b = randomKey(schema, r).toLowerCase();
    if (!t9(schema)) { a = String.valueOf((char) ('a' + r.nextInt(26))); b = "a"; }
    type(e, schema, a);
    type(e, schema, b);
    // 14-key merged letters may be rewritten to their key's code; only the length shows an old code.
    String now = e.nativeGetInput();
    if (now.replace("'", "").length() > 2) {
      violation(schema, round, "keys after clear joined old code: typed '" + a + b + "', input '" + now + "'");
    }
  }

  /** Backspace must always be able to empty the code. */
  static void drain(RimeEngine e, String schema, int round) {
    int limit = e.nativeGetInput().length() * 3 + 12;
    for (int i = 0; i < limit && !e.nativeGetInput().isEmpty(); i++) { e.nativeProcessKey(0xff08, 0); flush(e, schema); }
    log("DRAIN");
    String left = e.nativeGetInput();
    if (!left.isEmpty()) violation(schema, round, "backspace could not empty the code: '" + left + "'");
  }

  static void log(String op) {
    steps++;
    trace.addLast(op);
    while (trace.size() > 14) trace.removeFirst();
  }

  static void violation(String schema, int round, String what) {
    if (violations.size() < 40) violations.add("VIOLATION " + schema + " round " + round + ": " + what + "\n  after: " + String.join(" | ", trace));
  }
}
