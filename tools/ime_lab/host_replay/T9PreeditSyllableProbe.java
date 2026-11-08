import com.kingzcheung.xime.rime.RimeEngine;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Read-only: the nine-key code bar after every key of the fixture sentences. Flags a reading with digits left,
 * a letter count other than the key count, or a segment that is no syllable (or prefix) of the fixtures,
 * i.e. syllables run together or a syllable gone. Arguments: library_dir, a disposable user directory and
 * the fixture JSON files; SEED=FILE restores a preset *.userdb.txt first. The syllable list comes from the
 * fixtures only, so syllables they never use are flagged too: compare two engines' outputs.
 * Compile and run together with T9MixedLearnProbe.java and ../android_replay/RimeEngine.java.
 */
public final class T9PreeditSyllableProbe {
  public static void main(String[] a) throws Exception {
    System.load(new File(a[0], "librime_jni.so").getAbsolutePath());
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(a[1], a[1]);
    e.nativeEnsureT9SchemaPatches("t9_pinyin");
    if (System.getenv("SEED") != null) e.nativeHostRestoreUserDict(System.getenv("SEED"), "rime_ice");
    e.nativeCreateSession();
    Pattern p = Pattern.compile("\"text\":\\s*\"([^\"]+)\",\\s*\"pinyin\":\\s*\\[([^\\]]*)\\]");
    List<String[]> cases = new ArrayList<>();
    Set<String> syl = new HashSet<>();
    for (int i = 2; i < a.length; i++) {
      Matcher m = p.matcher(new String(Files.readAllBytes(Paths.get(a[i])), "UTF-8"));
      while (m.find()) {
        String[] s = m.group(2).replaceAll("[\"\\s]", "").split(",");
        syl.addAll(Arrays.asList(s));
        cases.add(new String[] {m.group(1), String.join("", s)});
      }
    }
    Set<String> prefixes = new HashSet<>();
    for (String s : syl) for (int k = 1; k <= s.length(); k++) prefixes.add(s.substring(0, k));
    T9MixedLearnProbe.schema = "t9_pinyin";
    e.nativeSwitchSchema("t9_pinyin");
    e.nativeSetOption("ascii_mode", false);
    int steps = 0, bad = 0;
    for (String[] c : cases) {
      String d = T9MixedLearnProbe.digits(c[1]);
      e.nativeT9ClearComposition(1); e.nativeT9FlushRimeInput();
      for (char k : d.toCharArray()) {
        e.nativeProcessKey(k, 0); e.nativeT9FlushRimeInput();
        String pre = e.nativePinyinEditSnapshot()[4];
        String in = e.nativePinyinEditSnapshot()[0];
        steps++;
        String why = null;
        if (pre.chars().anyMatch(Character::isDigit)) why = "digits";
        else {
          String letters = pre.replaceAll("[ ']", "");
          if (letters.length() != in.length()) why = "length " + letters.length() + "/" + in.length();
          for (String seg : pre.split("[ ']+")) if (!seg.isEmpty() && !prefixes.contains(seg)) why = "segment " + seg;
        }
        if (why != null) { bad++; if (bad <= 80) System.out.println(c[0] + "\t" + in + "\t'" + pre + "'\t" + why); }
      }
    }
    System.out.println("steps " + steps + " flagged " + bad);
    e.nativeDestroy();
  }
}
