package fr.lechappee.igagingdiag;

import android.view.KeyEvent;
import java.util.regex.Pattern;

/** Converts Android key codes from the iGaging HID keyboard into complete readings. */
public final class MeasurementDecoder {
    private static final Pattern VALID_READING =
        Pattern.compile("-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");
    private final StringBuilder buffer = new StringBuilder();

    public DecodeResult acceptKeyCode(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            buffer.append((char) ('0' + keyCode - KeyEvent.KEYCODE_0));
            return DecodeResult.inProgress(buffer.toString());
        }
        if (keyCode == KeyEvent.KEYCODE_PERIOD) {
            buffer.append('.');
            return DecodeResult.inProgress(buffer.toString());
        }
        if (keyCode == KeyEvent.KEYCODE_MINUS) {
            buffer.append('-');
            return DecodeResult.inProgress(buffer.toString());
        }
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            return DecodeResult.ignored(buffer.toString());
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            String value = buffer.toString();
            buffer.setLength(0);
            return VALID_READING.matcher(value).matches()
                ? DecodeResult.complete(value)
                : DecodeResult.invalid(value);
        }
        return DecodeResult.ignored(buffer.toString());
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
