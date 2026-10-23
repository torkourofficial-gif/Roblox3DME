package meblox;

public final class LavaRiseLevel extends GameLevel {
    public int id(){return 4;}
    public String name(){return "Lava ascendente";}
    public float[][] blocks(){float[][] b=new float[18][7];int[] c={0xE9B44C,0xD97B35,0xC85C3C,0xE5CF66};
for(int i=0;i<b.length;i++){float x=(float)Math.sin(i*1.7)*4.0f,z=-1.0f-i*1.05f,y=0.62f+i*0.48f;
b[i]=new float[]{x,y,z,2.8f,0.45f,2.8f,c[i%4]};}return b;}
}
