package meblox;

public final class KingHillLevel extends GameLevel {
    public int id(){return 9;}
    public String name(){return "Rey de la colina";}
    public float[][] blocks(){return new float[][]{
{0,0.35f,0,5,0.7f,5,0xD2A84B},{-4,0.18f,0,2,0.36f,3,0x6BAA55},
{4,0.18f,0,2,0.36f,3,0x6BAA55},{0,0.18f,-4,3,0.36f,2,0x6688C7},
{0,0.18f,4,3,0.36f,2,0x6688C7}};}
}
