import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

// Dedicated virtual display. No activity or window is opened on the user's display.
public class FloatingFooterProbe {
    static Class<?> cls(String name) throws Exception { return Class.forName(name); }
    static Object invoke(Object owner,String name,Object... args) throws Exception {
        for(Method m:owner.getClass().getMethods()) if(m.getName().equals(name) && m.getParameterCount()==args.length) {
            m.setAccessible(true); return m.invoke(owner,args);
        }
        throw new NoSuchMethodException(name);
    }
    static Handler main;
    static WindowManager wm;
    static ImageReader reader;
    static VirtualDisplay vd;
    static String root;
    static Object lifecycle, saved;
    static View compose;
    static Context ctx;
    static int W,H; static String mode; static Application testApp; static final List<String> commits=new ArrayList<>();
    static Field field(Class<?> type,String name) throws Exception {
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static Object proxy(String type,InvocationHandler h) throws Exception {
        return Proxy.newProxyInstance(FloatingFooterProbe.class.getClassLoader(),new Class[]{cls(type)},h);
    }
    static Object func() throws Exception {
        String name = mode.endsWith("-menu-layouts") ? "LayoutMenuScene" :
            mode.contains("-menu-") ? "MenuFeedbackScene" : mode.contains("-bottom-") ? "FixedBottomScene" :
            mode.contains("-input-") ? "InputOptionsScene" : "FloatingFooterScene";
        Class<?> source = cls("com.kingzcheung.xime.audit."+name+"Kt");
        Method scene = Arrays.stream(source.getDeclaredMethods())
            .filter(m -> m.getName().equals(name)).findFirst().get();
        Object unit = cls("kotlin.Unit").getField("INSTANCE").get(null);
        Object callback = proxy("kotlin.jvm.functions.Function1", (p,m,a) -> {
            if (m.getName().equals("invoke")) { commits.add(String.valueOf(a[0])); return unit; }
            return objectMethod(p,m,a);
        });
        return proxy("kotlin.jvm.functions.Function2", (p,m,a) -> {
            if (m.getName().equals("invoke")) {
                if (!name.equals("FloatingFooterScene")) scene.invoke(null,testApp,callback,mode,a[0],0);
                else scene.invoke(null,testApp,callback,mode.endsWith("-edge"),!mode.contains("-light-"),a[0],0);
                return unit;
            }
            return objectMethod(p,m,a);
        });
    }
    static Object objectMethod(Object p, Method m, Object[] a) {
        if (m.getName().equals("equals")) return p == a[0];
        if (m.getName().equals("hashCode")) return System.identityHashCode(p);
        return "footer probe";
    }
    static Bitmap render() throws Exception {
        RenderNode node=new RenderNode("keyboard");node.setPosition(0,0,W,H);compose.draw(node.beginRecording());node.endRecording();
        Method draw=HardwareRenderer.class.getDeclaredMethod("createHardwareBitmap",RenderNode.class,int.class,int.class);draw.setAccessible(true);
        Bitmap hardware = (Bitmap)draw.invoke(null,node,W,H);
        try { return hardware.copy(Bitmap.Config.ARGB_8888,false); }
        finally { hardware.recycle(); }
    }
    static void capture(String name) throws Exception {
        Bitmap b=render();
        try(FileOutputStream f=new FileOutputStream(root+"/"+mode+"-"+name+".png")){b.compress(Bitmap.CompressFormat.PNG,100,f);} finally {b.recycle();}
    }
    static float[] find(Object node,String tag) throws Exception {
        Object cfg=invoke(node,"getConfig");
        for(Object e:(Iterable<?>)cfg){Map.Entry<?,?> v=(Map.Entry<?,?>)e;
            Object key = invoke(v.getKey(),"getName");
            if(("TestTag".equals(key) && tag.equals(v.getValue())) ||
               (("Text".equals(key) || "ContentDescription".equals(key)) && ("["+tag+"]").equals(String.valueOf(v.getValue())))){
            Object rect=invoke(node,"getBoundsInRoot");
            return new float[]{edge(rect,"getLeft"),edge(rect,"getTop"),edge(rect,"getRight"),edge(rect,"getBottom")};}}
        for(Object n:(List<?>)invoke(node,"getChildren")){float[] at=find(n,tag);if(at!=null)return at;}return null;
    }
    static float[] at(String tag)throws Exception {
        Object owner=invoke(((ViewGroup)compose).getChildAt(0),"getSemanticsOwner");
        float[] at=find(invoke(owner,"getUnmergedRootSemanticsNode"),tag);
        if(at==null)throw new IllegalStateException("No visible "+tag);
        return at;
    }
    static float edge(Object rect,String name) throws Exception { return ((Number)invoke(rect,name)).floatValue(); }
    static float[] centre(float[] r) { return new float[]{(r[0]+r[2])/2,(r[1]+r[3])/2}; }
    static long downTime;
    static void touch(int action,float[] point) {
        long now=SystemClock.uptimeMillis();if(action==0)downTime=now;
        MotionEvent ev=MotionEvent.obtain(downTime,now,action,point[0],point[1],0);ev.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        compose.dispatchTouchEvent(ev); ev.recycle();
    }
    static void finish(){wm.removeViewImmediate(compose);vd.release();reader.close();System.exit(0);}
    static void tapTag(String tag) throws Exception {
        float[] rect = at(tag);
        if (rect[2]-rect[0] < 4 || rect[3]-rect[1] < 4) throw new AssertionError("Off screen: "+tag);
        float[] point = centre(rect);
        touch(0,point);touch(1,point);
    }
    static void swipeLayoutRow(boolean towardEnd) throws Exception {
        float[] row=at("profile-layout-list:list");
        float left=row[0]+10, right=row[2]-10, y=centre(row)[1];
        float[] start={towardEnd?right:left,y}, end={towardEnd?left:right,y};
        touch(0,start);
        main.postDelayed(()->touch(2,new float[]{(left+right)/2,y}),45);
        main.postDelayed(()->touch(2,end),90);
        main.postDelayed(()->touch(1,end),120);
    }
    static void layoutStep(int step) {
        main.postDelayed(() -> {
            try {
                String t9="layout-menu:zh:t9", qwerty="layout-menu:zh:qwerty", stroke="layout-menu:zh:stroke";
                switch(step) {
                    case 0: case 1: case 2: swipeLayoutRow(true);break;
                    case 3: tapTag("profile-layout-management");break;
                    case 4:
                        at("layout-menu:zh:custom_pinyin_abcdef0123456789abcdef0123456789");at(stroke);at(qwerty);
                        capture("keyboard");tapTag(stroke);break;
                    case 5: {
                        float[] from=centre(at(t9)), to=centre(at(qwerty));
                        from[0]=to[0]=65;to[1]-=7;
                        touch(0,from);
                        main.postDelayed(()->touch(2,new float[]{from[0],from[1]-12}),650);
                        main.postDelayed(()->touch(2,to),720);
                        main.postDelayed(()->touch(1,to),830);break;
                    }
                    case 6:
                        if (at(t9)[1] >= at(qwerty)[1]) throw new AssertionError("Drag did not move nine-key first");
                        tapTag("\u8fd4\u56de");break;
                    case 7: swipeLayoutRow(false);break;
                    case 8:
                        if (at("panel-layout:t9")[0] >= at("panel-layout:qwerty")[0]) throw new AssertionError("Keyboard order not saved");
                        tapTag("panel-layout:t9");break;
                    case 9: case 10: swipeLayoutRow(true);break;
                    case 11:
                        at("panel-layout:custom_pinyin_abcdef0123456789abcdef0123456789");
                        Object owner=invoke(((ViewGroup)compose).getChildAt(0),"getSemanticsOwner");
                        if (find(invoke(owner,"getUnmergedRootSemanticsNode"),"panel-layout:stroke")!=null)
                            throw new AssertionError("Hidden stroke still visible");
                        tapTag("profile-layout-management");break;
                    case 12: tapTag(stroke);break;
                    case 13: tapTag("\u8fd4\u56de");break;
                    case 14: case 15: swipeLayoutRow(true);break;
                    case 16:
                        at("panel-layout:stroke");
                        if (!commits.equals(Arrays.asList("manage:zh","select:t9_pinyin","manage:zh"))) throw new AssertionError(commits);
                        System.out.println("PASS gear, custom and built-in layouts, hide, drag, return, select and restore");
                        tapTag("profile-layout-management");break;
                    case 17: tapTag("\u7f16\u8f91\u7b26\u53f7");break;
                    case 18: at("symbol-editor-key:q");tapTag("\u8fd4\u56de");break;
                    case 19: tapTag("26\u4e2a\u6309\u952e \u00b7 0\u4e2a\u5b57\u6bcd\u6539\u4f4d");break;
                    case 20:
                        at("\u7f16\u8f91\u5e03\u5c40");at("\u6309\u952e\u6392\u5217");
                        System.out.println("PASS existing symbol and custom layout editors");finish();return;
                }
                layoutStep(step+1);
            } catch(Throwable error) {
                try { capture("failure"); } catch(Exception ignored) { }
                System.err.println("Layout step "+step);error.printStackTrace();System.exit(2);
            }
        }, step==0 ? 1800 : step==6 ? 1400 : 640);
    }
    static void menuStep(int step) {
        main.postDelayed(() -> {
            try {
                if (step == 0) capture("keyboard");
                float[] target;
                if (mode.endsWith("speech")) {
                    String id = "zipformer-ja-reazonspeech-fp32";
                    String light = "zipformer-ja-reazonspeech-int8";
                    if (step < 2) {
                        float[] model = at("speech-choice:ja:"+id);
                        float[] button = at("speech-download:"+id);
                        target = centre(button);
                        if (button[0] < model[2]-1 || target[1] < model[1] || target[1] > model[3]
                                || button[2]-button[0] < 48 || button[3]-button[1] < 47)
                            throw new AssertionError("Detached model action "+Arrays.toString(button)+" model="+Arrays.toString(model));
                        if (step == 1) at("\u8fde\u63a5\u4e2d\u65ad");
                        System.out.println("choice="+Arrays.toString(model)+" action="+Arrays.toString(button));
                    } else if (step == 2) {
                        at("\u4e0b\u8f7d\u4e2d 42%");
                        target = centre(at("speech-choice:ja:"+light));
                    } else {
                        if (!commits.equals(Arrays.asList("download:"+id,"download:"+id,"select:"+light))) throw new AssertionError(commits);
                        System.out.println("PASS download, retry, progress and independent selection");finish();return;
                    }
                } else {
                    float[] hide = at("toolbar-hide"), expand = at("candidate-expansion");
                    boolean narrow = W < 600;
                    float rowWidth = hide[2] - at("toolbar-leading")[0];
                    if (narrow && Math.abs(centre(hide)[0]-centre(expand)[0]) > 1
                            || !narrow && Math.abs(hide[2]-hide[0]-(rowWidth-8f)/6) > 1)
                        throw new AssertionError("Toolbar slots hide="+Arrays.toString(hide)+" expand="+Arrays.toString(expand));
                    if (step == 2) {
                        if (!commits.equals(Arrays.asList("hide","expand"))) throw new AssertionError(commits);
                        System.out.println("PASS hide="+Arrays.toString(hide)+" expand="+Arrays.toString(expand));finish();return;
                    }
                    target = centre(step == 0 ? hide : expand);
                }
                touch(0,target); touch(1,target); menuStep(step+1);
            } catch(Throwable error) { error.printStackTrace();System.exit(2); }
        }, step == 0 ? 1800 : 300);
    }
    static void bottomStep(int i) {
        main.postDelayed(() -> {
            try {
                if (mode.endsWith("resize")) {
                    int[] shown = {48,50,48,48,50};
                    if (i < shown.length) {
                        at("\u5e95\u90e8\u7559\u767d "+shown[i]+" dp");
                        float[] enter = at("t9-enter-key");
                        if (enter[3] > H-shown[i]+1) throw new AssertionError("Covered enter in resize "+Arrays.toString(enter));
                        if (i==0) capture("keyboard");
                        String[] actions = {"\u589e\u52a0\u5e95\u90e8\u7559\u767d","\u51cf\u5c11\u5e95\u90e8\u7559\u767d",
                            "\u51cf\u5c11\u5e95\u90e8\u7559\u767d","\u589e\u52a0\u5e95\u90e8\u7559\u767d","\u786e\u8ba4"};
                        float[] point=centre(at(actions[i]));
                        System.out.println("resize step="+i+" gap="+shown[i]+" action="+actions[i]+" point="+Arrays.toString(point));
                        touch(0,point);
                        main.postDelayed(() -> touch(1,point),55);
                        bottomStep(i+1);return;
                    }
                    if (Math.abs(H-at("fixed-keyboard-card")[3]-50)>1) throw new AssertionError("Saved gap differs from preview");
                    System.out.println("PASS resize 48/50/48/48/50, confirm kept 50 and key height");finish();return;
                }
                boolean buttons = mode.endsWith("buttons");
                int expected = (buttons ? 48 : 0) + (i == 1 ? 2 : 0);
                if (i == 4 && !buttons) expected = 18;
                float[] card = at("fixed-keyboard-card");
                float[] enter = at("t9-enter-key");
                if (Math.abs(H-card[3]-expected)>1 || enter[3] > H-expected+1)
                    throw new AssertionError("step="+i+" expected="+expected+" card="+Arrays.toString(card)+" enter="+Arrays.toString(enter));
                System.out.println("step="+i+" gap="+(H-card[3])+" enterBottom="+enter[3]);
                if (i == 0) {
                    capture("keyboard");
                    float[] low = {(enter[0]+enter[2])/2,enter[3]-2};
                    touch(0,low);touch(1,low);
                }
                if (i == 4) {
                    if (!commits.equals(Arrays.asList("enter"))) throw new AssertionError("Bottom-row input "+commits);
                    System.out.println("PASS raised, lowered, stopped at floor, restored system, typed enter");
                    finish();return;
                }
                String[] labels={"\u62ac\u9ad8","\u8c03\u4f4e","\u8c03\u4f4e","\u6062\u590d\u7cfb\u7edf\u5efa\u8bae"};
                float[] point=centre(at(labels[i]));
                touch(0,point);touch(1,point);
                bottomStep(i+1);
            } catch (Throwable failure) {
                try { capture("failure"); } catch (Exception captureError) { failure.addSuppressed(captureError); }
                failure.printStackTrace();System.exit(2);
            }
        }, i==0 ? 1800 : 300);
    }
    static final String capsLabel = "\u82f1\u6587\u53e5\u9996\u81ea\u52a8\u5927\u5199";
    static void inputStep(int i) {
        main.postDelayed(() -> {
            try {
                if (mode.endsWith("-input-completion") || mode.endsWith("-input-dictation")) {
                    cls("com.kingzcheung.xime.audit.InputOptionsSceneKt")
                        .getMethod(mode.endsWith("dictation") ? "verifyDictationRestart" : "verifyEnglishCompletions",
                            Application.class).invoke(null,testApp);
                    capture("keyboard"); finish(); return;
                } else if (mode.endsWith("settings")) {
                    boolean enabled = i == 1;
                    cls("com.kingzcheung.xime.audit.InputOptionsSceneKt")
                        .getMethod("verifyEnglishEditor",Application.class,boolean.class).invoke(null,testApp,enabled);
                    if (enabled) capture("keyboard");
                    if (i == 2) { System.out.println("PASS settings off/on/off and editor output"); finish(); return; }
                    float[] row = centre(at(capsLabel));
                    touch(0,row);touch(1,row);
                } else {
                    boolean legacy = mode.endsWith("legacy");
                    String[] expected = {"\uff0c","\u3002",",",".","'"};
                    String[] tags = {"qwerty-punctuation-key","qwerty-period-key"};
                    if (i == 0) capture("keyboard");
                    if (i > 0) {
                        String want = i <= 5 ? expected[i-1] : ".";
                        if (!commits.remove(want)) throw new AssertionError("Gesture "+i+" expected "+want+", got "+commits);
                    }
                    if (i == (mode.endsWith("long") ? 6 : 5)) {
                        if (mode.endsWith("default") && !commits.remove("dictionary-ok"))
                            throw new AssertionError("English dictionary did not finish");
                        if (!commits.isEmpty()) throw new AssertionError("Unexpected output "+commits);
                        System.out.println("PASS primary comma/period, alternate comma/period, pinyin split"+(i==6?", long press":""));
                        finish();return;
                    }
                    float[] p = centre(at(tags[i==1 || i==3 || i==5 ? 1 : 0]));
                    touch(0,p);
                    if (i == 5) main.postDelayed(() -> touch(1,p),750);
                    else if (i < 2) touch(1,p);
                    else {
                        float dy = (i==4) != legacy ? 52 : -52;
                        float[] end = {p[0],p[1]+dy};
                        main.postDelayed(() -> touch(2,end),45);
                        main.postDelayed(() -> touch(1,end),100);
                    }
                }
                inputStep(i+1);
            } catch(Throwable error) { error.printStackTrace();System.exit(2); }
        }, i==0 ? 2200 : i==6 ? 900 : 250);
    }
    static float[] drag;
    static void step(int i) {
        main.postDelayed(() -> {
            try {
                if (i == 0) {
                    float[] card = at("floating-keyboard-card");
                    float[] pill = centre(at("floating-drag-indicator"));
                    float[] hit = at("floating-drag-bar");
                    float expected = mode.contains("phone") || mode.contains("landscape") ? 16 : 28;
                    boolean baseline = mode.startsWith("before");
                    float[] band = baseline ? null : at("floating-footer");
                    float footer = baseline ? 28 : band[3] - band[1];
                    float centreY = card[3] - footer/2;
                    System.out.printf(Locale.ROOT,"card=%s pill=%s hit=%s expectedFooter=%.0f offsetY=%.1f%n",
                        Arrays.toString(card),Arrays.toString(pill),Arrays.toString(hit),footer,pill[1]-centreY);
                    if (!baseline) {
                        if (footer < expected || Math.abs(pill[1]-centreY)>1 ||
                            Math.abs(pill[0]-centre(card)[0])>1 || Math.abs(card[3]-card[1]-244-expected)>1)
                            throw new AssertionError("Footer geometry: "+Arrays.toString(band));
                    }
                    capture("keyboard");
                    if (mode.equals("after-tablet")) {
                        Bitmap b = render();
                        int x = Math.round(pill[0]), y = Math.round(pill[1]);
                        barContrast = Math.abs(Color.red(b.getPixel(x+15,y))-Color.red(b.getPixel(x-25,y)));
                        b.recycle();
                    }
                    float[] enter = at("qwerty-enter-key");
                    float[] low = new float[]{centre(enter)[0],enter[3]-2};
                    touch(0,low);touch(1,low);
                    drag = pill;
                } else if (i == 1) {
                    if (!commits.equals(Arrays.asList("enter"))) throw new AssertionError("Bottom-row tap: "+commits);
                    touch(0,drag);
                } else if (i == 2) {
                    touch(2,new float[]{drag[0]+20,drag[1]-10});
                } else if (i == 3) {
                    touch(2,new float[]{drag[0]+57,drag[1]-35});
                } else if (i == 4) {
                    touch(1,new float[]{drag[0]+57,drag[1]-35});
                } else if (mode.endsWith("-tablet") && !mode.startsWith("before") && i <= 6) {
                    if (!captureHintWhenSettled(i == 5)) { step(i); return; }
                } else if (mode.endsWith("-edge") && i <= 18) {
                    switch (i) {
                        case 5:
                            requireLocked(false);
                            drag = centre(at("floating-drag-indicator"));
                            capture("idle"); touch(0,drag);
                            main.postDelayed(() -> {
                                try { capture("starting"); }
                                catch (Exception e) { throw new RuntimeException(e); }
                            }, 135);
                            break;
                        case 6:
                            requireLocked(false); capture("partial"); touch(1,drag); break;
                        case 7:
                            requireLocked(false); capture("retreat"); break;
                        case 8:
                            requireLocked(false); capture("released"); touch(0,drag); break;
                        case 9:
                            requireLocked(false); break;
                        case 10:
                            requireLocked(true); touch(1,drag); break;
                        case 11:
                            requireLocked(true); capture("locked");
                            movements = Collections.frequency(commits, "move");
                            touch(0,drag);
                            touch(2,new float[]{drag[0]+25,drag[1]-22});
                            touch(2,new float[]{drag[0]+48,drag[1]-29});
                            touch(1,new float[]{drag[0]+48,drag[1]-29}); break;
                        case 12:
                            if (movements != Collections.frequency(commits, "move"))
                                throw new AssertionError("Locked card could be moved");
                            requireLocked(true); touch(0,drag); break;
                        case 13:
                            requireLocked(true); capture("unlock-partial"); touch(1,drag); break;
                        case 14:
                            requireLocked(true); capture("unlock-retreat"); break;
                        case 15:
                            requireLocked(true); capture("still-locked"); touch(0,drag); break;
                        case 16:
                            requireLocked(false); touch(1,drag); break;
                        case 17:
                            capture("unlocked");
                            touch(0,drag); touch(2,new float[]{drag[0]+25,drag[1]-22});
                            touch(2,new float[]{drag[0]+48,drag[1]-29});
                            touch(1,new float[]{drag[0]+48,drag[1]-29}); break;
                        case 18:
                            if (movements >= Collections.frequency(commits, "move"))
                                throw new AssertionError("Unlocked card could not be moved");
                            requireLocked(false); break;
                    }
                } else {
                    if (!commits.contains("move")) throw new AssertionError("Footer drag: "+commits);
                    System.out.println("PASS actions="+commits); finish(); return;
                }
                step(i+1);
            } catch(Throwable t) { t.printStackTrace(); System.exit(2); }
        }, stepDelay(i));
    }
    static int movements;
    static long hintWindowStart;
    static int barContrast;
    static boolean capturedFade;
    static long lastLockHint;
    static boolean captureHintWhenSettled(boolean lock) throws Exception {
        long now = SystemClock.uptimeMillis();
        if (hintWindowStart == 0) hintWindowStart = now;
        if (now - hintWindowStart > 5200) throw new AssertionError("Hint did not switch to " + (lock ? "lock" : "bar"));
        float[] pill = centre(at("floating-drag-indicator"));
        int x = Math.round(pill[0]), y = Math.round(pill[1]);
        Bitmap b = render();
        try {
            int contrast = Math.abs(Color.red(b.getPixel(x+15,y))-Color.red(b.getPixel(x-25,y)));
            if (lock && !capturedFade && contrast > .15 * barContrast && contrast < .88 * barContrast) {
                try (FileOutputStream f = new FileOutputStream(root+"/"+mode+"-hint-fade.png")) {
                    b.compress(Bitmap.CompressFormat.PNG,100,f);
                }
                capturedFade = true;
            }
            boolean settled = lock ? contrast < barContrast * .12 : contrast > barContrast * .96;
            if (settled) {
                String name = lock ? "hint-lock" : "hint-bar";
                try (FileOutputStream f = new FileOutputStream(root+"/"+mode+"-"+name+".png")) {
                    b.compress(Bitmap.CompressFormat.PNG,100,f);
                }
                System.out.println(name+" reached "+(now-hintWindowStart)+"ms after observation began");
                if (lock) lastLockHint = now;
                else {
                    long period = now-lastLockHint;
                    if (period < 2400 || period > 3700) throw new AssertionError("Hint interval "+period+" ms");
                    System.out.println("hint interval="+period+" ms");
                }
                hintWindowStart = 0;
            }
            return settled;
        } finally { b.recycle(); }
    }
    static void requireLocked(boolean expected) {
        boolean actual = ctx.getSharedPreferences("kime_settings",0).getBoolean("floating_geometry_locked",false);
        if (actual != expected) throw new AssertionError("Expected locked="+expected+", got "+actual);
    }
    static long stepDelay(int step) {
        if (step == 0) return 1700;
        if (mode.endsWith("-tablet") && !mode.startsWith("before")) {
            if (step == 5 || step == 6) return 45;
        }
        if (mode.endsWith("-edge")) {
            switch (step) {
                case 5: return 800;
                case 6: case 13: return 540;
                case 7: case 14: return 80;
                case 8: case 11: case 15: case 17: return 360;
                case 9: return 860;
                case 10: return 240;
                case 16: return 1110;
            }
        }
        return 150;
    }
    static SharedPreferences scratch(String name) {
        try{Constructor<?> c=cls("android.app.SharedPreferencesImpl").getDeclaredConstructor(File.class,int.class);
            c.setAccessible(true);return (SharedPreferences)c.newInstance(new File(root,name+".xml"),0);}
        catch(Exception e){throw new RuntimeException(e);}
    }
    static void run(String path) throws Exception {
        root=path;
        W=mode.contains("phone") ? 390 : 800;
        H=mode.contains("landscape") ? 390 : 720;
        if (Typeface.DEFAULT == null) Typeface.class.getDeclaredMethod("loadPreinstalledSystemFontMap").invoke(null);
        Looper.prepareMainLooper(); main=new Handler(Looper.myLooper());
        Object thread=cls("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        Context system=(Context)invoke(thread,"getSystemContext");
        Context shell=system.createPackageContext("com.android.shell",0);
        // app_process starts with the framework package. Use the installed shell identity for
        // contexts made by Presentation and for settings-provider attribution.
        Class<?> data=cls("android.app.ActivityThread$AppBindData");
        Constructor<?> ctor=data.getDeclaredConstructor();ctor.setAccessible(true);
        Object app=ctor.newInstance();
        field(data,"appInfo").set(app,shell.getApplicationInfo());
        field(data,"processName").set(app,"com.android.shell");
        field(data,"info").set(app,field(shell.getClass(),"mPackageInfo").get(shell));
        field(thread.getClass(),"mBoundApplication").set(thread,app);
        Application shellApp=new Application();
        Method attach=Application.class.getDeclaredMethod("attach",Context.class);
        attach.setAccessible(true);attach.invoke(shellApp,shell);
        field(thread.getClass(),"mInitialApplication").set(thread,shellApp);
        Object loaded=field(shell.getClass(),"mPackageInfo").get(shell);
        field(loaded.getClass(),"mApplication").set(loaded,shellApp);
        field(system.getContentResolver().getClass(),"mPackageName").set(system.getContentResolver(),"com.android.shell");
        field(system.getClass(),"mOpPackageName").set(system,"com.android.shell");
        android.content.AttributionSource attribution=new android.content.AttributionSource.Builder(android.os.Process.myUid()).setPackageName("com.android.shell").build();
        for (Context c:new Context[]{shell,system}) {
            field(c.getClass(),"mOpPackageName").set(c,"com.android.shell");
            field(c.getClass(),"mAttributionSource").set(c,attribution);
            field(c.getContentResolver().getClass(),"mPackageName").set(c.getContentResolver(),"com.android.shell");
            System.out.println("context="+c.getPackageName()+" op="+c.getOpPackageName()+" attr="+c.getAttributionSource().getPackageName());
        }
        // A standalone shell process obtains the settings provider with the same external
        // token interface as Android's content command, rather than an Activity token.
        Object am=ActivityManager.class.getDeclaredMethod("getService").invoke(null);
        Object holder=invoke(am,"getContentProviderExternal","settings",0,new Binder(),"CyIME floating footer");
        for(Method m:thread.getClass().getDeclaredMethods()) if(m.getName().equals("installProvider")&&m.getParameterCount()==6){
            m.setAccessible(true);m.invoke(thread,shell,holder,field(holder.getClass(),"info").get(holder),false,true,true);
        }
        if (mode.endsWith("-input-settings") || mode.endsWith("-input-completion") || mode.endsWith("-input-dictation") || mode.endsWith("-menu-layouts")) {
            // Android won't register change observers for a shell PID with no Activity.
            // The user's default IME never changes on this disposable settings display.
            Method getService = ContentResolver.class.getDeclaredMethod("getContentService");
            getService.setAccessible(true);
            Object original = getService.invoke(null);
            Object isolated = proxy("android.content.IContentService",(p,m,a)->{
                if (m.getName().equals("registerContentObserver") || m.getName().equals("unregisterContentObserver")) return null;
                return m.invoke(original,a);
            });
            field(ContentResolver.class,"sContentService").set(null,isolated);
        }
        reader=ImageReader.newInstance(W,H,PixelFormat.RGBA_8888,3);
        vd=shell.getSystemService(DisplayManager.class).createVirtualDisplay("CyIME floating footer",W,H,160,
            reader.getSurface(),DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION|DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        Context display=shell.createDisplayContext(vd.getDisplay()).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
        Context userResources=system.createPackageContext("com.cyletix.cyime",0).createDisplayContext(vd.getDisplay());
        Map<String,SharedPreferences> files=new HashMap<>();
        ctx=new ContextWrapper(display) {
            public SharedPreferences getSharedPreferences(String name,int flags){return files.computeIfAbsent(name,FloatingFooterProbe::scratch);}
            public File getFilesDir(){File f=new File(root,"files");f.mkdirs();return f;}
            public File getCacheDir(){File f=new File(root,"cache");f.mkdirs();return f;}
            public File getExternalFilesDir(String type){return getFilesDir();}
            public android.content.res.Resources getResources(){return userResources.getResources();}
            public android.content.res.AssetManager getAssets(){return userResources.getAssets();}
            public ContentResolver getContentResolver(){return shell.getContentResolver();}
            public Context getApplicationContext(){return testApp;}
            public ClassLoader getClassLoader(){return FloatingFooterProbe.class.getClassLoader();}
        };
        testApp=new Application(); attach.invoke(testApp,ctx);
        Object owner=proxy("androidx.savedstate.SavedStateRegistryOwner",(p,m,a)->{
            switch(m.getName()) {
                case "getLifecycle": return lifecycle;
                case "getSavedStateRegistry": return invoke(saved,"getSavedStateRegistry");
                case "hashCode": return System.identityHashCode(p);
                case "equals": return p==a[0];
                default: return "glass owner";
            }
        });
        lifecycle=cls("androidx.lifecycle.LifecycleRegistry").getConstructor(cls("androidx.lifecycle.LifecycleOwner")).newInstance(owner);
        Class<?> controller=cls("androidx.savedstate.SavedStateRegistryController");
        saved=invoke(controller.getField("Companion").get(null),"create",owner);
        invoke(saved,"performAttach");invoke(saved,"performRestore",(Object)null);
        Class<?> viewType=cls("androidx.compose.ui.platform.ComposeView");
        compose=(View)viewType.getConstructor(Context.class).newInstance(ctx);
        cls("androidx.lifecycle.ViewTreeLifecycleOwner").getMethod("set",View.class,cls("androidx.lifecycle.LifecycleOwner"))
            .invoke(null,compose,owner);
        cls("androidx.savedstate.ViewTreeSavedStateRegistryOwner").getMethod("set",View.class,cls("androidx.savedstate.SavedStateRegistryOwner"))
            .invoke(null,compose,owner);
        for(String event:new String[]{"ON_CREATE","ON_START","ON_RESUME"})
            invoke(lifecycle,"handleLifecycleEvent",Enum.valueOf((Class)cls("androidx.lifecycle.Lifecycle$Event"),event));
        wm=display.getSystemService(WindowManager.class);
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(W,H,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE);
        params.gravity=Gravity.TOP|Gravity.LEFT;params.setFitInsetsTypes(0);
        // An isolated display has no Activity token for watching system animation-scale changes.
        Class<?> recomposers=cls("androidx.compose.ui.platform.WindowRecomposer_androidKt");
        Object scale=cls("kotlinx.coroutines.flow.StateFlowKt").getMethod("MutableStateFlow",Object.class).invoke(null,1f);
        for(Field f:recomposers.getDeclaredFields()) if(f.getName().equals("animationScale")){
            f.setAccessible(true);Object cache=f.get(null);for(Context c:new Context[]{ctx,testApp,shellApp,system,shell})invoke(cache,"set",c,scale);}
        wm.addView(compose,params); invoke(compose,"setContent",func());
        if (mode.endsWith("-menu-layouts")) layoutStep(0);
        else if (mode.contains("-menu-")) menuStep(0);
        else if (mode.contains("-bottom-")) bottomStep(0);
        else if (mode.contains("-input-")) inputStep(0); else step(0);
        main.postDelayed(()->{System.err.println("Window capture timed out");System.exit(2);},mode.endsWith("-menu-layouts") ? 30000 : 22000);
        Looper.loop();
    }
    public static void main(String[] args) {
        try{mode=args[1];run(args[0]);}catch(Throwable e){e.printStackTrace();System.exit(1);}
    }
}
