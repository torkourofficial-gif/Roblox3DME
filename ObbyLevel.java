package meblox;

public final class ObbyLevel extends GameLevel {
    public int id(){return 0;}
    public String name(){return "Obby";}
    public float[][] blocks(){float[][] b=new float[81][7];
float[] route={0,1.5f,3,3,1.5f,0,-1.5f,-3,-3,-1.5f};
int[] colors={0xD94B45,0xE3C44D,0x4779D1,0xB56AC8};
for(int i=0;i<b.length;i++){boolean end=i==b.length-1;float h=end?0.60f:0.80f;
float top=end?h:0.65f+((i*7)%4)*0.12f;
b[i]=new float[]{route[i%route.length],top-h*0.5f,-3.0f-i*2.8f,
end?5.0f:2.2f,h,end?5.0f:2.0f,end?0x63C7A5:colors[i%colors.length]};}
return b;}
}
