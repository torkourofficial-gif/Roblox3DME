package meblox;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Imports simple Part geometry from Roblox binary or XML place files. */
public final class RobloxMapImporter {
    private static final int MAX_FILE = 20 * 1024 * 1024;
    private static final int MAX_PARTS = 10000;
    private static final float STUD_TO_GAME_UNIT = 0.4f;
    public static final int EFFECT_KILL = 1, EFFECT_SIT = 2, EFFECT_BOUNCE = 4;
    private static float[] spawnPoint;
    private static float[][] visualBlocks;
    private RobloxMapImporter() {}

    private static final class Reader {
        byte[] b; int length,p;
        Reader(byte[] data,int size) { b=data;length=size; }
        int u8() throws Exception { need(1); return b[p++]&255; }
        int i32() throws Exception { need(4); int v=(b[p]&255)|((b[p+1]&255)<<8)|((b[p+2]&255)<<16)|(b[p+3]<<24); p+=4; return v; }
        int u32() throws Exception { return i32(); }
        long i64() throws Exception { long lo=i32()&0xffffffffL,hi=i32();return (hi<<32)|lo; }
        float f32le() throws Exception { return Float.intBitsToFloat(i32()); }
        String str() throws Exception { int n=i32(); if(n<0||n>length-p)throw new Exception("RBXL: string size is invalid"); String s=new String(b,p,n,"UTF-8");p+=n;return s; }
        void need(int n) throws Exception { if(n<0||n>length-p)throw new Exception("RBXL: unexpected end of file"); }
        byte[] bytes(int n) throws Exception { need(n);byte[] r=new byte[n];System.arraycopy(b,p,r,0,n);p+=n;return r; }
    }
    private static final class MapBuffer extends ByteArrayOutputStream {
        byte[] data(){return buf;}
        int length(){return count;}
    }
    private static final class Part {
        int ref, color=0x888888, behavior,shape=1; String name=""; float x,y,z,sx=4,sy=1,sz=4; boolean hasFrame,hasSize,spawn,canCollide=true,visible=true;
        Part(int r){ref=r;}
    }
    private static final class ClassInfo {
        int id; String name; int[] refs,parentRefs; String[] sources; Part[] parts;
        ClassInfo(int i,String n,int[] r){id=i;name=n;refs=r;parentRefs=new int[r.length];if(isScript(n))sources=new String[r.length];if(isPart(n)){parts=new Part[r.length];for(int k=0;k<r.length;k++){parts[k]=new Part(r[k]);parts[k].spawn="SpawnLocation".equals(n);if("Seat".equals(n)||"VehicleSeat".equals(n))parts[k].behavior|=EFFECT_SIT;}}}
    }
    private static final class XmlFrame {String className,name;int start;String parentPartName;XmlFrame(String c,int s,String n,String p){className=c;start=s;name=n;parentPartName=p;}}

    public static float[][] load(InputStream input) throws Exception {
        spawnPoint=null;
        visualBlocks=null;
        MapBuffer out=new MapBuffer(); byte[] buf=new byte[4096]; int n;
        while((n=input.read(buf))!=-1){
            if(n>MAX_FILE-out.length())throw new Exception("Map file is larger than 20 MB");
            out.write(buf,0,n);
        }
        byte[] file=out.data();int fileLength=out.length();
        if(fileLength==0)throw new Exception("Empty map file");
        if(file[0]=='<'&&!(fileLength>7&&file[7]=='!'))return loadXml(new String(file,0,fileLength,"UTF-8"));
        return loadModernBinary(file,fileLength);
    }

    /** Version-0 binary RBXL reader; the XML reader above remains for legacy client exports. */
    private static float[][] loadModernBinary(byte[] file,int fileLength) throws Exception {
        if(fileLength<32)throw new Exception("Not a Roblox .rbxl file");
        Reader r=new Reader(file,fileLength);
        byte[] magic=r.bytes(14); String signature=new String(magic,0,8,"UTF-8");
        if(!"<roblox!".equals(signature))throw new Exception("Choose a binary .rbxl place file");
        if(r.u8()!=0||r.u8()!=0)throw new Exception("Unsupported RBXL version");
        int classCount=r.i32(),instanceCount=r.i32();r.bytes(8);
        if(classCount<0||classCount>2000||instanceCount<0||instanceCount>20000)throw new Exception("Map has too many Roblox objects");
        java.util.Vector classes=new java.util.Vector();
        long decodedBytes=0;
        while(r.p+16<=r.length){
            String chunk=new String(r.bytes(4),"UTF-8"); int compressed=r.i32(), size=r.i32(); r.bytes(4);
            if(size<0||size>MAX_FILE||compressed<0||compressed>r.length-r.p||(compressed==0&&size>r.length-r.p))throw new Exception("RBXL: invalid chunk size");
            decodedBytes+=(long)size;
            if(decodedBytes>MAX_FILE)throw new Exception("RBXL: decompressed data exceeds 20 MB");
            byte[] body=r.bytes(compressed==0?size:compressed);
            if("END".equals(chunk.substring(0,3)))break;
            if(compressed>0){
                if(body.length>=4&&(body[0]&255)==0x28&&(body[1]&255)==0xb5&&(body[2]&255)==0x2f&&(body[3]&255)==0xfd)
                    throw new Exception("This RBXL uses Zstandard compression; export an uncompressed/LZ4 copy from Studio");
                body=inflateLz4(body,size);
            }
            if("INST".equals(chunk))readInstances(body,classes);
            else if("PROP".equals(chunk))readProperty(body,classes);
            else if("PRNT".equals(chunk))readParents(body,classes);
        }
        java.util.Vector parts=new java.util.Vector();
        for(int c=0;c<classes.size();c++){
            ClassInfo ci=(ClassInfo)classes.elementAt(c);
            if(!isPart(ci.name))continue;
            for(int j=0;j<ci.parts.length;j++)if(ci.parts[j].hasFrame&&ci.parts[j].hasSize)parts.addElement(ci.parts[j]);
        }
        applyBinaryScriptBehaviors(classes);
        // The parsed instances are retained by the parts vector; drop class/reference
        // tables before allocating the render and collision arrays for large places.
        classes.removeAllElements();
        return finishParts(parts);
    }

    private static float[][] loadXml(String xml)throws Exception{
        if(xml.indexOf("<roblox")<0)throw new Exception("Choose a Roblox .rbxl map");
        java.util.Vector parts=new java.util.Vector();int cursor=0;
        while((cursor=xml.indexOf("<Item",cursor))>=0){
            int itemEnd=xml.indexOf('>',cursor);if(itemEnd<0)break;
            String itemTag=xml.substring(cursor,itemEnd+1);String className=xmlAttribute(itemTag,"class");cursor=itemEnd+1;
            if(!isPart(className))continue;
            int propsAt=xml.indexOf("<Properties",cursor);int nextItem=xml.indexOf("<Item",cursor);
            if(propsAt<0||(nextItem>=0&&nextItem<propsAt))continue;
            int propsStart=xml.indexOf('>',propsAt);int propsEnd=xml.indexOf("</Properties>",propsStart);
            if(propsStart<0||propsEnd<0)continue;
            String props=xml.substring(propsStart+1,propsEnd);
            String frame=xmlProperty(props,"CFrame");String size=xmlProperty(props,"size");if(size==null)size=xmlProperty(props,"Size");
            if(frame!=null&&size!=null){
                Part p=new Part(0);String partName=xmlProperty(props,"Name");if(partName!=null)p.name=xmlText(partName).trim();p.spawn="SpawnLocation".equals(className);if("Seat".equals(className)||"VehicleSeat".equals(className))p.behavior|=EFFECT_SIT;p.x=xmlChildFloat(frame,"X");p.y=xmlChildFloat(frame,"Y");p.z=xmlChildFloat(frame,"Z");
                p.sx=Math.abs(xmlChildFloat(size,"X"));p.sy=Math.abs(xmlChildFloat(size,"Y"));p.sz=Math.abs(xmlChildFloat(size,"Z"));p.hasFrame=p.hasSize=true;
                String brick=xmlProperty(props,"BrickColor");if(brick!=null){String value=xmlText(brick);try{p.color=brickColor(Integer.parseInt(value.trim()));}catch(Exception ignored){}}
                String color=xmlProperty(props,"Color");if(color!=null){try{float red=xmlChildFloat(color,"R"),green=xmlChildFloat(color,"G"),blue=xmlChildFloat(color,"B");if(color.indexOf("Color3uint8")>=0){red/=255f;green/=255f;blue/=255f;}p.color=rgb(red,green,blue);}catch(Exception ignored){}}
                String transparency=xmlProperty(props,"Transparency");if(transparency!=null){try{p.visible=Float.parseFloat(xmlText(transparency).trim())<1.0f;}catch(Exception ignored){}}
                String canCollide=xmlProperty(props,"CanCollide");if(canCollide!=null)p.canCollide="true".equalsIgnoreCase(xmlText(canCollide).trim());
                String shape=xmlProperty(props,"shape");if(shape==null)shape=xmlProperty(props,"Shape");if(shape!=null){try{p.shape=Integer.parseInt(xmlText(shape).trim());}catch(Exception ignored){}}
                if(p.sx>0&&p.sy>0&&p.sz>0)parts.addElement(p);
            }
            cursor=propsEnd+13;
        }
        applyXmlScriptBehaviors(xml,parts);
        return finishParts(parts);
    }
    private static String xmlAttribute(String tag,String name){
        int at=tag.indexOf(name+"=");if(at<0)return "";int start=at+name.length()+1;if(start>=tag.length())return "";
        char quote=tag.charAt(start);if(quote!='\''&&quote!='\"')return "";int end=tag.indexOf(quote,start+1);return end<0?"":tag.substring(start+1,end);
    }
    private static String xmlProperty(String props,String name){
        int at=props.indexOf("name=\""+name+"\"");if(at<0)at=props.indexOf("name='"+name+"'");if(at<0)return null;
        int start=props.lastIndexOf('<',at),end=props.indexOf('>',at);if(start<0||end<0)return null;
        int nameEnd=start+1;while(nameEnd<end&&props.charAt(nameEnd)!=' '&&props.charAt(nameEnd)!='>')nameEnd++;
        String tag=props.substring(start+1,nameEnd);int close=props.indexOf("</"+tag+">",end);if(close<0)return null;
        return props.substring(start,close+tag.length()+3);
    }
    private static String xmlText(String prop){int start=prop.indexOf('>')+1,end=prop.lastIndexOf('<');return start<=0||end<start?"":prop.substring(start,end);}
    private static float xmlChildFloat(String prop,String child)throws Exception{String open="<"+child+">",close="</"+child+">";int start=prop.indexOf(open);if(start<0)throw new Exception("Missing "+child+" in map part");start+=open.length();int end=prop.indexOf(close,start);if(end<0)throw new Exception("Invalid "+child+" in map part");return Float.parseFloat(prop.substring(start,end).trim());}
    private static float[][] finishParts(java.util.Vector parts)throws Exception{
        if(parts.size()==0)throw new Exception("No importable Part objects found in this map");
        if(parts.size()>MAX_PARTS)throw new Exception("Map has more than 10000 solid parts; simplify it in Roblox Studio first");
        float minX=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,minZ=Float.MAX_VALUE,maxZ=-Float.MAX_VALUE,minY=Float.MAX_VALUE;
        Part baseplate=null;
        for(int i=0;i<parts.size();i++){Part p=(Part)parts.elementAt(i);minX=Math.min(minX,p.x-p.sx/2);maxX=Math.max(maxX,p.x+p.sx/2);minZ=Math.min(minZ,p.z-p.sz/2);maxZ=Math.max(maxZ,p.z+p.sz/2);minY=Math.min(minY,p.y-p.sy/2);if(baseplate==null&&"Baseplate".equalsIgnoreCase(p.name))baseplate=p;if(isTrampolineName(p.name))p.behavior|=EFFECT_BOUNCE;}
        float cx=baseplate==null?(minX+maxX)/2:baseplate.x;
        float cz=baseplate==null?(minZ+maxZ)/2:baseplate.z;
        float baseY=baseplate==null?minY:baseplate.y-baseplate.sy/2;
        for(int i=0;i<parts.size()&&baseplate==null;i++){Part p=(Part)parts.elementAt(i);if(p.spawn){baseY=p.y-p.sy/2;break;}}
        float[][] result=new float[parts.size()][];java.util.Vector visuals=new java.util.Vector();
        for(int i=0;i<result.length;i++){
            Part p=(Part)parts.elementAt(i);float x=(p.x-cx)*STUD_TO_GAME_UNIT,y=(p.y-baseY)*STUD_TO_GAME_UNIT+0.03f,z=(p.z-cz)*STUD_TO_GAME_UNIT;
            result[i]=new float[]{x,y,z,p.sx*STUD_TO_GAME_UNIT,p.sy*STUD_TO_GAME_UNIT,p.sz*STUD_TO_GAME_UNIT,p.color&0xffffff,p.behavior,p.canCollide?1:0};
            if(p.visible)visuals.addElement(new float[]{x,y,z,p.sx*STUD_TO_GAME_UNIT,p.sy*STUD_TO_GAME_UNIT,p.sz*STUD_TO_GAME_UNIT,p.color&0xffffff,p.shape});
            if(p.spawn&&spawnPoint==null)spawnPoint=new float[]{x,y+p.sy*STUD_TO_GAME_UNIT*0.5f,z};
        }
        if(spawnPoint==null&&baseplate!=null)spawnPoint=new float[]{0,baseplate.sy*STUD_TO_GAME_UNIT+0.03f,0};
        float[][] visualSource=new float[visuals.size()][];visuals.copyInto(visualSource);visuals.removeAllElements();parts.removeAllElements();baseplate=null;
        visualBlocks=greedyMerge(visualSource);
        return result;
    }
    public static float[] spawnPoint(){return spawnPoint==null?null:new float[]{spawnPoint[0],spawnPoint[1],spawnPoint[2]};}
    public static float[][] greedyBlocks(){return visualBlocks;}

    private static float[][] greedyMerge(float[][] source){
        // This array is private to finishParts and is not needed afterward; sort and
        // merge it in place rather than duplicating every imported visual row.
        float[][] blocks=source;
        int count=blocks.length,previous;
        do{
            previous=count;
            for(int axis=0;axis<3;axis++){
                quickSort(blocks,0,count-1,axis);
                int write=0;
                for(int read=0;read<count;read++){
                    float[] block=blocks[read];
                    if(write>0&&canMerge(blocks[write-1],block,axis))mergeInto(blocks[write-1],block,axis);
                    else blocks[write++]=block;
                }
                count=write;
            }
        }while(count<previous);
        float[][] merged=new float[count][];System.arraycopy(blocks,0,merged,0,count);return merged;
    }
    private static void quickSort(float[][] blocks,int low,int high,int axis){
        int left=low,right=high;if(left>=right)return;float[] pivot=blocks[(left+right)>>>1];
        while(left<=right){
            while(compareForAxis(blocks[left],pivot,axis)<0)left++;
            while(compareForAxis(blocks[right],pivot,axis)>0)right--;
            if(left<=right){float[] swap=blocks[left];blocks[left]=blocks[right];blocks[right]=swap;left++;right--;}
        }
        if(low<right)quickSort(blocks,low,right,axis);
        if(left<high)quickSort(blocks,left,high,axis);
    }
    private static int compareForAxis(float[] a,float[] b,int axis){
        int c=compareInt((int)a[6],(int)b[6]);if(c!=0)return c;
        int u=(axis+1)%3,v=(axis+2)%3;
        c=compareInt(q(a[u]),q(b[u]));if(c!=0)return c;c=compareInt(q(a[3+u]),q(b[3+u]));if(c!=0)return c;
        c=compareInt(q(a[v]),q(b[v]));if(c!=0)return c;c=compareInt(q(a[3+v]),q(b[3+v]));if(c!=0)return c;
        return compareInt(q(a[axis]-a[3+axis]*0.5f),q(b[axis]-b[3+axis]*0.5f));
    }
    private static int q(float v){return (int)Math.floor(v*1000.0f+0.5f);}
    private static int compareInt(int a,int b){return a<b?-1:(a>b?1:0);}
    private static boolean canMerge(float[] a,float[] b,int axis){
        if((int)a[6]!=(int)b[6]||(int)a[7]!=1||(int)b[7]!=1)return false;
        int u=(axis+1)%3,v=(axis+2)%3;
        if(Math.abs(a[u]-b[u])>0.001f||Math.abs(a[v]-b[v])>0.001f||Math.abs(a[3+u]-b[3+u])>0.001f||Math.abs(a[3+v]-b[3+v])>0.001f)return false;
        float edgeA=a[axis]+a[3+axis]*0.5f,otherA=a[axis]-a[3+axis]*0.5f;
        float edgeB=b[axis]+b[3+axis]*0.5f,otherB=b[axis]-b[3+axis]*0.5f;
        return Math.abs(edgeA-otherB)<=0.001f||Math.abs(edgeB-otherA)<=0.001f;
    }
    private static void mergeInto(float[] a,float[] b,int axis){
        float min=Math.min(a[axis]-a[3+axis]*0.5f,b[axis]-b[3+axis]*0.5f);
        float max=Math.max(a[axis]+a[3+axis]*0.5f,b[axis]+b[3+axis]*0.5f);
        a[axis]=(min+max)*0.5f;a[3+axis]=max-min;
    }

    private static boolean isPart(String s){return "Part".equals(s)||"WedgePart".equals(s)||"CornerWedgePart".equals(s)||"SpawnLocation".equals(s)||"Seat".equals(s)||"VehicleSeat".equals(s)||"TrussPart".equals(s)||"MeshPart".equals(s)||"UnionOperation".equals(s)||"PartOperation".equals(s)||"NegateOperation".equals(s);}
    private static boolean isTrampolineName(String name){if(name==null)return false;String s=name.toLowerCase();return s.indexOf("bouncer")>=0||s.indexOf("tramp")>=0||s.indexOf("jumppad")>=0||s.indexOf("launchpad")>=0||s.indexOf("springboard")>=0;}
    private static boolean isScript(String s){return "Script".equals(s)||"LocalScript".equals(s);}
    private static int scriptBehavior(String source){
        if(source==null)return 0;
        StringBuffer normalized=new StringBuffer(source.length());
        for(int i=0;i<source.length();i++){
            char c=Character.toLowerCase(source.charAt(i));
            if(c!=' '&&c!='\t'&&c!='\r'&&c!='\n')normalized.append(c);
        }
        String compact=normalized.toString();
        int result=0;
        if(compact.indexOf(".health=0")>=0||compact.indexOf(":takedamage(")>=0||compact.indexOf(":breakjoints(")>=0)result|=EFFECT_KILL;
        if(compact.indexOf(".sit=true")>=0||compact.indexOf(".sit=1")>=0||compact.indexOf("changestate(enum.humanoidstatetype.seated)")>=0)result|=EFFECT_SIT;
        if(compact.indexOf(".velocity=vector3.new(")>=0||compact.indexOf(".assemblylinearvelocity=vector3.new(")>=0||compact.indexOf(".jumppower=")>=0)result|=EFFECT_BOUNCE;
        return result;
    }
    private static void applyBinaryScriptBehaviors(java.util.Vector classes){
        java.util.Hashtable byRef=new java.util.Hashtable(),owner=new java.util.Hashtable();
        for(int c=0;c<classes.size();c++){ClassInfo ci=(ClassInfo)classes.elementAt(c);for(int i=0;i<ci.refs.length;i++){Integer ref=new Integer(ci.refs[i]);owner.put(ref,ci);if(ci.parts!=null)byRef.put(ref,ci.parts[i]);}}
        for(int c=0;c<classes.size();c++){
            ClassInfo ci=(ClassInfo)classes.elementAt(c);if(!isScript(ci.name)||ci.sources==null)continue;
            for(int i=0;i<ci.sources.length;i++){
                int effect=scriptBehavior(ci.sources[i]),parent=ci.parentRefs[i];if(effect==0)continue;
                for(int depth=0;depth<12&&parent!=0;depth++){
                    Part target=(Part)byRef.get(new Integer(parent));if(target!=null){target.behavior|=effect;break;}
                    ClassInfo parentClass=(ClassInfo)owner.get(new Integer(parent));if(parentClass==null)break;
                    int index=refIndex(parentClass.refs,parent);if(index<0)break;parent=parentClass.parentRefs[index];
                }
            }
        }
    }
    private static int refIndex(int[] refs,int value){for(int i=0;i<refs.length;i++)if(refs[i]==value)return i;return -1;}
    private static void readParents(byte[] data,java.util.Vector classes)throws Exception{
        Reader r=new Reader(data,data.length);int version=r.u8();if(version!=0)return;int count=r.i32();
        if(count<0||count>20000)throw new Exception("RBXL: invalid parent table");
        int[] refs=ints(r,count),parents=ints(r,count);java.util.Hashtable owners=new java.util.Hashtable();
        for(int c=0;c<classes.size();c++){ClassInfo ci=(ClassInfo)classes.elementAt(c);for(int i=0;i<ci.refs.length;i++)owners.put(new Integer(ci.refs[i]),new Object[]{ci,new Integer(i)});}
        for(int i=0;i<count;i++){Object[] owner=(Object[])owners.get(new Integer(refs[i]));if(owner!=null){ClassInfo ci=(ClassInfo)owner[0];ci.parentRefs[((Integer)owner[1]).intValue()]=parents[i];}}
    }
    private static void applyXmlScriptBehaviors(String xml,java.util.Vector parts){
        java.util.Vector stack=new java.util.Vector();int cursor=0;
        while(cursor<xml.length()){
            int open=xml.indexOf("<Item",cursor),close=xml.indexOf("</Item>",cursor);
            if(open<0&&close<0)break;
            if(open>=0&&(close<0||open<close)){
                int end=xml.indexOf('>',open);if(end<0)break;String tag=xml.substring(open,end+1),className=xmlAttribute(tag,"class");
                String parentPart="";for(int i=stack.size()-1;i>=0;i--){XmlFrame f=(XmlFrame)stack.elementAt(i);if(isPart(f.className)){parentPart=f.name;break;}}
                String itemName="";if(isPart(className)){int props=xml.indexOf("<Properties",end+1),next=xml.indexOf("<Item",end+1);if(props>=0&&(next<0||props<next)){int pe=xml.indexOf("</Properties>",props);if(pe>props){String nameProp=xmlProperty(xml.substring(props,pe),"Name");if(nameProp!=null)itemName=xmlText(nameProp).trim();}}}
                stack.addElement(new XmlFrame(className,end+1,itemName,parentPart));cursor=end+1;
            }else{
                if(stack.size()==0){cursor=close+7;continue;}
                XmlFrame f=(XmlFrame)stack.elementAt(stack.size()-1);String content=xml.substring(f.start,close);
                if(isScript(f.className)&&f.parentPartName.length()>0){String src=xmlProperty(content,"Source");int effect=src==null?0:scriptBehavior(xmlText(src));if(effect!=0)for(int i=0;i<parts.size();i++){Part p=(Part)parts.elementAt(i);if(f.parentPartName.equals(p.name))p.behavior|=effect;}}
                stack.removeElementAt(stack.size()-1);cursor=close+7;
            }
        }
    }
    private static int brickColor(int id){switch(id){case 5:return 0xd7c59a;case 11:return 0x80bbdb;case 21:return 0xc4281c;case 23:return 0x0d69ac;case 24:return 0xf5cd30;case 26:return 0x1b2a35;case 27:return 0x6d6e6c;case 28:return 0x287f47;case 37:return 0x4b974b;case 38:return 0xa05f35;case 45:return 0xb4d2e4;case 102:return 0x6e99ca;case 106:return 0xda8541;case 133:return 0xd5733d;case 141:return 0x27462d;case 153:return 0x957977;case 192:return 0x694028;case 194:return 0xa3a2a5;case 199:return 0x635f62;case 208:return 0xe5e4df;case 217:return 0x7c5c46;default:return 0xa3a2a5;}}
    private static ClassInfo find(java.util.Vector v,int id)throws Exception{for(int i=0;i<v.size();i++){ClassInfo c=(ClassInfo)v.elementAt(i);if(c.id==id)return c;}throw new Exception("RBXL: property refers to an unknown class");}
    private static int[] ints(Reader r,int count)throws Exception{byte[] x=r.bytes(count*4);int[] a=new int[count];int sum=0;for(int i=0;i<count;i++){int v=((x[i]&255)<<24)|((x[count+i]&255)<<16)|((x[count*2+i]&255)<<8)|(x[count*3+i]&255);int signed=(v>>>1)^-(v&1);sum+=signed;a[i]=sum;}return a;}
    private static int[] uints(Reader r,int count)throws Exception{byte[] x=r.bytes(count*4);int[] a=new int[count];for(int i=0;i<count;i++)a[i]=((x[i]&255)<<24)|((x[count+i]&255)<<16)|((x[count*2+i]&255)<<8)|(x[count*3+i]&255);return a;}
    private static float[] floats(Reader r,int count)throws Exception{byte[] x=r.bytes(count*4);float[] f=new float[count];for(int i=0;i<count;i++){int q=i*4;int v=((x[q/4]&255)<<24)|((x[count+q/4]&255)<<16)|((x[count*2+q/4]&255)<<8)|(x[count*3+q/4]&255);int bits=((v&1)<<31)|(((v>>>24)&255)<<23)|((v>>>1)&0x7fffff);f[i]=Float.intBitsToFloat(bits);}return f;}
    private static void readInstances(byte[] data,java.util.Vector classes)throws Exception{Reader r=new Reader(data,data.length);int id=r.i32();String name=r.str();int service=r.u8();int n=r.i32();if(n<0||n>20000)throw new Exception("RBXL: invalid object count");int[] refs=ints(r,n);if(service!=0)r.bytes(n);classes.addElement(new ClassInfo(id,name,refs));}
    private static void readProperty(byte[] data,java.util.Vector classes)throws Exception{Reader r=new Reader(data,data.length);ClassInfo ci=find(classes,r.i32());String name=r.str();int type=r.u8(),n=ci.refs.length;boolean part=isPart(ci.name),script=isScript(ci.name);boolean needed=part&&("Name".equals(name)||"Parent".equals(name)||"CFrame".equals(name)||"Size".equals(name)||"size".equals(name)||"Color3uint8".equals(name)||"Color".equals(name)||"CanCollide".equals(name)||"Transparency".equals(name)||"BrickColor".equals(name)||"shape".equals(name)||"Shape".equals(name))||script&&("Name".equals(name)||"Source".equals(name)||"Parent".equals(name));boolean wanted=part&&("CFrame".equals(name)||"Size".equals(name)||"size".equals(name)||"Color3uint8".equals(name)||"Color".equals(name));
        // PROP chunks are independent, so ignore unrelated modern properties without
        // decoding their value types. This keeps newer Roblox classes from blocking map parts.
        if(!needed)return;
        if(type==1||type==0x1d){for(int i=0;i<n;i++){String value=r.str();if("Name".equals(name)&&type==1&&isPart(ci.name))ci.parts[i].name=value;if("Source".equals(name)&&isScript(ci.name)&&ci.sources!=null)ci.sources[i]=value;}return;}
        if(type==0x13){int[] refs=ints(r,n);if("Parent".equals(name))ci.parentRefs=refs;return;}
        if(type==2){byte[] values=r.bytes(n);if("CanCollide".equals(name)&&isPart(ci.name))for(int i=0;i<n;i++)ci.parts[i].canCollide=values[i]!=0;return;}
        if(type==4){float[] values=floats(r,n);if("Transparency".equals(name)&&part)for(int i=0;i<n;i++)ci.parts[i].visible=values[i]<1.0f;return;}
        if(type==0x0b){int[] values=uints(r,n);if("BrickColor".equals(name)&&part)for(int i=0;i<n;i++)ci.parts[i].color=brickColor(values[i]);return;}
        if(type==0x12){int[] values=uints(r,n);if(("shape".equals(name)||"Shape".equals(name))&&part)for(int i=0;i<n;i++)ci.parts[i].shape=values[i];return;}
        if(type==0x10){int[] ids=new int[n];for(int i=0;i<n;i++){ids[i]=r.u8();if(ids[i]==0)r.bytes(36);}float[] x=floats(r,n),y=floats(r,n),z=floats(r,n);for(int i=0;i<n;i++)if(wanted){Part p=ci.parts[i];p.x=x[i];p.y=y[i];p.z=z[i];p.hasFrame=true;}}
        else if(type==0x11){for(int i=0;i<n;i++)if(r.u8()==0)r.bytes(16);float[] x=floats(r,n),y=floats(r,n),z=floats(r,n);for(int i=0;i<n;i++)if(wanted){Part p=ci.parts[i];p.x=x[i];p.y=y[i];p.z=z[i];p.hasFrame=true;}}
        else if(type==0x0e||type==0x0c){int dims=3;float[][] vals=new float[dims][];for(int d=0;d<dims;d++)vals[d]=floats(r,n);for(int i=0;i<n;i++)if(wanted){Part p=ci.parts[i];if("Size".equals(name)||"size".equals(name)){p.sx=Math.abs(vals[0][i]);p.sy=Math.abs(vals[1][i]);p.sz=Math.abs(vals[2][i]);p.hasSize=true;}else if("Color".equals(name)||"Color3uint8".equals(name))p.color=rgb(vals[0][i],vals[1][i],vals[2][i]);}}
        else if(type==0x1a){byte[] rr=r.bytes(n),gg=r.bytes(n),bb=r.bytes(n);for(int i=0;i<n;i++)if(wanted)ci.parts[i].color=((rr[i]&255)<<16)|((gg[i]&255)<<8)|(bb[i]&255);}
        else skip(r,type,n);
    }
    private static int rgb(float a,float b,float c){return ((int)(clamp(a)*255)<<16)|((int)(clamp(b)*255)<<8)|(int)(clamp(c)*255);}private static float clamp(float f){return Math.max(0,Math.min(1,f));}
    private static void skip(Reader r,int t,int n)throws Exception{switch(t){case 1:case 0x1d:for(int i=0;i<n;i++)r.str();break;case 2:case 9:case 10:r.bytes(n);break;case 3:case 4:case 0x0b:case 0x12:case 0x13:case 0x1c:r.bytes(n*4);break;case 5:r.bytes(n*8);break;case 6:r.bytes(n*8);break;case 7:r.bytes(n*16);break;case 8:r.bytes(n*24);break;case 0x0d:r.bytes(n*8);break;case 0x0e:case 0x0c:r.bytes(n*12);break;case 0x0f:r.bytes(n*4);break;case 0x14:r.bytes(n*6);break;case 0x10:for(int i=0;i<n;i++){if(r.u8()==0)r.bytes(36);}r.bytes(n*12);break;case 0x11:r.bytes(n*28);break;case 0x15:for(int i=0;i<n;i++){int count=r.i32();if(count<0||count>10000)throw new Exception("RBXL: invalid sequence");r.bytes(count*12);}break;case 0x16:for(int i=0;i<n;i++){int count=r.i32();if(count<0||count>10000)throw new Exception("RBXL: invalid sequence");r.bytes(count*20);}break;case 0x17:r.bytes(n*8);break;case 0x18:r.bytes(n*16);break;case 0x19:for(int i=0;i<n;i++){int flags=r.u8();if((flags&1)!=0)r.bytes((flags&2)!=0?24:20);}break;case 0x1a:r.bytes(n*3);break;case 0x1b:r.bytes(n*8);break;case 0x1e:if(r.u8()!=0x10)throw new Exception("RBXL: invalid optional CFrame");skip(r,0x10,n);if(r.u8()!=2)throw new Exception("RBXL: invalid optional CFrame flags");r.bytes(n);break;case 0x1f:r.bytes(n*16);break;case 0x20:for(int i=0;i<n;i++){r.str();r.bytes(3);r.str();}break;case 0x22:r.bytes(n*4);int uris=r.i32();for(int i=0;i<uris;i++)r.str();int refs=r.i32();r.bytes(refs*4);int external=r.i32();r.bytes(external*4);break;case 0x23:for(int i=0;i<n;i++){int flags=r.u8();if((flags&1)!=0)r.str();if((flags&2)!=0){long count=r.i64();if(count<0||count>10000)throw new Exception("RBXL: invalid asset map");for(int j=0;j<(int)count;j++){r.str();r.str();}}}break;default:throw new Exception("RBXL property type is not supported: "+t);}}
    private static byte[] inflateLz4(byte[] src,int size)throws Exception{byte[] dst=new byte[size];int si=0,di=0;while(si<src.length){int token=src[si++]&255,lit=token>>>4;if(lit==15){int v;do{if(si>=src.length)throw new Exception("RBXL: invalid LZ4 chunk");v=src[si++]&255;lit+=v;}while(v==255);}if(si+lit>src.length||di+lit>dst.length)throw new Exception("RBXL: invalid LZ4 literals");System.arraycopy(src,si,dst,di,lit);si+=lit;di+=lit;if(si>=src.length)break;if(si+2>src.length)throw new Exception("RBXL: invalid LZ4 offset");int off=(src[si]&255)|((src[si+1]&255)<<8);si+=2;if(off==0||off>di)throw new Exception("RBXL: invalid LZ4 back-reference");int len=(token&15)+4;if((token&15)==15){int v;do{if(si>=src.length)throw new Exception("RBXL: invalid LZ4 match");v=src[si++]&255;len+=v;}while(v==255);}for(int k=0;k<len;k++){if(di>=dst.length)throw new Exception("RBXL: decompressed chunk is too large");dst[di]=dst[di-off];di++;}}if(di!=dst.length)throw new Exception("RBXL: LZ4 size mismatch");return dst;}
}
