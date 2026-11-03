import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Common-word typo benchmark (2026-11-03, word_typo_bench.py).
 *
 * WordTypoProbe LIB USER SCHEMA CASES OUT [NEIGHBORS]
 *   Each case (id, kind, typed, target) is typed key by key on a freshly deployed user directory (nothing is
 *   committed, nothing learned). Row: id, kind, typed, target, rank while typing, rank after the pause
 *   (nativeRefineOmission, as the keyboard does 180 ms after the last key), letters, total and slowest key in
 *   microseconds (key + reading five candidates), pause time, top 3 before and after. Rank 0 = not in the first 20.
 * PROBE_OPTIONS (environment, optional): comma separated session switches, e.g. cyime_mixed_input=1.
 */
public class WordTypoProbe {
  static int rank(String[][] rows, String target) {
    for (int i = 0; i < rows.length; ++i) if (rows[i][0].equals(target)) return i + 1;
    return 0;
  }
  static String top(String[][] rows, int n) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < Math.min(n, rows.length); ++i) { if (i > 0) b.append('|'); b.append(rows[i][0]); }
    return b.toString();
  }
  public static void main(String[] a) throws Exception {
    System.load(new File(a[0], "librime_jni.so").getAbsolutePath());
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(a[1], a[1]);
    if (!e.nativeCreateSession()) throw new Exception("no session");
    if (!e.nativeSwitchSchema(a[2])) throw new Exception("no schema " + a[2]);
    e.nativeSetOption("ascii_mode", false);
    e.nativeSetNeighborMap(a.length > 5 ? a[5] : "");
    String opts = System.getenv("PROBE_OPTIONS");
    if (opts != null && !opts.isEmpty()) for (String o : opts.split(",")) { String[] kv = o.split("="); e.nativeSetOption(kv[0], kv[1].equals("1")); }
    List<String> cases = Files.readAllLines(new File(a[3]).toPath(), StandardCharsets.UTF_8);
    // Warm up so the first case does not pay for loading the prism and table.
    for (char c : "nihao".toCharArray()) e.nativeProcessKey(c, 0);
    e.nativeProcessKey(0xFF1B, 0);
    try (PrintStream out = new PrintStream(new File(a[4]), "UTF-8")) {
      for (String line : cases) {
        String[] f = line.split("\t");
        String typed = f[2], target = f[3];
        long total = 0, max = 0;
        for (char c : typed.toCharArray()) {
          long t0 = System.nanoTime();
          e.nativeProcessKey(c, 0);
          String[][] r = e.nativeInspectCandidates(5);
          long dt = System.nanoTime() - t0; total += dt; max = Math.max(max, dt);
        }
        String[][] rows = e.nativeInspectCandidates(20);
        int r0 = rank(rows, target);
        String t0s = top(rows, 3);
        long p0 = System.nanoTime();
        boolean changed = e.nativeRefineOmission();
        long pause = System.nanoTime() - p0;
        String[][] rows2 = changed ? e.nativeInspectCandidates(20) : rows;
        int r1 = rank(rows2, target);
        out.println(f[0] + "\t" + f[1] + "\t" + typed + "\t" + target + "\t" + r0 + "\t" + r1 + "\t" + typed.length() + "\t" +
            total / 1000 + "\t" + max / 1000 + "\t" + pause / 1000 + "\t" + t0s + "\t" + top(rows2, 3));
        e.nativeProcessKey(0xFF1B, 0); // Escape clears composition
      }
    }
    e.nativeDestroy();
  }
}
