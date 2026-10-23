package meblox;

public final class MiniTycoonLevel extends GameLevel {
    public int id(){return 13;}
    public String name(){return "Mini tycoon";}
    public float[][] blocks(){return new float[][]{
        // Player's yellow base, then the three bot-owned bases.
        {0.0f,-0.08f,-7.0f,5.6f,0.20f,5.6f,0xD2A84B},
        {-7.0f,-0.08f,6.0f,5.6f,0.20f,5.6f,0xC43C35},
        {0.0f,-0.08f,7.0f,5.6f,0.20f,5.6f,0x6688C7},
        {7.0f,-0.08f,6.0f,5.6f,0.20f,5.6f,0x7F58A5},
        // The smaller gold plate marks where the player can build with key 3.
        {0.0f,-0.06f,-7.0f,1.8f,0.16f,1.8f,0xF5D34A}
    };}
}