package fr.lechappee.igagingdiag;

import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MeasurementDecoderTest {
    @Test public void decodesSignedReadingWithoutKeyboardLayout() {
        MeasurementDecoder decoder = new MeasurementDecoder();
        int[] codes = { KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_ENTER };
        MeasurementDecoder.DecodeResult result = null;
        for (int code : codes) result = decoder.acceptKeyCode(code);
        assertEquals(MeasurementDecoder.DecodeResult.Type.COMPLETE, result.type);
        assertEquals("-2.44", result.value);
    }

    @Test public void ignoresSpacesAndRequiresEnter() {
        MeasurementDecoder decoder = new MeasurementDecoder();
        decoder.acceptKeyCode(KeyEvent.KEYCODE_0);
        decoder.acceptKeyCode(KeyEvent.KEYCODE_SPACE);
        MeasurementDecoder.DecodeResult result = decoder.acceptKeyCode(KeyEvent.KEYCODE_PERIOD);
        assertEquals(MeasurementDecoder.DecodeResult.Type.IN_PROGRESS, result.type);
        assertEquals("0.", result.value);
    }

    @Test public void acceptsZeroAndRejectsIncompleteReadingAtEnter() {
        MeasurementDecoder decoder = new MeasurementDecoder();
        decoder.acceptKeyCode(KeyEvent.KEYCODE_0);
        decoder.acceptKeyCode(KeyEvent.KEYCODE_PERIOD);
        decoder.acceptKeyCode(KeyEvent.KEYCODE_0);
        decoder.acceptKeyCode(KeyEvent.KEYCODE_0);
        MeasurementDecoder.DecodeResult zero = decoder.acceptKeyCode(KeyEvent.KEYCODE_ENTER);
        assertEquals(MeasurementDecoder.DecodeResult.Type.COMPLETE, zero.type);
        assertEquals("0.00", zero.value);
        decoder.acceptKeyCode(KeyEvent.KEYCODE_ENTER);
        assertEquals(MeasurementDecoder.DecodeResult.Type.INVALID,
            decoder.acceptKeyCode(KeyEvent.KEYCODE_ENTER).type);
    }
}
