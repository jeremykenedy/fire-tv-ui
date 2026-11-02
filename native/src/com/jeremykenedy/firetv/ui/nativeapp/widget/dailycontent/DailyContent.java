package com.jeremykenedy.firetv.ui.nativeapp.widget.dailycontent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Offline editions repeat a disclosed small collection on the TV's local calendar. */
public final class DailyContent {
    public enum Kind {
        ASTRONOMY("daily-astronomy", "Daily astronomy"), ART("daily-art", "Daily art"),
        WORD("word-of-day", "Word of the day"), TRIVIA("daily-trivia", "Daily trivia"),
        INSPIRATION("daily-inspiration", "Daily inspiration"), HOROSCOPE("daily-horoscope", "Daily horoscope"),
        COMIC("daily-comic", "Daily comic");
        public final String id;
        public final String title;
        Kind(String id, String title) { this.id = id; this.title = title; }
    }
    public static final String CREDIT = "Original Fire TV UI collection. Copyright 2026 Jeremy Kenedy. MIT License.";
    public static final String NASA = "https://science.nasa.gov/solar-system/solar-system-facts/";
    public static final String[] SIGNS = {"Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"};
    public final Kind kind;
    public final LocalDate date;
    public final int edition;
    public final String headline;
    public final String text;
    public final String answer;
    public final String attribution;

    private DailyContent(Kind kind, LocalDate date, int edition, String headline, String text, String answer, String attribution) {
        this.kind = kind; this.date = date; this.edition = edition; this.headline = headline;
        this.text = text; this.answer = answer; this.attribution = attribution;
    }
    public static LocalDate date(long millis, ZoneId zone) { return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(); }
    public static int index(LocalDate date, int size, int offset) {
        if (size < 1) throw new IllegalArgumentException("Empty collection");
        return (int) Math.floorMod(date.toEpochDay() + offset, (long) size);
    }
    public static DailyContent forDate(Kind kind, LocalDate date, int sign) {
        if (kind == null || date == null || sign < 0 || sign >= SIGNS.length) throw new IllegalArgumentException("Invalid daily selection");
        String[][] rows;
        switch (kind) {
            case ASTRONOMY: rows = ASTRONOMY; break;
            case WORD: rows = WORDS; break;
            case TRIVIA: rows = TRIVIA; break;
            case INSPIRATION: rows = INSPIRATION; break;
            case HOROSCOPE: rows = HOROSCOPES; break;
            case COMIC: rows = COMICS; break;
            default: rows = ART;
        }
        int edition = index(date, rows.length, kind == Kind.HOROSCOPE ? sign : 0);
        String[] row = rows[edition];
        String attribution = CREDIT + "\nOffline collection: " + rows.length + " entries, repeats daily. TV local date.";
        if (kind == Kind.ASTRONOMY) attribution += "\nOriginal summaries of NASA Solar System Facts. Source: " + NASA + "\nNo NASA photographs are included.";
        if (kind == Kind.HOROSCOPE) attribution += "\nEntertainment only. Original writing, not predictions or advice.";
        return new DailyContent(kind, date, edition, kind == Kind.HOROSCOPE ? SIGNS[sign] : row[0], row[1], row.length > 2 ? row[2] : "", attribution);
    }
    private static final String[][] ASTRONOMY = {
        {"Eight planets", "Our solar system contains eight planets orbiting the Sun."},
        {"A stellar center", "The Sun is a star. Its gravity binds the solar system together."},
        {"Rocky neighbors", "Mercury, Venus, Earth and Mars are the four terrestrial planets."},
        {"Giant worlds", "Jupiter and Saturn are gas giants; Uranus and Neptune are ice giants."},
        {"Beyond Neptune", "The Kuiper Belt is a distant region of icy objects beyond Neptune."},
        {"A dwarf planet", "Pluto is classified as a dwarf planet, rather than one of the eight planets."},
        {"Small travelers", "Asteroids and comets are smaller members of the solar system."}
    };
    private static final String[][] WORDS = {
        {"Amble · verb", "To walk at a relaxed pace. Example: We amble along the river after lunch."},
        {"Lucid · adjective", "Clear and easy to understand. Example: Her lucid explanation helped everyone."},
        {"Tend · verb", "To care for something. Example: We tend the garden together."},
        {"Resilient · adjective", "Able to recover after difficulty. Example: The resilient team tried again."},
        {"Glimmer · noun", "A small, faint light. Example: A glimmer appeared beneath the door."},
        {"Savor · verb", "To enjoy something slowly and fully. Example: Savor the first warm day."},
        {"Confluence · noun", "A meeting of rivers or ideas. Example: The town grew at the confluence of two rivers."},
        {"Candid · adjective", "Honest and direct. Example: We had a candid conversation."},
        {"Meander · verb", "To follow a winding course. Example: The stream meanders through the meadow."},
        {"Kindred · adjective", "Similar in character or interests. Example: The hikers were kindred spirits."},
        {"Nimble · adjective", "Able to move or respond quickly. Example: Nimble fingers untied the knot."},
        {"Apt · adjective", "Well suited to a purpose. Example: That was an apt description."},
        {"Solace · noun", "Comfort during sadness or difficulty. Example: She found solace in music."},
        {"Unfurl · verb", "To open out from a rolled or folded shape. Example: The leaves unfurl in spring."}
    };
    private static final String[][] TRIVIA = {
        {"A square question", "How many sides do two separate squares have in total?", "Eight. Each square has four sides: 2 × 4 = 8."},
        {"Prime time", "What is the only even prime number?", "Two. Every larger even number is divisible by two and is not prime."},
        {"Half a dozen", "What is half of a dozen?", "Six. A dozen is twelve; 12 ÷ 2 = 6."},
        {"Triangle total", "How many degrees are in the interior angles of a triangle on a flat plane?", "180 degrees. This applies to Euclidean triangles."},
        {"Time puzzle", "How many minutes are in two and a half hours?", "150 minutes: 2 × 60 + 30."},
        {"Cube count", "How many faces does a cube have?", "Six square faces."},
        {"Number pattern", "What comes next: 1, 4, 9, 16, ... if the pattern is consecutive squares?", "25. The terms are 1², 2², 3², 4² and 5²."},
        {"A fair coin", "How many possible ordered outcomes are there when a coin is tossed twice?", "Four: heads-heads, heads-tails, tails-heads and tails-tails."},
        {"Round trip", "You walk three kilometers out and return by the same route. How far did you walk?", "Six kilometers."},
        {"A thousand", "How many hundreds are in one thousand?", "Ten. 1000 ÷ 100 = 10."},
        {"Clock face", "At exactly three o'clock, what is the smaller angle between the clock hands?", "90 degrees, a right angle."},
        {"A dozen pairs", "How many individual socks are in twelve pairs?", "Twenty-four. Each pair contains two socks."},
        {"Digit hunt", "What is the largest two-digit decimal whole number?", "99. The next integer, 100, has three digits."},
        {"Weekend arithmetic", "How many days are in four complete weeks?", "28 days. Each week has seven days."}
    };
    private static final String[][] INSPIRATION = {
        {"One small start", "Choose one task small enough to begin now. A beginning can be useful even when it is imperfect."},
        {"Room to notice", "Leave a little space in the day to notice something you usually pass by."},
        {"A useful kindness", "Offer one specific kindness: a listening ear, a clear answer, or a shared chore."},
        {"Keep a little", "Record one thing you learned today. Small discoveries deserve a place to land."},
        {"Rest has a place", "A pause can be part of your plan. You do not have to fill every quiet moment."},
        {"Try another path", "If a task feels stuck, change one small part of your approach and observe what happens."},
        {"Close the loop", "Finish one small promise you made to yourself. Let that be enough for a moment."}
    };
    private static final String[][] HOROSCOPES = {
        {"", "Your imaginary cosmic theme today is curiosity. Ask a cheerful question and let the conversation wander."},
        {"", "Today's fictional stars favor a fresh arrangement. Move a familiar object and see the room anew."},
        {"", "Your make-believe celestial forecast suggests patience. Give a slow idea time to find its shape."},
        {"", "Today's playful constellation is the Helpful Teacup. A small kindness is its unofficial symbol."},
        {"", "Your fictional cosmic color is sunset orange. Let it remind you to notice an ordinary bright moment."},
        {"", "The imaginary planets recommend a tiny adventure: take a different route through a familiar routine."},
        {"", "Today's invented star chart features a wide-open window. Make room for a perspective you have not heard before."}
    };
    private static final String[][] ART = {
        {"Tidal geometry", "An original study of overlapping circles and quiet horizons."},
        {"Night windows", "An original rhythm of small lights in a geometric night."},
        {"Paper mountains", "An original landscape built from layered triangles."},
        {"Orbit study", "An original arrangement of circles around an offset center."},
        {"Garden grid", "An original pattern of geometric petals and repeating stems."},
        {"Warm current", "An original field of flowing parallel curves."},
        {"Color steps", "An original ascending sequence of colored blocks."}
    };
    private static final String[][] COMICS = {
        {"Pebble & Sprout: big plans", "Pebble: I am working on my next big move.", "Sprout: Let me know if you need a little push."},
        {"Pebble & Sprout: quiet day", "Sprout: You have been very quiet today.", "Pebble: I am keeping my thoughts grounded."},
        {"Pebble & Sprout: growing", "Sprout: I grew a whole centimeter!", "Pebble: I am taking the scenic route."},
        {"Pebble & Sprout: weather", "Sprout: I hope it rains tomorrow.", "Pebble: I already reserved our puddle."},
        {"Pebble & Sprout: patience", "Sprout: How do you stay so patient?", "Pebble: Years of sitting on the answer."},
        {"Pebble & Sprout: a gift", "Sprout: I brought you some shade.", "Pebble: Perfect. I was feeling a little boulder."},
        {"Pebble & Sprout: the journey", "Sprout: Do you think we will travel far?", "Pebble: One garden story at a time."}
    };
}
