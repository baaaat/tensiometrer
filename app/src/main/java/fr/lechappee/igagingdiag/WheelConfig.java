package fr.lechappee.igagingdiag;

/** Immutable configuration entered before starting a wheel survey. */
public final class WheelConfig {
    public enum FirstSide { LEFT, RIGHT }

    public final String title;
    public final String leftSpoke;
    public final String rightSpoke;
    public final int spokeCount;
    public final FirstSide firstSide;
    public final double rightTargetKgf;
    public final double leftRatioPercent;

    public WheelConfig(String title, String leftSpoke, String rightSpoke, int spokeCount,
                       FirstSide firstSide, double rightTargetKgf, double leftRatioPercent) {
        if (spokeCount < 20 || spokeCount > 36 || spokeCount % 4 != 0) {
            throw new IllegalArgumentException("Spoke count must be 20, 24, 28, 32 or 36");
        }
        if (rightTargetKgf <= 0 || leftRatioPercent <= 0 || leftRatioPercent > 100) {
            throw new IllegalArgumentException("Targets must be positive and ratio must be <= 100");
        }
        this.title = title == null ? "" : title.trim();
        this.leftSpoke = leftSpoke == null ? "" : leftSpoke;
        this.rightSpoke = rightSpoke == null ? "" : rightSpoke;
        this.spokeCount = spokeCount;
        this.firstSide = firstSide == null ? FirstSide.RIGHT : firstSide;
        this.rightTargetKgf = rightTargetKgf;
        this.leftRatioPercent = leftRatioPercent;
    }

    public double leftTargetKgf() {
        return rightTargetKgf * leftRatioPercent / 100.0;
    }
}
