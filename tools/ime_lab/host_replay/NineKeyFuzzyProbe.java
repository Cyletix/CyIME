import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;

/** Test the production settings from NineKeyFuzzySettingsTest in a disposable deployed user directory. */
public final class NineKeyFuzzyProbe {
    public static void main(String[] args) throws Exception {
        System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
        RimeEngine engine = new RimeEngine();
        engine.nativeInitialize(args[1], args[1]);
        try {
            engine.nativeEnsureT9SchemaPatches("t9_pinyin");
            LayoutSyncProbe.maintain(engine, true);
            if (!engine.nativeCreateSession()) throw new AssertionError("session unavailable");
            for (String schema : new String[]{"t9_pinyin", "double_pinyin_flypy_t9"}) {
                LayoutSyncProbe.select(engine, schema);
                boolean fullPinyin = schema.equals("t9_pinyin");
                select(engine, "7454", "", 0, "司机", "");
                select(engine, "7454", "si", 2, "实际", "");
                select(engine, "7454", fullPinyin ? "shi" : "ui", 2, "实际", "");
                select(engine, fullPinyin ? "74454" : "8454", "si", fullPinyin ? 3 : 2, "司机", "");
                select(engine, fullPinyin ? "94674" : "9274", fullPinyin ? "xing" : "xk",
                    fullPinyin ? 3 : 2, "星期", "");
                if (fullPinyin) select(engine, "7454", "si", 2, "是", "54");
            }
        } finally { engine.nativeDestroy(); }
    }

    private static void select(RimeEngine engine, String digits, String reading, int used,
                               String word, String remaining) throws Exception {
        LayoutSyncProbe.clear(engine, true);
        LayoutSyncProbe.type(engine, digits);
        if (!reading.isEmpty()) {
            if (!engine.nativeT9SelectPinyinDirect(reading, used)) throw new AssertionError(reading);
            engine.nativeT9FlushRimeInput();
        }
        String[][] rows = engine.nativeInspectCandidates(160);
        for (int index = 0; index < rows.length; index++) {
            if (!word.equals(rows[index][0])) continue;
            boolean done = engine.nativeT9SelectCandidate(rows[index][1], word, word.codePointCount(0, word.length()));
            if (done != remaining.isEmpty() || !remaining.equals(engine.nativeT9GetRemainingDigits()))
                throw new AssertionError(word + ": incorrect remaining digits");
            System.out.println(digits + " " + reading + " #" + (index + 1) + " " + word + " remaining=" + remaining);
            return;
        }
        throw new AssertionError(digits + " / " + reading + ": missing " + word);
    }
}
