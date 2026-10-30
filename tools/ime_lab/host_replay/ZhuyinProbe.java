import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;

/**
 * 注音（大千）, cyime_zhuyin (2026-10-30). Arguments: the built library directory and a fresh test user directory
 * whose schema_list includes cyime_zhuyin, with assets/rime_chinese copied in (cyime_zhuyin.schema.yaml,
 * cyime_tw.dict.yaml, opencc/). Keys are the 大千 ASCII codes ImeKeyRouter sends for the bopomofo key caps
 * (ZhuyinKeys): ㄅ=1 ㄆ=q ㄇ=a …, tones ˊ=6 ˇ=3 ˋ=4 ˙=7 and the space for the first tone.
 */
public final class ZhuyinProbe {
  static final String SYMBOLS = "ㄅㄉˇˋㄓˊ˙ㄚㄞㄢㄦㄆㄊㄍㄐㄔㄗㄧㄛㄟㄣㄇㄋㄎㄑㄕㄘㄨㄜㄠㄤㄈㄌㄏㄒㄖㄙㄩㄝㄡㄥ";
  static final String CODES = "1234567890-qwertyuiopasdfghjkl;zxcvbnm,./";
  /** {bopomofo as typed (ˉ = space), expected candidate, rank it must reach} */
  static final String[][] CASES = {
    {"ㄋㄧˇㄏㄠˇ", "你好", "1"}, {"ㄋㄧㄏㄠ", "你好", "1"}, {"ㄋㄏ", "你好", "5"},
    {"ㄊㄚˉ", "他", "3"}, {"ㄊㄞˊㄨㄢˉ", "台灣", "1"}, {"ㄊㄞㄨㄢ", "台灣", "1"},
    {"ㄐㄧˋㄏㄨㄚˋ", "計畫", "1"}, {"ㄌㄧㄠˇㄐㄧㄝˇ", "了解", "1"}, {"ㄊㄡˊㄈㄚˇ", "頭髮", "1"},
    // Taiwan readings and words from cyime_tw (McBopomofo)
    {"ㄌㄜˋㄙㄜˋ", "垃圾", "1"}, {"ㄕㄨㄟˋㄈㄨˊ", "說服", "1"}, {"ㄓㄢˋㄕˊ", "暫時", "1"},
    {"ㄐㄧㄢˋㄅㄠˇ", "健保", "1"}, {"ㄌㄧˋㄨㄟˇ", "立委", "1"}, {"ㄅㄠㄍㄨㄚˋ", "包括", "1"},
    // the mainland reading still types
    {"ㄌㄚㄐㄧ", "垃圾", "3"},
    // sentences
    {"ㄨㄛˇㄇㄣ˙ㄒㄧㄢˋㄗㄞˋㄑㄩˋㄔˉㄈㄢˋ", "我們現在去吃飯", "1"},
    {"ㄐㄧㄣㄊㄧㄢㄊㄧㄢㄑㄧˋㄏㄣˇㄏㄠˇ", "今天天氣很好", "1"},
  };
  static int failures = 0;

  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    File user = new File(args[1]);
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(user.getPath(), user.getPath());
    try {
      if (!new File(user, "build/cyime_zhuyin.schema.yaml").isFile()) maintain(e);
      if (!e.nativeCreateSession()) throw new Exception("session unavailable");
      if (!e.nativeSwitchSchema("cyime_zhuyin")) throw new Exception("schema unavailable: cyime_zhuyin");
      e.nativeSetOption("ascii_mode", false);
      for (String[] c : CASES) {
        e.nativeSetInput("");
        String keys = keys(c[0]);
        boolean typed = true;
        for (char k : keys.toCharArray()) typed &= e.nativeProcessKey(k, 0);
        String[][] rows = e.nativeInspectCandidates(10);
        int rank = Integer.parseInt(c[2]);
        StringBuilder head = new StringBuilder();
        boolean found = false;
        for (int i = 0; i < Math.min(rows.length, 5); i++) {
          head.append(rows[i][0]).append(' ');
          if (i < rank && rows[i][0].equals(c[1])) found = true;
        }
        String[] snapshot = e.nativePinyinEditSnapshot();
        check(c[0] + " [" + keys + "] preedit " + snapshot[4] + " → " + c[1] + " in top " + rank + ": " + head, typed && found);
      }
      // A tone only follows a syllable. A second tone right after one is not spelling either: Rime would commit the
      // composition on it, so ImeKeyRouter drops it before it reaches Rime (ZhuyinKeys.acceptsTone).
      e.nativeSetInput("");
      check("tone with nothing typed is not taken", !e.nativeProcessKey('3', 0));
      e.nativeSetInput("");
      e.nativeProcessKey('s', 0); e.nativeProcessKey('u', 0);
      e.nativeProcessKey(' ', 0);
      check("space after ㄋㄧ is the first tone: input " + e.nativeGetInput(), e.nativeGetInput().equals("su "));
      e.nativeSetInput("");
    } finally { e.nativeDestroy(); }
    System.out.println(failures == 0 ? "PASS" : "FAIL " + failures);
    if (failures > 0) System.exit(1);
  }

  static String keys(String bopomofo) {
    StringBuilder out = new StringBuilder();
    for (char c : bopomofo.toCharArray()) out.append(c == 'ˉ' ? ' ' : CODES.charAt(SYMBOLS.indexOf(c)));
    return out.toString();
  }

  static void check(String what, boolean ok) {
    if (!ok) failures++;
    System.out.println((ok ? "  ok   " : "  FAIL ") + what);
  }

  static void maintain(RimeEngine e) throws Exception {
    e.nativeStartMaintenance(true);
    long until = System.currentTimeMillis() + 1200000;
    while (e.nativeIsMaintaining()) {
      if (System.currentTimeMillis() > until) throw new Exception("maintenance timeout");
      Thread.sleep(100);
    }
  }
}
