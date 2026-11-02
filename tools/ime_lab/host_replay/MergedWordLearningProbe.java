import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Run "learn", then "reopen" in separate processes against the same disposable user directory. */
public final class MergedWordLearningProbe {
    public static void main(String[] args) {
        System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
        RimeEngine e = new RimeEngine();
        e.nativeInitialize(args[1], args[1]);
        try {
            require(e.nativeCreateSession() && e.nativeSwitchSchema("pinyin_14jian"), "no 14-key session");
            e.nativeSetOption("ascii_mode", false);
            if (args[2].equals("learn")) {
                pick(e, "GAUA", "还是");
                pick(e, "QOTEA", "我也是");
            } else {
                require(args[2].equals("reopen"), "expected learn or reopen");
            }
            firstWithAlternative(e, "GAUA", "还是", "还啊");
            firstWithAlternative(e, "QOTEA", "我也是", "我也啊");
            require(type(e, "GAUAGU").get(0).equals("还是"), "lost the full spelling");
            List<String> exact = type(e, "GAUa");
            require(exact.get(0).equals("还啊") && !exact.contains("还是"), "ignored an exact final a");
            if (args[2].equals("reopen")) {
                pick(e, "GAUA", "还啊");
                firstWithAlternative(e, "GAUA", "还啊", "还是");
            }
        } finally {
            e.nativeDestroy();
        }
    }

    private static List<String> type(RimeEngine e, String raw) {
        e.nativeSetInput("");
        for (char key : raw.toCharArray()) require(e.nativeProcessKey(key, 0), "key " + key);
        require(raw.equals(e.nativeGetInput()), "incorrect input for " + raw);
        return Arrays.stream(e.nativeInspectCandidates(32)).map(row -> row[0]).collect(Collectors.toList());
    }

    private static void pick(RimeEngine e, String raw, String text) {
        List<String> words = type(e, raw);
        int rank = words.indexOf(text);
        require(rank >= 0 && e.nativeSelectCandidateByGlobalIndex(rank), "could not select " + text + " in " + words);
        require(text.equals(e.nativeCommit()) && e.nativeGetInput().isEmpty(), "incorrect commit for " + text);
        System.out.println(raw + " selected " + text + " at rank " + (rank + 1));
    }

    private static void firstWithAlternative(RimeEngine e, String raw, String first, String other) {
        List<String> words = type(e, raw);
        int alternate = words.indexOf(other);
        require(first.equals(words.get(0)) && alternate > 0 && alternate < 5, raw + ": " + words);
        System.out.println(raw + " first " + first + ", " + other + " at rank " + (alternate + 1));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
