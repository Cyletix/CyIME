import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;

/** Run with an empty, deployed full-pinyin fixture and the public word model. */
public final class SentenceParticleProbe {
  private static void check(RimeEngine engine, String keys, String... parts) {
    if (!engine.nativeGetInput().isEmpty()) throw new AssertionError("leftover composition");
    for (char key : keys.toCharArray()) {
      if (!engine.nativeProcessKey(key, 0)) throw new AssertionError("unhandled " + key);
    }
    StringBuilder committed = new StringBuilder();
    for (String part : parts) {
      // The shortened particle should be visible beside the two normal first choices.
      int limit = part.equals("呢") ? 3 : 10;
      String[][] candidates = engine.nativeInspectCandidates(limit);
      int index = 0;
      while (index < candidates.length && !part.equals(candidates[index][0])) ++index;
      if (index == candidates.length || !engine.nativeSelectCandidateByGlobalIndex(index)) {
        throw new AssertionError(keys + " missing " + part + " in the first " + limit);
      }
      String text = engine.nativeCommit();
      if (text != null) committed.append(text);
    }
    String expected = String.join("", parts);
    if (!expected.contentEquals(committed) || !engine.nativeGetInput().isEmpty()) {
      throw new AssertionError(keys + " committed " + committed + ", expected " + expected);
    }
  }

  private static void punctuation(RimeEngine engine, char key, String expected) {
    if (!engine.nativeProcessKey(key, 0) || !expected.equals(engine.nativeCommit())) {
      throw new AssertionError("could not commit " + expected);
    }
  }

  public static void main(String[] args) {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    RimeEngine engine = new RimeEngine();
    engine.nativeInitialize(args[1], args[1]);
    try {
      if (!engine.nativeCreateSession() || !engine.nativeSwitchSchema("rime_ice")) {
        throw new AssertionError("full-pinyin session unavailable");
      }
      engine.nativeSetOption("ascii_mode", false);
      check(engine, "tazaidengchen", "他在", "等车", "呢");
      check(engine, "nixianzaizaixiangxieshenmen", "你现在在想些什么呢");
      check(engine, "chen", "晨");
      check(engine, "wogangcaishuodenaliangcheyijingdaole", "我刚才说的那辆车已经到了");
      punctuation(engine, ',', "，");
      check(engine, "enwozhidaonideyisile", "嗯我知道你的意思了");
      punctuation(engine, '.', "。");
      if (!engine.nativeSwitchSchema("pinyin_14jian")) throw new AssertionError("14-key unavailable");
      engine.nativeSetOption("ascii_mode", false);
      check(engine, "EBGAUTOUCGEmA", "嗯", "还有车吗");
      check(engine, "CGEmA", "车马");
      System.out.println("PASS");
    } finally {
      engine.nativeDestroy();
    }
  }
}
