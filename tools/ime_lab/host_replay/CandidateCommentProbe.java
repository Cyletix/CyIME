import com.kingzcheung.xime.rime.RimeEngine;
import java.io.File;
import java.util.Arrays;

public class CandidateCommentProbe {
  public static void main(String[] args) throws Exception {
    System.load(new File(args[0],"librime_jni.so").getAbsolutePath());
    RimeEngine engine=new RimeEngine();engine.nativeInitialize(args[1],args[1]);
    try {
      engine.nativeStartMaintenance(true);
      long until=System.currentTimeMillis()+180000;
      while(engine.nativeIsMaintaining()) {if(System.currentTimeMillis()>until)throw new Exception("deploy_timeout");Thread.sleep(100);}
      if(!engine.nativeCreateSession())throw new Exception("session_failed");
      String[][] cases={{"rime_ice","nihao"},{"pinyin_14jian","bugao"},{"double_pinyin_flypy","nihk"},{"double_pinyin_flypy_14jian","bugj"},{"wubi86","wqiy"}};
      for(String[] test:cases){
        if(!engine.nativeSwitchSchema(test[0]))throw new Exception("schema_unavailable="+test[0]);
        engine.nativeSetOption("ascii_mode",false);
        for(char c:test[1].toCharArray())engine.nativeProcessKey(c,0);
        String[][] rows=engine.nativeInspectCandidates(5);
        System.out.println(test[0]+" "+test[1]+" => "+Arrays.deepToString(rows));
        if(rows.length==0||rows[0].length<2||rows[0][1].isEmpty())throw new Exception("missing_comment="+test[0]);
        engine.nativeProcessKey(0xff1b,0);
      }
      System.out.println("PASS five real engine schemas return nonempty annotations");
    } finally {engine.nativeDestroy();}
  }
}
