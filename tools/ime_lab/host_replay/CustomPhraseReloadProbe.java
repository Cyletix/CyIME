import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * When does a saved custom phrase reach a running engine? The settings page only rewrites
 * custom_phrase.txt; this probe adds a phrase while a session is open and reports which step makes
 * it appear: nothing, re-selecting the schema, or incremental maintenance. Arguments: the built
 * library directory and a copy of a deployed user directory (never an installed app's data).
 */
public final class CustomPhraseReloadProbe {
  static final String SCHEMA = "rime_ice";

  public static void main(String[] args) throws Exception {
    System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
    File user = new File(args[1]);
    File phrases = new File(user, "custom_phrase.txt");
    Files.write(phrases.toPath(), ("# Rime table\n#@/db_name\tcustom_phrase\n#@/db_type\ttabledb\n#\n"
        + "探针短语甲\tzzqa\t1\n").getBytes(StandardCharsets.UTF_8));
    RimeEngine e = new RimeEngine();
    e.nativeInitialize(user.getPath(), user.getPath());
    try {
      if (!new File(user, "build/" + SCHEMA + ".schema.yaml").isFile()) maintain(e, true);
      if (!e.nativeCreateSession() || !e.nativeSwitchSchema(SCHEMA)) throw new Exception("session unavailable");
      e.nativeSetOption("ascii_mode", false);
      report("present before the session", e, "zzqa", "探针短语甲");

      Files.write(phrases.toPath(), "探针短语乙\tzzqb\t1\n".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
      boolean live = report("added while open, nothing else", e, "zzqb", "探针短语乙");
      e.nativeSwitchSchema(SCHEMA);
      boolean reselect = report("after re-selecting the schema", e, "zzqb", "探针短语乙");
      maintain(e, false);
      e.nativeSwitchSchema(SCHEMA);
      boolean maintenance = report("after incremental maintenance + re-select", e, "zzqb", "探针短语乙");
      System.out.println("RESULT live=" + live + " reselect=" + reselect + " maintenance=" + maintenance);
    } finally { e.nativeDestroy(); }
  }

  static boolean report(String step, RimeEngine e, String code, String phrase) {
    e.nativeSetInput("");
    e.nativeSetInput(code);
    String[][] rows = e.nativeInspectCandidates(20);
    boolean found = false;
    for (String[] row : rows) if (row.length > 0 && row[0].equals(phrase)) found = true;
    System.out.println(step + ": " + code + " -> " + (found ? "FOUND " : "missing ") + phrase
        + (rows.length > 0 ? " (first: " + rows[0][0] + ")" : " (no candidates)"));
    e.nativeSetInput("");
    return found;
  }

  static void maintain(RimeEngine e, boolean full) throws Exception {
    e.nativeStartMaintenance(full);
    long until = System.currentTimeMillis() + 600000;
    while (e.nativeIsMaintaining()) {
      if (System.currentTimeMillis() > until) throw new Exception("maintenance timeout");
      Thread.sleep(100);
    }
  }
}
