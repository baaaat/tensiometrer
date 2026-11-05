package fr.lechappee.igagingdiag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the ordered measurements of one wheel survey.
 * The raw value is never altered; zeroing is deliberately not applied yet.
 */
public final class WheelSurveySession {
    private final WheelConfig config;
    private final CalibrationConverter converter;
    private final List<MeasurementRecord> records = new ArrayList<>();
    private MeasurementRecord.Side activeSide;
    private int activeSpoke = 1;

    public WheelSurveySession(WheelConfig config, CalibrationConverter converter) {
        if (config == null) throw new IllegalArgumentException("Missing wheel configuration");
        this.config = config;
        this.converter = converter;
        this.activeSide = config.firstSide == WheelConfig.FirstSide.LEFT
            ? MeasurementRecord.Side.LEFT : MeasurementRecord.Side.RIGHT;
    }

    public WheelConfig config() { return config; }
    public MeasurementRecord.Side activeSide() { return activeSide; }
    public int activeSpoke() { return activeSpoke; }
    public boolean isComplete() { return records.size() >= config.spokeCount * 2; }
    public List<MeasurementRecord> records() {
        return Collections.unmodifiableList(records);
    }

    /** Adds one ENTER-validated reading and advances to the next slot. */
    public MeasurementRecord accept(String rawReading, long timestampMillis) {
        if (isComplete()) throw new IllegalStateException("Survey is complete");
        String exact = rawReading == null ? "" : rawReading.trim();
        String category = activeSide == MeasurementRecord.Side.LEFT
            ? config.leftSpoke : config.rightSpoke;
        Double kgf = converter == null ? null : converter.toKgf(exact, category);
        MeasurementRecord record = new MeasurementRecord(activeSpoke, activeSide,
            exact, exact, exact, kgf, timestampMillis);
        records.add(record);
        advance();
        return record;
    }

    private void advance() {
        if (activeSpoke < config.spokeCount) {
            activeSpoke++;
        } else if (activeSide == (config.firstSide == WheelConfig.FirstSide.LEFT
                ? MeasurementRecord.Side.LEFT : MeasurementRecord.Side.RIGHT)) {
            activeSide = activeSide == MeasurementRecord.Side.LEFT
                ? MeasurementRecord.Side.RIGHT : MeasurementRecord.Side.LEFT;
            activeSpoke = 1;
        } else {
            activeSpoke = config.spokeCount;
        }
    }
}
