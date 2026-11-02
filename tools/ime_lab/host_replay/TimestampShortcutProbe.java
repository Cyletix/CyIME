import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.util.Arrays;

/** Exercise both bundled timestamp providers with the same session option the Android IME sets. */
public final class TimestampShortcutProbe {
  static void check(RimeEngine engine, String schema, String code, boolean enabled) throws Exception {
    boolean nine = schema.equals("t9_pinyin");
    LayoutSyncProbe.select(engine, schema);
    LayoutSyncProbe.clear(engine, nine);
    engine.nativeSetOption("_cyime_datetime_off", !enabled);
    LayoutSyncProbe.type(engine, code);
    String[][] rows = engine.nativeInspectCandidates(100);
    long now = System.currentTimeMillis() / 1000;
    boolean stamp = false, ordinary = false;
    for (String[] row : rows) {
      if (row[0].matches("[0-9]{10}") && Math.abs(Long.parseLong(row[0]) - now) < 60) {
        stamp = true;
        if (!Arrays.asList(row).contains("时间戳")) throw new AssertionError("Missing label: " + Arrays.toString(row));
      } else ordinary = true;
    }
    System.out.println(schema + " " + code + " enabled=" + enabled + " timestamp=" + stamp + " " +
        Arrays.deepToString(Arrays.copyOf(rows, Math.min(rows.length, 4))));
    if (stamp != enabled || !ordinary) throw new AssertionError("Unexpected candidates");
  }

  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    RimeEngine engine = new RimeEngine();
    engine.nativeInitialize(args[1], args[1]);
    try {
      engine.nativeEnsureT9SchemaPatches("t9_pinyin");
      if (!new File(args[1], "build/rime_ice.schema.yaml").isFile()) LayoutSyncProbe.maintain(engine, true);
      if (!engine.nativeCreateSession()) throw new AssertionError("No session");
      for (boolean enabled : new boolean[]{false, true, false}) {
        check(engine, "rime_ice", "ts", enabled);
        check(engine, "t9_pinyin", "87", enabled);
      }
      LayoutSyncProbe.clear(engine, true);
      LayoutSyncProbe.type(engine, "524");
      if (Arrays.stream(engine.nativeInspectCandidates(30)).noneMatch(row -> row[0].equals("来")))
        throw new AssertionError("524 should offer 来");
      System.out.println("524 contains 来");
    } finally { engine.nativeDestroy(); }
  }
}
