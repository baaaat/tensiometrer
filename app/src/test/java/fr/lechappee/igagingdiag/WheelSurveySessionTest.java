package fr.lechappee.igagingdiag;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WheelSurveySessionTest {
    private WheelConfig config() {
        return new WheelConfig("test", "steel_1.00", "steel_1.00", 20,
            WheelConfig.FirstSide.RIGHT, 114, 72);
    }

    @Test public void recordsExactValueWithoutApplyingZeroOrConversion() {
        WheelSurveySession session = new WheelSurveySession(config(), null);
        MeasurementRecord record = session.accept("-2.44", 123L);
        assertEquals("-2.44", record.rawReading);
        assertEquals("-2.44", record.originReading);
        assertEquals("-2.44", record.correctedReading);
        assertNull(record.tensionKgf);
        assertEquals(2, session.activeSpoke());
    }

    @Test public void completesFirstSideThenSecondSide() {
        WheelSurveySession session = new WheelSurveySession(config(), null);
        for (int i = 0; i < 20; i++) session.accept("0.00", i);
        assertEquals(MeasurementRecord.Side.LEFT, session.activeSide());
        assertEquals(1, session.activeSpoke());
    }
}
