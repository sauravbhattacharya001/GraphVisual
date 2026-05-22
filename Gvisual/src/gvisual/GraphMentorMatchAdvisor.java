package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * GraphMentorMatchAdvisor &mdash; agentic mentor/mentee pairing advisor over a
 * <em>single</em> graph snapshot. 9th sibling to
 * {@link GraphFriendshipTriadAdvisor}, {@link GraphSignedConflictAdvisor},
 * {@link GraphCommunityChurnAdvisor}, {@link GraphCascadingFailureAdvisor},
 * {@link GraphInfluenceSeedAdvisor}, {@link GraphPrivacyExposureAuditor},
 * {@link GraphAdversaryForecaster} and {@link GraphIntelligenceAdvisor}.
 *
 * <p>Whereas {@code GraphFriendshipTriadAdvisor} <em>classifies</em>
 * triadic-closure profiles, this advisor <em>acts</em> on the result: for
 * every low-degree / isolated / socially-thin "mentee" vertex it recommends
 * up to N "mentor" candidates from the rest of the population, computing a
 * deterministic per-pair score and a cross-population coverage grade.
 *
 * <p>Score components (per (mentee, candidate)):</p>
 * <ul>
 *   <li>+30 when at least one common neighbour exists (a warm
 *       introducer-path is already available); +10 per extra shared
 *       neighbour up to +30 total bonus.</li>
 *   <li>+20 &times; (1 - load_fraction_of_cap) &mdash; prefer unloaded
 *       mentors so capacity is spread fairly.</li>
 *   <li>+15 when the candidate's clustering coefficient is in
 *       [0.30, 0.70] (a balanced mentor with structural diversity).</li>
 *   <li>+10 when the candidate's degree &ge; the median degree of the
 *       graph (well-connected enough to actually help).</li>
 *   <li>-25 if mentee and candidate are already directly connected
 *       (no-op pairing).</li>
 *   <li>-10 if the candidate is itself a mentee.</li>
 *   <li>Scores are scaled by the {@link RiskAppetite} multiplier
 *       (CAUTIOUS 0.95, BALANCED 1.00, AGGRESSIVE 1.10).</li>
 * </ul>
 *
 * <p>Per-mentee verdicts:
 * {@link MenteeVerdict#MATCHED MATCHED} (&ge; 1 high-score pairing),
 * {@link MenteeVerdict#WEAK_MATCH WEAK_MATCH} (&ge; 1 pairing all below the
 * high threshold), {@link MenteeVerdict#NO_MATCH NO_MATCH} (no acceptable
 * mentor). Non-mentees receive
 * {@link MenteeVerdict#NOT_A_MENTEE NOT_A_MENTEE}.</p>
 *
 * <p>Pure JDK + JUNG. Single file. Never mutates the input graph.
 * Deterministic given a fixed {@link Clock}. JSON output has stable
 * lexicographic key order on each object.</p>
 *
 * @author sauravbhattacharya001
 */
public final class GraphMentorMatchAdvisor<V, E> {

    // -- Public types ------------------------------------------------------

    public enum MenteeVerdict {
        MATCHED, WEAK_MATCH, NO_MATCH, NOT_A_MENTEE
    }
    public enum MentorVerdict {
        PRIME_MENTOR, WEAK_MENTOR, OVERLOADED, NOT_SUITABLE
    }
    public enum Priority { P0, P1, P2, P3 }
    public enum RiskAppetite { CAUTIOUS, BALANCED, AGGRESSIVE }
    public enum Grade { A, B, C, D, F }

    /** Recommended (mentor, score) pair for a mentee. */
    public static final class MentorRec<V> {
        public final V mentor;
        public final double score;
        public final int sharedNeighbours;
        public final boolean bridge; // true when sharedNeighbours >= 1
        public final boolean alreadyConnected;
        public MentorRec(V mentor, double score, int sharedNeighbours,
                         boolean bridge, boolean alreadyConnected) {
            this.mentor = mentor;
            this.score = score;
            this.sharedNeighbours = sharedNeighbours;
            this.bridge = bridge;
            this.alreadyConnected = alreadyConnected;
        }
    }

    public static final class MenteeMatch<V> {
        public final V mentee;
        public final MenteeVerdict verdict;
        public final Priority priority;
        public final int degree;
        public final double clusteringCoefficient;
        public final List<MentorRec<V>> mentors;
        public final List<String> reasons;
        public MenteeMatch(V mentee, MenteeVerdict verdict, Priority priority,
                           int degree, double clusteringCoefficient,
                           List<MentorRec<V>> mentors, List<String> reasons) {
            this.mentee = mentee;
            this.verdict = verdict;
            this.priority = priority;
            this.degree = degree;
            this.clusteringCoefficient = clusteringCoefficient;
            this.mentors = Collections.unmodifiableList(new ArrayList<>(mentors));
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
        }
    }

    public static final class MentorProfile<V> {
        public final V mentor;
        public final MentorVerdict verdict;
        public final int degree;
        public final double clusteringCoefficient;
        public final int assignedLoad;
        public final int cap;
        public MentorProfile(V mentor, MentorVerdict verdict, int degree,
                             double clusteringCoefficient, int assignedLoad, int cap) {
            this.mentor = mentor;
            this.verdict = verdict;
            this.degree = degree;
            this.clusteringCoefficient = clusteringCoefficient;
            this.assignedLoad = assignedLoad;
            this.cap = cap;
        }
    }

    public static final class Action {
        public final String id;
        public final Priority priority;
        public final String label;
        public final String reason;
        public final String owner;
        public final int blastRadius;
        public final String reversibility;
        public final List<Object> targets;
        public Action(String id, Priority priority, String label, String reason,
                      String owner, int blastRadius, String reversibility,
                      List<Object> targets) {
            this.id = id;
            this.priority = priority;
            this.label = label;
            this.reason = reason;
            this.owner = owner;
            this.blastRadius = blastRadius;
            this.reversibility = reversibility;
            this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
        }
    }

    public static final class Plan {
        public final Instant generatedAt;
        public final RiskAppetite riskAppetite;
        public final Grade grade;
        public final String headline;
        public final double matchedFraction;
        public final double weakFraction;
        public final double unmatchedFraction;
        public final double mentorUtilization;
        public final double bridgeMatchFraction;
        public final List<MenteeMatch<?>> mentees;
        public final List<MentorProfile<?>> mentors;
        public final List<Action> playbook;
        public final List<String> insights;
        public Plan(Instant generatedAt, RiskAppetite riskAppetite, Grade grade,
                    String headline, double matchedFraction, double weakFraction,
                    double unmatchedFraction, double mentorUtilization,
                    double bridgeMatchFraction,
                    List<? extends MenteeMatch<?>> mentees,
                    List<? extends MentorProfile<?>> mentors,
                    List<Action> playbook, List<String> insights) {
            this.generatedAt = generatedAt;
            this.riskAppetite = riskAppetite;
            this.grade = grade;
            this.headline = headline;
            this.matchedFraction = matchedFraction;
            this.weakFraction = weakFraction;
            this.unmatchedFraction = unmatchedFraction;
            this.mentorUtilization = mentorUtilization;
            this.bridgeMatchFraction = bridgeMatchFraction;
            this.mentees = Collections.unmodifiableList(new ArrayList<MenteeMatch<?>>(mentees));
            this.mentors = Collections.unmodifiableList(new ArrayList<MentorProfile<?>>(mentors));
            this.playbook = Collections.unmodifiableList(new ArrayList<>(playbook));
            this.insights = Collections.unmodifiableList(new ArrayList<>(insights));
        }
    }

    // -- Inputs ------------------------------------------------------------

    private static final double HIGH_SCORE_THRESHOLD = 35.0;
    private static final double MIN_ACCEPTABLE_SCORE = 10.0;

    private final Graph<V, E> graph;
    private RiskAppetite appetite = RiskAppetite.BALANCED;
    private Clock clock = Clock.systemUTC();
    private int menteeDegreeThreshold = 2;
    private int maxMentorsPerMentee = 3;
    private int topMentorPoolSize = 8;

    public GraphMentorMatchAdvisor(Graph<V, E> graph) {
        if (graph == null) throw new IllegalArgumentException("graph must be non-null");
        this.graph = graph;
    }

    public GraphMentorMatchAdvisor<V, E> withRiskAppetite(RiskAppetite a) {
        if (a != null) this.appetite = a; return this;
    }
    public GraphMentorMatchAdvisor<V, E> withFixedClock(Clock c) {
        if (c != null) this.clock = c; return this;
    }
    public GraphMentorMatchAdvisor<V, E> withMenteeDegreeThreshold(int t) {
        if (t >= 0) this.menteeDegreeThreshold = t; return this;
    }
    public GraphMentorMatchAdvisor<V, E> withMaxMentorsPerMentee(int n) {
        if (n >= 1) this.maxMentorsPerMentee = n; return this;
    }
    public GraphMentorMatchAdvisor<V, E> withTopMentorPoolSize(int n) {
        if (n >= 1) this.topMentorPoolSize = n; return this;
    }

    // -- Core analysis -----------------------------------------------------

    public Plan analyze() {
        // Build undirected neighbour map.
        Map<V, Set<V>> nbrs = new LinkedHashMap<>();
        for (V v : graph.getVertices()) nbrs.put(v, new LinkedHashSet<V>());
        for (E e : graph.getEdges()) {
            Collection<V> inc = graph.getIncidentVertices(e);
            Iterator<V> it = inc.iterator();
            if (!it.hasNext()) continue;
            V a = it.next();
            V b = it.hasNext() ? it.next() : a;
            if (a == null || b == null || a.equals(b)) continue;
            nbrs.get(a).add(b);
            nbrs.get(b).add(a);
        }

        // Empty-graph short-circuit.
        if (nbrs.isEmpty()) {
            return emptyPlan();
        }

        double appetiteMult = appetiteMultiplier(appetite);

        // Per-node degree + clustering.
        Map<V, Integer> degree = new LinkedHashMap<>();
        Map<V, Double> clustering = new LinkedHashMap<>();
        List<Integer> degSeq = new ArrayList<>();
        for (V v : nbrs.keySet()) {
            Set<V> n = nbrs.get(v);
            int deg = n.size();
            degree.put(v, deg);
            degSeq.add(deg);
            int closed = 0;
            for (V u : n) {
                for (V w : n) {
                    if (u.equals(w)) continue;
                    if (nbrs.get(u).contains(w)) closed++;
                }
            }
            closed /= 2;
            int possible = deg * (deg - 1) / 2;
            double cc = (possible == 0) ? 0.0 : (double) closed / (double) possible;
            clustering.put(v, cc);
        }

        double medianDegree = median(degSeq);

        // Identify mentee + mentor candidate pools.
        int mentorMinDegree = Math.max(4, 2 * menteeDegreeThreshold);
        List<V> mentees = new ArrayList<>();
        List<V> mentorCandidates = new ArrayList<>();
        Set<V> menteeSet = new LinkedHashSet<>();
        for (V v : nbrs.keySet()) {
            int d = degree.get(v);
            if (d <= menteeDegreeThreshold) {
                mentees.add(v);
                menteeSet.add(v);
            }
            if (d >= mentorMinDegree) {
                mentorCandidates.add(v);
            }
        }

        // Stable mentee order: degree asc, then string asc.
        mentees.sort(new Comparator<V>() {
            @Override public int compare(V a, V b) {
                int da = degree.get(a), db = degree.get(b);
                if (da != db) return Integer.compare(da, db);
                return Objects.toString(a).compareTo(Objects.toString(b));
            }
        });

        // Mentor cap. A small per-mentor cap so a single popular mentor can
        // become OVERLOADED when many mentees compete for them.
        int cap = Math.max(2, (int) Math.ceil(
                mentees.size() / (double) Math.max(1, mentorCandidates.size())));

        // Pre-rank mentor pool (top-N by quality score) so we always consider
        // a deterministic prefix.
        List<V> mentorPool = new ArrayList<>(mentorCandidates);
        mentorPool.sort(new Comparator<V>() {
            @Override public int compare(V a, V b) {
                double ca = clustering.get(a), cb = clustering.get(b);
                double qa = degree.get(a) + balancedBonus(ca);
                double qb = degree.get(b) + balancedBonus(cb);
                if (Double.compare(qb, qa) != 0) return Double.compare(qb, qa);
                return Objects.toString(a).compareTo(Objects.toString(b));
            }
        });
        if (mentorPool.size() > topMentorPoolSize) {
            mentorPool = new ArrayList<>(mentorPool.subList(0, topMentorPoolSize));
        }

        Map<V, Integer> load = new LinkedHashMap<>();
        for (V m : mentorPool) load.put(m, 0);

        List<MenteeMatch<?>> menteeMatches = new ArrayList<>();
        int matchedCount = 0, weakCount = 0, unmatchedCount = 0;
        int bridgeMatches = 0, totalAssignments = 0;

        for (V mentee : mentees) {
            final int mdeg = degree.get(mentee);
            final Set<V> mNbrs = nbrs.get(mentee);

            // Score each pool mentor for this mentee.
            List<MentorRec<V>> scored = new ArrayList<>();
            for (V cand : mentorPool) {
                if (cand.equals(mentee)) continue;
                int curLoad = load.get(cand);
                if (curLoad >= cap) continue; // OVERLOADED — skip
                Set<V> cNbrs = nbrs.get(cand);
                int shared = 0;
                for (V x : mNbrs) if (cNbrs.contains(x)) shared++;
                boolean directlyConnected = cNbrs.contains(mentee);

                double s = 0.0;
                if (shared >= 1) {
                    s += 30.0;
                    int extra = Math.min(3, shared - 1);
                    s += 10.0 * extra;
                }
                s += 20.0 * (1.0 - (curLoad / (double) cap));
                double cc = clustering.get(cand);
                if (cc >= 0.30 && cc <= 0.70) s += 15.0;
                if (degree.get(cand) >= medianDegree) s += 10.0;
                if (directlyConnected) s -= 25.0;
                if (menteeSet.contains(cand)) s -= 10.0;
                s *= appetiteMult;
                s = round1(s);

                scored.add(new MentorRec<V>(cand, s, shared,
                        shared >= 1, directlyConnected));
            }

            // Sort by score desc, then mentor-string asc.
            final Map<V, Integer> degRef = degree;
            scored.sort(new Comparator<MentorRec<V>>() {
                @Override public int compare(MentorRec<V> a, MentorRec<V> b) {
                    int sc = Double.compare(b.score, a.score);
                    if (sc != 0) return sc;
                    return Objects.toString(a.mentor).compareTo(Objects.toString(b.mentor));
                }
            });

            List<MentorRec<V>> picked = new ArrayList<>();
            for (MentorRec<V> r : scored) {
                if (picked.size() >= maxMentorsPerMentee) break;
                if (r.score < MIN_ACCEPTABLE_SCORE) break;
                picked.add(r);
                load.put(r.mentor, load.get(r.mentor) + 1);
                totalAssignments++;
                if (r.bridge) bridgeMatches++;
            }

            List<String> reasons = new ArrayList<>();
            MenteeVerdict mv;
            Priority pr;
            if (picked.isEmpty()) {
                mv = MenteeVerdict.NO_MATCH;
                unmatchedCount++;
                if (mdeg <= 1) {
                    pr = Priority.P0;
                    reasons.add("ISOLATED_NO_MATCH");
                } else {
                    pr = Priority.P1;
                    reasons.add("NO_ACCEPTABLE_MENTOR");
                }
            } else {
                boolean anyHigh = false, anyBridge = false;
                for (MentorRec<V> r : picked) {
                    if (r.score >= HIGH_SCORE_THRESHOLD) anyHigh = true;
                    if (r.bridge) anyBridge = true;
                }
                if (anyHigh) {
                    mv = MenteeVerdict.MATCHED;
                    matchedCount++;
                    reasons.add(anyBridge ? "BRIDGE_MATCH" : "STRONG_MATCH");
                    pr = anyBridge ? Priority.P1 : Priority.P3;
                } else {
                    mv = MenteeVerdict.WEAK_MATCH;
                    weakCount++;
                    pr = Priority.P2;
                    reasons.add("WEAK_PAIRING_ONLY");
                }
            }
            if (mdeg == 0) reasons.add("DEGREE_ZERO");
            menteeMatches.add(new MenteeMatch<V>(mentee, mv, pr, mdeg,
                    round3(clustering.get(mentee)), picked, reasons));
        }

        // Sort mentees: priority asc (P0 first), then string asc.
        menteeMatches.sort(new Comparator<MenteeMatch<?>>() {
            @Override public int compare(MenteeMatch<?> a, MenteeMatch<?> b) {
                int p = Integer.compare(a.priority.ordinal(), b.priority.ordinal());
                if (p != 0) return p;
                return Objects.toString(a.mentee).compareTo(Objects.toString(b.mentee));
            }
        });

        // Build mentor profiles.
        List<MentorProfile<?>> mentorProfiles = new ArrayList<>();
        for (V cand : mentorPool) {
            int l = load.get(cand);
            double cc = clustering.get(cand);
            MentorVerdict mv;
            if (l >= cap) mv = MentorVerdict.OVERLOADED;
            else if (cc >= 0.20 && cc <= 0.75 && degree.get(cand) >= mentorMinDegree)
                mv = MentorVerdict.PRIME_MENTOR;
            else mv = MentorVerdict.WEAK_MENTOR;
            mentorProfiles.add(new MentorProfile<V>(cand, mv, degree.get(cand),
                    round3(cc), l, cap));
        }
        // Include also non-pool mentor candidates as NOT_SUITABLE? Skip — pool is
        // already the considered set; but include any candidate that did not
        // make the pool as WEAK_MENTOR for visibility.
        for (V cand : mentorCandidates) {
            boolean inPool = false;
            for (MentorProfile<?> mp : mentorProfiles) {
                if (mp.mentor.equals(cand)) { inPool = true; break; }
            }
            if (!inPool) {
                mentorProfiles.add(new MentorProfile<V>(cand, MentorVerdict.WEAK_MENTOR,
                        degree.get(cand), round3(clustering.get(cand)), 0, cap));
            }
        }
        // Stable order: verdict ordinal, then string.
        mentorProfiles.sort(new Comparator<MentorProfile<?>>() {
            @Override public int compare(MentorProfile<?> a, MentorProfile<?> b) {
                int p = Integer.compare(a.verdict.ordinal(), b.verdict.ordinal());
                if (p != 0) return p;
                return Objects.toString(a.mentor).compareTo(Objects.toString(b.mentor));
            }
        });

        int menteeTotal = Math.max(1, mentees.size());
        double matchedFrac = matchedCount / (double) menteeTotal;
        double weakFrac = weakCount / (double) menteeTotal;
        double unmatchedFrac = unmatchedCount / (double) menteeTotal;
        double util = 0.0;
        if (!mentorPool.isEmpty()) {
            int sum = 0;
            for (V m : mentorPool) sum += load.get(m);
            util = sum / (double) (mentorPool.size() * cap);
        }
        double bridgeFrac = (totalAssignments == 0) ? 0.0
                : bridgeMatches / (double) totalAssignments;

        List<Action> playbook = buildPlaybook(menteeMatches, mentorProfiles,
                mentees.size(), mentorPool.isEmpty(),
                matchedFrac, weakFrac, unmatchedFrac, util, bridgeFrac);
        List<String> insights = buildInsights(mentees.size(), mentorPool.isEmpty(),
                matchedFrac, unmatchedFrac, util, bridgeFrac, menteeMatches);

        Grade grade = grade(mentees.size(), mentorPool.isEmpty(),
                matchedFrac, unmatchedFrac);
        String headline = String.format(Locale.ROOT,
                "VERDICT: grade %s on %d mentees (matched=%s weak=%s unmatched=%s utilization=%s)",
                grade.name(), mentees.size(),
                pct(matchedFrac), pct(weakFrac), pct(unmatchedFrac), pct(util));

        return new Plan(clock.instant().atZone(ZoneOffset.UTC).toInstant(), appetite,
                grade, headline, round3(matchedFrac), round3(weakFrac),
                round3(unmatchedFrac), round3(util), round3(bridgeFrac),
                menteeMatches, mentorProfiles, playbook, insights);
    }

    private Plan emptyPlan() {
        List<Action> pb = new ArrayList<>();
        pb.add(new Action("EMPTY_GRAPH", Priority.P3, "Provide a non-empty graph",
                "No vertices found", "researcher", 1, "high",
                Collections.<Object>emptyList()));
        List<String> ins = new ArrayList<>();
        ins.add("EMPTY_SNAPSHOT");
        return new Plan(clock.instant().atZone(ZoneOffset.UTC).toInstant(),
                appetite, Grade.A,
                "VERDICT: grade A on 0 mentees (matched=0% weak=0% unmatched=0% utilization=0%)",
                0.0, 0.0, 0.0, 0.0, 0.0,
                Collections.<MenteeMatch<?>>emptyList(),
                Collections.<MentorProfile<?>>emptyList(),
                pb, ins);
    }

    // -- Playbook ----------------------------------------------------------

    private List<Action> buildPlaybook(List<MenteeMatch<?>> menteeMatches,
                                       List<MentorProfile<?>> mentorProfiles,
                                       int menteeCount, boolean noMentors,
                                       double matchedFrac, double weakFrac,
                                       double unmatchedFrac, double util,
                                       double bridgeFrac) {
        List<Action> out = new ArrayList<>();

        List<Object> isolatedNoMatch = new ArrayList<>();
        List<Object> allNoMatch = new ArrayList<>();
        List<Object> bridgeTargets = new ArrayList<>();
        List<Object> overloadedMentors = new ArrayList<>();
        int matchCount = 0;
        for (MenteeMatch<?> m : menteeMatches) {
            if (m.verdict == MenteeVerdict.NO_MATCH) {
                allNoMatch.add(m.mentee);
                if (m.degree <= 1) isolatedNoMatch.add(m.mentee);
            }
            if (m.verdict == MenteeVerdict.MATCHED || m.verdict == MenteeVerdict.WEAK_MATCH) {
                matchCount++;
                for (MentorRec<?> r : m.mentors) {
                    if (r.bridge) {
                        bridgeTargets.add(Objects.toString(m.mentee)
                                + "->" + Objects.toString(r.mentor));
                    }
                }
            }
        }
        for (MentorProfile<?> mp : mentorProfiles) {
            if (mp.verdict == MentorVerdict.OVERLOADED) overloadedMentors.add(mp.mentor);
        }

        // P0: recruit external mentors when coverage is poor.
        if (menteeCount > 0 && (unmatchedFrac >= 0.20 || isolatedNoMatch.size() >= 3
                || (noMentors && menteeCount > 0))) {
            out.add(new Action("RECRUIT_EXTERNAL_MENTORS", Priority.P0,
                    "Recruit external mentors to cover unmatched mentees",
                    "Unmatched fraction is " + pct(unmatchedFrac)
                            + (noMentors ? "; no internal mentors available" : ""),
                    "research_chair", 4, "medium",
                    Collections.<Object>emptyList()));
        }

        // P0: pair up isolated mentees.
        if (!isolatedNoMatch.isEmpty()) {
            out.add(new Action("PAIR_UP_ISOLATED_MENTEES", Priority.P0,
                    "Hand-match isolated mentees to senior peers",
                    isolatedNoMatch.size() + " isolated mentees have NO_MATCH",
                    "community_lead", 3, "high",
                    capTargets(isolatedNoMatch, 10)));
        }

        // P1: launch mentorship program when any matches exist.
        if (matchCount > 0) {
            out.add(new Action("LAUNCH_MENTORSHIP_PROGRAM", Priority.P1,
                    "Kick off mentorship pairings based on the proposed plan",
                    matchCount + " mentees have at least one proposed mentor",
                    "community_lead", 2, "medium",
                    Collections.<Object>emptyList()));
        }

        // P1: redistribute overloaded mentors.
        if (!overloadedMentors.isEmpty()) {
            out.add(new Action("REDISTRIBUTE_OVERLOADED_MENTORS", Priority.P1,
                    "Spread load away from overloaded mentors",
                    overloadedMentors.size() + " mentors are at cap",
                    "research_chair", 3, "high",
                    capTargets(overloadedMentors, 8)));
        }

        // P1: warm introductions via shared friends.
        if (!bridgeTargets.isEmpty()) {
            out.add(new Action("WARM_INTRODUCE_VIA_SHARED_FRIEND", Priority.P1,
                    "Use shared neighbours to warm-introduce matched pairs",
                    bridgeTargets.size() + " pairings have at least one shared neighbour",
                    "community_lead", 2, "high",
                    capTargets(bridgeTargets, 10)));
        }

        // P2: training / pool growth / followup.
        if (util >= 0.75) {
            out.add(new Action("EXPAND_MENTOR_TRAINING", Priority.P2,
                    "Train additional mentors before utilization saturates",
                    "Mentor utilization is " + pct(util),
                    "research_chair", 3, "medium",
                    Collections.<Object>emptyList()));
        }
        if (menteeCount == 0) {
            out.add(new Action("SOLICIT_NEW_MENTEES", Priority.P2,
                    "Solicit new mentees - none detected this term",
                    "Mentee pool is empty",
                    "community_lead", 2, "high",
                    Collections.<Object>emptyList()));
        }

        // Cautious tail.
        if (appetite == RiskAppetite.CAUTIOUS) {
            Grade g = grade(menteeCount, noMentors, matchedFrac, unmatchedFrac);
            if (g != Grade.A) {
                out.add(new Action("SCHEDULE_FOLLOWUP_AUDIT", Priority.P2,
                        "Re-run the mentor-match audit next term",
                        "Cautious appetite recommends a periodic check-in",
                        "research_chair", 1, "high",
                        Collections.<Object>emptyList()));
            }
        }

        // Healthy fallback.
        boolean any = false;
        for (Action a : out) {
            if (a.priority != Priority.P3) { any = true; break; }
        }
        if (!any) {
            out.add(new Action("HEALTHY_MENTORSHIP_PIPELINE", Priority.P3,
                    "Maintain current mentorship programming",
                    "No urgent gaps detected",
                    "community_lead", 1, "high",
                    Collections.<Object>emptyList()));
        }

        // Aggressive trim: drop lone P2 when any P0/P1 present.
        if (appetite == RiskAppetite.AGGRESSIVE) {
            boolean hasUrgent = false;
            int p2count = 0;
            for (Action a : out) {
                if (a.priority == Priority.P0 || a.priority == Priority.P1) hasUrgent = true;
                if (a.priority == Priority.P2) p2count++;
            }
            if (hasUrgent && p2count == 1) {
                List<Action> trimmed = new ArrayList<>();
                for (Action a : out) {
                    if (a.priority == Priority.P2) continue;
                    trimmed.add(a);
                }
                out = trimmed;
            }
        }

        // Stable sort: priority then id.
        out.sort(new Comparator<Action>() {
            @Override public int compare(Action a, Action b) {
                int p = Integer.compare(a.priority.ordinal(), b.priority.ordinal());
                if (p != 0) return p;
                return a.id.compareTo(b.id);
            }
        });
        return out;
    }

    private List<Object> capTargets(List<Object> in, int max) {
        if (in.size() <= max) return new ArrayList<>(in);
        return new ArrayList<>(in.subList(0, max));
    }

    // -- Insights ----------------------------------------------------------

    private List<String> buildInsights(int menteeCount, boolean noMentors,
                                       double matchedFrac, double unmatchedFrac,
                                       double util, double bridgeFrac,
                                       List<MenteeMatch<?>> menteeMatches) {
        List<String> out = new ArrayList<>();
        if (menteeCount == 0) {
            out.add("NO_MENTEES");
            return out;
        }
        if (noMentors) out.add("NO_MENTORS_AVAILABLE");
        if (unmatchedFrac >= 0.20) out.add("HIGH_UNMATCHED_LOAD");
        int isolatedNoMatch = 0;
        for (MenteeMatch<?> m : menteeMatches) {
            if (m.verdict == MenteeVerdict.NO_MATCH && m.degree <= 1) isolatedNoMatch++;
        }
        if (isolatedNoMatch >= 3) out.add("ISOLATED_CLUSTER_DETECTED");
        if (matchedFrac >= 0.80) out.add("WELL_COVERED_PIPELINE");
        if (util >= 0.85) out.add("MENTOR_BURNOUT_RISK");
        if (bridgeFrac >= 0.30) out.add("BRIDGE_OPPORTUNITIES_PRESENT");
        if (out.isEmpty()) out.add("HEALTHY_MENTORSHIP_PROFILE");
        return out;
    }

    // -- Grade --------------------------------------------------------------

    private Grade grade(int menteeCount, boolean noMentors,
                        double matchedFrac, double unmatchedFrac) {
        if (menteeCount == 0) return Grade.A;
        if (noMentors) return Grade.F;
        if (unmatchedFrac >= 0.40) return Grade.F;
        if (unmatchedFrac >= 0.20) return Grade.D;
        if (matchedFrac < 0.50) return Grade.C;
        if (matchedFrac < 0.80) return Grade.B;
        return Grade.A;
    }

    // -- Helpers -----------------------------------------------------------

    private static double appetiteMultiplier(RiskAppetite a) {
        switch (a) {
            case CAUTIOUS:   return 0.95;
            case AGGRESSIVE: return 1.10;
            default:          return 1.00;
        }
    }

    private static double balancedBonus(double cc) {
        return (cc >= 0.30 && cc <= 0.70) ? 1.5 : 0.0;
    }

    private static double median(List<Integer> xs) {
        if (xs.isEmpty()) return 0.0;
        List<Integer> s = new ArrayList<>(xs);
        Collections.sort(s);
        int n = s.size();
        if (n % 2 == 1) return s.get(n / 2);
        return (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }
    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v * 100.0);
    }

    // -- Renderers ---------------------------------------------------------

    public String toText(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(plan.headline).append('\n');
        sb.append("Generated at: ").append(plan.generatedAt).append('\n');
        sb.append("Risk appetite: ").append(plan.riskAppetite).append('\n');
        sb.append(String.format(Locale.ROOT,
                "matched=%s weak=%s unmatched=%s utilization=%s bridge_match=%s%n",
                pct(plan.matchedFraction), pct(plan.weakFraction),
                pct(plan.unmatchedFraction), pct(plan.mentorUtilization),
                pct(plan.bridgeMatchFraction)));
        sb.append("\n== Mentees ==\n");
        int shown = 0;
        for (MenteeMatch<?> m : plan.mentees) {
            if (shown++ >= 20) break;
            sb.append(String.format(Locale.ROOT,
                    "  [%s] %-20s verdict=%s deg=%d mentors=%d reasons=%s%n",
                    m.priority, Objects.toString(m.mentee), m.verdict,
                    m.degree, m.mentors.size(), m.reasons));
            for (MentorRec<?> r : m.mentors) {
                sb.append(String.format(Locale.ROOT,
                        "      -> %-20s score=%.1f shared=%d bridge=%s%n",
                        Objects.toString(r.mentor), r.score, r.sharedNeighbours, r.bridge));
            }
        }
        sb.append("\n== Mentors ==\n");
        int ms = 0;
        for (MentorProfile<?> mp : plan.mentors) {
            if (ms++ >= 20) break;
            sb.append(String.format(Locale.ROOT,
                    "  %-20s verdict=%s deg=%d cc=%.2f load=%d/%d%n",
                    Objects.toString(mp.mentor), mp.verdict, mp.degree,
                    mp.clusteringCoefficient, mp.assignedLoad, mp.cap));
        }
        sb.append("\n== Playbook ==\n");
        for (Action a : plan.playbook) {
            sb.append(String.format(Locale.ROOT,
                    "  [%s] %s (owner=%s blast=%d rev=%s) - %s%n",
                    a.priority, a.label, a.owner, a.blastRadius,
                    a.reversibility, a.reason));
        }
        sb.append("\n== Insights ==\n");
        for (String i : plan.insights) sb.append("  - ").append(i).append('\n');
        return sb.toString();
    }

    public String toMarkdown(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Graph Mentor Match\n\n");
        sb.append("**").append(plan.headline).append("**\n\n");
        sb.append("- Generated at: ").append(plan.generatedAt).append('\n');
        sb.append("- Risk appetite: ").append(plan.riskAppetite).append('\n');
        sb.append("- Matched: ").append(pct(plan.matchedFraction))
          .append(" | Weak: ").append(pct(plan.weakFraction))
          .append(" | Unmatched: ").append(pct(plan.unmatchedFraction)).append('\n');
        sb.append("- Mentor utilization: ").append(pct(plan.mentorUtilization))
          .append(" | Bridge matches: ").append(pct(plan.bridgeMatchFraction)).append("\n\n");
        sb.append("## Mentees\n\n");
        sb.append("| Priority | Node | Verdict | Mentors | Score | Shared | Bridge | Reasons |\n");
        sb.append("|---|---|---|---|---:|---:|:---:|---|\n");
        int shown = 0;
        for (MenteeMatch<?> m : plan.mentees) {
            if (shown++ >= 20) break;
            StringBuilder mns = new StringBuilder();
            StringBuilder scs = new StringBuilder();
            StringBuilder shs = new StringBuilder();
            StringBuilder brs = new StringBuilder();
            for (int i = 0; i < m.mentors.size(); i++) {
                if (i > 0) { mns.append("<br/>"); scs.append("<br/>");
                             shs.append("<br/>"); brs.append("<br/>"); }
                MentorRec<?> r = m.mentors.get(i);
                mns.append(Objects.toString(r.mentor));
                scs.append(String.format(Locale.ROOT, "%.1f", r.score));
                shs.append(r.sharedNeighbours);
                brs.append(r.bridge ? "Y" : "-");
            }
            sb.append("| ").append(m.priority)
              .append(" | ").append(Objects.toString(m.mentee))
              .append(" | ").append(m.verdict)
              .append(" | ").append(mns)
              .append(" | ").append(scs)
              .append(" | ").append(shs)
              .append(" | ").append(brs)
              .append(" | ").append(m.reasons)
              .append(" |\n");
        }
        sb.append("\n## Playbook\n\n");
        for (Action a : plan.playbook) {
            sb.append("- **[").append(a.priority).append("] ")
              .append(a.label).append("** — ").append(a.reason)
              .append(" (owner: ").append(a.owner)
              .append(", blast: ").append(a.blastRadius)
              .append(", reversibility: ").append(a.reversibility).append(")\n");
        }
        sb.append("\n## Insights\n\n");
        for (String i : plan.insights) sb.append("- ").append(i).append('\n');
        return sb.toString();
    }

    public String toJson(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"bridge_match_fraction\": ").append(plan.bridgeMatchFraction).append(",\n");
        sb.append("  \"generated_at\": ").append(jstr(Objects.toString(plan.generatedAt))).append(",\n");
        sb.append("  \"grade\": ").append(jstr(plan.grade.name())).append(",\n");
        sb.append("  \"headline\": ").append(jstr(plan.headline)).append(",\n");
        sb.append("  \"matched_fraction\": ").append(plan.matchedFraction).append(",\n");
        sb.append("  \"mentor_utilization\": ").append(plan.mentorUtilization).append(",\n");
        sb.append("  \"risk_appetite\": ").append(jstr(plan.riskAppetite.name())).append(",\n");
        sb.append("  \"unmatched_fraction\": ").append(plan.unmatchedFraction).append(",\n");
        sb.append("  \"weak_fraction\": ").append(plan.weakFraction).append(",\n");

        sb.append("  \"mentees\": [");
        boolean first = true;
        for (MenteeMatch<?> m : plan.mentees) {
            if (!first) sb.append(','); first = false;
            sb.append("\n    {");
            sb.append("\"clustering_coefficient\": ").append(m.clusteringCoefficient);
            sb.append(", \"degree\": ").append(m.degree);
            sb.append(", \"mentee\": ").append(jstr(Objects.toString(m.mentee)));
            sb.append(", \"mentors\": [");
            boolean f2 = true;
            for (MentorRec<?> r : m.mentors) {
                if (!f2) sb.append(','); f2 = false;
                sb.append("{");
                sb.append("\"already_connected\": ").append(r.alreadyConnected);
                sb.append(", \"bridge\": ").append(r.bridge);
                sb.append(", \"mentor\": ").append(jstr(Objects.toString(r.mentor)));
                sb.append(", \"score\": ").append(r.score);
                sb.append(", \"shared_neighbours\": ").append(r.sharedNeighbours);
                sb.append("}");
            }
            sb.append("]");
            sb.append(", \"priority\": ").append(jstr(m.priority.name()));
            sb.append(", \"reasons\": ").append(jstrArr(m.reasons));
            sb.append(", \"verdict\": ").append(jstr(m.verdict.name()));
            sb.append("}");
        }
        sb.append("\n  ],\n");

        sb.append("  \"mentors\": [");
        first = true;
        for (MentorProfile<?> mp : plan.mentors) {
            if (!first) sb.append(','); first = false;
            sb.append("\n    {");
            sb.append("\"assigned_load\": ").append(mp.assignedLoad);
            sb.append(", \"cap\": ").append(mp.cap);
            sb.append(", \"clustering_coefficient\": ").append(mp.clusteringCoefficient);
            sb.append(", \"degree\": ").append(mp.degree);
            sb.append(", \"mentor\": ").append(jstr(Objects.toString(mp.mentor)));
            sb.append(", \"verdict\": ").append(jstr(mp.verdict.name()));
            sb.append("}");
        }
        sb.append("\n  ],\n");

        sb.append("  \"playbook\": [");
        first = true;
        for (Action a : plan.playbook) {
            if (!first) sb.append(','); first = false;
            sb.append("\n    {");
            sb.append("\"blast_radius\": ").append(a.blastRadius);
            sb.append(", \"id\": ").append(jstr(a.id));
            sb.append(", \"label\": ").append(jstr(a.label));
            sb.append(", \"owner\": ").append(jstr(a.owner));
            sb.append(", \"priority\": ").append(jstr(a.priority.name()));
            sb.append(", \"reason\": ").append(jstr(a.reason));
            sb.append(", \"reversibility\": ").append(jstr(a.reversibility));
            List<String> tgts = new ArrayList<>();
            for (Object o : a.targets) tgts.add(Objects.toString(o));
            sb.append(", \"targets\": ").append(jstrArr(tgts));
            sb.append("}");
        }
        sb.append("\n  ],\n");
        sb.append("  \"insights\": ").append(jstrArr(plan.insights)).append('\n');
        sb.append("}\n");
        return sb.toString();
    }

    private static String jstr(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String jstrArr(List<String> items) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (String s : items) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(jstr(s));
        }
        sb.append(']');
        return sb.toString();
    }

    // Kept to silence unused-import warnings on some toolchains.
    @SuppressWarnings("unused")
    private static final List<?> _UNUSED = Arrays.asList();
}
