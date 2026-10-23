package meblox;

public final class HideSeekLevel extends GameLevel {
    public int id(){return 11;}
    public String name(){return "Escondite";}
    public float[][] blocks(){
        float[][] b=new float[40][7];int p=0;
        float[][] rooms={{-14,-13},{14,-13},{-14,13},{14,13},{-1.5f,-17}};
        for(int i=0;i<rooms.length;i++){
            float x=rooms[i][0],z=rooms[i][1];
            b[p++]=new float[]{x,1.2f,z-2.25f,5,2.4f,0.45f,0x74614A};
            b[p++]=new float[]{x-2.25f,1.2f,z,0.45f,2.4f,4.5f,0x66563F};
            b[p++]=new float[]{x+2.25f,1.2f,z,0.45f,2.4f,4.5f,0x795438};
            b[p++]=new float[]{x-1.55f,1.2f,z+2.25f,1.75f,2.4f,0.45f,0x8B6B45};
            b[p++]=new float[]{x+1.55f,1.2f,z+2.25f,1.75f,2.4f,0.45f,0x8B6B45};
            b[p++]=new float[]{x,2.4f,z,5,0.25f,5,0x8B6B45};
        }
        b[p++]=new float[]{0,1.1f,-8,18,2.2f,0.5f,0x66563F};
        b[p++]=new float[]{-8,0.8f,0,0.5f,1.6f,14,0x795438};
        b[p++]=new float[]{8,0.8f,1,0.5f,1.6f,14,0x74614A};
        b[p++]=new float[]{-3,0.6f,7,6,1.2f,0.5f,0x8B6B45};
        b[p++]=new float[]{4,0.55f,-2,5,1.1f,0.5f,0x66563F};
        float[][] compact=new float[p][7];
        for(int i=0;i<p;i++)compact[i]=b[i];
        return compact;
    }
}
