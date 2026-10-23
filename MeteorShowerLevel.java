package meblox;

public final class MeteorShowerLevel extends GameLevel {
    public int id(){return 5;}
    public String name(){return "Lluvia de meteoritos";}
    public float[][] blocks(){return new float[][]{
{-6,0.75f,-4,2.4f,1.5f,1.0f,0x74614A},{6,0.75f,-2,2.4f,1.5f,1.0f,0x66563F},
{-3,0.55f,4,1.5f,1.1f,1.5f,0x8B6B45},{3,0.55f,-7,1.5f,1.1f,1.5f,0x8B6B45},
{0,0.35f,7,3.0f,0.7f,1.0f,0x6688C7}};}
}
