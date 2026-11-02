import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.util.Arrays;

/** Run on a freshly deployed, isolated user directory with the spelling trials enabled. */
public final class SpellingPauseProbe {
    public static void main(String[] args) {
        System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
        RimeEngine engine = new RimeEngine();
        engine.nativeInitialize(args[1], args[1]);
        try {
            require(engine.nativeCreateSession(), "no session");
            for (String input : new String[]{"946476642876264524", "946476642872264524",
                                             "946476642873264524", "956476642875264524"}) {
                enter(engine, "t9_pinyin", input, "", true);
                require(Arrays.stream(engine.nativeInspectCandidates(5)).noneMatch(row -> "形容不上来".equals(row[0])),
                        "unexpected target in the control before refinement: " + input);
                engine.nativeRefineOmission();
                require(Arrays.stream(engine.nativeInspectCandidates(5)).noneMatch(row -> "形容不上来".equals(row[0])),
                        "promoted a remote, diagonal, row-wrap or two-tap error: " + input);
                System.out.println("t9_pinyin " + input + " -> no promotion of 形容不上来");
            }
            check(engine, "t9_pinyin", "946476642872464524", "形容不上来", "", true);
            check(engine, "t9_pinyin", "946476642875264524", "形容不上来", "", true);
            check(engine, "rime_ice", "xingrongbusahnglai", "形容不上来", args[2], false);
        } finally {
            engine.nativeDestroy();
        }
    }

    private static void enter(RimeEngine e, String schema, String input, String neighbors, boolean digits) {
        require(e.nativeSwitchSchema(schema), schema);
        e.nativeSetInput("");
        e.nativeSetOption("ascii_mode", false);
        e.nativeSetNeighborMap(neighbors);
        if (digits) e.nativeT9ClearComposition(1);
        for (char c : input.toCharArray()) {
            require(e.nativeProcessKey(c, 0), input);
            if (digits) e.nativeT9FlushRimeInput();
        }
    }

    private static void check(RimeEngine e, String schema, String input, String target,
                              String neighbors, boolean digits) {
        enter(e, schema, input, neighbors, digits);
        e.nativeSetCaret(input.length() - 1);
        require(!e.nativeRefineOmission(), "changed the menu while editing inside the spelling");
        e.nativeSetCaret(input.length());
        if (!digits) {
            e.nativeSetNeighborMap("");
            e.nativeRefineOmission();
            require(Arrays.stream(e.nativeInspectCandidates(5)).noneMatch(row -> target.equals(row[0])),
                    "touch transposition applied without a keyboard layout");
            e.nativeSetNeighborMap(neighbors);
        }
        String[][] before = e.nativeInspectCandidates(5);
        require(e.nativeGetInput().equals(input), "lost a key");
        require(e.nativeRefineOmission(), "no new spelling candidate in " + schema);
        String[][] after = e.nativeInspectCandidates(5);
        for (int i = 0; i < 2; ++i) {
            require(before[i][0].equals(after[i][0]) && before[i][6].equals(after[i][6]), "moved protected candidate");
        }
        int index = 0;
        while (index < after.length && !target.equals(after[index][0])) ++index;
        require(index < after.length && Integer.parseInt(after[index][6]) == input.length(), Arrays.deepToString(after));
        if (digits) {
            require("xing rong bu shang lai".equals(after[index][1]), "lost the candidate reading");
            require(e.nativeT9SelectCandidate(after[index][1], target, target.length()), "partial digit selection");
            require(e.nativeT9GetRemainingDigits().isEmpty(), "digits left after selection");
        } else {
            require(e.nativeSelectCandidateByGlobalIndex(index), "select failed");
            require(target.equals(e.nativeCommit()), "wrong commit");
        }
        System.out.println(schema + " " + input + " -> " + target + ", rank " + (index + 1) + ", selected");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
