import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Run only in an isolated fixture: the optional learn stage writes synthetic personal entries. */
public class PinyinCorrectionProbe {
  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    RimeEngine engine = new RimeEngine();
    engine.nativeInitialize(args[1], args.length > 3 ? args[3] : args[1]);
    try {
      if (!engine.nativeCreateSession()) throw new Exception("session unavailable");
      if (args.length > 2 && args[2].equals("learn")) {
        engine.nativeSwitchSchema("rime_ice");
        engine.nativeSetOption("ascii_mode", false);
        engine.nativeSetNeighborMap("");
        for (int i = 0; i < 30; ++i) {
          for (char c : "bawentishi".toCharArray()) engine.nativeProcessKey(c, 0);
          engine.nativeProcessKey(' ', 0);
        }
        System.out.println("Seeded 30 synthetic commits of bawentishi");
      }
      List<String> failures = new ArrayList<>();
      for (String neighbors : new String[] {"", "n:bmjh;b:nvgh;m:njk;w:qase;t:rfgy;i:uojk;s:awedh"}) {
        if (!engine.nativeSwitchSchema("rime_ice")) throw new Exception("schema unavailable");
        engine.nativeSetOption("ascii_mode", false);
        engine.nativeSetNeighborMap(neighbors);
        for (String input : new String[] {"nawentishi", "nawenti", "bawentishi", "nihao", "nihso"}) {
          int length = 0;
          for (char c : input.toCharArray()) {
            long started = System.nanoTime();
            if (!engine.nativeProcessKey(c, 0)) throw new Exception("unhandled key " + c);
            if (!engine.nativeGetInput().equals(input.substring(0, ++length)))
              throw new Exception("input changed: " + engine.nativeGetInput());
            String[][] step = engine.nativeInspectCandidates(3);
            System.out.println("STEP " + input.substring(0, length) + " us=" +
                ((System.nanoTime() - started) / 1000) + " => " +
                Arrays.deepToString(step));
          }
          String[][] rows = engine.nativeInspectCandidates(100);
          System.out.println("neighbors=" + !neighbors.isEmpty() + " " + input + " raw=" + engine.nativeGetInput());
          for (int i = 0; i < Math.min(rows.length, 10); ++i) System.out.println(i+" "+Arrays.toString(rows[i]));
          String expected = input.equals("nawentishi") ? "那问题是" :
              input.equals("nawenti") ? "那问题" :
              input.equals("bawentishi") ? "把问题是" : "你好";
          int target = -1;
          for (int i = 0; i < rows.length; ++i) if (rows[i][0].equals(expected)) { target = i; break; }
          System.out.println("TARGET " + expected + " RANK=" + target);
          if (input.equals("nihso")) {
            if (!neighbors.isEmpty() && (target < 0 || target > 4)) failures.add("correction lost: nihso");
          } else if (target != 0) failures.add("neighbors=" + !neighbors.isEmpty() + " " + input + " rank=" + target);
          engine.nativeProcessKey(0xff1b, 0);
        }
      }
      Map<String, String> exactSentences = new LinkedHashMap<>();
      for (String input : new String[] {"nawentishishenme", "nawentizainali", "nihaonawenti", "zhegewentishi"}) {
        engine.nativeSetNeighborMap("");
        for (char c : input.toCharArray()) engine.nativeProcessKey(c, 0);
        String[][] rows = engine.nativeInspectCandidates(1);
        if (rows.length == 0) throw new AssertionError("missing reference sentence: " + input);
        exactSentences.put(input, rows[0][0]);
        engine.nativeProcessKey(0xff1b, 0);
      }
      engine.nativeSetNeighborMap("n:bmjh;b:nvgh;m:njk;w:qase;t:rfgy;i:uojk;s:awedh");
      for (Map.Entry<String, String> test : exactSentences.entrySet()) {
        for (char c : test.getKey().toCharArray()) engine.nativeProcessKey(c, 0);
        String[][] rows = engine.nativeInspectCandidates(100);
        int rank = -1;
        for (int i = 0; i < rows.length; ++i) if (rows[i][0].equals(test.getValue())) { rank = i; break; }
        System.out.println("EXTENDED " + test.getKey() + " exact=" + test.getValue() + " rank=" + rank);
        System.out.println("EXTENDED_ROWS " + Arrays.deepToString(Arrays.copyOf(rows, Math.min(rows.length, 5))));
        if (rank != 0) failures.add("exact sentence changed: " + test.getKey() + " rank=" + rank);
        engine.nativeProcessKey(0xff1b, 0);
      }
      // Abbreviations that spell a whole phrase keep it first even when corrected
      // spellings of the same length weigh more (hzp: 化妆品, not 或作品).
      engine.nativeSetNeighborMap("h:yugjvbn;z:asdx;p:ol;d:ersfzxc");
      for (String[] test : new String[][] {{"hzp", "化妆品"}, {"zdh", "自动化"}}) {
        for (char c : test[0].toCharArray()) engine.nativeProcessKey(c, 0);
        String[][] abbreviated = engine.nativeInspectCandidates(5);
        System.out.println("ABBREVIATION " + test[0] + " " + Arrays.deepToString(abbreviated));
        if (abbreviated.length == 0 || !abbreviated[0][0].equals(test[1]))
          failures.add("abbreviated phrase displaced: " + test[0]);
        engine.nativeProcessKey(0xff1b, 0);
      }
      if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
      System.out.println("PASS exact spelling, learned phrase, typo correction and every input prefix");
    } finally { engine.nativeDestroy(); }
  }
}
