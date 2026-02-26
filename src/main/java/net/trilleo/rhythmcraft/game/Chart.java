package net.trilleo.rhythmcraft.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialisable rhythm-game chart: metadata + ordered list of notes.
 * Stored on disk as JSON via {@link ChartSerializer}.
 */
public class Chart {

    public String title     = "Untitled";
    /** Relative or absolute path to the music file (stored for reference). */
    public String musicPath = "";
    public int    keyCount  = 4;
    public int    bpm       = 120;
    /** Chart offset in milliseconds (positive = delay before notes start). */
    public long   offsetMs  = 0;
    public List<ChartNote> notes = new ArrayList<>();

    // ── Inner note record ─────────────────────────────────────────────────────

    /** A single serialisable note entry. */
    public static class ChartNote {
        public int    lane;
        public String type;     // "TAP" or "HOLD"
        public long   hitTime;  // ms from chart start
        public long   duration; // ms; 0 for TAP

        /** No-arg constructor required by Gson. */
        public ChartNote() {}

        public ChartNote(int lane, NoteType type, long hitTime, long duration) {
            this.lane     = lane;
            this.type     = type.name();
            this.hitTime  = hitTime;
            this.duration = duration;
        }
    }

    // ── Conversion ────────────────────────────────────────────────────────────

    /**
     * Returns a list of {@link Note} objects suitable for the game engine.
     * Entries with unknown or null type are silently skipped.
     */
    public List<Note> toNotes() {
        List<Note> result = new ArrayList<>();
        if (notes == null) return result;
        for (ChartNote cn : notes) {
            NoteType nt;
            try {
                nt = NoteType.valueOf(cn.type);
            } catch (IllegalArgumentException | NullPointerException ignored) {
                continue;
            }
            result.add(new Note(cn.lane, nt, cn.hitTime, cn.duration));
        }
        return result;
    }

    /**
     * Total number of scored note-units used in the 10 100 000 formula.
     * TAP = 1 unit; HOLD = 2 units (head + tail judged separately).
     */
    public int totalNotes() {
        if (notes == null) return 0;
        int n = 0;
        for (ChartNote c : notes) {
            n++;
            if ("HOLD".equals(c.type)) n++; // tail counts separately
        }
        return n;
    }
}
