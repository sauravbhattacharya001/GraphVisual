package app;

/**
 * Configuration for a single meeting-relationship query.
 *
 * <p>Each relationship type (friends, classmates, study-groups, strangers,
 * familiar strangers) uses the same parameterised SQL pattern but differs
 * in location filter, duration comparison direction, and count comparison
 * direction.  This class captures those differences so that
 * {@link Network} can iterate a list of configs instead of repeating
 * five near-identical SQL strings.</p>
 *
 * @author zalenix
 */
public final class MeetingQueryConfig {

    /** Location filter mode — determines the WHERE clause for the location column. */
    public enum LocationFilter {
        /** Match a single exact location value (e.g. 'public', 'class'). */
        EXACT,
        /** Exclude a set of location values (NOT IN). */
        EXCLUDE
    }

    /** Comparison direction for the threshold in the outer query. */
    public enum Comparison {
        /** Use {@code >=} for the count threshold. */
        GTE,
        /** Use {@code <=} for the count threshold. */
        LTE,
        /** Use {@code <} for the count threshold. */
        LT,
        /** Use {@code >} for the count threshold. */
        GT
    }

    private final String edgePrefix;
    private final LocationFilter locationFilter;
    private final String[] locationValues;
    private final Comparison durationComparison;
    private final Comparison countComparison;
    private final int durationThreshold;
    private final int countThreshold;

    /**
     * @param edgePrefix          edge type prefix written to output (e.g. "f", "sg")
     * @param locationFilter      how to filter on the location column
     * @param locationValues      location value(s) for the filter
     * @param durationComparison  comparison operator for duration in the inner query
     * @param countComparison     comparison operator for count in the outer query
     * @param durationThreshold   duration threshold value
     * @param countThreshold      count threshold value
     */
    public MeetingQueryConfig(String edgePrefix,
                              LocationFilter locationFilter,
                              String[] locationValues,
                              Comparison durationComparison,
                              Comparison countComparison,
                              int durationThreshold,
                              int countThreshold) {
        this.edgePrefix = edgePrefix;
        this.locationFilter = locationFilter;
        this.locationValues = locationValues;
        this.durationComparison = durationComparison;
        this.countComparison = countComparison;
        this.durationThreshold = durationThreshold;
        this.countThreshold = countThreshold;
    }

    public String getEdgePrefix() { return edgePrefix; }
    public int getDurationThreshold() { return durationThreshold; }
    public int getCountThreshold() { return countThreshold; }

    /**
     * Builds the parameterised SQL query string for this configuration.
     *
     * <p>The query always expects 4 positional parameters:
     * {@code ?1=month, ?2=date, ?3=durationThreshold, ?4=countThreshold}.</p>
     *
     * @return the SQL query string
     */
    public String buildSql() {
        String durationOp = comparisonToSql(durationComparison);
        String countOp = comparisonToSql(countComparison);
        String locationClause = buildLocationClause();

        return " SELECT x.id , y.id , C , d "
                + " FROM ( SELECT imei1, imei2, count(*) as C, avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND " + locationClause
                + " AND duration " + durationOp + " ?) as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C " + countOp + " ? AND a.imei1= x.imei AND a.imei2 = y.imei";
    }

    private String buildLocationClause() {
        if (locationFilter == LocationFilter.EXACT) {
            return "location= '" + locationValues[0] + "'";
        } else {
            // NOT IN ('class', 'unknown', '')
            StringBuilder sb = new StringBuilder("location NOT IN (");
            for (int i = 0; i < locationValues.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append("'").append(locationValues[i]).append("'");
            }
            sb.append(")");
            return sb.toString();
        }
    }

    private static String comparisonToSql(Comparison c) {
        switch (c) {
            case GTE: return ">=";
            case LTE: return "<=";
            case LT:  return "<";
            case GT:  return ">";
            default:  return ">=";
        }
    }
}
