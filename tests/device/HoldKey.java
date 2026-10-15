import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyEvent;
import java.lang.reflect.Method;
public class HoldKey {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("android.hardware.input.InputManager");
        Object manager = type.getMethod("getInstance").invoke(null);
        Method inject = type.getMethod("injectInputEvent", InputEvent.class, int.class);
        int key = Integer.parseInt(args[0]);
        long down = SystemClock.uptimeMillis();
        inject.invoke(manager, new KeyEvent(down, down, KeyEvent.ACTION_DOWN, key, 0), 2);
        Thread.sleep(Long.parseLong(args[1]));
        inject.invoke(manager, new KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, key, 1, 0, -1, 0, KeyEvent.FLAG_LONG_PRESS), 2);
        inject.invoke(manager, new KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, key, 0), 2);
    }
}
