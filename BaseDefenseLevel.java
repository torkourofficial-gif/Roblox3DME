package meblox;

public final class BaseDefenseLevel extends GameLevel {
    public int id(){return 14;}
    public String name(){return "Defensa de base";}
    public float[][] blocks(){return new float[][]{
{-5,0.2f,-5,1.5f,0.4f,1.5f,0x6688C7},{5,0.2f,-5,1.5f,0.4f,1.5f,0x6688C7},
{-5,0.2f,5,1.5f,0.4f,1.5f,0x6688C7},{5,0.2f,5,1.5f,0.4f,1.5f,0x6688C7},
{0,0.25f,0,3,0.5f,3,0xD2A84B}};}
}
