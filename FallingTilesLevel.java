package meblox;

public final class FallingTilesLevel extends GameLevel {
    public int id(){return 6;}
    public String name(){return "Plataformas que caen";}
    public float[][] blocks(){float[][] b=new float[18][7];int[] c={0xD94B45,0xE3C44D,0x4779D1};
for(int i=0;i<b.length;i++){float x=(i%3-1)*2.4f+((i/3)%2==0?0:1.0f),z=-1.0f-(i/3)*2.4f;
b[i]=new float[]{x,0.28f,z,2.0f,0.56f,2.0f,c[i%3]};}return b;}
}
