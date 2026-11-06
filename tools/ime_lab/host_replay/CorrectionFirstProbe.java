import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 常用纠错词排第一（2026-11-05）：纠错得到的整串词比所打字母读出的词常用 50 倍以上时排第一；
 * 不到 50 倍、或所打字母读出的是整句时，所打的读法照旧第一。
 * Arguments: LIB USER NEIGHBORS. USER: a deployed rime_ice user directory (nothing is committed).
 * Each row: input, the expected first candidate, and why. Typed after the pause, as the keyboard shows them.
 */
public class CorrectionFirstProbe {
  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    RimeEngine engine = new RimeEngine();
    engine.nativeInitialize(args[1], args[1]);
    List<String> failures = new ArrayList<>();
    try {
      if (!engine.nativeCreateSession() || !engine.nativeSwitchSchema("rime_ice")) throw new Exception("schema unavailable");
      engine.nativeSetOption("ascii_mode", false);
      engine.nativeSetNeighborMap(args[2]);
      String[][] cases = {
          {"chengong", "成功", "doubled g typed once; 成功 is thousands of times 陈宫"},
          {"jinian", "几年", "今年 is only 2.9 times 几年"},
          {"aniu", "阿牛", "按钮 is not 50 times 阿牛"},
          {"henan", "河南", "a typed word that is common itself"},
          {"jiucup", "就粗平", "a typed sentence keeps its place"},
          {"nihao", "你好", "correct spelling"},
          {"ao", "奥", "a single syllable keeps its typed character (not 我)"},
          {"ga", "尬", "a single syllable keeps its typed character (not 他)"},
          {"den", "扽", "a single syllable keeps its typed character (not 人; 的呢 is read first here)"},
          {"shangshigongsi", "上市公司", "must-pass"},
          {"bangwonagekuaidi", "帮我拿个快递", "must-pass"},
      };
      // Shorter words that break the input where the first word does come right after it (2026-11-06).
      String[][] second = {{"kejishuip", "科技"}, {"womenzoub", "我们走"}};
      for (String[] c : second) {
        for (char ch : c[0].toCharArray()) engine.nativeProcessKey(ch, 0);
        engine.nativeRefineOmission();
        String[][] rows = engine.nativeInspectCandidates(2);
        engine.nativeProcessKey(0xff1b, 0);
        System.out.println(c[0] + " second => " + (rows.length > 1 ? rows[1][0] : "-"));
        if (rows.length < 2 || !rows[1][0].equals(c[1])) failures.add(c[0] + " second should be " + c[1]);
      }
      for (String[] c : cases) {
        for (char ch : c[0].toCharArray()) engine.nativeProcessKey(ch, 0);
        engine.nativeRefineOmission();
        String[][] rows = engine.nativeInspectCandidates(3);
        engine.nativeProcessKey(0xff1b, 0);
        List<String> top = new ArrayList<>();
        for (String[] row : rows) top.add(row[0]);
        System.out.println(c[0] + " => " + top);
        if (top.isEmpty() || !top.get(0).equals(c[1])) failures.add(c[0] + " first should be " + c[1] + " (" + c[2] + "): " + top);
      }
    } finally {
      engine.nativeDestroy();
    }
    for (String failure : failures) System.out.println("FAIL " + failure);
    System.out.println(failures.isEmpty() ? "PASS" : "FAILED " + failures.size());
    if (!failures.isEmpty()) System.exit(1);
  }
}
