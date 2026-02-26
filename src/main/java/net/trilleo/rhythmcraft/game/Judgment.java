package net.trilleo.rhythmcraft.game;

/**
 * Chunithm-style hit-judgment grades for each scored note-unit.
 *
 * <p>Timing windows (applies to both press and HOLD tail release):
 * <ul>
 *   <li>CRITICAL_JUSTICE – within ±50 ms → 101% of note-unit value</li>
 *   <li>JUSTICE          – within ±83 ms  → 100% of note-unit value; combo continues</li>
 *   <li>ATTACK           – within ±116 ms → 60%  of note-unit value; combo broken</li>
 *   <li>MISS             – outside ±116 ms → 0%;  combo broken</li>
 * </ul>
 * Maximum achievable score = 1 010 000 (every note-unit is CRITICAL_JUSTICE).
 */
public enum Judgment {
    /** 101% of note-unit value. Combo continues. */
    CRITICAL_JUSTICE,
    /** 100% of note-unit value. Combo continues. */
    JUSTICE,
    /** 60% of note-unit value. Combo broken. */
    ATTACK,
    /** 0% score. Combo broken. */
    MISS
}
