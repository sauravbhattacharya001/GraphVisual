using System;
using System.Collections.Generic;

namespace Vidly.Models
{
    // ── Movie Night Planner Models ──────────────────────────────────

    /// <summary>
    /// Theme for a movie night — determines how movies are selected and ordered.
    /// </summary>
    public enum MovieNightTheme
    {
        /// <summary>All movies from a single genre.</summary>
        GenreFocus,

        /// <summary>A mix of genres for variety.</summary>
        GenreMix,

        /// <summary>Movies from a specific decade.</summary>
        DecadeFocus,

        /// <summary>Highest-rated movies in the catalog.</summary>
        CriticsChoice,

        /// <summary>Most popular movies by rental count.</summary>
        FanFavorites,

        /// <summary>Hidden gems — highly rated but rarely rented.</summary>
        HiddenGems,

        /// <summary>New releases (within 90 days).</summary>
        NewReleases,

        /// <summary>Random surprise selection.</summary>
        SurpriseMe
    }

    /// <summary>
    /// Configuration for generating a movie night plan.
    /// </summary>
    public class MovieNightRequest
    {
        /// <summary>The theme for movie selection.</summary>
        public MovieNightTheme Theme { get; set; } = MovieNightTheme.SurpriseMe;

        /// <summary>Optional genre filter (used with GenreFocus theme).</summary>
        public Genre? Genre { get; set; }

        /// <summary>Optional decade filter (e.g. 2010 for the 2010s).</summary>
        public int? Decade { get; set; }

        /// <summary>Number of movies to include (1-8, default 3).</summary>
        public int MovieCount { get; set; } = 3;

        /// <summary>Estimated runtime per movie in minutes (default 120).</summary>
        public int EstimatedRuntimeMinutes { get; set; } = 120;

        /// <summary>Optional customer ID to personalize based on rental history.</summary>
        public int? CustomerId { get; set; }

        /// <summary>Optional start time for the movie night (default 7 PM today).</summary>
        public DateTime? StartTime { get; set; }

        /// <summary>Minutes between movies for breaks (default 15).</summary>
        public int BreakMinutes { get; set; } = 15;
    }

    /// <summary>
    /// A complete movie night plan with viewing order, schedule, and suggestions.
    /// </summary>
    public class MovieNightPlan
    {
        /// <summary>Name of the movie night (auto-generated).</summary>
        public string Title { get; set; }

        /// <summary>Theme used for this plan.</summary>
        public MovieNightTheme Theme { get; set; }

        /// <summary>Ordered list of movies to watch.</summary>
        public List<MovieNightSlot> Slots { get; set; } = new List<MovieNightSlot>();

        /// <summary>Total estimated runtime including breaks (in minutes).</summary>
        public int TotalMinutes { get; set; }

        /// <summary>Formatted total duration (e.g. "5h 30m").</summary>
        public string TotalDuration { get; set; }

        /// <summary>Estimated end time.</summary>
        public DateTime EstimatedEndTime { get; set; }

        /// <summary>Suggested snacks for this theme.</summary>
        public List<string> SnackSuggestions { get; set; } = new List<string>();

        /// <summary>Fun trivia or theme description.</summary>
        public string ThemeDescription { get; set; }

        /// <summary>Number of movies in the plan.</summary>
        public int MovieCount { get; set; }

        /// <summary>Number of movies that are currently available (not rented out).</summary>
        public int AvailableCount { get; set; }

        /// <summary>Warning if some movies are unavailable.</summary>
        public string AvailabilityNote { get; set; }
    }

    /// <summary>
    /// A single movie slot in the night plan with scheduling info.
    /// </summary>
    public class MovieNightSlot
    {
        /// <summary>Position in the viewing order (1-based).</summary>
        public int Order { get; set; }

        /// <summary>The movie for this slot.</summary>
        public Movie Movie { get; set; }

        /// <summary>Scheduled start time.</summary>
        public DateTime StartTime { get; set; }

        /// <summary>Scheduled end time.</summary>
        public DateTime EndTime { get; set; }

        /// <summary>Estimated runtime in minutes.</summary>
        public int RuntimeMinutes { get; set; }

        /// <summary>Whether this movie is currently available (not rented out).</summary>
        public bool IsAvailable { get; set; }

        /// <summary>A note about this slot (e.g. "Opening feature", "Grand finale").</summary>
        public string SlotNote { get; set; }

        /// <summary>Break activity suggested after this movie (null for last).</summary>
        public string BreakSuggestion { get; set; }
    }
}
