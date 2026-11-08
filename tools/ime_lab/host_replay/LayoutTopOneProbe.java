import com.kingzcheung.xime.rime.RimeEngine;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Read-only: first candidate and rank (within 30) of fixture sentences on 9/14/26 keys, one TSV row each.
 * Arguments: library_dir, a disposable user directory, a preset *.userdb.txt or "-", the output TSV and the
 * fixture JSON files ("text" + "pinyin"). Compare two engines' TSVs to see which sentences changed.
 * Compile and run together with T9MixedLearnProbe.java and ../android_replay/RimeEngine.java.
 */
public final class LayoutTopOneProbe {
  public static void main(String[] a) throws Exception {
    System.load(new File(a[0], "librime_jni.so").getAbsolutePath());
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(a[1], a[1]);
    e.nativeEnsureT9SchemaPatches("t9_pinyin");
    if (!a[2].equals("-")) e.nativeHostRestoreUserDict(a[2], "rime_ice");
    e.nativeCreateSession();
    List<String[]> cases = new ArrayList<>();
    Pattern p = Pattern.compile("\"text\":\\s*\"([^\"]+)\",\\s*\"pinyin\":\\s*\\[([^\\]]*)\\]");
    for (int i = 4; i < a.length; i++) {
      Matcher m = p.matcher(new String(Files.readAllBytes(Paths.get(a[i])), "UTF-8"));
      while (m.find()) cases.add(new String[] {m.group(1), m.group(2).replaceAll("[\"\\s,]", "")});
    }
    PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(a[3]), "UTF-8"));
    for (String schema : new String[] {"t9_pinyin", "pinyin_14jian", "rime_ice"}) {
      T9MixedLearnProbe.schema = schema;
      e.nativeSwitchSchema(schema);
      e.nativeSetOption("ascii_mode", false);
      for (String[] c : cases) {
        T9MixedLearnProbe.type(e, T9MixedLearnProbe.digits(c[1]));
        String[][] r = e.nativeInspectCandidates(30);
        out.println(schema + "\t" + c[0] + "\t" + (r.length > 0 ? r[0][0] : "") + "\t" + T9MixedLearnProbe.rank(r, c[0]));
      }
    }
    out.close();
    e.nativeDestroy();
  }
}
