import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** args: libDir userDir cases.tsv. Each case: schema TAB keys TAB expected TAB label. Prints rank of expected. */
public class ArkProbe {
  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    RimeEngine engine = new RimeEngine();
    engine.nativeInitialize(args[1], args[1]);
    try {
      engine.nativeEnsureT9SchemaPatches("t9_pinyin");
      engine.nativeStartMaintenance(true);
      long until = System.currentTimeMillis() + 900000;
      while (engine.nativeIsMaintaining()) {
        if (System.currentTimeMillis() > until) throw new Exception("deploy_timeout");
        Thread.sleep(100);
      }
      if (!engine.nativeCreateSession()) throw new Exception("session_failed");
      List<String> cases = Files.readAllLines(new File(args[2]).toPath(), StandardCharsets.UTF_8);
      String current = "";
      for (String line : cases) {
        if (line.isEmpty()) continue;
        String[] c = line.split("\t");
        if (!c[0].equals(current)) {
          if (!engine.nativeSwitchSchema(c[0])) throw new Exception("schema_unavailable=" + c[0]);
          engine.nativeSetOption("ascii_mode", false);
          engine.nativeSetOption("full_shape", false);
          current = c[0];
        }
        boolean t9 = c[0].equals("t9_pinyin");
        if (t9) { engine.nativeT9ClearComposition(1); engine.nativeT9FlushRimeInput(); }
        for (char k : c[1].toCharArray()) {
          engine.nativeProcessKey(k, 0);
          if (t9) engine.nativeT9FlushRimeInput();
        }
        if (t9) {
          long start = System.currentTimeMillis();
          while (engine.nativeT9RefinementState() == 1 && System.currentTimeMillis() - start < 3000) Thread.sleep(10);
          if (engine.nativeT9RefinementState() == 2) engine.nativeRefineT9Sentences();
        }
        String[][] rows = engine.nativeInspectCandidates(30);
        int rank = -1;
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < rows.length; i++) {
          if (rank < 0 && rows[i][0].equals(c[2])) rank = i + 1;
          if (i < 3) top.append(i == 0 ? "" : "/").append(rows[i][0]);
        }
        System.out.println("CASE\t" + c[3] + "\t" + c[0] + "\t" + c[2] + "\t" + rank + "\t" + top);
        if (t9) { engine.nativeT9ClearComposition(1); engine.nativeT9FlushRimeInput(); }
        else engine.nativeProcessKey(0xff1b, 0);
      }
    } finally { engine.nativeDestroy(); }
  }
}
