package meblox;

public final class HideSeekLevel extends GameLevel {
    public int id(){return 11;}
    public String name(){return "Escondite";}

    public float[][] blocks(){
        float[][] b=new float[80][7];
        int p=0;
        p=addCabin(b,p,-15.0f,-13.0f,false,0x795A3D);
        p=addCabin(b,p,15.0f,-13.0f,false,0x66563F);
        p=addCabin(b,p,-15.0f,13.0f,true,0x74614A);
        p=addCabin(b,p,15.0f,13.0f,true,0x8B6B45);
        p=addCabin(b,p,-1.5f,-17.0f,false,0x66563F);

        // Central maze: alternating walls force the seeker to change lanes.
        p=put(b,p,-8.0f,1.25f,-3.5f,0.45f,2.5f,8.0f,0x66563F);
        p=put(b,p,-4.0f,1.0f,-1.0f,0.45f,2.0f,6.0f,0x795438);
        p=put(b,p,0.0f,1.3f,-4.0f,7.0f,2.6f,0.45f,0x74614A);
        p=put(b,p,4.0f,1.0f,0.5f,0.45f,2.0f,7.0f,0x66563F);
        p=put(b,p,8.0f,1.25f,3.5f,0.45f,2.5f,8.0f,0x795A3D);
        p=put(b,p,-2.0f,1.0f,5.0f,7.0f,2.0f,0.45f,0x8B6B45);
        p=put(b,p,2.0f,1.0f,-8.5f,8.0f,2.0f,0.45f,0x66563F);

        // Crate stacks, narrow gaps, and low cover make extra places to duck behind.
        p=put(b,p,-10.0f,0.65f,6.0f,2.2f,1.3f,2.2f,0x8B6B45);
        p=put(b,p,-10.0f,1.95f,6.0f,1.5f,1.3f,1.5f,0x795438);
        p=put(b,p,10.0f,0.65f,-6.0f,2.4f,1.3f,2.0f,0x74614A);
        p=put(b,p,10.0f,1.95f,-6.0f,1.5f,1.3f,1.4f,0x66563F);
        p=put(b,p,6.5f,0.65f,9.0f,2.0f,1.3f,2.0f,0x795438);
        p=put(b,p,-6.0f,0.65f,-9.0f,2.2f,1.3f,2.2f,0x8B6B45);
        p=put(b,p,0.0f,0.7f,10.0f,5.0f,1.4f,0.45f,0x66563F);
        p=put(b,p,-2.2f,0.65f,10.0f,0.45f,1.3f,3.0f,0x74614A);
        p=put(b,p,2.2f,0.65f,10.0f,0.45f,1.3f,3.0f,0x795A3D);

        float[][] compact=new float[p][7];
        for(int i=0;i<p;i++)compact[i]=b[i];
        return compact;
    }

    private static int addCabin(float[][] b,int p,float x,float z,
            boolean northDoor,int color){
        float y=1.45f;
        p=put(b,p,x,y,z-2.9f,6.2f,2.9f,0.45f,color);
        p=put(b,p,x-2.9f,y,z,0.45f,2.9f,6.2f,color);
        p=put(b,p,x+2.9f,y,z,0.45f,2.9f,6.2f,color);
        float entryZ=z+(northDoor?-2.9f:2.9f);
        p=put(b,p,x-2.15f,y,entryZ,1.5f,2.9f,0.45f,color);
        p=put(b,p,x+2.15f,y,entryZ,1.5f,2.9f,0.45f,color);
        p=put(b,p,x,3.0f,z,6.2f,0.2f,6.2f,0x8B6B45);
        // Interior divider hides a hider from the doorway without sealing the room.
        p=put(b,p,x,1.25f,z+0.65f,2.2f,2.5f,0.35f,0x66563F);
        p=put(b,p,x+(northDoor?1.7f:-1.7f),0.7f,z-0.7f,1.1f,1.4f,1.1f,0x795438);
        return p;
    }

    private static int put(float[][] b,int p,float x,float y,float z,
            float sx,float sy,float sz,int color){
        b[p++]=new float[]{x,y,z,sx,sy,sz,color};
        return p;
    }
}