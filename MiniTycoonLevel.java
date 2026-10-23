package meblox;

public final class MiniTycoonLevel extends GameLevel {
    public int id(){return 13;}
    public String name(){return "Mini tycoon";}
    public float[][] blocks(){return new float[][]{
{0,0.18f,0,8,0.36f,8,0x6BAA55},{-8,0.8f,0,1,1.6f,10,0x6688C7},
{8,0.8f,0,1,1.6f,10,0x6688C7},{0,0.8f,-8,10,1.6f,1,0x6688C7},
{0,0.205f,-3,1.8f,0.05f,1.8f,0xE3C44D}};}
}
