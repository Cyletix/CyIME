import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.util.Arrays;

/** Exercises the deployed prism, including mixed exact letters and ambiguous taps. */
public final class MergedLetterProbe {
  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    File logs = new File(args[1], "logs");
    if (!logs.isDirectory() && !logs.mkdirs()) throw new Exception("cannot create native log directory");
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(args[1], args[1]);
    try {
      e.nativeStartMaintenance(true);
      long until = System.currentTimeMillis() + 180000;
      while (e.nativeIsMaintaining()) {
        if (System.currentTimeMillis() > until) throw new Exception("deployment timeout");
        Thread.sleep(100);
      }
      if (!e.nativeCreateSession()) throw new Exception("session unavailable");
      if (args.length > 2 && args[2].equals("--single-key")) {
        for (String schema : new String[]{"pinyin_14jian", "double_pinyin_flypy_14jian"}) {
          reset(e, schema, "A");
          java.util.List<String> ambiguous = firstWords(e);
          String sWord = schema.equals("pinyin_14jian") ? "是" : "三";
          System.out.println(schema + " ambiguous A: " + ambiguous +
              " native syllables=" + Arrays.toString(e.nativePinyinSyllableOptions()));
          require(ambiguous.contains("啊") && ambiguous.contains(sWord), "tap lost one letter family");
          for (String letter : new String[]{"s", "a", "s"}) {
            require(e.nativeSetInput(letter) && e.nativeGetInput().equals(letter), "exact letter " + letter);
            String[] edit = e.nativePinyinEditSnapshot();
            require(edit[1].equals("1") && edit[2].equals("0"), "wrong caret or confirmation after letter pick");
            java.util.List<String> words = firstWords(e);
            System.out.println(schema + " exact " + letter + ": " + words);
            require(words.contains(letter.equals("s") ? sWord : "啊") &&
                !words.contains(letter.equals("s") ? "啊" : sWord), "wrong family for " + letter);
          }
        }
        return;
      }
      if (args.length > 2 && args[2].equals("--prefix-options")) {
        reset(e, "pinyin_14jian", "BUn");
        System.out.println("PREFIX OPTIONS " + Arrays.toString(e.nativePinyinSyllableOptions()));
        option(e, "ni");
        return;
      }
      verify(e, "pinyin_14jian", "bugao", "bu gao", "ni hao", "bu hao");
      verify(e, "pinyin_14jian", "bUGaO", "bu hao", "ni hao");
      verify(e, "pinyin_14jian", "BUGaO", "ni hao");
      verify(e, "pinyin_14jian", "nihao", "ni hao", "bu hao");
      verify(e, "double_pinyin_flypy_14jian", "bugj", "bu gan", "ni gan", "bu han");
      verify(e, "double_pinyin_flypy_14jian", "bUGJ", "bu han", "ni gan");
      verify(e, "double_pinyin_flypy_14jian", "BUGJ", "ni gan");
      System.out.println("PASS exact, ambiguous and mixed merged-key inputs");
      selection(e, "pinyin_14jian", "BUGaO", "ni", "bu");
      selection(e, "double_pinyin_flypy_14jian", "BUGJ", "ni", "bu");
      selection(e, "double_pinyin_flypy", "lo", "lo", "luo");
      selection(e, "rime_ice", "xian", "xian", "xi");
      exactLetter(e);
      confirmedPrefix(e);
      lockedBoundary(e);
      delimiterAndIncompletePrefix(e);
      invalidation(e);
      chainedChoices(e);
      System.out.println("PASS native syllable selection: real code, reselect, confirmed prefix, stale rejection and invalidation");
    } finally { e.nativeDestroy(); }
  }
  static void verify(RimeEngine e, String schema, String code, String required, String... excluded) throws Exception {
    if (!e.nativeSwitchSchema(schema)) throw new Exception("schema unavailable: " + schema);
    e.nativeSetOption("ascii_mode", false);
    for (char c : code.toCharArray()) if (!e.nativeProcessKey(c, 0)) throw new Exception("unhandled key " + c);
    if (!e.nativeGetInput().equals(code)) throw new Exception("input changed: " + e.nativeGetInput());
    String[][] rows = e.nativeInspectCandidates(100);
    System.out.println(schema + " " + code + " => " + Arrays.deepToString(Arrays.copyOf(rows, Math.min(rows.length, 6))));
    boolean found = false;
    for (String[] row : rows) {
      if (row.length < 7 || !row[6].equals(Integer.toString(code.length()))) continue;
      if (row[1].equals(required)) found = true;
      for (String bad : excluded) if (row[1].equals(bad)) throw new Exception("wrong exact letter: " + code + " => " + Arrays.toString(row));
    }
    if (!found) throw new Exception("missing expected reading " + required + " for " + code);
    e.nativeProcessKey(0xff1b, 0);
  }

  static java.util.List<String> firstWords(RimeEngine e) {
    java.util.List<String> words = new java.util.ArrayList<>();
    for (String[] row : e.nativeInspectCandidates(16)) words.add(row[0]);
    return words;
  }

  static void reset(RimeEngine e, String schema, String code) throws Exception {
    e.nativeProcessKey(0xff1b, 0);
    if (!e.nativeSwitchSchema(schema)) throw new Exception("schema unavailable: " + schema);
    e.nativeSetOption("ascii_mode", false);
    for (char c : code.toCharArray()) if (!e.nativeProcessKey(c, 0)) throw new Exception("unhandled key " + c);
    require(code.equals(e.nativeGetInput()), "raw input changed before selection");
  }

  /** Readings chosen so far; the rail itself moves on to the next syllable after a choice. */
  static final java.util.Map<String, String[]> chosen = new java.util.HashMap<>();

  static String[] option(RimeEngine e, String reading) throws Exception {
    for (String encoded : e.nativePinyinSyllableOptions()) {
      String[] value = encoded.split("\\t", -1);
      if (value.length == 5 && value[1].equals(reading)) return value;
    }
    throw new Exception("missing reading " + reading + " for " + e.nativeGetInput() + ": " +
        Arrays.toString(e.nativePinyinSyllableOptions()));
  }

  static void choose(RimeEngine e, String reading) throws Exception {
    String[] value = option(e, reading);
    String[] before = e.nativePinyinEditSnapshot();
    require(e.nativeSelectPinyinSyllable(before[0], e.nativeGetCurrentSchema(),
        Integer.parseInt(value[2]), Integer.parseInt(value[3]), Integer.parseInt(value[0])),
        "selection failed: " + reading + " " + Arrays.toString(value));
    String[] after = e.nativePinyinEditSnapshot();
    for (int i = 0; i < 4; ++i) require(before[i].equals(after[i]),
        "selection changed raw/caret/confirmed state " + Arrays.toString(before) + " => " + Arrays.toString(after));
    chosen.put(reading, value);
    // A later syllable of a chain has no candidate of its own start; chainedChoices checks the words.
    if (e.nativePinyinSyllablesChosen() == 1) assertConstrained(e, reading);
    System.out.println("LOCK " + e.nativeGetCurrentSchema() + " " + before[0] + " => " + reading +
        " id=" + value[0] + " raw/caret/confirmed preserved");
  }

  static void assertConstrained(RimeEngine e, String reading) throws Exception {
    String[] value = chosen.get(reading);
    require(value != null && e.nativePinyinSyllablesChosen() > 0, "chosen reading not selected: " + reading);
    boolean found = false;
    for (String[] row : e.nativeInspectCandidates(500)) {
      // Independent English/custom table translators use another dictionary's
      // syllable IDs. This contract constrains Chinese phonetic candidates.
      if (row.length < 9 || row[8].isEmpty() || !row[5].equals(value[2]) ||
          !row[0].codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)) continue;
      found = true;
      require(row[8].split(",")[0].equals(value[0]),
          "candidate escaped selected pronunciation " + reading + ": " + Arrays.toString(row));
    }
    require(found, "selection produced no inspectable phonetic candidate: " + reading);
  }

  static void selection(RimeEngine e, String schema, String code, String first, String second) throws Exception {
    reset(e, schema, code);
    // Both readings must actually exist before the constraint; a UI label is not proof.
    String firstId = option(e, first)[0];
    require(!firstId.equals(option(e, second)[0]), "counterexample has identical syllable IDs");
    choose(e, first);
    // The rail's delete key takes the choice back; the same syllable can then be chosen again.
    require(e.nativeUndoPinyinSyllable(code, schema) && e.nativePinyinSyllablesChosen() == 0, "undo failed");
    choose(e, second);
    String[] before = e.nativePinyinEditSnapshot();
    int current = e.nativePinyinSyllablesChosen();
    require(!e.nativeSelectPinyinSyllable(code + "z", schema, 0, 2, Integer.parseInt(firstId)), "stale raw accepted");
    require(!e.nativeSelectPinyinSyllable(code, schema + "_stale", 0, 2, Integer.parseInt(firstId)), "stale schema accepted");
    require(!e.nativeSelectPinyinSyllable(code, schema, 0, code.length() + 1, Integer.parseInt(firstId)), "invalid span accepted");
    require(!e.nativeSelectPinyinSyllable(code, schema, 0, 2, Integer.MAX_VALUE), "invalid syllable accepted");
    require(Arrays.equals(before, e.nativePinyinEditSnapshot()), "rejected selection changed composition");
    require(e.nativePinyinSyllablesChosen() == current, "rejected selection lost previous lock");
    assertConstrained(e, second);
  }

  static void exactLetter(RimeEngine e) throws Exception {
    reset(e, "pinyin_14jian", "bUGaO");
    for (String encoded : e.nativePinyinSyllableOptions())
      require(!encoded.split("\\t")[1].equals("ni"), "precise lowercase b became ambiguous");
    choose(e, "bu");
  }

  static void confirmedPrefix(RimeEngine e) throws Exception {
    reset(e, "pinyin_14jian", "niBUGaO");
    int selected = -1;
    for (String[] row : e.nativeInspectCandidates(500)) {
      if (row[0].equals("你") && row[6].equals("2")) { selected = Integer.parseInt(row[4]); break; }
    }
    require(selected >= 0 && e.nativeSelectCandidateByGlobalIndex(selected), "cannot prepare confirmed prefix 你");
    String[] state = e.nativePinyinEditSnapshot();
    require(state[2].equals("2") && state[3].equals("你"), "prefix was committed instead of confirmed: " + Arrays.toString(state));
    choose(e, "ni");
    require(e.nativeUndoPinyinSyllable("niBUGaO", "pinyin_14jian"), "undo after confirmed prefix");
    choose(e, "bu");
    require(e.nativeGetInput().equals("niBUGaO"), "remaining ambiguous suffix was rewritten");
    e.nativeProcessKey('n', 0);
    assertConstrained(e, "bu");
    state = e.nativePinyinEditSnapshot();
    require(state[2].equals("2") && state[3].equals("你"), "suffix append lost confirmed Chinese text");
  }

  static void noSelection(RimeEngine e, String reason) throws Exception {
    require(e.nativePinyinSyllablesChosen() == 0, reason + ": old selection revived");
    for (String value : e.nativePinyinSyllableOptions())
      require(!value.endsWith("\t1"), reason + ": old selection revived " + value);
  }

  static void lockedBoundary(RimeEngine e) throws Exception {
    reset(e, "pinyin_14jian", "BU");
    choose(e, "ni");
    e.nativeProcessKey('n', 0);
    require(e.nativeGetInput().equals("BUn"), "boundary test lost original grouped code");
    assertConstrained(e, "ni");
    choose(e, "nin");
    choose(e, "ni");
    System.out.println("PASS chosen ni boundary survives competing nin reading in BUn");
  }

  static void delimiterAndIncompletePrefix(RimeEngine e) throws Exception {
    reset(e, "pinyin_14jian", "BU'GaO");
    choose(e, "ni");
    e.nativeProcessKey('n', 0);
    assertConstrained(e, "ni");
    require(e.nativeGetInput().equals("BU'GaOn"), "explicit delimiter was rewritten");
    e.nativeSetCaret(1);
    e.nativePinyinSyllableOptions();
    e.nativeSetCaret(7);
    assertConstrained(e, "ni");
    require(e.nativeSetInput("B"), "incomplete prefix replacement failed");
    noSelection(e, "incomplete prefix");
    require(e.nativeSetInput("BU'GaO"), "restore after incomplete prefix failed");
    noSelection(e, "prefix restored after replacement");
    System.out.println("PASS explicit delimiter/caret preserve selected span; incomplete replacement clears it");
  }

  static void invalidation(RimeEngine e) throws Exception {
    reset(e, "pinyin_14jian", "BUGaO");
    choose(e, "ni");
    e.nativeProcessKey('n', 0);
    assertConstrained(e, "ni");
    e.nativeProcessKey(0xff08, 0);
    require(e.nativeGetInput().equals("BUGaO"), "append/delete test did not restore original raw");
    assertConstrained(e, "ni");
    e.nativeProcessKey(0xff08, 0);
    assertConstrained(e, "ni");
    e.nativeProcessKey('O', 0);
    require(e.nativeGetInput().equals("BUGaO"), "delete/retype test did not restore original raw");
    assertConstrained(e, "ni");
    // set_input is the incremental append path used by code editing; it must
    // retain the locked prefix without recycling an incompatible WordGraph.
    require(e.nativeSetInput("BUGaOn"), "set_input append failed");
    assertConstrained(e, "ni");
    require(e.nativeSetInput("BUGaO"), "set_input suffix deletion failed");
    assertConstrained(e, "ni");
    e.nativeSetCaret(1);
    e.nativePinyinSyllableOptions(); // Rendering the truncated graph must not clear the lock.
    e.nativeSetCaret(5);
    assertConstrained(e, "ni");
    for (int i = 0; i < 4; ++i) e.nativeProcessKey(0xff08, 0);
    for (char c : "UGaO".toCharArray()) e.nativeProcessKey(c, 0);
    require(e.nativeGetInput().equals("BUGaO"), "locked-span edit did not restore original raw");
    noSelection(e, "delete inside locked span then retype");
    choose(e, "ni");
    e.nativeProcessKey(0xff1b, 0);
    for (char c : "BUGaO".toCharArray()) e.nativeProcessKey(c, 0);
    noSelection(e, "clear then retype");
    choose(e, "ni");
    reset(e, "double_pinyin_flypy_14jian", "BUGJ");
    noSelection(e, "schema change");
    System.out.println("PASS suffix edits/append retain lock, caret round-trip retains lock, locked-span edit/clear/schema invalidate");
  }

  /** Several syllables are chosen one after another, as on the nine-key rail. */
  static void chainedChoices(RimeEngine e) throws Exception {
    reset(e, "pinyin_14jian", "BUGaOdE");
    choose(e, "ni");
    for (String encoded : e.nativePinyinSyllableOptions())
      require(encoded.split("\\t")[2].equals("2"), "rail did not move to the second syllable: " + encoded);
    choose(e, "hao");
    choose(e, "de");
    require(e.nativePinyinSyllablesChosen() == 3, "three chosen syllables");
    require(firstWords(e).get(0).equals("你好的"), "chained readings lost: " + firstWords(e));
    assertConstrained(e, "ni");
    e.nativeProcessKey(0xff08, 0);
    require(e.nativePinyinSyllablesChosen() == 2, "deleting the last letter dropped more than its reading");
    e.nativeProcessKey('E', 0);
    require(e.nativePinyinSyllablesChosen() == 2, "retyping revived a dropped reading");
    choose(e, "de");
    int selected = -1;
    for (String[] row : e.nativeInspectCandidates(500)) {
      if (row[0].equals("你") && row[6].equals("2")) { selected = Integer.parseInt(row[4]); break; }
    }
    require(selected >= 0 && e.nativeSelectCandidateByGlobalIndex(selected), "cannot confirm 你");
    require(e.nativePinyinSyllablesChosen() == 2 && firstWords(e).get(0).equals("好的"),
        "confirming the first word lost the readings after it: " + firstWords(e));
    assertConstrained(e, "hao");
    require(e.nativeUndoPinyinSyllable("BUGaOdE", "pinyin_14jian") && e.nativePinyinSyllablesChosen() == 1,
        "undo of the last reading");
    require(e.nativeUndoPinyinSyllable("BUGaOdE", "pinyin_14jian") && e.nativePinyinSyllablesChosen() == 0,
        "undo of the first reading");
    require(!e.nativeUndoPinyinSyllable("BUGaOdE", "pinyin_14jian"), "undo without a reading");
    String[] state = e.nativePinyinEditSnapshot();
    require(state[2].equals("2") && state[3].equals("你"), "undo touched the confirmed word");
    System.out.println("PASS chained syllables: next syllable, partial word keeps the rest, delete/undo drop only the last");
  }

  static void require(boolean value, String message) throws Exception {
    if (!value) throw new Exception(message);
  }
}
