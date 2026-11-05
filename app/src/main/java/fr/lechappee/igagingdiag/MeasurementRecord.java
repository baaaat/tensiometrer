package fr.lechappee.igagingdiag;

/** One raw reading and its optional, explicitly supported conversion. */
public final class MeasurementRecord {
    public enum Side { LEFT, RIGHT }

    public final int spokeNumber;
    public final Side side;
    public final String rawReading;
    public final String originReading;
    public final String correctedReading;
    public final Double tensionKgf;
    public final long timestampMillis;

    public MeasurementRecord(int spokeNumber, Side side, String rawReading,
                             String originReading, String correctedReading,
                             Double tensionKgf, long timestampMillis) {
        this.spokeNumber = spokeNumber;
        this.side = side;
        this.rawReading = rawReading;
        this.originReading = originReading;
        this.correctedReading = correctedReading;
        this.tensionKgf = tensionKgf;
        this.timestampMillis = timestampMillis;
    }
}
