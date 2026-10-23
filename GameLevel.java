package meblox;

/** Shared geometry contract for a selectable game level. Each game keeps its map in its own source file. */
public abstract class GameLevel {
    public abstract int id();
    public abstract String name();
    /** Rows are centerX, centerY, centerZ, sizeX, sizeY, sizeZ, RGB. */
    public abstract float[][] blocks();
}
