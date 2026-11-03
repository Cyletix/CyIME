import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simulated real typist for 26-key full pinyin (2026-10-31, real_typing_bench.py). Not a ranking query: it plays
 * what a person does and counts how often they have to stop.
 *
 * PROBE_OPTIONS (environment, optional): comma separated session switches, e.g. cyime_mixed_input=1.
 * PROBE_SCHEMA (environment, optional): schema to type in, default rime_ice (e.g. double_pinyin_flypy, t9_pinyin).
 *
 * type  LIB USER CASES OUT NEIGHBORS
 *   Each case (id, typed letters, target) is typed key by key without picking. Per key the first candidate is
 *   kept (to count how often text already on screen is rewritten). At the end the person reads the candidate
 *   bar: target first = one space, no stop. Otherwise they pick, left to right, the longest visible candidate
 *   that the rest of the target starts with (first 5 = one stop; 6-20 = two stops, they had to open the
 *   panel), and repeat on what is left. Nothing usable in the first 20 = stuck (delete and retype). The last
 *   piece is counted but not committed, so nothing is learned and every case starts from the same state.
 *
 * history LIB USER STREAM PROBES OUT NEIGHBORS MISPICK SEED CHECKPOINTS
 *   A learning history: each stream line (id, letters, target) is typed and committed piece by piece with the
 *   same policy (so Rime learns as on the phone). With probability MISPICK a whole-sentence or last-piece commit
 *   lands on the neighbouring candidate (fat finger); the person deletes it on screen and types it again
 *   correctly, but the wrong commit has been learned (default settings: 删掉刚打的词不学习 is off). At each
 *   checkpoint (comma separated stream positions; 0 = before any learning) every probe is typed without
 *   committing and its first 5 candidates and target place are written.
 */
public final class RealTypingProbe {
  static final int VISIBLE = 5;
  static final int PANEL = 20;

  static List<String[]> read(String path) throws Exception {
    List<String[]> rows = new ArrayList<>();
    for (String line : Files.readAllLines(new File(path).toPath(), StandardCharsets.UTF_8))
      if (!line.isEmpty() && !line.startsWith("#")) rows.add(line.split("\t"));
    return rows;
  }

  static RimeEngine open(String lib, String user, String neighbors) throws Exception {
    System.load(new File(lib, "librime_jni.so").getAbsolutePath());
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(user, user);
    String schema = System.getenv("PROBE_SCHEMA");
    if (schema == null || schema.isEmpty()) schema = "rime_ice";
    t9 = schema.equals("t9_pinyin");
    // Nine-key: the app writes its schema patches before deploying (as ChatReplay does).
    if (t9) e.nativeEnsureT9SchemaPatches(schema);
    if (!new File(user, "build/" + schema + ".schema.yaml").isFile()) {
      e.nativeStartMaintenance(true);
      while (e.nativeIsMaintaining()) Thread.sleep(100);
    }
    if (!e.nativeCreateSession()) throw new Exception("session unavailable");
    if (!e.nativeSwitchSchema(schema)) throw new Exception("schema unavailable: " + schema);
    e.nativeSetOption("ascii_mode", false);
    // PROBE_OPTIONS=name=1,other=0 sets session switches the app sets from its settings (cyime_mixed_input).
    String options = System.getenv("PROBE_OPTIONS");
    if (options != null && !options.isEmpty())
      for (String o : options.split(",")) {
        String[] kv = o.split("=", 2);
        e.nativeSetOption(kv[0].trim(), kv.length < 2 || kv[1].trim().equals("1") || kv[1].trim().equals("true"));
      }
    e.nativeSetNeighborMap(neighbors);
    return e;
  }

  /** Nine-key (t9_pinyin): digits go through the T9 processor, which needs a flush after each key. */
  static boolean t9;

  static void clear(RimeEngine e) {
    if (t9) {
      e.nativeT9ClearComposition(1);
      e.nativeT9FlushRimeInput();
    }
    e.nativeSetInput("");
  }

  /** After the last key the person looks at the bar; nine-key finishes its pause refinement first (ChatReplay). */
  static void settle(RimeEngine e) throws Exception {
    if (!t9) return;
    long start = System.nanoTime();
    int state = e.nativeT9RefinementState();
    while (state == 1 && System.nanoTime() - start < 2_000_000_000L) {
      Thread.sleep(5);
      state = e.nativeT9RefinementState();
    }
    if (state == 2) e.nativeRefineT9Sentences();
  }

  static String clean(String s) { return s.replace('\t', ' ').replace('|', '/').replace('\n', ' '); }

  static void typeKeys(RimeEngine e, String keys, List<String> firsts) throws Exception {
    for (char c : keys.toCharArray()) {
      if (!e.nativeProcessKey(c, 0)) throw new Exception("unhandled key " + c + " in " + keys);
      if (t9) e.nativeT9FlushRimeInput();
      if (firsts != null) {
        String[][] rows = e.nativeInspectCandidates(1);
        firsts.add(rows.length > 0 ? clean(rows[0][0]) : "");
      }
    }
  }

  /** Index of the longest candidate in [0, limit) that `rest` starts with, or -1. */
  static int bestPrefix(String[][] rows, String rest, int limit) {
    int best = -1;
    for (int i = 0; i < Math.min(limit, rows.length); ++i) {
      String t = rows[i][0];
      if (!t.isEmpty() && rest.startsWith(t) && (best < 0 || t.length() > rows[best][0].length())) best = i;
    }
    return best;
  }

  /** Whether selecting this candidate would consume the rest of the input (inspect column 6 = end). */
  static boolean coversInput(String[] row, String input) {
    return row.length > 6 && !row[6].isEmpty() && Integer.parseInt(row[6]) >= input.length();
  }

  static String top(String[][] rows, int n) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < Math.min(n, rows.length); ++i) {
      if (i > 0) b.append('|');
      b.append(clean(rows[i][0]));
    }
    return b.toString();
  }

  static int rankOf(String[][] rows, String target) {
    for (int i = 0; i < rows.length; ++i) if (rows[i][0].equals(target)) return i;
    return -1;
  }

  static void type(String[] a) throws Exception {
    RimeEngine e = open(a[1], a[2], a.length > 5 ? a[5] : "");
    try (PrintStream out = new PrintStream(new File(a[4]), "UTF-8")) {
      for (String[] f : read(a[3])) {
        List<String> firsts = new ArrayList<>();
        long started = System.nanoTime();
        clear(e);
        typeKeys(e, f[1], firsts);
        long typed = System.nanoTime() - started;
        settle(e);
        String[][] rows = e.nativeInspectCandidates(PANEL);
        int rank = rankOf(e.nativeInspectCandidates(100), f[2]);
        String first = rows.length > 0 ? rows[0][0] : "";
        String finalTop = top(rows, 10);
        int picks = 0, stops = 0;
        boolean stuck = false;
        String rest = f[2];
        if (!first.equals(f[2])) {
          while (!rest.isEmpty()) {
            int i = bestPrefix(rows, rest, PANEL);
            if (i < 0) { stuck = true; break; }
            picks++;
            stops += i < VISIBLE ? 1 : 2;
            String chosen = rows[i][0];
            rest = rest.substring(chosen.length());
            if (rest.isEmpty()) break;  // last piece: counted, not committed
            // A candidate that covers all typed letters while target text is left (zsh -> 早上 for 早上好)
            // would commit and be learned, changing later cases: the person is stuck, nothing is selected.
            if (coversInput(rows[i], e.nativeGetInput()) || !e.nativeSelectCandidateByGlobalIndex(i)
                || e.nativeGetInput().isEmpty()) {
              stuck = true;
              break;
            }
            rows = e.nativeInspectCandidates(PANEL);
          }
        }
        out.println(String.join("\t", f[0], f[1], f[2], String.valueOf(rank), clean(first),
            String.valueOf(picks), String.valueOf(stops), stuck ? "1" : "0", stuck ? rest : "",
            String.join("\u001f", firsts), finalTop, String.valueOf(typed / 1000)));
        clear(e);
      }
    } finally { e.nativeDestroy(); }
  }

  /** Type and commit one stream line; returns false when the target could not be reached. */
  static boolean commit(RimeEngine e, String keys, String target, Random rng, double mispick, int[] wrong)
      throws Exception {
    typeKeys(e, keys, null);
    String rest = target;
    while (!rest.isEmpty()) {
      String[][] rows = e.nativeInspectCandidates(PANEL);
      int i = bestPrefix(rows, rest, PANEL);
      if (i < 0) { e.nativeSetInput(""); return false; }
      boolean last = rows[i][0].equals(rest);
      int j = -1;  // a neighbouring candidate over the same letters, so selecting it commits
      for (int k : new int[] {i + 1, i - 1})
        if (j < 0 && k >= 0 && k < rows.length && rows[k][0].length() == rows[i][0].length()) j = k;
      if (last && j >= 0 && rng.nextDouble() < mispick) {
        e.nativeSelectCandidateByGlobalIndex(j);
        if (!e.nativeGetInput().isEmpty()) e.nativeSetInput("");
        else wrong[0]++;
        // Deleted on screen, typed again correctly (and learned again).
        return commit(e, keys, target, rng, 0, wrong);
      }
      if (!e.nativeSelectCandidateByGlobalIndex(i)) { e.nativeSetInput(""); return false; }
      rest = rest.substring(rows[i][0].length());
      if (e.nativeGetInput().isEmpty() && !rest.isEmpty()) return false;
    }
    if (!e.nativeGetInput().isEmpty()) e.nativeSetInput("");
    return true;
  }

  static void history(String[] a) throws Exception {
    RimeEngine e = open(a[1], a[2], a[6]);
    List<String[]> stream = read(a[3]);
    List<String[]> probes = read(a[4]);
    double mispick = Double.parseDouble(a[7]);
    Random rng = new Random(Long.parseLong(a[8]));
    List<Integer> checkpoints = new ArrayList<>();
    for (String s : a[9].split(",")) checkpoints.add(Integer.parseInt(s.trim()));
    int[] wrong = {0};
    int failed = 0;
    try (PrintStream out = new PrintStream(new File(a[5]), "UTF-8")) {
      for (int pos = 0; pos <= stream.size(); ++pos) {
        if (checkpoints.contains(pos)) {
          for (String[] p : probes) {
            typeKeys(e, p[1], null);
            String[][] rows = e.nativeInspectCandidates(100);
            out.println(String.join("\t", "probe", String.valueOf(pos), p[0], p[1], p[2],
                String.valueOf(rankOf(rows, p[2])), top(rows, 5)));
            e.nativeSetInput("");
          }
          out.println("state\t" + pos + "\tmispicks=" + wrong[0] + "\tunreached=" + failed);
          out.flush();
        }
        if (pos == stream.size()) break;
        String[] s = stream.get(pos);
        if (!commit(e, s[1], s[2], rng, mispick, wrong)) failed++;
      }
    } finally { e.nativeDestroy(); }
  }

  public static void main(String[] a) throws Exception {
    if (a[0].equals("type")) type(a);
    else if (a[0].equals("history")) history(a);
    else throw new IllegalArgumentException(a[0]);
  }
}
