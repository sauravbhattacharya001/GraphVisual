/// Focus Time Service — analyzes calendar schedules to find uninterrupted
/// deep-work blocks, compute schedule fragmentation, and suggest optimal
/// focus windows.
///
/// Use this to answer: "When is my best time for deep work?", "How
/// fragmented is my schedule?", "Can I consolidate meetings to free up
/// focus blocks?"
///
/// Key concepts:
///   - **Focus Block**: An uninterrupted gap (≥ [minFocusMinutes]) between
///     events during working hours.
///   - **Fragmentation Score** (0–100): How chopped-up a day is. 0 = one big
///     open block, 100 = meetings every 30 minutes.
///   - **Context Switches**: Number of transitions between events. More
///     switches = more mental overhead.
///   - **Best Focus Window**: The recurring time slot (across multiple days)
///     that is most consistently free.

import '../../models/event_model.dart';

// ─── Data Classes ───────────────────────────────────────────────

/// A continuous block of free time suitable for deep work.
class FocusBlock {
  /// Start of the free block.
  final DateTime start;

  /// End of the free block.
  final DateTime end;

  /// Duration in minutes.
  int get minutes => end.difference(start).inMinutes;

  /// Quality label based on length.
  String get quality {
    if (minutes >= 180) return 'excellent';
    if (minutes >= 120) return 'great';
    if (minutes >= 90) return 'good';
    if (minutes >= 60) return 'fair';
    return 'short';
  }

  const FocusBlock({required this.start, required this.end});

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is FocusBlock && start == other.start && end == other.end;

  @override
  int get hashCode => Object.hash(start, end);

  @override
  String toString() =>
      'FocusBlock(${_fmtTime(start)}–${_fmtTime(end)}, $minutes min, $quality)';
}

/// Per-day analysis results.
class DayAnalysis {
  /// The date (time stripped to midnight).
  final DateTime date;

  /// All focus blocks found on this day.
  final List<FocusBlock> focusBlocks;

  /// Number of events that occurred during working hours.
  final int meetingCount;

  /// Total meeting minutes during working hours.
  final int meetingMinutes;

  /// Total available focus minutes (sum of all focus blocks).
  int get focusMinutes =>
      focusBlocks.fold<int>(0, (sum, b) => sum + b.minutes);

  /// Fragmentation score (0 = open, 100 = heavily fragmented).
  final double fragmentationScore;

  /// Number of context switches (transitions between events).
  final int contextSwitches;

  /// The best (longest) focus block, or null if none.
  FocusBlock? get bestBlock =>
      focusBlocks.isEmpty
          ? null
          : (List.of(focusBlocks)..sort((a, b) => b.minutes - a.minutes))
              .first;

  /// Ratio of focus time to total working-hour time (0.0–1.0).
  final double focusRatio;

  const DayAnalysis({
    required this.date,
    required this.focusBlocks,
    required this.meetingCount,
    required this.meetingMinutes,
    required this.fragmentationScore,
    required this.contextSwitches,
    required this.focusRatio,
  });

  @override
  String toString() =>
      'DayAnalysis(${_fmtDate(date)}: ${focusBlocks.length} blocks, '
      '$focusMinutes min focus, frag=${fragmentationScore.toStringAsFixed(1)})';
}

/// A recurring time-of-day window that is consistently free across days.
class FocusWindow {
  /// Hour of day when this window starts (0–23).
  final int startHour;

  /// Hour of day when this window ends (0–23, exclusive).
  final int endHour;

  /// How many of the analyzed days had this window free.
  final int freeDays;

  /// Total days analyzed.
  final int totalDays;

  /// Availability rate as a percentage (0–100).
  double get availabilityRate =>
      totalDays > 0 ? (freeDays / totalDays) * 100 : 0;

  /// Duration of the window in hours.
  int get hours => endHour - startHour;

  const FocusWindow({
    required this.startHour,
    required this.endHour,
    required this.freeDays,
    required this.totalDays,
  });

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is FocusWindow &&
          startHour == other.startHour &&
          endHour == other.endHour &&
          freeDays == other.freeDays &&
          totalDays == other.totalDays;

  @override
  int get hashCode => Object.hash(startHour, endHour, freeDays, totalDays);

  @override
  String toString() =>
      'FocusWindow(${startHour.toString().padLeft(2, '0')}:00–'
      '${endHour.toString().padLeft(2, '0')}:00, '
      'free $freeDays/$totalDays days, '
      '${availabilityRate.toStringAsFixed(0)}%)';
}

/// Consolidated focus-time report across multiple days.
class FocusTimeReport {
  /// Per-day breakdowns.
  final List<DayAnalysis> days;

  /// Average fragmentation score across all days (0–100).
  final double averageFragmentation;

  /// Average focus minutes per day.
  final double averageFocusMinutes;

  /// Average meetings per day.
  final double averageMeetings;

  /// Average context switches per day.
  final double averageContextSwitches;

  /// Best recurring focus windows sorted by availability.
  final List<FocusWindow> bestWindows;

  /// Overall focus score (0–100, higher = more deep-work friendly).
  final double focusScore;

  /// Actionable suggestions to improve focus time.
  final List<String> suggestions;

  const FocusTimeReport({
    required this.days,
    required this.averageFragmentation,
    required this.averageFocusMinutes,
    required this.averageMeetings,
    required this.averageContextSwitches,
    required this.bestWindows,
    required this.focusScore,
    required this.suggestions,
  });

  /// Human-readable summary text.
  String get summary {
    final buf = StringBuffer();
    buf.writeln('── Focus Time Report ──');
    buf.writeln('Days analyzed: ${days.length}');
    buf.writeln('Avg focus time: ${averageFocusMinutes.toStringAsFixed(0)} min/day');
    buf.writeln('Avg meetings: ${averageMeetings.toStringAsFixed(1)}/day');
    buf.writeln('Avg fragmentation: ${averageFragmentation.toStringAsFixed(1)}/100');
    buf.writeln('Avg context switches: ${averageContextSwitches.toStringAsFixed(1)}/day');
    buf.writeln('Focus score: ${focusScore.toStringAsFixed(0)}/100');
    if (bestWindows.isNotEmpty) {
      buf.writeln('Best focus window: ${bestWindows.first}');
    }
    if (suggestions.isNotEmpty) {
      buf.writeln('');
      buf.writeln('Suggestions:');
      for (final s in suggestions) {
        buf.writeln('  • $s');
      }
    }
    return buf.toString().trimRight();
  }

  @override
  String toString() =>
      'FocusTimeReport(days: ${days.length}, score: ${focusScore.toStringAsFixed(0)}, '
      'avgFocus: ${averageFocusMinutes.toStringAsFixed(0)} min)';
}

// ─── Service ────────────────────────────────────────────────────

/// Analyzes event schedules for focus-time quality and fragmentation.
///
/// Configure working hours, minimum focus-block length, and buffer time
/// between meetings. All time calculations respect working-hour boundaries
/// so early-morning and evening gaps don't inflate focus-time counts.
class FocusTimeService {
  /// Start of the working day (hour, 0–23). Default: 9.
  final int workStartHour;

  /// End of the working day (hour, 0–23, exclusive). Default: 17.
  final int workEndHour;

  /// Minimum gap length (in minutes) to qualify as a focus block.
  /// Default: 30 minutes.
  final int minFocusMinutes;

  /// Buffer minutes after each meeting for context-switching overhead.
  /// Default: 5 minutes.
  final int bufferMinutes;

  /// Creates a [FocusTimeService] with configurable working-hour parameters.
  const FocusTimeService({
    this.workStartHour = 9,
    this.workEndHour = 17,
    this.minFocusMinutes = 30,
    this.bufferMinutes = 5,
  });

  /// Total working minutes in a day.
  int get _workingMinutes => (workEndHour - workStartHour) * 60;

  // ─── Public API ─────────────────────────────────────────────

  /// Analyze a single day's events for focus-time quality.
  ///
  /// Returns a [DayAnalysis] with focus blocks, fragmentation, and meeting
  /// statistics. Events outside working hours are excluded. Events that
  /// span across working-hour boundaries are clipped to the work window.
  DayAnalysis analyzeDay(DateTime day, List<EventModel> events) {
    final date = _dateOnly(day);
    final workStart = DateTime(date.year, date.month, date.day, workStartHour);
    final workEnd = DateTime(date.year, date.month, date.day, workEndHour);

    // Filter and clip events to working hours
    final meetings = _clipToWorkingHours(events, workStart, workEnd);
    meetings.sort((a, b) => a.start.compareTo(b.start));

    // Merge overlapping meetings
    final merged = _mergeMeetings(meetings);

    // Find gaps (focus blocks)
    final blocks = _findFocusBlocks(merged, workStart, workEnd);

    // Compute fragmentation
    final frag = _computeFragmentation(merged, workStart, workEnd);

    // Context switches = transitions between meetings
    final switches = merged.length > 0 ? merged.length - 1 : 0;

    // Meeting stats
    final meetingMins = merged.fold<int>(
        0, (sum, m) => sum + m.end.difference(m.start).inMinutes);

    final focusMins = blocks.fold<int>(0, (sum, b) => sum + b.minutes);
    final ratio =
        _workingMinutes > 0 ? focusMins / _workingMinutes : 0.0;

    return DayAnalysis(
      date: date,
      focusBlocks: blocks,
      meetingCount: merged.length,
      meetingMinutes: meetingMins,
      fragmentationScore: frag,
      contextSwitches: switches,
      focusRatio: ratio,
    );
  }

  /// Analyze multiple days of events and produce a consolidated report.
  ///
  /// Groups events by date, analyzes each day, finds recurring focus
  /// windows, and generates actionable suggestions.
  ///
  /// [events] — all events in the analysis period.
  /// [from] — first day to analyze (inclusive).
  /// [to] — last day to analyze (inclusive).
  /// [includeWeekends] — if false (default), skip Saturday and Sunday.
  FocusTimeReport analyzeRange(
    List<EventModel> events, {
    required DateTime from,
    required DateTime to,
    bool includeWeekends = false,
  }) {
    final fromDate = _dateOnly(from);
    final toDate = _dateOnly(to);

    // Build list of days to analyze
    final days = <DateTime>[];
    var cursor = fromDate;
    while (!cursor.isAfter(toDate)) {
      if (includeWeekends || (cursor.weekday <= 5)) {
        days.add(cursor);
      }
      cursor = cursor.add(const Duration(days: 1));
    }

    if (days.isEmpty) {
      return FocusTimeReport(
        days: const [],
        averageFragmentation: 0,
        averageFocusMinutes: 0,
        averageMeetings: 0,
        averageContextSwitches: 0,
        bestWindows: const [],
        focusScore: 100,
        suggestions: const [],
      );
    }

    // Expand recurring events
    final expanded = _expandRecurring(events);

    // Group events by date
    final eventsByDate = <DateTime, List<EventModel>>{};
    for (final e in expanded) {
      final d = _dateOnly(e.date);
      eventsByDate.putIfAbsent(d, () => []).add(e);
    }

    // Analyze each day
    final analyses = <DayAnalysis>[];
    for (final day in days) {
      final dayEvents = eventsByDate[day] ?? [];
      analyses.add(analyzeDay(day, dayEvents));
    }

    // Compute averages
    final avgFrag = analyses.fold<double>(
            0, (sum, a) => sum + a.fragmentationScore) /
        analyses.length;
    final avgFocus = analyses.fold<double>(
            0, (sum, a) => sum + a.focusMinutes) /
        analyses.length;
    final avgMeetings = analyses.fold<double>(
            0, (sum, a) => sum + a.meetingCount) /
        analyses.length;
    final avgSwitches = analyses.fold<double>(
            0, (sum, a) => sum + a.contextSwitches) /
        analyses.length;

    // Find best recurring focus windows
    final windows = _findBestWindows(analyses, days.length);

    // Compute overall focus score
    final score = _computeFocusScore(avgFrag, avgFocus, avgMeetings);

    // Generate suggestions
    final suggestions = _generateSuggestions(analyses, avgFrag, avgFocus, windows);

    return FocusTimeReport(
      days: analyses,
      averageFragmentation: avgFrag,
      averageFocusMinutes: avgFocus,
      averageMeetings: avgMeetings,
      averageContextSwitches: avgSwitches,
      bestWindows: windows,
      focusScore: score,
      suggestions: suggestions,
    );
  }

  /// Quick method: get today's focus blocks without a full report.
  ///
  /// Useful for dashboard widgets showing available focus time remaining.
  List<FocusBlock> todaysFocusBlocks(List<EventModel> events,
      {DateTime? referenceDate}) {
    final today = _dateOnly(referenceDate ?? DateTime.now());
    final dayEvents = events.where((e) => _dateOnly(e.date) == today).toList();
    return analyzeDay(today, dayEvents).focusBlocks;
  }

  /// Quick method: get today's fragmentation score (0–100).
  double todaysFragmentation(List<EventModel> events,
      {DateTime? referenceDate}) {
    final today = _dateOnly(referenceDate ?? DateTime.now());
    final dayEvents = events.where((e) => _dateOnly(e.date) == today).toList();
    return analyzeDay(today, dayEvents).fragmentationScore;
  }

  // ─── Private Helpers ────────────────────────────────────────

  /// Clips events to the working-hour window.
  ///
  /// Events entirely outside working hours are excluded. Events that
  /// partially overlap are clipped to the work-window boundaries.
  /// All-day events (no endDate) are treated as 1-hour events.
  List<_TimeSlot> _clipToWorkingHours(
      List<EventModel> events, DateTime workStart, DateTime workEnd) {
    final slots = <_TimeSlot>[];
    for (final e in events) {
      var start = e.date;
      var end = e.endDate ?? e.date.add(const Duration(hours: 1));

      // Skip events entirely outside working hours
      if (end.isBefore(workStart) || !start.isBefore(workEnd)) continue;

      // Clip to working-hour boundaries
      if (start.isBefore(workStart)) start = workStart;
      if (end.isAfter(workEnd)) end = workEnd;

      // Skip zero-duration after clipping
      if (!start.isBefore(end)) continue;

      // Add buffer time after meeting (simulates context-switch cost)
      final bufferedEnd = end.add(Duration(minutes: bufferMinutes));
      final clippedBufferedEnd =
          bufferedEnd.isAfter(workEnd) ? workEnd : bufferedEnd;

      slots.add(_TimeSlot(start: start, end: clippedBufferedEnd));
    }
    return slots;
  }

  /// Merges overlapping time slots into non-overlapping intervals.
  List<_TimeSlot> _mergeMeetings(List<_TimeSlot> meetings) {
    if (meetings.isEmpty) return [];
    final sorted = List.of(meetings)
      ..sort((a, b) => a.start.compareTo(b.start));
    final merged = <_TimeSlot>[sorted.first];

    for (int i = 1; i < sorted.length; i++) {
      final last = merged.last;
      final current = sorted[i];
      if (!current.start.isAfter(last.end)) {
        // Overlap — extend the last slot
        merged[merged.length - 1] = _TimeSlot(
          start: last.start,
          end: last.end.isAfter(current.end) ? last.end : current.end,
        );
      } else {
        merged.add(current);
      }
    }
    return merged;
  }

  /// Finds focus blocks (gaps ≥ minFocusMinutes) between meetings.
  List<FocusBlock> _findFocusBlocks(
      List<_TimeSlot> meetings, DateTime workStart, DateTime workEnd) {
    final blocks = <FocusBlock>[];

    if (meetings.isEmpty) {
      // Entire working day is free
      final mins = workEnd.difference(workStart).inMinutes;
      if (mins >= minFocusMinutes) {
        blocks.add(FocusBlock(start: workStart, end: workEnd));
      }
      return blocks;
    }

    // Gap before first meeting
    final preGap = meetings.first.start.difference(workStart).inMinutes;
    if (preGap >= minFocusMinutes) {
      blocks.add(FocusBlock(start: workStart, end: meetings.first.start));
    }

    // Gaps between meetings
    for (int i = 0; i < meetings.length - 1; i++) {
      final gapStart = meetings[i].end;
      final gapEnd = meetings[i + 1].start;
      final gap = gapEnd.difference(gapStart).inMinutes;
      if (gap >= minFocusMinutes) {
        blocks.add(FocusBlock(start: gapStart, end: gapEnd));
      }
    }

    // Gap after last meeting
    final postGap = workEnd.difference(meetings.last.end).inMinutes;
    if (postGap >= minFocusMinutes) {
      blocks.add(FocusBlock(start: meetings.last.end, end: workEnd));
    }

    return blocks;
  }

  /// Computes the fragmentation score for a day (0–100).
  ///
  /// Formula:
  ///   - Count of gaps between meetings (more gaps = more fragmentation)
  ///   - Weight by how small the gaps are (many tiny gaps worse than few big ones)
  ///   - Scale to 0–100 where 0 = no meetings, 100 = maximum fragmentation
  double _computeFragmentation(
      List<_TimeSlot> meetings, DateTime workStart, DateTime workEnd) {
    if (meetings.isEmpty) return 0.0;
    if (meetings.length == 1) {
      // Single meeting — fragmentation based on position
      // (middle of day = more fragmentation than edge)
      final midWork = workStart.add(Duration(
          minutes: workEnd.difference(workStart).inMinutes ~/ 2));
      final meetMid = meetings.first.start.add(Duration(
          minutes: meetings.first.end.difference(meetings.first.start).inMinutes ~/ 2));
      final distFromEdge = (meetMid.difference(midWork).inMinutes).abs();
      final maxDist = _workingMinutes / 2;
      // Edge meetings fragment less (they don't split the day)
      return maxDist > 0
          ? (1 - (distFromEdge / maxDist)).clamp(0, 1) * 25
          : 0.0;
    }

    // Multiple meetings: fragmentation = function of gap count and sizes
    final totalWorkMins = workEnd.difference(workStart).inMinutes;
    if (totalWorkMins <= 0) return 0.0;

    // Count all gaps (including before first and after last)
    final gaps = <int>[];

    // Pre-meeting gap
    gaps.add(meetings.first.start.difference(workStart).inMinutes);

    // Inter-meeting gaps
    for (int i = 0; i < meetings.length - 1; i++) {
      gaps.add(meetings[i + 1].start.difference(meetings[i].end).inMinutes);
    }

    // Post-meeting gap
    gaps.add(workEnd.difference(meetings.last.end).inMinutes);

    // Remove zero/negative gaps
    final positiveGaps = gaps.where((g) => g > 0).toList();

    if (positiveGaps.isEmpty) {
      // Meetings fill the entire day — fully fragmented
      return 100.0;
    }

    // Fragmentation score: more gaps = worse, smaller avg gap = worse
    final gapCount = positiveGaps.length;
    final avgGap = positiveGaps.reduce((a, b) => a + b) / gapCount;
    final maxPossibleGap = totalWorkMins.toDouble();

    // Component 1: Number of gaps (normalized to max ~10)
    final gapCountScore = (gapCount / 10.0).clamp(0.0, 1.0);

    // Component 2: Average gap size (smaller = more fragmented)
    final gapSizeScore = 1.0 - (avgGap / maxPossibleGap).clamp(0.0, 1.0);

    // Component 3: Meeting density
    final meetingMins = meetings.fold<int>(
        0, (sum, m) => sum + m.end.difference(m.start).inMinutes);
    final densityScore = (meetingMins / totalWorkMins).clamp(0.0, 1.0);

    // Weighted combination
    return ((gapCountScore * 30) + (gapSizeScore * 40) + (densityScore * 30))
        .clamp(0, 100);
  }

  /// Finds recurring focus windows across analyzed days.
  ///
  /// Scans each hour of the working day across all analyzed days, and
  /// finds multi-hour windows that are consistently meeting-free.
  List<FocusWindow> _findBestWindows(List<DayAnalysis> analyses, int totalDays) {
    if (analyses.isEmpty) return [];

    // For each hour of the work day, count how many days it's free
    final hourFreeCount = <int, int>{};
    for (var h = workStartHour; h < workEndHour; h++) {
      hourFreeCount[h] = 0;
    }

    for (final day in analyses) {
      for (var h = workStartHour; h < workEndHour; h++) {
        final hourStart = DateTime(day.date.year, day.date.month, day.date.day, h);
        final hourEnd = hourStart.add(const Duration(hours: 1));

        // Check if any focus block covers this entire hour
        final isFree = day.focusBlocks.any((b) =>
            !b.start.isAfter(hourStart) && !b.end.isBefore(hourEnd));

        if (isFree) {
          hourFreeCount[h] = (hourFreeCount[h] ?? 0) + 1;
        }
      }
    }

    // Find contiguous multi-hour windows with high availability
    final windows = <FocusWindow>[];
    final minAvailability = totalDays > 1 ? 0.5 : 0.0; // At least 50% free

    for (var startH = workStartHour; startH < workEndHour; startH++) {
      if ((hourFreeCount[startH] ?? 0) / totalDays < minAvailability) continue;

      var endH = startH + 1;
      while (endH < workEndHour &&
          (hourFreeCount[endH] ?? 0) / totalDays >= minAvailability) {
        endH++;
      }

      if (endH - startH >= 1) {
        // Take the minimum free count across the window hours
        var minFree = totalDays;
        for (var h = startH; h < endH; h++) {
          final free = hourFreeCount[h] ?? 0;
          if (free < minFree) minFree = free;
        }

        windows.add(FocusWindow(
          startHour: startH,
          endHour: endH,
          freeDays: minFree,
          totalDays: totalDays,
        ));
        startH = endH - 1; // Skip consumed hours
      }
    }

    // Sort by window size × availability
    windows.sort((a, b) {
      final scoreA = a.hours * a.availabilityRate;
      final scoreB = b.hours * b.availabilityRate;
      return scoreB.compareTo(scoreA);
    });

    return windows.take(5).toList();
  }

  /// Computes an overall focus score (0–100, higher = better).
  ///
  /// Factors:
  ///   - Low fragmentation → higher score
  ///   - More focus minutes → higher score
  ///   - Fewer meetings → higher score
  double _computeFocusScore(
      double avgFrag, double avgFocusMins, double avgMeetings) {
    // Fragmentation component (inverted, 0 frag = 40 points)
    final fragComponent = ((100 - avgFrag) / 100) * 40;

    // Focus time component (4+ hours of focus = 40 points)
    final focusTarget = 240.0; // 4 hours ideal
    final focusComponent = (avgFocusMins / focusTarget).clamp(0.0, 1.0) * 40;

    // Meeting load component (0 meetings = 20 points, 8+ = 0)
    final meetingComponent =
        ((8 - avgMeetings) / 8).clamp(0.0, 1.0) * 20;

    return (fragComponent + focusComponent + meetingComponent).clamp(0, 100);
  }

  /// Generates actionable suggestions based on the analysis.
  List<String> _generateSuggestions(
    List<DayAnalysis> analyses,
    double avgFrag,
    double avgFocusMins,
    List<FocusWindow> windows,
  ) {
    final suggestions = <String>[];

    if (avgFrag > 70) {
      suggestions.add(
          'Your schedule is highly fragmented. Try batching meetings into '
          'specific time blocks (e.g., all meetings after 2 PM).');
    } else if (avgFrag > 50) {
      suggestions.add(
          'Moderate fragmentation detected. Consider consolidating back-to-back '
          'meetings to create larger focus blocks.');
    }

    if (avgFocusMins < 60) {
      suggestions.add(
          'Very little focus time available (${avgFocusMins.toStringAsFixed(0)} min/day avg). '
          'Block out at least 2 hours daily for deep work.');
    } else if (avgFocusMins < 120) {
      suggestions.add(
          'Limited focus time (${avgFocusMins.toStringAsFixed(0)} min/day avg). '
          'Aim for at least 2–3 hours of uninterrupted work per day.');
    }

    if (windows.isNotEmpty) {
      final best = windows.first;
      suggestions.add(
          'Your most consistent focus window is '
          '${best.startHour.toString().padLeft(2, '0')}:00–'
          '${best.endHour.toString().padLeft(2, '0')}:00 '
          '(free ${best.availabilityRate.toStringAsFixed(0)}% of days). '
          'Protect this time for deep work.');
    }

    // Check for early-morning vs late-afternoon patterns
    final morningHeavy = analyses.where((a) {
      return a.focusBlocks.isNotEmpty &&
          a.focusBlocks.first.start.hour >= workStartHour + 2;
    }).length;
    if (morningHeavy > analyses.length * 0.6) {
      suggestions.add(
          'Most of your mornings start with meetings. If possible, shift '
          'meetings to afternoons to leverage morning energy for deep work.');
    }

    final highSwitchDays =
        analyses.where((a) => a.contextSwitches >= 5).length;
    if (highSwitchDays > analyses.length * 0.5) {
      suggestions.add(
          'High context-switching detected on most days (5+ switches). '
          'Each switch costs 15–25 minutes of refocus time. '
          'Try grouping similar meetings together.');
    }

    return suggestions;
  }

  /// Expands recurring events into individual occurrences.
  List<EventModel> _expandRecurring(List<EventModel> events) {
    final expanded = <EventModel>[];
    for (final e in events) {
      expanded.add(e);
      if (e.isRecurring) {
        expanded.addAll(e.generateOccurrences());
      }
    }
    return expanded;
  }
}

// ─── Private Utilities ──────────────────────────────────────────

/// Internal time slot for meeting interval calculations.
class _TimeSlot {
  final DateTime start;
  final DateTime end;

  const _TimeSlot({required this.start, required this.end});

  @override
  String toString() => '_TimeSlot(${_fmtTime(start)}–${_fmtTime(end)})';
}

/// Strips time, returning midnight on the same date.
DateTime _dateOnly(DateTime dt) => DateTime(dt.year, dt.month, dt.day);

/// Formats a time as "HH:MM".
String _fmtTime(DateTime dt) =>
    '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';

/// Formats a date as "Mon DD".
String _fmtDate(DateTime dt) {
  const months = [
    'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'
  ];
  return '${months[dt.month - 1]} ${dt.day}';
}
