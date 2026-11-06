import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模糊音只多给读音，不抢打准的字（2026-11-05：开 n/l 后 ne 首选「了」、neng 首选「冷」）。
 * Arguments: LIB USER NEIGHBORS [PLAIN_FIRSTS_OUT]. USER is a disposable copy of a deployed rime_ice user directory: this probe
 * writes rime_ice.custom.yaml with every fuzzy group the app offers (penalized fuzz/ rules before the scheme's own
 * algebra, as SpellingPatches writes them) and deploys it. Every single syllable of the bundled 8105 dictionary
 * is typed with the groups off and on: a first word spelled by the typed letters must stay first with them on.
 * The fuzzy readings must still come right after (ne still offers 了 in the first three).
 */
public class FuzzyExactFirstProbe {
  static final String[] FUZZY = {
      "fuzz/^zh/z/", "fuzz/^z([^h])/zh$1/", "fuzz/^ch/c/", "fuzz/^c([^h])/ch$1/", "fuzz/^sh/s/", "fuzz/^s([^h])/sh$1/",
      "fuzz/^n/l/", "fuzz/^l/n/", "fuzz/^f/h/", "fuzz/^h/f/", "fuzz/^r/l/", "fuzz/^l/r/",
      "fuzz/(^|[^iu])ang$/$1an/", "fuzz/(^|[^iu])an$/$1ang/", "fuzz/eng$/en/", "fuzz/en$/eng/",
      "fuzz/ing$/in/", "fuzz/in$/ing/", "fuzz/iang$/ian/", "fuzz/ian$/iang/", "fuzz/uang$/uan/", "fuzz/uan$/uang/"};

  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    File user = new File(args[1]);
    File customFile = new File(user, "rime_ice.custom.yaml");
    List<String> inputs = new ArrayList<>(syllables(new File(user, "cn_dicts/8105.dict.yaml")));
    List<String> algebra = algebra(new File(user, "rime_ice.schema.yaml"));
    // First pass without fuzzy pinyin: the first word of each syllable as the user gets it today.
    Files.deleteIfExists(customFile.toPath());
    Map<String, String[]> plain = run(user, args[2], inputs, null);
    // Optional fourth argument: the first word of each syllable without fuzzy pinyin, to compare engines.
    if (args.length > 3) {
      StringBuilder dump = new StringBuilder();
      for (Map.Entry<String, String[]> e : plain.entrySet())
        dump.append(e.getKey()).append('\t').append(e.getValue()[0]).append('\n');
      Files.write(new File(args[3]).toPath(), dump.toString().getBytes(StandardCharsets.UTF_8));
    }
    StringBuilder custom = new StringBuilder("patch:\n  \"speller/algebra\":\n");
    List<String> rules = new ArrayList<>(Arrays.asList(FUZZY));
    rules.addAll(algebra);
    for (String rule : rules)
      custom.append("    - \"").append(rule.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"\n");
    Files.write(customFile.toPath(), custom.toString().getBytes(StandardCharsets.UTF_8));
    List<String> failures = new ArrayList<>();
    Map<String, String[]> fuzzy = run(user, args[2], inputs, failures);
    int checked = 0, same = 0;
    for (String input : inputs) {
      String[] before = plain.get(input), after = fuzzy.get(input);
      // Only syllables whose first word is spelled by the typed letters without fuzzy pinyin (e gives 嗯 by
      // abbreviation either way and is not this rule's business).
      if (before == null || after == null || !typed(before, input)) continue;
      // den is also deng typed one letter short: the engine keeps 等 as its completion, ahead of the rare 扽.
      if (input.equals("den")) continue;
      ++checked;
      if (before[0].equals(after[0])) ++same;
      else failures.add(input + " first " + before[0] + " became " + after[0] + "(" + after[1] + ")");
    }
    System.out.println("SYLLABLES checked=" + checked + " same_first=" + same);
    for (String failure : failures) System.out.println("FAIL " + failure);
    System.out.println(failures.isEmpty() ? "PASS" : "FAILED " + failures.size());
    if (!failures.isEmpty()) System.exit(1);
  }

  /** Deploys USER as it is now and returns each input's first row; with [failures] also checks the reported words. */
  static Map<String, String[]> run(File user, String neighbors, List<String> inputs, List<String> failures)
      throws Exception {
    Map<String, String[]> first = new LinkedHashMap<>();
    RimeEngine engine = new RimeEngine();
    engine.nativeInitialize(user.getPath(), user.getPath());
    engine.nativeStartMaintenance(true);
    while (engine.nativeIsMaintaining()) Thread.sleep(100);
    try {
      if (!engine.nativeCreateSession() || !engine.nativeSwitchSchema("rime_ice")) throw new Exception("schema unavailable");
      engine.nativeSetOption("ascii_mode", false);
      engine.nativeSetNeighborMap(neighbors);
      long micros = 0;
      for (String input : inputs) {
        long started = System.nanoTime();
        for (char c : input.toCharArray()) engine.nativeProcessKey(c, 0);
        String[][] rows = engine.nativeInspectCandidates(1);
        micros += (System.nanoTime() - started) / 1000;
        engine.nativeProcessKey(0xff1b, 0);
        if (rows.length > 0) first.put(input, rows[0]);
      }
      System.out.println((failures == null ? "PLAIN" : "FUZZY") + " avg_us=" + micros / Math.max(1, inputs.size()));
      if (failures == null) return first;
      // The reported words; the second word is the fuzzy one that must still be offered in the first three.
      // nu stays 女: the scheme's own nu for nv is how ü is typed, not a fuzzy sound.
      String[][] fixed = {{"ne", "呢", "了"}, {"neng", "能", "冷"}, {"le", "了", null}, {"leng", "冷", null},
                          {"nu", "女", null}};
      for (String[] c : fixed) {
        for (char ch : c[0].toCharArray()) engine.nativeProcessKey(ch, 0);
        String[][] rows = engine.nativeInspectCandidates(3);
        engine.nativeProcessKey(0xff1b, 0);
        List<String> top = new ArrayList<>();
        for (String[] row : rows) top.add(row[0]);
        System.out.println("FIXED " + c[0] + " => " + top);
        if (top.isEmpty() || !top.get(0).equals(c[1])) failures.add(c[0] + " first should be " + c[1] + ": " + top);
        if (c[2] != null && !top.contains(c[2])) failures.add(c[0] + " lost fuzzy " + c[2] + ": " + top);
      }
      return first;
    } finally {
      engine.nativeDestroy();
    }
  }

  /** A word read with exactly the typed letters: its pinyin comment is the input, and it is no correction. */
  static boolean typed(String[] row, String input) {
    return row.length > 9 && row[1].replace(" ", "").equals(input) && row[9].isEmpty() &&
        !row[0].codePoints().anyMatch(cp -> cp < 0x3400);
  }

  static List<String> algebra(File schema) throws Exception {
    List<String> rules = new ArrayList<>();
    boolean inside = false;
    for (String raw : Files.readAllLines(schema.toPath(), StandardCharsets.UTF_8)) {
      String line = raw.replace("\t", "  ");
      if (line.matches("^  algebra:\\s*$")) { inside = true; continue; }
      if (!inside) continue;
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
      if (!trimmed.startsWith("- ") || !line.startsWith("    ")) break;
      String rule = trimmed.substring(2).replaceAll("\\s+#.*$", "").trim();
      if (rule.startsWith("\"") && rule.endsWith("\"")) rule = rule.substring(1, rule.length() - 1).replace("\\\\", "\\");
      rules.add(rule);
    }
    if (rules.isEmpty()) throw new Exception("no speller/algebra in " + schema);
    return rules;
  }

  static Set<String> syllables(File dict) throws Exception {
    Set<String> out = new LinkedHashSet<>();
    boolean body = false;
    for (String line : Files.readAllLines(dict.toPath(), StandardCharsets.UTF_8)) {
      if (line.equals("...")) { body = true; continue; }
      if (!body || line.startsWith("#")) continue;
      String[] cols = line.split("\t");
      if (cols.length > 1 && cols[1].matches("[a-z]+")) out.add(cols[1]);
    }
    return out;
  }
}
