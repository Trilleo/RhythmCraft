package net.trilleo.rhythmcraft.game;

public class Note {
    public final int lane;
    public final NoteType type;
    /** Time in milliseconds from game start when the head should be hit. */
    public final long hitTime;
    /** Duration in milliseconds; 0 for TAP, > 0 for HOLD. */
    public final long duration;

    private boolean hit = false;
    /** True while a HOLD note's head has been pressed and the tail has not yet been released/completed. */
    private boolean holdActive = false;

    public Note(int lane, NoteType type, long hitTime, long duration) {
        this.lane = lane;
        this.type = type;
        this.hitTime = hitTime;
        this.duration = duration;
    }

    public boolean isTap() {
        return type == NoteType.TAP;
    }

    public boolean isHold() {
        return type == NoteType.HOLD;
    }

    public boolean isHit() {
        return hit;
    }

    public void setHit(boolean hit) {
        this.hit = hit;
    }

    public boolean isHoldActive() {
        return holdActive;
    }

    public void setHoldActive(boolean holdActive) {
        this.holdActive = holdActive;
    }
}
