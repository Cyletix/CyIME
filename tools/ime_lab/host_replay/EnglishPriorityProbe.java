import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.util.Arrays;

/** Isolated production-engine probe; no personal dictionary is learned or copied. */
public class EnglishPriorityProbe {
  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    RimeEngine engine = new RimeEngine();
    engine.nativeInitialize(args[1], args[1]);
    try {
      if (!engine.nativeCreateSession()) throw new Exception("session unavailable");
      if (!engine.nativeSwitchSchema("rime_ice")) throw new Exception("schema unavailable");
      engine.nativeSetOption("ascii_mode", false);
      engine.nativeSetNeighborMap("");
      for (String input : new String[]{"fuck", "hello", "thanks", "world", "please", "computer", "you", "shi", "nihao", "fuchanke"}) {
        for (char c : input.toCharArray()) engine.nativeProcessKey(c, 0);
        String[][] rows = engine.nativeInspectCandidates(400);
        int rank = -1;
        for (int i = 0; i < rows.length; i++) if (rows[i][0].equals(input)) { rank = i; break; }
        System.out.println(input + " exactRank=" + rank + " count=" + rows.length + " head=" +
            Arrays.deepToString(Arrays.copyOf(rows, Math.min(8, rows.length))));
        engine.nativeProcessKey(0xff1b, 0);
      }
    } finally { engine.nativeDestroy(); }
  }
}
