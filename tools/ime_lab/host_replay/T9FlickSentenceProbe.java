import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.util.Arrays;

/**
 * Nine-key flick letters typed for a whole sentence (2026-11-03 report): with no digits in the T9 buffer,
 * each flicked letter goes to Rime as a plain letter. Choosing the first word used to count as the whole
 * sentence, so the rest of the code vanished. Now the chosen word takes only its letters and the rest
 * stays; deleting the rest and one more backspace brings the chosen word's letters back.
 * Arguments: the built library directory and a separate test user directory (as T9PartialClearProbe).
 */
public final class T9FlickSentenceProbe {
  static final String SCHEMA = "t9_pinyin";

  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    File logs = new File(args[1], "logs");
    if (!logs.isDirectory() && !logs.mkdirs()) throw new Exception("cannot create native log directory");
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(args[1], args[1]);
    try {
      e.nativeEnsureT9SchemaPatches(SCHEMA);
      if (!new File(args[1], "build/" + SCHEMA + ".schema.yaml").isFile()) e.nativeStartMaintenance(true);
      long until = System.currentTimeMillis() + 600000;
      while (e.nativeIsMaintaining()) {
        if (System.currentTimeMillis() > until) throw new Exception("deployment timeout");
        Thread.sleep(100);
      }
      if (!e.nativeCreateSession() || !e.nativeSwitchSchema(SCHEMA)) throw new Exception("session unavailable");
      e.nativeSetOption("ascii_mode", false);

      e.nativeT9ClearComposition(1);
      e.nativeT9FlushRimeInput();
      // As the app does while nothing is composing: each flicked letter is an ordinary letter key.
      type(e, "womenquchifan");
      expect("letters reach Rime", "womenquchifan", e.nativeGetInput());
      expect("no T9 digits", "", e.nativeT9GetRemainingDigits());

      if (choose(e, "我们")) throw new Exception("choosing 我们 committed the whole sentence");
      expect("rest after 我们", "quchifan", e.nativeGetInput());

      // Delete the rest, then one more backspace takes 我们 back.
      for (int i = 0; i < "quchifan".length(); i++) key(e, 0xff08);
      expect("rest deleted", "", e.nativeGetInput());
      e.nativeT9GetAndConsumeUndoneRightCommitCount();
      key(e, 0xff08);
      expect("我们 taken back", "women", e.nativeGetInput());
      if (e.nativeT9GetAndConsumeUndoneRightCommitCount() != 1) throw new Exception("undo not reported as one segment");

      // Again, and finish the sentence with the remaining word.
      type(e, "quchifan");
      if (choose(e, "我们")) throw new Exception("choosing 我们 again committed the whole sentence");
      String[][] rows = e.nativeInspectCandidates(50);
      if (rows.length == 0) throw new Exception("no candidates for the rest");
      String last = rows[0][0];
      if (!choose(e, last)) throw new Exception("choosing " + last + " for the rest was not a full commit");
      System.out.println("PASS 我们 + " + last + " (rest kept, undo restores the chosen letters)");
    } finally { e.nativeDestroy(); }
  }

  static boolean choose(RimeEngine e, String text) throws Exception {
    String[][] rows = e.nativeInspectCandidates(200);
    for (String[] row : rows) {
      if (row.length > 1 && row[0].equals(text)) {
        boolean full = e.nativeT9SelectCandidate(row[1], text, text.length());
        e.nativeT9FlushRimeInput();
        return full;
      }
    }
    throw new Exception(text + " not offered for " + e.nativeGetInput() + ": "
        + Arrays.deepToString(Arrays.copyOf(rows, Math.min(rows.length, 8))));
  }

  static void type(RimeEngine e, String letters) throws Exception {
    for (char c : letters.toCharArray()) key(e, c);
  }

  static void key(RimeEngine e, int code) throws Exception {
    if (!e.nativeProcessKey(code, 0)) throw new Exception("unhandled key " + code);
    e.nativeT9FlushRimeInput();
  }

  static void expect(String what, String expected, String actual) throws Exception {
    if (!expected.equals(actual)) throw new Exception(what + ": expected '" + expected + "', got '" + actual + "'");
  }
}
