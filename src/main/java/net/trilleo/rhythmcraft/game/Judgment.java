package net.trilleo.rhythmcraft.game;

/**
 * Chunithm-style hit-judgment grades for each scored note-unit.
 *
 * <p>Timing windows (applies to both press and HOLD tail release):
 * <ul>
 *   <li>CRITICAL_JUSTICE – within ±50 ms of the judgment line</li>
 *   <li>JUSTICE          – within ±83 ms</li>
 *   <li>ATTACK           – within ±116 ms</li>
 *   <li>MISS             – outside ±116 ms (or never pressed)</li>
 * </ul>
 */
public enum Judgment {
    /** Perfect timing. Full note-unit score. Combo continues. */
    CRITICAL_JUSTICE,
    /** Good timing. Half note-unit score. Combo continues. */
    JUSTICE,
    /** Borderline hit. No score. Combo broken. */
    ATTACK,
    /** Not hit in time. No score. Combo broken. */
    MISS
}
