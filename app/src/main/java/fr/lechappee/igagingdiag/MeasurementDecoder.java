package fr.lechappee.igagingdiag;

import android.view.KeyEvent;
import java.util.regex.Pattern;

/** Converts Android key codes from the iGaging HID keyboard into complete readings. */
public final class MeasurementDecoder {
    private static final Pattern VALID_READING =
        Pattern.compile("-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");
    private final StringBuilder buffer = new StringBuilder();

    public DecodeResult acceptKeyCode(int keyCode) {
        int digit = digitFor(keyCode);
        if (digit >= 0) {
            buffer.append((char) ('0' + digit));
            return DecodeResult.inProgress(buffer.toString());
        }
        // Android may report a physical decimal key as PERIOD, COMMA or NUMPAD_DOT.
        if (keyCode == KeyEvent.KEYCODE_PERIOD
            || keyCode == KeyEvent.KEYCODE_COMMA
            || keyCode == KeyEvent.KEYCODE_NUMPAD_DOT) {
            buffer.append('.');
            return DecodeResult.inProgress(buffer.toString());
        }
        if (keyCode == KeyEvent.KEYCODE_MINUS || keyCode == KeyEvent.KEYCODE_NUMPAD_SUBTRACT) {
            buffer.append('-');
            return DecodeResult.inProgress(buffer.toString());
        }
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            return DecodeResult.ignored(buffer.toString());
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            String value = buffer.toString();
            buffer.setLength(0);
            return VALID_READING.matcher(value).matches()
                ? DecodeResult.complete(value)
                : DecodeResult.invalid(value);
        }
        return DecodeResult.ignored(buffer.toString());
    }

    private int digitFor(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return keyCode - KeyEvent.KEYCODE_0;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            return keyCode - KeyEvent.KEYCODE_NUMPAD_0;
        }
        return -1;
    }

    public String getBuffer() {
        return buffer.toString();
    }

    public void reset() {
        buffer.setLength(0);
    }

    public static final class DecodeResult {
        public enum Type { IN_PROGRESS, COMPLETE, INVALID, IGNORED }
        public final Type type;
        public final String value;

        private DecodeResult(Type type, String value) {
            this.type = type;
            this.value = value;
        }

        static DecodeResult inProgress(String value) {
            return new DecodeResult(Type.IN_PROGRESS, value);
        }

        static DecodeResult complete(String value) {
            return new DecodeResult(Type.COMPLETE, value);
        }

        static DecodeResult invalid(String value) {
            return new DecodeResult(Type.INVALID, value);
        }

        static DecodeResult ignored(String value) {
            return new DecodeResult(Type.IGNORED, value);
        }
    }
}
