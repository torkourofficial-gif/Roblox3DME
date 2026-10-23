package meblox;

public final class SwordArenaLevel extends GameLevel {
    public int id(){return 10;}
    public String name(){return "Arena de espadas";}
    public float[][] blocks(){float[][] b=new float[8][7];
for(int i=0;i<8;i++){double a=6.2831853*i/8.0;b[i]=new float[]{(float)Math.cos(a)*7.0f,0.8f,(float)Math.sin(a)*7.0f,1.3f,1.6f,1.3f,i%2==0?0x74614A:0x6688C7};}return b;}
}
