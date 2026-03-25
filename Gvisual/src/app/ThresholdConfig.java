package app;

/**
 * Encapsulates the duration and meeting-count thresholds for each
 * relationship type used by {@link Network#generateFile}.
 *
 * <p>Replaces the 12-parameter method signature with a single,
 * self-documenting configuration object. Each relationship type
 * has a duration threshold and a count threshold whose semantics
 * depend on the category (minimum for friends/classmates, maximum
 * for strangers, etc.).</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * ThresholdConfig config = new ThresholdConfig.Builder()
 *     .friend(10, 2)
 *     .familiarStranger(2, 1)
 *     .classmate(30, 1)
 *     .stranger(2, 2)
 *     .studyGroup(20, 1)
 *     .build();
 *
 * Network.generateFile("graph.txt", "03", "15", config);
 * }</pre>
 *
 * @author zalenix
 */
public final class ThresholdConfig {

    private final int friendDuration;
    private final int friendCount;
    private final int familiarStrangerDuration;
    private final int familiarStrangerCount;
    private final int classmateDuration;
    private final int classmateCount;
    private final int strangerDuration;
    private final int strangerCount;
    private final int studyGroupDuration;
    private final int studyGroupCount;

    private ThresholdConfig(Builder builder) {
        this.friendDuration = builder.friendDuration;
        this.friendCount = builder.friendCount;
        this.familiarStrangerDuration = builder.familiarStrangerDuration;
        this.familiarStrangerCount = builder.familiarStrangerCount;
        this.classmateDuration = builder.classmateDuration;
        this.classmateCount = builder.classmateCount;
        this.strangerDuration = builder.strangerDuration;
        this.strangerCount = builder.strangerCount;
        this.studyGroupDuration = builder.studyGroupDuration;
        this.studyGroupCount = builder.studyGroupCount;
    }

    /** Minimum average duration threshold for friend edges. */
    public int getFriendDuration() { return friendDuration; }

    /** Minimum meeting count threshold for friend edges. */
    public int getFriendCount() { return friendCount; }

    /** Maximum duration threshold for familiar-stranger edges. */
    public int getFamiliarStrangerDuration() { return familiarStrangerDuration; }

    /** Minimum count threshold for familiar-stranger edges. */
    public int getFamiliarStrangerCount() { return familiarStrangerCount; }

    /** Minimum duration threshold for classmate edges. */
    public int getClassmateDuration() { return classmateDuration; }

    /** Minimum count threshold for classmate edges. */
    public int getClassmateCount() { return classmateCount; }

    /** Maximum duration threshold for stranger edges. */
    public int getStrangerDuration() { return strangerDuration; }

    /** Maximum count threshold for stranger edges. */
    public int getStrangerCount() { return strangerCount; }

    /** Minimum duration threshold for study-group edges. */
    public int getStudyGroupDuration() { return studyGroupDuration; }

    /** Maximum count threshold for study-group edges. */
    public int getStudyGroupCount() { return studyGroupCount; }

    @Override
    public String toString() {
        return "ThresholdConfig{"
                + "friend(" + friendDuration + "/" + friendCount + ")"
                + ", familiar(" + familiarStrangerDuration + "/" + familiarStrangerCount + ")"
                + ", classmate(" + classmateDuration + "/" + classmateCount + ")"
                + ", stranger(" + strangerDuration + "/" + strangerCount + ")"
                + ", studyGroup(" + studyGroupDuration + "/" + studyGroupCount + ")"
                + "}";
    }

    /**
     * Builder for {@link ThresholdConfig}.
     *
     * <p>Defaults match the {@link gvisual.EdgeType} default thresholds
     * so callers only need to override the values they care about.</p>
     */
    public static final class Builder {
        private int friendDuration = 10;
        private int friendCount = 2;
        private int familiarStrangerDuration = 2;
        private int familiarStrangerCount = 1;
        private int classmateDuration = 30;
        private int classmateCount = 1;
        private int strangerDuration = 2;
        private int strangerCount = 2;
        private int studyGroupDuration = 20;
        private int studyGroupCount = 1;

        public Builder() {}

        /**
         * Sets friend thresholds.
         * @param duration minimum average duration (minutes)
         * @param count    minimum meeting count
         */
        public Builder friend(int duration, int count) {
            this.friendDuration = duration;
            this.friendCount = count;
            return this;
        }

        /**
         * Sets familiar-stranger thresholds.
         * @param duration maximum duration (minutes)
         * @param count    minimum meeting count
         */
        public Builder familiarStranger(int duration, int count) {
            this.familiarStrangerDuration = duration;
            this.familiarStrangerCount = count;
            return this;
        }

        /**
         * Sets classmate thresholds.
         * @param duration minimum duration (minutes)
         * @param count    minimum meeting count
         */
        public Builder classmate(int duration, int count) {
            this.classmateDuration = duration;
            this.classmateCount = count;
            return this;
        }

        /**
         * Sets stranger thresholds.
         * @param duration maximum duration (minutes)
         * @param count    maximum meeting count
         */
        public Builder stranger(int duration, int count) {
            this.strangerDuration = duration;
            this.strangerCount = count;
            return this;
        }

        /**
         * Sets study-group thresholds.
         * @param duration minimum duration (minutes)
         * @param count    maximum meeting count
         */
        public Builder studyGroup(int duration, int count) {
            this.studyGroupDuration = duration;
            this.studyGroupCount = count;
            return this;
        }

        public ThresholdConfig build() {
            return new ThresholdConfig(this);
        }
    }
}
