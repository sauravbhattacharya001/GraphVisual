using System;
using System.Collections.Generic;
using System.Linq;
using Vidly.Models;
using Vidly.Repositories;

namespace Vidly.Services
{
    /// <summary>
    /// Generates themed movie night plans from the catalog. Selects and orders
    /// movies based on theme, checks availability, builds a viewing schedule
    /// with breaks, and suggests snacks and activities.
    /// </summary>
    public class MovieNightPlannerService
    {
        private readonly IMovieRepository _movieRepository;
        private readonly IRentalRepository _rentalRepository;
        private readonly Random _random;

        // Slot notes for viewing order positions
        private static readonly string[] _slotNotes = new[]
        {
            "Opening feature \u2014 sets the mood",
            "Building momentum",
            "The centerpiece",
            "Keeping the energy up",
            "Late-night pick",
            "Almost there!",
            "The grand finale",
            "Bonus round \u2014 for the truly dedicated"
        };

        // Break suggestions between movies
        private static readonly string[] _breakSuggestions = new[]
        {
            "Stretch your legs and refill drinks",
            "Popcorn refill and bathroom break",
            "Quick snack run \u2014 grab something sweet",
            "Stand up, stretch, discuss what you just watched",
            "Fresh air break \u2014 step outside for a minute",
            "Rate the last movie and make predictions for the next",
            "Switch up your snacks \u2014 try something different"
        };

        // Genre-specific snack suggestions
        private static readonly Dictionary<Genre, List<string>> _genreSnacks =
            new Dictionary<Genre, List<string>>
        {
            { Genre.Action, new List<string> { "Nachos with jalape\u00f1os", "Energy drinks", "Buffalo wings", "Beef jerky" } },
            { Genre.Comedy, new List<string> { "Popcorn (extra butter)", "Gummy bears", "Pretzels", "Soda" } },
            { Genre.Drama, new List<string> { "Wine and cheese board", "Dark chocolate", "Bruschetta", "Sparkling water" } },
            { Genre.Horror, new List<string> { "Red velvet cupcakes", "Gummy worms", "Blood orange soda", "Candy corn" } },
            { Genre.SciFi, new List<string> { "Freeze-dried ice cream", "Galaxy cookies", "Blue Gatorade", "Space-themed candy" } },
            { Genre.Animation, new List<string> { "Fruit snacks", "Goldfish crackers", "Juice boxes", "Animal crackers" } },
            { Genre.Thriller, new List<string> { "Trail mix", "Coffee", "Dark chocolate", "Stress ball (you'll need it)" } },
            { Genre.Romance, new List<string> { "Chocolate-covered strawberries", "Ros\u00e9", "Heart-shaped cookies", "Champagne" } },
            { Genre.Documentary, new List<string> { "Artisan popcorn", "Craft beer", "Hummus and veggies", "Green tea" } },
            { Genre.Adventure, new List<string> { "Trail mix", "Granola bars", "Tropical fruit", "Coconut water" } },
        };

        // Theme descriptions
        private static readonly Dictionary<MovieNightTheme, string> _themeDescriptions =
            new Dictionary<MovieNightTheme, string>
        {
            { MovieNightTheme.GenreFocus, "Deep dive into a single genre \u2014 for the purists" },
            { MovieNightTheme.GenreMix, "A variety pack of genres \u2014 something for everyone" },
            { MovieNightTheme.DecadeFocus, "Time machine movie night \u2014 revisit a decade's best" },
            { MovieNightTheme.CriticsChoice, "The highest-rated films in our catalog \u2014 quality guaranteed" },
            { MovieNightTheme.FanFavorites, "The most-rented movies \u2014 crowd-tested and approved" },
            { MovieNightTheme.HiddenGems, "Underrated masterpieces you probably haven't seen yet" },
            { MovieNightTheme.NewReleases, "Fresh off the shelf \u2014 the latest additions to our catalog" },
            { MovieNightTheme.SurpriseMe, "Life is an adventure \u2014 let fate decide your movie night" },
        };

        public MovieNightPlannerService(
            IMovieRepository movieRepository,
            IRentalRepository rentalRepository)
        {
            _movieRepository = movieRepository
                ?? throw new ArgumentNullException(nameof(movieRepository));
            _rentalRepository = rentalRepository
                ?? throw new ArgumentNullException(nameof(rentalRepository));
            _random = new Random();
        }

        /// <summary>
        /// Internal constructor for deterministic testing.
        /// </summary>
        internal MovieNightPlannerService(
            IMovieRepository movieRepository,
            IRentalRepository rentalRepository,
            int seed) : this(movieRepository, rentalRepository)
        {
            _random = new Random(seed);
        }

        /// <summary>
        /// Generate a movie night plan based on the request parameters.
        /// </summary>
        public MovieNightPlan GeneratePlan(MovieNightRequest request)
        {
            if (request == null)
                throw new ArgumentNullException(nameof(request));

            // Clamp movie count to 1-8
            var movieCount = Math.Max(1, Math.Min(8, request.MovieCount));

            // Select movies based on theme
            var selectedMovies = SelectMovies(request, movieCount);

            if (selectedMovies.Count == 0)
            {
                return new MovieNightPlan
                {
                    Title = "No Movies Available",
                    Theme = request.Theme,
                    ThemeDescription = "We couldn't find enough movies matching your criteria. Try a different theme or fewer movies.",
                    MovieCount = 0,
                    AvailableCount = 0,
                    TotalMinutes = 0,
                    TotalDuration = "0m",
                    EstimatedEndTime = request.StartTime ?? DefaultStartTime()
                };
            }

            // Order movies for best viewing experience
            var orderedMovies = OrderMovies(selectedMovies, request.Theme);

            // Build schedule
            var startTime = request.StartTime ?? DefaultStartTime();
            var runtimePerMovie = Math.Max(60, Math.Min(240, request.EstimatedRuntimeMinutes));
            var breakMinutes = Math.Max(0, Math.Min(60, request.BreakMinutes));

            var slots = BuildSchedule(orderedMovies, startTime, runtimePerMovie, breakMinutes);

            // Check availability
            var availableCount = 0;
            foreach (var slot in slots)
            {
                slot.IsAvailable = !_rentalRepository.IsMovieRentedOut(slot.Movie.Id);
                if (slot.IsAvailable) availableCount++;
            }

            // Calculate totals
            var totalMinutes = (slots.Count * runtimePerMovie) +
                               (Math.Max(0, slots.Count - 1) * breakMinutes);

            var plan = new MovieNightPlan
            {
                Title = GenerateTitle(request, orderedMovies),
                Theme = request.Theme,
                ThemeDescription = GetThemeDescription(request.Theme),
                Slots = slots,
                MovieCount = slots.Count,
                AvailableCount = availableCount,
                TotalMinutes = totalMinutes,
                TotalDuration = FormatDuration(totalMinutes),
                EstimatedEndTime = startTime.AddMinutes(totalMinutes),
                SnackSuggestions = GetSnackSuggestions(orderedMovies),
            };

            if (availableCount < slots.Count)
            {
                var unavailable = slots.Count - availableCount;
                plan.AvailabilityNote = unavailable == 1
                    ? "1 movie is currently rented out. Consider swapping or waiting."
                    : $"{unavailable} movies are currently rented out. Consider swapping or waiting.";
            }

            return plan;
        }

        /// <summary>
        /// Generate multiple alternative plans for the user to choose from.
        /// </summary>
        public List<MovieNightPlan> GenerateAlternatives(MovieNightRequest baseRequest, int count = 3)
        {
            if (count < 1 || count > 10)
                throw new ArgumentOutOfRangeException(nameof(count), "Count must be between 1 and 10.");

            var plans = new List<MovieNightPlan>();
            var usedMovieIds = new HashSet<int>();

            for (var i = 0; i < count; i++)
            {
                var plan = GeneratePlan(baseRequest);
                if (plan.MovieCount == 0) break;

                plans.Add(plan);

                foreach (var slot in plan.Slots)
                    usedMovieIds.Add(slot.Movie.Id);
            }

            return plans;
        }

        /// <summary>
        /// Get all available themes with descriptions.
        /// </summary>
        public List<ThemeOption> GetAvailableThemes()
        {
            var themes = new List<ThemeOption>();
            foreach (MovieNightTheme theme in Enum.GetValues(typeof(MovieNightTheme)))
            {
                themes.Add(new ThemeOption
                {
                    Theme = theme,
                    Name = FormatThemeName(theme),
                    Description = GetThemeDescription(theme)
                });
            }
            return themes;
        }

        // ── Movie Selection ─────────────────────────────────────────────

        private List<Movie> SelectMovies(MovieNightRequest request, int count)
        {
            var allMovies = _movieRepository.GetAll();
            if (allMovies == null || allMovies.Count == 0)
                return new List<Movie>();

            List<Movie> candidates;

            switch (request.Theme)
            {
                case MovieNightTheme.GenreFocus:
                    var genre = request.Genre ?? PickRandomGenre(allMovies);
                    candidates = allMovies.Where(m => m.Genre == genre).ToList();
                    break;

                case MovieNightTheme.GenreMix:
                    candidates = SelectGenreMix(allMovies, count);
                    return candidates;

                case MovieNightTheme.DecadeFocus:
                    var decade = request.Decade ?? PickRandomDecade(allMovies);
                    candidates = allMovies.Where(m =>
                        m.ReleaseDate.HasValue &&
                        m.ReleaseDate.Value.Year >= decade &&
                        m.ReleaseDate.Value.Year < decade + 10
                    ).ToList();
                    break;

                case MovieNightTheme.CriticsChoice:
                    candidates = allMovies
                        .Where(m => m.Rating.HasValue)
                        .OrderByDescending(m => m.Rating.Value)
                        .ThenBy(m => m.Name)
                        .ToList();
                    break;

                case MovieNightTheme.FanFavorites:
                    candidates = SelectFanFavorites(allMovies);
                    break;

                case MovieNightTheme.HiddenGems:
                    candidates = SelectHiddenGems(allMovies);
                    break;

                case MovieNightTheme.NewReleases:
                    candidates = allMovies
                        .Where(m => m.IsNewRelease)
                        .OrderByDescending(m => m.ReleaseDate)
                        .ToList();
                    break;

                case MovieNightTheme.SurpriseMe:
                default:
                    candidates = allMovies.ToList();
                    Shuffle(candidates);
                    break;
            }

            // Personalize if customer ID provided
            if (request.CustomerId.HasValue)
            {
                var rentedMovieIds = new HashSet<int>(
                    _rentalRepository.GetAll()
                        .Where(r => r.CustomerId == request.CustomerId.Value)
                        .Select(r => r.MovieId)
                );
                var unrented = candidates.Where(m => !rentedMovieIds.Contains(m.Id)).ToList();
                if (unrented.Count >= count)
                    candidates = unrented;
            }

            return candidates.Take(count).ToList();
        }

        private List<Movie> SelectGenreMix(IReadOnlyList<Movie> allMovies, int count)
        {
            var byGenre = new Dictionary<Genre, List<Movie>>();
            foreach (var movie in allMovies)
            {
                if (!movie.Genre.HasValue) continue;
                if (!byGenre.ContainsKey(movie.Genre.Value))
                    byGenre[movie.Genre.Value] = new List<Movie>();
                byGenre[movie.Genre.Value].Add(movie);
            }

            if (byGenre.Count == 0)
                return allMovies.Take(count).ToList();

            var genres = byGenre.Keys.ToList();
            Shuffle(genres);

            var result = new List<Movie>();
            var genreIndex = 0;
            var usedIds = new HashSet<int>();

            while (result.Count < count && genres.Count > 0)
            {
                var genre = genres[genreIndex % genres.Count];
                var genreMovies = byGenre[genre];

                var pick = genreMovies.FirstOrDefault(m => !usedIds.Contains(m.Id));
                if (pick != null)
                {
                    result.Add(pick);
                    usedIds.Add(pick.Id);
                }
                else
                {
                    genres.Remove(genre);
                    if (genres.Count == 0) break;
                }
                genreIndex++;
            }

            return result;
        }

        private List<Movie> SelectFanFavorites(IReadOnlyList<Movie> allMovies)
        {
            var rentals = _rentalRepository.GetAll();
            var rentalCounts = new Dictionary<int, int>();
            foreach (var rental in rentals)
            {
                if (!rentalCounts.ContainsKey(rental.MovieId))
                    rentalCounts[rental.MovieId] = 0;
                rentalCounts[rental.MovieId]++;
            }

            return allMovies
                .OrderByDescending(m => rentalCounts.TryGetValue(m.Id, out var c) ? c : 0)
                .ThenByDescending(m => m.Rating ?? 0)
                .ToList();
        }

        private List<Movie> SelectHiddenGems(IReadOnlyList<Movie> allMovies)
        {
            var rentals = _rentalRepository.GetAll();
            var rentalCounts = new Dictionary<int, int>();
            foreach (var rental in rentals)
            {
                if (!rentalCounts.ContainsKey(rental.MovieId))
                    rentalCounts[rental.MovieId] = 0;
                rentalCounts[rental.MovieId]++;
            }

            return allMovies
                .Where(m => m.Rating.HasValue && m.Rating.Value >= 4)
                .OrderBy(m => rentalCounts.TryGetValue(m.Id, out var c) ? c : 0)
                .ThenByDescending(m => m.Rating.Value)
                .ToList();
        }

        // ── Movie Ordering ──────────────────────────────────────────────

        private List<Movie> OrderMovies(List<Movie> movies, MovieNightTheme theme)
        {
            switch (theme)
            {
                case MovieNightTheme.DecadeFocus:
                    return movies.OrderBy(m => m.ReleaseDate ?? DateTime.MaxValue).ToList();

                case MovieNightTheme.CriticsChoice:
                    return movies.OrderBy(m => m.Rating ?? 0).ToList();

                case MovieNightTheme.NewReleases:
                    return movies.OrderBy(m => m.ReleaseDate ?? DateTime.MaxValue).ToList();

                case MovieNightTheme.GenreMix:
                    return movies;

                default:
                    return movies.OrderBy(m => m.Rating ?? 3).ToList();
            }
        }

        // ── Schedule Building ───────────────────────────────────────────

        private List<MovieNightSlot> BuildSchedule(
            List<Movie> movies, DateTime startTime, int runtimeMinutes, int breakMinutes)
        {
            var slots = new List<MovieNightSlot>();
            var currentTime = startTime;

            for (var i = 0; i < movies.Count; i++)
            {
                var slot = new MovieNightSlot
                {
                    Order = i + 1,
                    Movie = movies[i],
                    StartTime = currentTime,
                    EndTime = currentTime.AddMinutes(runtimeMinutes),
                    RuntimeMinutes = runtimeMinutes,
                    SlotNote = i < _slotNotes.Length ? _slotNotes[i] : string.Format("Movie #{0}", i + 1),
                    BreakSuggestion = i < movies.Count - 1
                        ? _breakSuggestions[i % _breakSuggestions.Length]
                        : null
                };

                slots.Add(slot);
                currentTime = slot.EndTime;

                if (i < movies.Count - 1)
                    currentTime = currentTime.AddMinutes(breakMinutes);
            }

            return slots;
        }

        // ── Title Generation ────────────────────────────────────────────

        private string GenerateTitle(MovieNightRequest request, List<Movie> movies)
        {
            switch (request.Theme)
            {
                case MovieNightTheme.GenreFocus:
                    var genre = movies.FirstOrDefault()?.Genre;
                    return genre.HasValue
                        ? string.Format("{0} Movie Marathon", genre.Value)
                        : "Genre Movie Night";

                case MovieNightTheme.GenreMix:
                    return "Genre Sampler Night";

                case MovieNightTheme.DecadeFocus:
                    var decade = request.Decade ?? (movies.FirstOrDefault()?.ReleaseDate?.Year / 10 * 10);
                    return decade.HasValue
                        ? string.Format("Best of the {0}s", decade)
                        : "Decade Classics Night";

                case MovieNightTheme.CriticsChoice:
                    return "Critics' Choice Marathon";

                case MovieNightTheme.FanFavorites:
                    return "Fan Favorites Night";

                case MovieNightTheme.HiddenGems:
                    return "Hidden Gems Discovery Night";

                case MovieNightTheme.NewReleases:
                    return "New Releases Premiere Night";

                case MovieNightTheme.SurpriseMe:
                    return "Mystery Movie Night";

                default:
                    return "Movie Night";
            }
        }

        // ── Helpers ─────────────────────────────────────────────────────

        private string GetThemeDescription(MovieNightTheme theme) =>
            _themeDescriptions.TryGetValue(theme, out var desc) ? desc : "A great night of movies awaits!";

        private List<string> GetSnackSuggestions(List<Movie> movies)
        {
            var snacks = new HashSet<string>();
            foreach (var movie in movies)
            {
                if (movie.Genre.HasValue && _genreSnacks.TryGetValue(movie.Genre.Value, out var genreList))
                {
                    foreach (var snack in genreList)
                        snacks.Add(snack);
                }
            }

            if (snacks.Count == 0)
            {
                return new List<string>
                {
                    "Classic buttered popcorn",
                    "Assorted candy",
                    "Soft drinks",
                    "Pizza (the ultimate movie food)"
                };
            }

            return snacks.Take(6).ToList();
        }

        private Genre PickRandomGenre(IReadOnlyList<Movie> movies)
        {
            var genres = movies
                .Where(m => m.Genre.HasValue)
                .Select(m => m.Genre.Value)
                .Distinct()
                .ToList();

            return genres.Count > 0
                ? genres[_random.Next(genres.Count)]
                : Genre.Action;
        }

        private int PickRandomDecade(IReadOnlyList<Movie> movies)
        {
            var decades = movies
                .Where(m => m.ReleaseDate.HasValue)
                .Select(m => m.ReleaseDate.Value.Year / 10 * 10)
                .Distinct()
                .ToList();

            return decades.Count > 0
                ? decades[_random.Next(decades.Count)]
                : 2020;
        }

        private static DateTime DefaultStartTime()
        {
            var today = DateTime.Today;
            return new DateTime(today.Year, today.Month, today.Day, 19, 0, 0);
        }

        private static string FormatDuration(int totalMinutes)
        {
            var hours = totalMinutes / 60;
            var minutes = totalMinutes % 60;
            if (hours == 0) return string.Format("{0}m", minutes);
            if (minutes == 0) return string.Format("{0}h", hours);
            return string.Format("{0}h {1}m", hours, minutes);
        }

        private static string FormatThemeName(MovieNightTheme theme)
        {
            switch (theme)
            {
                case MovieNightTheme.GenreFocus: return "Genre Focus";
                case MovieNightTheme.GenreMix: return "Genre Mix";
                case MovieNightTheme.DecadeFocus: return "Decade Focus";
                case MovieNightTheme.CriticsChoice: return "Critics' Choice";
                case MovieNightTheme.FanFavorites: return "Fan Favorites";
                case MovieNightTheme.HiddenGems: return "Hidden Gems";
                case MovieNightTheme.NewReleases: return "New Releases";
                case MovieNightTheme.SurpriseMe: return "Surprise Me";
                default: return theme.ToString();
            }
        }

        private void Shuffle<T>(List<T> list)
        {
            for (var i = list.Count - 1; i > 0; i--)
            {
                var j = _random.Next(i + 1);
                var temp = list[i];
                list[i] = list[j];
                list[j] = temp;
            }
        }
    }

    /// <summary>
    /// Describes an available theme option.
    /// </summary>
    public class ThemeOption
    {
        public MovieNightTheme Theme { get; set; }
        public string Name { get; set; }
        public string Description { get; set; }
    }
}
