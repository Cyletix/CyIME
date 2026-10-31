import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Typed-letter typos in whole 26-key sentences (2026-10-31). Arguments: the built library directory, a fresh
 * test user directory listing rime_ice (never an installed app's data), a cases TSV (id, input, target) from
 * typo_bench.py, the output TSV, and the measured neighbour map (typo_bench.py neighbors). Each case is typed
 * key by key; a row records the per-key time, the first 10 candidates, the target's place (-1 when absent) and,
 * with a host build (CYIME_T9_WORK_COUNTERS), the per-key dictionary work (all counters but cache hits, summed).
 */
public final class TypoSentenceProbe {
  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    File user = new File(args[1]);
    List<String> cases = Files.readAllLines(new File(args[2]).toPath(), StandardCharsets.UTF_8);
    String neighbors = args.length > 4 ? args[4] : "";
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(user.getPath(), user.getPath());
    try (PrintStream out = new PrintStream(new File(args[3]), "UTF-8")) {
      if (!new File(user, "build/rime_ice.schema.yaml").isFile()) {
        e.nativeStartMaintenance(true);
        while (e.nativeIsMaintaining()) Thread.sleep(100);
      }
      if (!e.nativeCreateSession()) throw new Exception("session unavailable");
      if (!e.nativeSwitchSchema("rime_ice")) throw new Exception("schema unavailable");
      e.nativeSetOption("ascii_mode", false);
      e.nativeSetNeighborMap(neighbors);
      String[] names = null;
      try { names = e.nativeHostWorkCounterNames(); } catch (UnsatisfiedLinkError ignored) { }
      for (String line : cases) {
        if (line.isEmpty() || line.startsWith("#")) continue;
        String[] f = line.split("\t");
        StringBuilder times = new StringBuilder();
        StringBuilder work = new StringBuilder();
        if (names != null) e.nativeHostTakeWorkCounters();
        for (char c : f[1].toCharArray()) {
          long started = System.nanoTime();
          if (!e.nativeProcessKey(c, 0)) throw new Exception("unhandled key " + c + " in " + f[1]);
          e.nativeInspectCandidates(10);
          if (times.length() > 0) times.append(',');
          times.append((System.nanoTime() - started) / 1000);
          if (names != null) {
            long[] counts = e.nativeHostTakeWorkCounters();
            long sum = 0;
            for (int i = 0; i < counts.length; ++i)
              if (!names[i].contains("hits") && !names[i].contains("reuse")) sum += counts[i];
            if (work.length() > 0) work.append(',');
            work.append(sum);
          }
        }
        if (!e.nativeGetInput().equals(f[1])) throw new Exception("input changed: " + e.nativeGetInput());
        String[][] rows = e.nativeInspectCandidates(100);
        int rank = -1;
        for (int i = 0; i < rows.length; ++i) if (rows[i][0].equals(f[2])) { rank = i; break; }
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(rows.length, 10); ++i) {
          if (i > 0) top.append('|');
          top.append(rows[i][0]);
        }
        out.println(f[0] + "\t" + f[1] + "\t" + f[2] + "\t" + rank + "\t" + times + "\t" + top + "\t" + work);
        e.nativeSetInput("");
      }
    } finally { e.nativeDestroy(); }
  }
}
