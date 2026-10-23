package meblox;

public final class ZombieHordeLevel extends GameLevel {
    public int id(){return 2;}
    public String name(){return "Horda zombi";}
    public float[][] blocks(){return new float[][]{// Low cover around the arena.
            {-7.5f,0.75f,-2.0f, 2.8f,1.5f,0.8f, 0x74614A},
            { 7.5f,0.75f,-2.0f, 2.8f,1.5f,0.8f, 0x74614A},
            {-7.5f,0.75f, 3.0f, 2.8f,1.5f,0.8f, 0x66563F},
            { 7.5f,0.75f, 3.0f, 2.8f,1.5f,0.8f, 0x66563F},
            {-3.2f,0.55f,-1.8f, 1.2f,1.1f,1.2f, 0x8B6B45},
            { 3.2f,0.55f, 1.8f, 1.2f,1.1f,1.2f, 0x8B6B45},
            { 0.0f,0.45f, 5.8f, 3.2f,0.9f,0.8f, 0x6D5842},
            // Jumpable stepped platforms make a raised route through the arena.
            {-5.5f,0.30f,-5.0f, 3.0f,0.6f,3.0f, 0x6BAA55},
            {-5.5f,0.80f,-7.8f, 2.6f,0.5f,2.6f, 0xD2A84B},
            {-3.4f,1.30f,-9.3f, 2.4f,0.5f,2.4f, 0x6688C7},
            {-0.7f,1.80f,-9.0f, 2.5f,0.5f,2.5f, 0xA26BC0},
            { 2.3f,1.30f,-8.0f, 2.4f,0.5f,2.4f, 0xD2A84B},
            { 5.1f,0.80f,-6.0f, 2.8f,0.5f,2.8f, 0x6BAA55},
            { 5.7f,0.30f,-3.0f, 3.0f,0.6f,2.4f, 0x6688C7},
            // Side ledges and a central raised island.
            {-9.0f,0.35f, 0.0f, 1.8f,0.7f,4.0f, 0x6BAA55},
            { 9.0f,0.35f, 0.0f, 1.8f,0.7f,4.0f, 0x6BAA55},
            {-3.8f,0.40f, 7.3f, 2.6f,0.8f,2.6f, 0xD2A84B},
            { 0.0f,0.85f, 8.0f, 3.4f,0.7f,2.8f, 0x6688C7},
            { 3.8f,0.40f, 7.3f, 2.6f,0.8f,2.6f, 0xA26BC0}};}
}
