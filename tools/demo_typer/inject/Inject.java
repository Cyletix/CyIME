import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;

/**
 * Plays a monkey raw-event script (UserWait / DispatchPointer lines, as demo_typer.py writes them)
 * through InputManager, like scrcpy does from app_process. Unlike `monkey -f`, it never freezes and
 * thaws the display rotation, so a tablet resting upside down is not flipped between runs.
 *
 *   CLASSPATH=/data/local/tmp/cyime-inject.dex app_process / Inject /data/local/tmp/script.txt
 */
public final class Inject {
    private static final int INJECT_ASYNC = 0;

    public static void main(String[] args) throws Exception {
        Method inject = injector();
        Object manager = inject.getDeclaringClass().getMethod("getInstance").invoke(null);
        long downTime = 0;
        boolean data = false;
        try (BufferedReader in = new BufferedReader(new FileReader(args[0]))) {
            for (String line; (line = in.readLine()) != null; ) {
                line = line.trim();
                if (!data) {
                    data = line.startsWith("start data");
                    continue;
                }
                if (line.isEmpty()) continue;
                int open = line.indexOf('('), close = line.lastIndexOf(')');
                String name = line.substring(0, open);
                String[] a = line.substring(open + 1, close).split(",");
                if (name.equals("UserWait")) {
                    SystemClock.sleep(Long.parseLong(a[0].trim()));
                } else if (name.equals("DispatchPointer")) {
                    int action = Integer.parseInt(a[2].trim());
                    long now = SystemClock.uptimeMillis();
                    if (action == MotionEvent.ACTION_DOWN) downTime = now;
                    MotionEvent event = MotionEvent.obtain(downTime, now, action,
                        Float.parseFloat(a[3]), Float.parseFloat(a[4]), Float.parseFloat(a[5]),
                        Float.parseFloat(a[6]), 0, 1f, 1f, 0, 0);
                    event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                    inject.invoke(manager, event, INJECT_ASYNC);
                    event.recycle();
                } else {
                    throw new IllegalArgumentException("unsupported event: " + line);
                }
            }
        }
    }

    /** InputManagerGlobal on Android 14+, InputManager before it. */
    private static Method injector() throws ClassNotFoundException, NoSuchMethodException {
        Class<?> cls;
        try {
            cls = Class.forName("android.hardware.input.InputManagerGlobal");
        } catch (ClassNotFoundException e) {
            cls = Class.forName("android.hardware.input.InputManager");
        }
        return cls.getMethod("injectInputEvent", InputEvent.class, int.class);
    }
}
