import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Nine-key English typed half way: for each word, the fewest keys after which it is among the first
 * five candidates, and its rank once fully typed. Arguments: library_dir, a deployed t9 replay user
 * directory (a t9_replay run's native-run copy; nothing is learned or committed) and a word list file.
 * Compile and run together with ../android_replay/RimeEngine.java.
 */
public class T9EnglishProbe {
  static final String KEYS = "22233344455566677778889999";

  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    RimeEngine engine = new RimeEngine();
    engine.nativeInitialize(args[1], args[1]);
    if (!engine.nativeCreateSession() || !engine.nativeSwitchSchema("t9_pinyin"))
      throw new Exception("t9_pinyin session unavailable");
    engine.nativeSetOption("ascii_mode", false);
    List<String> words = Files.readAllLines(new File(args[2]).toPath(), StandardCharsets.UTF_8);
    int found = 0, fullTop5 = 0, total = 0;
    long keysSaved = 0;
    for (String word : words) {
      word = word.trim();
      if (word.isEmpty() || !word.matches("[A-Za-z]+")) continue;
      StringBuilder digits = new StringBuilder();
      for (char c : word.toLowerCase().toCharArray()) digits.append(KEYS.charAt(c - 'a'));
      engine.nativeT9ClearComposition(1);
      engine.nativeT9FlushRimeInput();
      int firstSeen = -1, fullRank = -1;
      for (int i = 0; i < digits.length(); i++) {
        engine.nativeProcessKey(digits.charAt(i), 0);
        engine.nativeT9FlushRimeInput();
        String[][] rows = engine.nativeInspectCandidates(10);
        int rank = -1;
        for (int r = 0; r < rows.length; r++) if (rows[r][0].equalsIgnoreCase(word)) { rank = r; break; }
        if (firstSeen < 0 && rank >= 0 && rank < 5) firstSeen = i + 1;
        if (i == digits.length() - 1) fullRank = rank;
      }
      total++;
      if (firstSeen > 0) { found++; keysSaved += digits.length() - firstSeen; }
      if (fullRank >= 0 && fullRank < 5) fullTop5++;
      System.out.println(word + "\t" + digits.length() + "\t" + firstSeen + "\t" + fullRank);
    }
    engine.nativeT9ClearComposition(1);
    System.out.println("SUMMARY words=" + total + " seen_in_top5_before_or_at_end=" + found +
        " full_top5=" + fullTop5 + " keys_saved=" + keysSaved);
  }
}
