package meblox;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.m3g.*;

/**
 * Roblox3DME: minimal JSR-184 playground/controller.
 * The character is generated in Java; put faceNEW.png and faceZomb.png in /meblox/ (or the JAR root).
 * Intended for MIDP 2.0 + JSR-184 (M3G) devices.
 */
public final class main extends MIDlet {
    private Game game;
    protected void startApp() {
        if (game == null) game = new Game(this);
        Display.getDisplay(this).setCurrent(game);
        game.start();
    }
    protected void pauseApp() { if (game != null) game.stop(); }
    protected void destroyApp(boolean unconditional) {
        if (game != null) game.stop();
    }
    void quit() { destroyApp(true); notifyDestroyed(); }

    private static final class Game extends Canvas implements Runnable {
        private final main midlet;
        private Thread thread;
        private boolean running;
        private Graphics3D g3d = Graphics3D.getInstance();
        private World world;
        private Camera camera;
        private Group avatar;
        private Group activeModel;
        private Mesh leftArm, rightArm, leftLeg, rightLeg;
        private int animation = 0; // 0 idle, 1 run, 2 jump
        private float px = 0, pz = 0, yaw = 0;
        private float camYaw = 0, camPitch = 0.22f;
        private float camDistance = 5.0f;
        private float cameraSensitivity = 0.0025f;
        private int sensitivityLevel = 1;
        private boolean invertCamera;
        private int currentGame;
        private boolean paused, configMode;
        private int pauseSelection, configSelection;
        private float jumpY = 0;
        private long lastTime;
        private int animTime;
        private boolean forward, back, left, right, camLeft, camRight, camUp, camDown, jumping;
        private boolean loaded;
        private boolean menuMode = true;
        private int selectedGame;
        private int inventorySelection;
        private StudioObject[] studioObjects = new StudioObject[40];
        private int studioObjectCount, studioSelected = -1, studioPalette, studioProperty;
        private boolean studioTesting;
        private static final String[] GAME_NAMES = new String[] {
            "Obby", "Baseplate", "Horda zombi", "Studio", "Lava ascendente",
            "Lluvia de meteoritos", "Plataformas que caen", "Carrera contrarreloj",
            "Caza de monedas", "Rey de la colina", "Arena de espadas", "Escondite",
            "Simon dice", "Mini tycoon", "Defensa de base"
        };
        private float[][] modeBlocks = new float[96][7];
        private Node[] modeBlockNodes = new Node[96];
        private boolean[] modeBlockActive = new boolean[96];
        private boolean[] modeBlockFalling = new boolean[96];
        private long[] modeBlockFallAt = new long[96];
        private float[] modeBlockFallVelocity = new float[96];
        private int modeBlockCount;
        private Node lavaNode;
        private float lavaHeight;
        private long modeStartAt, modeEventAt, modeScoreAt;
        private int modeScore, modeLives, modeCash, tycoonLevel, simonTarget, simonRound;
        private int modeCollected, modeGoal;
        private Node[] tokenNodes = new Node[20];
        private float[][] tokenPositions = new float[20][3];
        private boolean[] tokenActive = new boolean[20];
        private int tokenCount;
        private int defenseLeaked;
        private static final String[] STUDIO_TYPE_NAMES =
                {"Block", "Platform", "Hazard", "Pillar", "Spawn Pad"};
        private static final String[] STUDIO_PROPERTY_NAMES =
                {"X", "Y", "Z", "Size X", "Size Y", "Size Z", "Color"};
        private static final int[] STUDIO_COLORS =
                {0x8B6B45, 0x6BAA55, 0xE32636, 0x6688C7, 0x35B6C8, 0xD2A84B};
        private long swordAttackUntil, nextWaveAt;
        private int zombieWave;
        private Group swordVisual;
        private Image logoSource, titleBackgroundSource, skySource;
        private Image scaledLogo;
        private int[] menuPanPixels;
        private float menuPanScale;
        private float menuPanWidth;
        private Image2D skyBackgroundImage;
        private Background sceneBackground;
        private int cachedMenuWidth = -1, cachedMenuHeight = -1;
        private int[] pauseOverlayPixels;
        private int pauseOverlayWidth = -1, pauseOverlayHeight = -1;
        private long menuPanTime;
        private float playerVelocityY;
        private boolean grounded = true, dead, lobbyMode;
        private long gameClock, respawnAt, invulnerableUntil;
        private PhysicsPiece[] deathPieces;
        private Zombie[] zombies = new Zombie[12];
        private WorldObject[] worldObjects = new WorldObject[80];
        private Group protectionAura;
        private Material protectionAuraMaterial;
        private String modelStatus = "Select a game";
        private String modelError = "";
        private String initError = "";

        private static final int OBBY_PLATFORM_COUNT = 81;
        private static final float[][] OBBY_BLOCKS = createObbyBlocks();

        private static final float[][] ZOMBIE_BLOCKS = new float[][] {
            // Low cover around the arena.
            {-7.5f,0.75f,-2.0f, 2.8f,1.5f,0.8f, 0x74614A},
            { 7.5f,0.75f,-2.0f, 2.8f,1.5f,0.8f, 0x74614A},
            {-7.5f,0.75f, 3.0f, 2.8f,1.5f,0.8f, 0x66563F},
            { 7.5f,0.75f, 3.0f, 2.8f,1.5f,0.8f, 0x66563F},
            {-3.2f,0.55f,-1.8f, 1.2f,1.1f,1.2f, 0x8B6B45},
            { 3.2f,0.55f, 1.8f, 1.2f,1.1f,1.2f, 0x8B6B45},
            { 0.0f,0.45f, 5.8f, 3.2f,0.9f,0.8f, 0x6D5842},
            // Jumpable stepped platforms make a raised route through the arena.
            {-5.5f,0.30f,-5.0f, 3.0f,0.6f,3.0f, 0x6BAA55},
            {-5.5f,0.80f,-7.8f, 2.6f,0.5f,2.6f, 0xD2A84B},
            {-3.4f,1.30f,-9.3f, 2.4f,0.5f,2.4f, 0x6688C7},
            {-0.7f,1.80f,-9.0f, 2.5f,0.5f,2.5f, 0xA26BC0},
            { 2.3f,1.30f,-8.0f, 2.4f,0.5f,2.4f, 0xD2A84B},
            { 5.1f,0.80f,-6.0f, 2.8f,0.5f,2.8f, 0x6BAA55},
            { 5.7f,0.30f,-3.0f, 3.0f,0.6f,2.4f, 0x6688C7},
            // Side ledges and a central raised island.
            {-9.0f,0.35f, 0.0f, 1.8f,0.7f,4.0f, 0x6BAA55},
            { 9.0f,0.35f, 0.0f, 1.8f,0.7f,4.0f, 0x6BAA55},
            {-3.8f,0.40f, 7.3f, 2.6f,0.8f,2.6f, 0xD2A84B},
            { 0.0f,0.85f, 8.0f, 3.4f,0.7f,2.8f, 0x6688C7},
            { 3.8f,0.40f, 7.3f, 2.6f,0.8f,2.6f, 0xA26BC0}
        };

        private static float[][] createObbyBlocks() {
            float[][] blocks = new float[OBBY_PLATFORM_COUNT][7];
            float[] routeX = new float[] {0, 1.5f, 3, 3, 1.5f, 0, -1.5f, -3, -3, -1.5f};
            int[] colors = new int[] {0xD94B45, 0xE3C44D, 0x4779D1, 0xB56AC8};
            for (int i = 0; i < OBBY_PLATFORM_COUNT; i++) {
                boolean finish = i == OBBY_PLATFORM_COUNT - 1;
                float top = 0.65f + ((i * 7) % 4) * 0.12f;
                float width = finish ? 5.0f : 2.2f;
                float depth = finish ? 5.0f : 2.0f;
                float height = finish ? 0.60f : 0.80f;
                if (finish) top = height;
                blocks[i][0] = routeX[i % routeX.length];
                blocks[i][1] = top - height * 0.5f;
                blocks[i][2] = -3.0f - i * 2.8f;
                blocks[i][3] = width;
                blocks[i][4] = height;
                blocks[i][5] = depth;
                blocks[i][6] = finish ? 0x63C7A5 : colors[i % colors.length];
            }
            return blocks;
        }

        private static final class PhysicsPiece {
            Node node;
            float x, y, z, vx, vy, vz, rotation, spin, spinX, spinY, spinZ;
            float halfX, halfY, halfZ, extentX, extentY, extentZ;
            PhysicsPiece(Node n, float px, float py, float pz,
                    float velX, float velY, float velZ, float spinRate,
                    float axisX, float axisY, float axisZ,
                    float sizeX, float sizeY, float sizeZ) {
                node = n; x = px; y = py; z = pz;
                vx = velX; vy = velY; vz = velZ; spin = spinRate;
                spinX = axisX; spinY = axisY; spinZ = axisZ;
                halfX = sizeX; halfY = sizeY; halfZ = sizeZ;
            }
        }

        private static final class Zombie {
            Group model;
            float x, z;
            long attackAt;
            boolean alive = true;
            Zombie(Group model, float x, float z) {
                this.model = model; this.x = x; this.z = z;
            }
        }

        private static final class WorldObject {
            PhysicsPiece body;
            boolean bomb, active = true;
            long explodeAt, removeAt;
            WorldObject(PhysicsPiece body, boolean bomb, long explodeAt, long removeAt) {
                this.body = body; this.bomb = bomb;
                this.explodeAt = explodeAt; this.removeAt = removeAt;
            }
        }

        private static final class StudioObject {
            int type, color;
            float x, y, z, sx, sy, sz;
            Node node;
            StudioObject(int type, float x, float y, float z,
                    float sx, float sy, float sz, int color) {
                this.type=type; this.x=x; this.y=y; this.z=z;
                this.sx=sx; this.sy=sy; this.sz=sz; this.color=color;
            }
        }

        Game(main m) {
            midlet = m;
            setFullScreenMode(true);
        }
        synchronized void start() {
            if (running) return;
            running = true;
            thread = new Thread(this);
            thread.start();
        }
        synchronized void stop() { running = false; thread = null; }

        public void run() {
            try { init3D(); } catch (Throwable t) {
                loaded = false;
                initError = "Init: " + t.toString();
            }
            lastTime = System.currentTimeMillis();
            while (running) {
                long now = System.currentTimeMillis();
                int dt = (int)(now - lastTime);
                if (dt < 1) dt = 1;
                if (dt > 100) dt = 100;
                lastTime = now;
                update(dt);
                repaint();
                try { Thread.sleep(25); } catch (InterruptedException e) {}
            }
        }

        private void init3D() throws Exception {
            logoSource = loadImageResource("/logoR.png", "/meblox/logoR.png");
            titleBackgroundSource = loadImageResource("/bgtitle.png", "/meblox/bgtitle.png");
            skySource = loadImageResource("/skyboxR.png", "/meblox/skyboxR.png");
            if (logoSource == null) modelError = "logoR.png not found";
            if (titleBackgroundSource == null) modelError = "bgtitle.png not found";
            if (skySource != null) {
                try {
                    // The file is JPEG data despite its .png name.
                    skyBackgroundImage = new Image2D(Image2D.RGB, skySource);
                } catch (Throwable t) {
                    modelError = "skyboxR.png: " + t.toString();
                }
            } else {
                modelError = "skyboxR.png not found";
            }
            menuMode = true;
            loaded = false;
        }

        private Image loadImageResource(String primary, String secondary) {
            java.io.InputStream in = null;
            try {
                in = getClass().getResourceAsStream(primary);
                if (in == null) in = midlet.getClass().getResourceAsStream(primary);
                if (in == null) in = getClass().getResourceAsStream(secondary);
                if (in == null) in = midlet.getClass().getResourceAsStream(secondary);
                if (in == null) return null;
                return Image.createImage(in);
            } catch (Throwable t) {
                return null;
            } finally {
                if (in != null) try { in.close(); } catch (Throwable ignored) {}
            }
        }

        private void startGame(int game) throws Exception {
            currentGame = game;
            if (game == 3) studioTesting = false;
            menuMode = false;
            paused = false;
            configMode = false;
            loaded = false;
            resetPlayerSpawn();
            yaw = 0;
            jumping = false;
            forward = back = left = right = false;
            playerVelocityY = 0;
            inventorySelection = 0;
            swordAttackUntil = 0;
            zombieWave = 0;
            nextWaveAt = 0;
            zombies = new Zombie[12];
            worldObjects = new WorldObject[80];
            grounded = true;
            dead = false;
            lobbyMode = game == 1;
            gameClock = 0;
            invulnerableUntil = 0;
            deathPieces = null;

            modeBlockCount = 0;
            for (int i=0; i<modeBlockActive.length; i++) {
                modeBlockActive[i]=false;
                modeBlockFalling[i]=false;
                modeBlockFallAt[i]=0;
                modeBlockFallVelocity[i]=0;
                modeBlockNodes[i]=null;
            }
            for (int i=0; i<tokenActive.length; i++) {
                tokenActive[i]=false;
                tokenNodes[i]=null;
            }
            tokenCount=0; modeCollected=0; modeGoal=0; modeScore=0; modeLives=3;
            modeCash=10; tycoonLevel=0; simonTarget=0; simonRound=0;
            defenseLeaked=0; lavaHeight=-1.0f; lavaNode=null;
            modeStartAt=0; modeEventAt=0; modeScoreAt=0;
            world = new World();
            sceneBackground = new Background();
            sceneBackground.setColor(0x78BCE8);
            sceneBackground.setDepthClearEnable(true);
            sceneBackground.setColorClearEnable(true);
            if (skyBackgroundImage != null) {
                sceneBackground.setImage(skyBackgroundImage);
                sceneBackground.setImageMode(Background.REPEAT, Background.BORDER);
                updateSkyCrop();
            }
            world.setBackground(sceneBackground);

            camera = new Camera();
            camera.setPerspective(65.0f, 1.0f, 0.1f, 240.0f);
            world.addChild(camera);
            world.setActiveCamera(camera);

            // Every mode uses a compact procedural scene; Lava Rise replaces the floor with lava.
            if (game != 4) world.addChild(makeBox(0, -0.25f, 0, 24, 0.5f, 24, 0x54A64B));
            if (game == 0) {
                // An 80-platform zigzag course ending on an enlarged green finish.
                for (int i = 0; i < OBBY_BLOCKS.length; i++) {
                    float[] b = OBBY_BLOCKS[i];
                    world.addChild(makeBox(b[0],b[1],b[2],b[3],b[4],b[5],(int)b[6]));
                }
            } else if (game == 1) {
                // Red touch-to-die test block in the baseplate lobby.
                world.addChild(makeBox(3, 0.35f, -3, 1.2f, 0.7f, 1.2f, 0xE32636));
            } else if (game == 2) {
                // Multi-level zombie arena with cover, side ledges and jumpable steps.
                for (int i = 0; i < ZOMBIE_BLOCKS.length; i++) {
                    float[] b = ZOMBIE_BLOCKS[i];
                    world.addChild(makeBox(b[0],b[1],b[2],b[3],b[4],b[5],(int)b[6]));
                }
            } else if (game == 3) {
                // Studio begins with a clear baseplate and restores any objects in the editor.
                syncStudioNodes();
            } else if (game >= 4) {
                buildExtraGame(game);
                resetPlayerSpawn();
            }

            avatar = new Group();
            activeModel = buildNoob();
            avatar.addChild(activeModel);
            world.addChild(avatar);
            protectionAura = makeProtectionAura();
            avatar.addChild(protectionAura);
            invulnerableUntil = gameClock + 4000;
            animation = 0;
            animateNoob();
            updateCamera();
            modelStatus = GAME_NAMES[game];
            loaded = true;
            modeStartAt = gameClock;
            modeEventAt = game == 12 ? gameClock + 4000 : gameClock + 900;
            modeScoreAt = gameClock + 1000;
            if (game == 2 || game == 10) spawnZombieWave();
            if (game == 9) spawnKingBots();
            if (game == 11) spawnSeekBots();
            if (game == 14) spawnDefenseWave();
        }

        private int addModeBlock(float x, float y, float z, float sx, float sy,
                float sz, int color) {
            if (modeBlockCount >= modeBlocks.length) return -1;
            int i = modeBlockCount++;
            modeBlocks[i] = new float[] {x,y,z,sx,sy,sz,color};
            modeBlockNodes[i] = makeBox(x,y,z,sx,sy,sz,color);
            modeBlockActive[i] = true;
            world.addChild(modeBlockNodes[i]);
            return i;
        }

        private void addToken(float x, float y, float z) {
            if (tokenCount >= tokenNodes.length) return;
            int i=tokenCount++;
            tokenPositions[i][0]=x; tokenPositions[i][1]=y; tokenPositions[i][2]=z;
            tokenNodes[i]=makeBox(x,y,z,0.28f,0.28f,0.28f,0xFFD633);
            tokenActive[i]=true;
            world.addChild(tokenNodes[i]);
        }

        private void buildExtraGame(int game) {
            if (game == 4) {
                lavaNode=makeBox(0,lavaHeight-0.12f,0,24,0.24f,24,0xF04B25);
                world.addChild(lavaNode);
                for (int i=0;i<18;i++) {
                    float x=(float)Math.sin(i*1.7)*4.0f;
                    float z=-1.0f-i*1.05f;
                    float y=0.62f+i*0.48f;
                    addModeBlock(x,y,z,2.8f,0.45f,2.8f,
                            new int[] {0xE9B44C,0xD97B35,0xC85C3C,0xE5CF66}[i%4]);
                }
            } else if (game == 5) {
                addModeBlock(-6,0.75f,-4,2.4f,1.5f,1.0f,0x74614A);
                addModeBlock(6,0.75f,-2,2.4f,1.5f,1.0f,0x66563F);
                addModeBlock(-3,0.55f,4,1.5f,1.1f,1.5f,0x8B6B45);
                addModeBlock(3,0.55f,-7,1.5f,1.1f,1.5f,0x8B6B45);
                addModeBlock(0,0.35f,7,3.0f,0.7f,1.0f,0x6688C7);
            } else if (game == 6) {
                for (int i=0;i<18;i++) {
                    float x=(i%3-1)*2.4f + ((i/3)%2==0?0:1.0f);
                    float z=-1.0f-(i/3)*2.4f;
                    addModeBlock(x,0.28f,z,2.0f,0.56f,2.0f,
                            new int[] {0xD94B45,0xE3C44D,0x4779D1}[i%3]);
                }
            } else if (game == 7) {
                for (int i=0;i<OBBY_BLOCKS.length;i++) {
                    float[] b=OBBY_BLOCKS[i];
                    addModeBlock(b[0],b[1],b[2],b[3],b[4],b[5],(int)b[6]);
                }
            } else if (game == 8) {
                addModeBlock(-5,0.5f,-5,2,1,2,0x6688C7);
                addModeBlock(5,0.5f,-4,2,1,2,0xA26BC0);
                addModeBlock(-4,0.5f,5,2,1,2,0xD2A84B);
                addModeBlock(5,0.5f,5,2,1,2,0x6BAA55);
                for (int i=0;i<12;i++) {
                    float x=-9.0f+(i%4)*6.0f;
                    float z=-8.0f+(i/4)*7.5f;
                    addToken(x,0.55f,z);
                }
                modeGoal=tokenCount;
            } else if (game == 9) {
                addModeBlock(0,0.35f,0,5.0f,0.7f,5.0f,0xD2A84B);
                addModeBlock(-4,0.18f,0,2.0f,0.36f,3.0f,0x6BAA55);
                addModeBlock(4,0.18f,0,2.0f,0.36f,3.0f,0x6BAA55);
                addModeBlock(0,0.18f,-4,3.0f,0.36f,2.0f,0x6688C7);
                addModeBlock(0,0.18f,4,3.0f,0.36f,2.0f,0x6688C7);
            } else if (game == 10) {
                for (int i=0;i<8;i++) {
                    double a=6.2831853*i/8.0;
                    addModeBlock((float)Math.cos(a)*7.0f,0.8f,
                            (float)Math.sin(a)*7.0f,1.3f,1.6f,1.3f,
                            i%2==0?0x74614A:0x6688C7);
                }
            } else if (game == 11) {
                addModeBlock(0,1.0f,-3,8,2,0.7f,0x74614A);
                addModeBlock(-4,0.7f,2,0.7f,1.4f,7,0x66563F);
                addModeBlock(4,0.7f,3,0.7f,1.4f,6,0x795438);
                addModeBlock(-1,0.5f,7,5,1,0.7f,0x8B6B45);
                addModeBlock(1,0.5f,-8,4,1,0.7f,0x795438);
            } else if (game == 12) {
                addModeBlock(-4,0.18f,-4,3.5f,0.36f,3.5f,0xE34B4B);
                addModeBlock(4,0.18f,-4,3.5f,0.36f,3.5f,0x4B78E3);
                addModeBlock(-4,0.18f,4,3.5f,0.36f,3.5f,0x55B66A);
                addModeBlock(4,0.18f,4,3.5f,0.36f,3.5f,0xE3C44D);
            } else if (game == 13) {
                addModeBlock(0,0.18f,0,8,0.36f,8,0x6BAA55);
                addModeBlock(-8,0.8f,0,1,1.6f,10,0x6688C7);
                addModeBlock(8,0.8f,0,1,1.6f,10,0x6688C7);
                addModeBlock(0,0.8f,-8,10,1.6f,1,0x6688C7);
                addModeBlock(0,0.18f,5,1.8f,0.36f,1.8f,0xE3C44D);
            } else if (game == 14) {
                addModeBlock(-5,0.2f,-5,1.5f,0.4f,1.5f,0x6688C7);
                addModeBlock(5,0.2f,-5,1.5f,0.4f,1.5f,0x6688C7);
                addModeBlock(-5,0.2f,5,1.5f,0.4f,1.5f,0x6688C7);
                addModeBlock(5,0.2f,5,1.5f,0.4f,1.5f,0x6688C7);
                addModeBlock(0,0.25f,0,3.0f,0.5f,3.0f,0xD2A84B);
            }
        }

        private void spawnSimpleBot(int slot, float x, float z) {
            if (slot<0 || slot>=zombies.length) return;
            try {
                Group model=buildNoob(true);
                model.setTranslation(x,0,z);
                world.addChild(model);
                zombies[slot]=new Zombie(model,x,z);
            } catch (Throwable t) {
                initError="NPC: "+t.toString();
            }
        }

        private void spawnKingBots() {
            zombies=new Zombie[4];
            spawnSimpleBot(0,-8,-8);
            spawnSimpleBot(1,8,-8);
            spawnSimpleBot(2,0,9);
        }

        private void spawnSeekBots() {
            zombies=new Zombie[6];
            spawnSimpleBot(0,-9,-8);
            spawnSimpleBot(1,8,-8);
            spawnSimpleBot(2,-8,8);
            spawnSimpleBot(3,8,8);
            spawnSimpleBot(4,0,-10);
            modeGoal=5;
        }

        private void spawnDefenseWave() {
            zombies=new Zombie[12];
            int count=Math.min(3+(int)(modeStartAt/12000),zombies.length);
            for(int i=0;i<count;i++)
                spawnSimpleBot(i,(i%3-1)*2.2f,-10.0f-(i/3)*1.4f);
            modeStatus("Wave "+(1+modeScore/3)+" - stop the enemies");
        }

        private void modeStatus(String message) {
            modelStatus=message;
        }

        private void updateExtraGame(int dt) {
            if (currentGame==4) {
                lavaHeight+=dt*0.00004f;
                if (lavaNode!=null) lavaNode.setTranslation(0,lavaHeight-0.12f,0);
                if (jumpY<lavaHeight+0.85f && Math.abs(px)<11.5f && Math.abs(pz)<11.5f) {
                    beginDeath(); return;
                }
                if (jumpY>8.0f) modeStatus("Lava escaped!");
            } else if (currentGame==5) {
                if (gameClock>=modeEventAt) {
                    modeEventAt=gameClock+850;
                    long seed=gameClock/850L+modeScore*17L;
                    float x=(float)((seed*37L)%190L)/10.0f-9.5f;
                    float z=(float)((seed*61L)%190L)/10.0f-9.5f;
                    addWorldObject(makeBox(0,0,0,0.72f,0.72f,0.72f,0xA94732),
                            x,8.5f,z,0,0,0,0.72f,0.72f,0.72f,0,false,0);
                }
                for(int i=0;i<worldObjects.length;i++) {
                    WorldObject o=worldObjects[i];
                    if(o==null||!o.active) continue;
                    float dx=o.body.x-px,dz=o.body.z-pz;
                    if(gameClock>=invulnerableUntil&&o.body.y<jumpY+1.6f&&
                            o.body.y>jumpY-0.5f&&dx*dx+dz*dz<1.0f) {
                        removeWorldObject(o); beginDeath(); return;
                    }
                }
                if(gameClock-modeStartAt>=30000) modeStatus("Meteor shower survived!");
            } else if (currentGame==6) {
                updateFallingTiles(dt);
                if(pz < -12.0f) modeStatus("You crossed the collapsing tiles!");
            } else if (currentGame==7) {
                if(gameClock-modeStartAt>0&&modelStatus.indexOf("complete")>=0) {
                    modeScore=(int)((gameClock-modeStartAt)/1000);
                }
            } else if (currentGame==8) {
                updateTokens(dt);
                if(modeCollected>=modeGoal) modeStatus("All coins collected!");
                else if(gameClock-modeStartAt>60000) modeStatus("Time up - coins "+modeCollected+"/"+modeGoal);
            } else if (currentGame==9) {
                updateKingHill(dt);
            } else if (currentGame==11) {
                updateSeekGame();
            } else if (currentGame==12) {
                updateSimonGame();
            } else if (currentGame==13) {
                if(gameClock>=modeEventAt) {
                    modeEventAt=gameClock+4000;
                    modeCash+=2+tycoonLevel;
                    modeStatus("Tycoon cash: "+modeCash+" - press 3 to build");
                }
            } else if (currentGame==14) {
                updateDefenseGame(dt);
            }
        }

        private void updateFallingTiles(int dt) {
            for(int i=0;i<modeBlockCount;i++) {
                if(!modeBlockActive[i]) continue;
                float[] b=modeBlocks[i];
                if(!modeBlockFalling[i] && onPlatform(b[0],b[2],b[3]*0.5f,b[5]*0.5f) &&
                        grounded && jumpY>=b[1]+b[4]*0.5f-0.08f) {
                    if(modeBlockFallAt[i]==0) modeBlockFallAt[i]=gameClock+650;
                    if(gameClock>=modeBlockFallAt[i]) modeBlockFalling[i]=true;
                }
                if(modeBlockFalling[i]) {
                    modeBlockFallVelocity[i]-=0.000018f*dt;
                    b[1]+=modeBlockFallVelocity[i]*dt;
                    modeBlockNodes[i].setTranslation(b[0],b[1],b[2]);
                    if(b[1]<-3.0f) {
                        modeBlockActive[i]=false;
                        try { world.removeChild(modeBlockNodes[i]); } catch(Throwable ignored) {}
                    }
                }
            }
        }

        private void updateTokens(int dt) {
            for(int i=0;i<tokenCount;i++) {
                if(!tokenActive[i]) continue;
                Node node=tokenNodes[i];
                node.setOrientation((float)((gameClock*0.12f)%360L),0,1,0);
                float dx=tokenPositions[i][0]-px,dz=tokenPositions[i][2]-pz;
                if(dx*dx+dz*dz<0.55f*0.55f&&Math.abs(jumpY-tokenPositions[i][1])<1.2f) {
                    tokenActive[i]=false; modeCollected++;
                    try { world.removeChild(node); } catch(Throwable ignored) {}
                    modeStatus("Coins "+modeCollected+"/"+modeGoal);
                }
            }
        }

        private void updateKingHill(int dt) {
            for(int i=0;i<zombies.length;i++) {
                Zombie z=zombies[i]; if(z==null||!z.alive) continue;
                float dx=px-z.x,dz=pz-z.z;
                float d=(float)Math.sqrt(dx*dx+dz*dz);
                if(d>1.0f) {
                    float step=dt*0.0008f;
                    z.x+=dx/d*step; z.z+=dz/d*step;
                    z.model.setTranslation(z.x,0,z.z);
                } else if(gameClock>=z.attackAt) {
                    z.attackAt=gameClock+1400;
                    if(d>0.01f) { px+=dx/d*1.2f; pz+=dz/d*1.2f; }
                    playerVelocityY=0.0028f; grounded=false;
                }
            }
            boolean onHill=Math.abs(px)<2.35f&&Math.abs(pz)<2.35f&&jumpY>=0.68f;
            if(onHill&&gameClock>=modeScoreAt) {
                modeScore++; modeScoreAt=gameClock+1000;
                modeStatus("Hill held: "+modeScore+" / 20 sec");
            } else if(!onHill) modeScore=0;
            if(modeScore>=20) modeStatus("Hill captured!");
        }

        private void updateSeekGame() {
            for(int i=0;i<zombies.length;i++) {
                Zombie z=zombies[i]; if(z==null||!z.alive) continue;
                float dx=px-z.x,dz=pz-z.z;
                if(dx*dx+dz*dz<1.1f*1.1f) {
                    z.alive=false;
                    try {world.removeChild(z.model);}catch(Throwable ignored){}
                    modeStatus("Found "+(modeGoal-countZombies())+"/"+modeGoal);
                }
            }
            if(modeGoal-countZombies()>=modeGoal) modeStatus("Everyone found!");
        }

        private void updateSimonGame() {
            if(simonRound>12 || gameClock<modeEventAt) return;
            if(simonRound>0) {
                float[][] pos=new float[][] {{-4,-4},{4,-4},{-4,4},{4,4}};
                if(Math.abs(px-pos[simonTarget][0])>1.85f ||
                        Math.abs(pz-pos[simonTarget][1])>1.85f) {
                    beginDeath(); return;
                }
                modeScore++;
            }
            simonTarget=(int)((gameClock/3200L*17L+3L)%4L);
            simonRound++; modeEventAt=gameClock+3200;
            String[] color=new String[] {"RED","BLUE","GREEN","YELLOW"};
            modeStatus("SIMON SAYS: "+color[simonTarget]+"! Round "+simonRound);
            if(simonRound>12) modeStatus("Simon Says complete!");
        }

        private void useTycoonAction() {
            if(currentGame!=13) return;
            if(Math.abs(px)>2.0f||Math.abs(pz-5.0f)>2.0f) {
                modeStatus("Stand on the yellow build pad");
                return;
            }
            int cost=5+tycoonLevel*5;
            if(modeCash<cost) {modeStatus("Need "+cost+" cash");return;}
            modeCash-=cost; tycoonLevel++;
            float x=((tycoonLevel-1)%3-1)*2.2f;
            float z=-2.0f-(tycoonLevel-1)/3*2.2f;
            addModeBlock(x,0.8f,z,1.8f,1.6f,1.8f,STUDIO_COLORS[tycoonLevel%STUDIO_COLORS.length]);
            modeStatus("Built "+tycoonLevel+" / 9 - cash "+modeCash);
            if(tycoonLevel>=9) modeStatus("Tycoon completed!");
        }

        private void useArenaSword() {
            if(gameClock<swordAttackUntil) return;
            swordAttackUntil=gameClock+500;
            float best=3.0f*3.0f; int target=-1;
            for(int i=0;i<zombies.length;i++) {
                Zombie z=zombies[i]; if(z==null||!z.alive) continue;
                float dx=z.x-px,dz=z.z-pz, d=dx*dx+dz*dz;
                if(d<best){best=d;target=i;}
            }
            animateNoob();
            if(target>=0) {
                Zombie z=zombies[target]; z.alive=false;
                try {world.removeChild(z.model);}catch(Throwable ignored){}
                modeScore++;
                modeStatus("Enemy down: "+modeScore);
            }
        }

        private void updateDefenseGame(int dt) {
            if(defenseLeaked>=3) {
                modeStatus("Base lost - restart from pause");
                return;
            }
            if(countZombies()==0) {
                if(gameClock>=modeEventAt) {
                    modeStartAt=gameClock;
                    modeEventAt=gameClock+1800;
                    spawnDefenseWave();
                }
                return;
            }
            for(int i=0;i<zombies.length;i++) {
                Zombie z=zombies[i]; if(z==null||!z.alive) continue;
                float dx=-z.x,dz=-z.z;
                float d=(float)Math.sqrt(dx*dx+dz*dz);
                if(d<1.0f) {
                    z.alive=false; defenseLeaked++;
                    try {world.removeChild(z.model);}catch(Throwable ignored){}
                    modeStatus("Base lives: "+(3-defenseLeaked));
                    if(defenseLeaked>=3) {beginDeath();return;}
                } else {
                    float step=dt*0.00065f;
                    z.x+=dx/d*step; z.z+=dz/d*step;
                    z.model.setTranslation(z.x,0,z.z);
                }
            }
        }

        private void spawnZombieWave() {
            if ((currentGame != 2 && currentGame != 10) || world == null) return;
            zombieWave++;
            nextWaveAt = 0;
            int count = Math.min(4 + zombieWave, zombies.length);
            for (int i = 0; i < count; i++) {
                int slot = -1;
                for (int j = 0; j < zombies.length; j++)
                    if (zombies[j] == null || !zombies[j].alive) { slot = j; break; }
                if (slot < 0) break;
                double angle = 6.28318530718 * i / count + zombieWave * 0.31;
                float radius = 6.0f + (i % 3) * 0.9f;
                float x = px + (float)Math.cos(angle) * radius;
                float z = pz + (float)Math.sin(angle) * radius;
                try {
                    Group model = buildNoob(true);
                    model.setTranslation(x, 0, z);
                    world.addChild(model);
                    zombies[slot] = new Zombie(model, x, z);
                } catch (Throwable t) {
                    initError = "Zombie: " + t.toString();
                }
            }
            updateHordeStatus();
        }

        private int countZombies() {
            int count = 0;
            if (zombies == null) return count;
            for (int i = 0; i < zombies.length; i++)
                if (zombies[i] != null && zombies[i].alive) count++;
            return count;
        }

        private void updateHordeStatus() {
            if (currentGame == 2)
                modelStatus = "Wave " + zombieWave + " - Zombies: " + countZombies();
        }

        private void updateZombies(int dt) {
            if ((currentGame != 2 && currentGame != 10) || zombies == null) return;
            float speed = dt * 0.0022f;
            for (int i = 0; i < zombies.length; i++) {
                Zombie z = zombies[i];
                if (z == null || !z.alive) continue;
                float dx = px - z.x, dz = pz - z.z;
                float distance = (float)Math.sqrt(dx * dx + dz * dz);
                if (distance > 0.82f) {
                    float moveX = dx / distance * speed;
                    float moveZ = dz / distance * speed;
                    float nextX = z.x + moveX, nextZ = z.z + moveZ;
                    if (!isWorldBlocked(nextX, z.z, 0.0f, 1.9f)) z.x = nextX;
                    if (!isWorldBlocked(z.x, nextZ, 0.0f, 1.9f)) z.z = nextZ;
                    dx = px - z.x; dz = pz - z.z;
                    if (moveX != 0.0f || moveZ != 0.0f)
                        z.model.setOrientation((float)(fastAtan2(-moveX, -moveZ) * 57.29578f), 0, 1, 0);
                }
                z.model.setTranslation(z.x, 0, z.z);
                distance = (float)Math.sqrt(dx * dx + dz * dz);
                if (distance < 0.9f && gameClock >= z.attackAt) {
                    z.attackAt = gameClock + 900;
                    if (gameClock >= invulnerableUntil && !dead) {
                        beginDeath();
                        return;
                    }
                }
            }
            if (countZombies() == 0) {
                if (nextWaveAt == 0) nextWaveAt = gameClock + 1400;
                else if (gameClock >= nextWaveAt) spawnZombieWave();
            } else {
                nextWaveAt = 0;
            }
        }

        private boolean isPlayerBlocked(float x, float z) {
            return isWorldBlocked(x, z, jumpY, 1.9f);
        }

        private boolean isWorldBlocked(float x, float z, float bottom, float height) {
            if (worldObjects != null) {
                for (int i = 0; i < worldObjects.length; i++) {
                    WorldObject o = worldObjects[i];
                    if (o == null || !o.active || o.bomb) continue;
                    PhysicsPiece b = o.body;
                    if (overlapsBlock(x, z, bottom, height,
                            b.x, b.y, b.z, b.extentX, b.extentY, b.extentZ, 0.30f))
                        return true;
                }
            }
            if (currentGame == 3 && studioTesting) {
                for (int i=0; i<studioObjectCount; i++) {
                    StudioObject object = studioObjects[i];
                    if (object == null || object.type == 2) continue;
                    if (overlapsBlock(x,z,bottom,height,object.x,object.y,object.z,
                            object.sx*0.5f,object.sy*0.5f,object.sz*0.5f,0.30f))
                        return true;
                }
            }
            if (currentGame >= 4) {
                for (int i=0;i<modeBlockCount;i++) {
                    if (!modeBlockActive[i]) continue;
                    float[] b=modeBlocks[i];
                    if (overlapsBlock(x,z,bottom,height,b[0],b[2],b[1],
                            b[3]*0.5f,b[5]*0.5f,b[4]*0.5f,0.30f)) return true;
                }
            }
            float[][] blocks = currentGame == 0 ? OBBY_BLOCKS :
                    (currentGame == 2 ? ZOMBIE_BLOCKS : null);
            if (blocks != null) {
                for (int i = 0; i < blocks.length; i++) {
                    float[] b = blocks[i];
                    if (overlapsBlock(x, z, bottom, height,
                            b[0], b[1], b[2], b[3]*0.5f, b[4]*0.5f, b[5]*0.5f, 0.30f))
                        return true;
                }
            }
            return false;
        }

        private boolean overlapsBlock(float x, float z, float bottom, float height,
                float bx, float by, float bz, float halfX, float halfY, float halfZ,
                float playerRadius) {
            boolean vertical = bottom < by + halfY && bottom + height > by - halfY;
            return vertical && Math.abs(x - bx) < halfX + playerRadius &&
                    Math.abs(z - bz) < halfZ + playerRadius;
        }

        private void updateWorldObjects(int dt) {
            if (worldObjects == null) return;
            for (int i = 0; i < worldObjects.length; i++) {
                WorldObject o = worldObjects[i];
                if (o == null || !o.active) continue;
                if (!o.bomb && gameClock >= o.removeAt) {
                    removeWorldObject(o);
                    continue;
                }
                PhysicsPiece p = o.body;
                p.vy -= 0.000018f * dt;
                p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
                p.rotation += p.spin * dt * 0.001f;
                updatePieceExtents(p);
                resolvePieceEnvironment(p);
            }
            for (int i = 0; i < worldObjects.length; i++) {
                WorldObject a = worldObjects[i];
                if (a == null || !a.active) continue;
                for (int j = i + 1; j < worldObjects.length; j++) {
                    WorldObject b = worldObjects[j];
                    if (b != null && b.active) resolvePiecePair(a.body, b.body);
                }
            }
            for (int i = 0; i < worldObjects.length; i++) {
                WorldObject o = worldObjects[i];
                if (o == null || !o.active) continue;
                PhysicsPiece p = o.body;
                p.node.setTranslation(p.x, p.y, p.z);
                p.node.setOrientation(p.rotation, p.spinX, p.spinY, p.spinZ);
                if (o.bomb) {
                    boolean flash = gameClock >= o.explodeAt - 900 &&
                            ((gameClock / 110L) % 2L == 0L);
                    p.node.setRenderingEnable(gameClock < o.explodeAt - 900 || flash);
                    if (gameClock >= o.explodeAt) explodeBomb(o);
                }
            }
        }

        private void addWorldObject(Node node, float x, float y, float z,
                float vx, float vy, float vz, float sx, float sy, float sz,
                float orientation, boolean bomb, long fuse) {
            int slot = -1;
            for (int i = 0; i < worldObjects.length; i++)
                if (worldObjects[i] == null || !worldObjects[i].active) { slot = i; break; }
            if (slot < 0) {
                WorldObject oldest = worldObjects[0];
                for (int i = 1; i < worldObjects.length; i++)
                    if (worldObjects[i].removeAt < oldest.removeAt) oldest = worldObjects[i];
                removeWorldObject(oldest);
                slot = 0;
            }
            node.setTranslation(x, y, z);
            node.setOrientation(orientation, 0.0f, 1.0f, 0.0f);
            world.addChild(node);
            PhysicsPiece body = new PhysicsPiece(node, x, y, z, vx, vy, vz,
                    bomb ? 95.0f : 18.0f, 0.0f, 1.0f, 0.0f,
                    sx * 0.5f, sy * 0.5f, sz * 0.5f);
            body.rotation = orientation;
            updatePieceExtents(body);
            worldObjects[slot] = new WorldObject(body, bomb,
                    bomb ? gameClock + fuse : 0, bomb ? 0 : gameClock + 45000);
        }

        private void removeWorldObject(WorldObject o) {
            if (o == null || !o.active) return;
            o.active = false;
            try { o.body.node.setRenderingEnable(false); } catch (Throwable ignored) {}
            try { world.removeChild(o.body.node); } catch (Throwable ignored) {}
        }

        private void explodeBomb(WorldObject bomb) {
            float bx = bomb.body.x, bz = bomb.body.z;
            removeWorldObject(bomb);
            float radius = 4.8f, radiusSquared = radius * radius;
            for (int i = 0; i < zombies.length; i++) {
                Zombie z = zombies[i];
                if (z == null || !z.alive) continue;
                float dx = z.x - bx, dz = z.z - bz;
                if (dx * dx + dz * dz <= radiusSquared) {
                    z.alive = false;
                    try { world.removeChild(z.model); } catch (Throwable ignored) {}
                }
            }
            float playerX = px - bx, playerZ = pz - bz;
            if (!dead && gameClock >= invulnerableUntil &&
                    playerX * playerX + playerZ * playerZ <= radiusSquared) beginDeath();
            updateHordeStatus();
        }

        private void useInventoryItem() {
            if (dead || currentGame != 2) return;
            if (inventorySelection == 0) {
                if (gameClock < swordAttackUntil) return;
                swordAttackUntil = gameClock + 600;
                float fx = (float)Math.sin(yaw), fz = -(float)Math.cos(yaw);
                int target = -1;
                float closest = 2.55f * 2.55f;
                for (int i = 0; i < zombies.length; i++) {
                    Zombie z = zombies[i];
                    if (z == null || !z.alive) continue;
                    float dx = z.x - px, dz = z.z - pz;
                    float distanceSquared = dx * dx + dz * dz;
                    if (distanceSquared > closest) continue;
                    float dot = dx * fx + dz * fz;
                    if (dot < 0.10f) continue;
                    target = i;
                    closest = distanceSquared;
                }
                if (target >= 0) {
                    zombies[target].alive = false;
                    try { world.removeChild(zombies[target].model); } catch (Throwable ignored) {}
                    updateHordeStatus();
                }
                animateNoob();
            } else if (inventorySelection == 1) {
                float fx = (float)Math.sin(yaw), fz = -(float)Math.cos(yaw);
                Group bomb = new Group();
                bomb.addChild(makeBox(0, 0, 0, 0.34f, 0.34f, 0.34f, 0xE52B2B));
                bomb.addChild(makeBox(0, 0.21f, 0, 0.12f, 0.10f, 0.12f, 0x303030));
                addWorldObject(bomb, px + fx * 0.85f, jumpY + 0.45f, pz + fz * 0.85f,
                        fx * 0.0018f, 0.0f, fz * 0.0018f,
                        0.34f, 0.44f, 0.34f, 0.0f, true, 2600);
            } else {
                float fx = (float)Math.sin(yaw), fz = -(float)Math.cos(yaw);
                float rx = (float)Math.cos(yaw), rz = (float)Math.sin(yaw);
                float centerX = px + fx * 1.75f, centerZ = pz + fz * 1.75f;
                for (int row = 0; row < 2; row++) {
                    for (int col = -1; col <= 1; col++) {
                        Group block = new Group();
                        block.addChild(makeBox(0, 0, 0, 0.88f, 0.82f, 0.36f,
                                row == 0 ? 0x777777 : 0x969696));
                        float x = centerX + rx * col * 0.86f;
                        float z = centerZ + rz * col * 0.86f;
                        addWorldObject(block, x, jumpY + 1.5f + row * 0.84f, z,
                                0.0f, 0.0f, 0.0f, 0.88f, 0.82f, 0.36f,
                                yaw * 57.29578f, false, 0);
                    }
                }
            }
        }

        private Group makeSwordVisual() {
            Group sword = new Group();
            sword.addChild(makeBox(0, 0.47f, 0, 0.13f, 0.78f, 0.06f, 0xDCE9F2));
            sword.addChild(makeBox(0, 0.05f, 0, 0.38f, 0.08f, 0.12f, 0xB68A42));
            sword.addChild(makeBox(0, -0.12f, 0, 0.10f, 0.30f, 0.10f, 0x654321));
            sword.setTranslation(0.56f, 1.02f, -0.08f);
            return sword;
        }

        private void updateSkyCrop() {
            if (sceneBackground == null || skySource == null || getWidth() <= 0 || getHeight() <= 0) return;
            int imageWidth = skySource.getWidth();
            int imageHeight = skySource.getHeight();
            float verticalFov = 1.134464f; // 65 degrees
            float aspect = (float)getWidth() / (float)getHeight();
            float halfHorizontalTangent =
                    (float)Math.tan(verticalFov * 0.5f) * aspect;
            float horizontalFov = 2.0f * fastAtan2(halfHorizontalTangent, 1.0f);
            int cropWidth = (int)(imageWidth * horizontalFov / 6.2831853f);
            int cropHeight = (int)(imageHeight * verticalFov / 3.14159265f);
            if (cropWidth < 1) cropWidth = 1;
            if (cropHeight < 1) cropHeight = 1;

            // As the camera turns right, the panorama scrolls left at the matching angular rate.
            int cropX = (imageWidth - cropWidth) / 2 +
                    (int)(camYaw * imageWidth / 6.2831853f);
            cropX %= imageWidth;
            if (cropX < 0) cropX += imageWidth;

            float pitchPosition = (camPitch + 1.20f) / 2.40f;
            if (pitchPosition < 0.0f) pitchPosition = 0.0f;
            if (pitchPosition > 1.0f) pitchPosition = 1.0f;
            int cropY = (int)((imageHeight - cropHeight) * pitchPosition);
            sceneBackground.setCrop(cropX, cropY, cropWidth, cropHeight);
        }

        private void prepareMenuImages() {
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;
            boolean logoReady = logoSource == null || scaledLogo != null;
            boolean backgroundReady = titleBackgroundSource == null ||
                    (menuPanPixels != null && menuPanScale > 0.0f);
            if (width == cachedMenuWidth && height == cachedMenuHeight &&
                    logoReady && backgroundReady) return;
            cachedMenuWidth = width;
            cachedMenuHeight = height;
            scaledLogo = scaleToFit(logoSource, width - 24, Math.max(1, height / 4));
            menuPanPixels = new int[width * height];
            if (titleBackgroundSource != null) {
                menuPanScale = Math.max((float)width / titleBackgroundSource.getWidth(),
                        (float)height / titleBackgroundSource.getHeight());
                menuPanWidth = titleBackgroundSource.getWidth() * menuPanScale;
            }
        }

        private Image scaleToFit(Image source, int maxWidth, int maxHeight) {
            if (source == null || maxWidth < 1 || maxHeight < 1) return null;
            float scale = Math.min((float)maxWidth / source.getWidth(),
                    (float)maxHeight / source.getHeight());
            int width = Math.max(1, (int)(source.getWidth() * scale));
            int height = Math.max(1, (int)(source.getHeight() * scale));
            return resizeImage(source, width, height, 0, 0,
                    source.getWidth(), source.getHeight());
        }

        private Image scalePanorama(Image source, int width, int height) {
            if (source == null || width < 1 || height < 1) return null;
            float scale = Math.max((float)width / source.getWidth(),
                    (float)height / source.getHeight());
            int scaledWidth = Math.max(width, (int)(source.getWidth() * scale + 0.999f));
            int scaledHeight = Math.max(height, (int)(source.getHeight() * scale + 0.999f));
            return resizeImage(source, scaledWidth, scaledHeight, 0, 0,
                    source.getWidth(), source.getHeight());
        }

        private Image resizeImage(Image source, int width, int height,
                int cropX, int cropY, float cropWidth, float cropHeight) {
            int sourceWidth = source.getWidth();
            int[] pixels = new int[width * height];
            int[] row = new int[sourceWidth];
            int previousY = -1;
            for (int y = 0; y < height; y++) {
                int sourceY = cropY + (int)(cropHeight * y / height);
                if (sourceY >= source.getHeight()) sourceY = source.getHeight() - 1;
                if (sourceY != previousY) {
                    source.getRGB(row, 0, sourceWidth, 0, sourceY, sourceWidth, 1);
                    previousY = sourceY;
                }
                for (int x = 0; x < width; x++) {
                    int sourceX = cropX + (int)(cropWidth * x / width);
                    if (sourceX >= sourceWidth) sourceX = sourceWidth - 1;
                    pixels[y * width + x] = row[sourceX];
                }
            }
            return Image.createRGBImage(pixels, width, height, true);
        }

        private void drawMenu(Graphics g) {
            prepareMenuImages();
            g.setColor(0x101820);
            g.fillRect(0, 0, getWidth(), getHeight());
            drawMenuPanorama(g);

            if (scaledLogo != null) {
                int logoX = (getWidth() - scaledLogo.getWidth()) / 2;
                int logoY = Math.max(8, getHeight() / 14);
                g.drawImage(scaledLogo, logoX, logoY, Graphics.TOP | Graphics.LEFT);
            } else {
                g.setColor(0xFFFFFF);
                g.drawString("ROBLOX 3DME", getWidth() / 2, getHeight() / 8,
                        Graphics.TOP | Graphics.HCENTER);
            }

            int buttonWidth = Math.max(1, getWidth() - 32);
            int pageSize = getHeight() < 220 ? 4 : 5;
            int buttonHeight = Math.max(14, Math.min(24, getHeight() / 11));
            int gap = Math.max(2, buttonHeight / 6);
            int page = selectedGame / pageSize;
            int pageCount = (GAME_NAMES.length + pageSize - 1) / pageSize;
            int first = page * pageSize;
            int count = Math.min(pageSize, GAME_NAMES.length - first);
            int listHeight = count * buttonHeight + (count - 1) * gap;
            int startY = Math.max(getHeight() / 3,
                    getHeight() / 2 - listHeight / 2 + 8);
            Font font = g.getFont();
            for (int i = 0; i < count; i++) {
                int gameIndex = first + i;
                int y = startY + i * (buttonHeight + gap);
                g.setColor(gameIndex == selectedGame ? 0xF5C542 : 0x203040);
                g.fillRoundRect((getWidth() - buttonWidth) / 2, y,
                        buttonWidth, buttonHeight, 10, 10);
                g.setColor(gameIndex == selectedGame ? 0x17202A : 0xFFFFFF);
                g.drawString(GAME_NAMES[gameIndex], getWidth() / 2,
                        y + (buttonHeight - font.getHeight()) / 2,
                        Graphics.TOP | Graphics.HCENTER);
            }
            g.setColor(0xFFFFFF);
            g.drawString("PAGE " + (page + 1) + "/" + pageCount +
                    "  UP/DOWN SELECT  7/9 PAGE",
                    getWidth() / 2, getHeight() - font.getHeight() - 8,
                    Graphics.TOP | Graphics.HCENTER);
            if (modelError.length() > 0)
                g.drawString(modelError, 4, 4, Graphics.TOP | Graphics.LEFT);
            if (initError.length() > 0)
                g.drawString(initError, 4, g.getFont().getHeight() + 6, Graphics.TOP | Graphics.LEFT);
        }

        private void drawMenuPanorama(Graphics g) {
            if (titleBackgroundSource == null || menuPanPixels == null || menuPanScale <= 0.0f) return;
            int width = getWidth();
            int height = getHeight();
            int sourceWidth = titleBackgroundSource.getWidth();
            int sourceHeight = titleBackgroundSource.getHeight();
            int[] sourceRow = new int[sourceWidth];
            float scaledHeight = sourceHeight * menuPanScale;
            float verticalCrop = (scaledHeight - height) * 0.5f;
            float offset = (float)((menuPanTime / 60L) % Math.max(1, (int)menuPanWidth));
            for (int y = 0; y < height; y++) {
                int sourceY = (int)((verticalCrop + y) / menuPanScale);
                if (sourceY < 0) sourceY = 0;
                if (sourceY >= sourceHeight) sourceY = sourceHeight - 1;
                titleBackgroundSource.getRGB(sourceRow, 0, sourceWidth, 0, sourceY, sourceWidth, 1);
                int row = y * width;
                for (int x = 0; x < width; x++) {
                    float panoramaX = (offset + x) % menuPanWidth;
                    int sourceX = (int)(panoramaX / menuPanScale);
                    if (sourceX >= sourceWidth) sourceX = sourceWidth - 1;
                    menuPanPixels[row + x] = sourceRow[sourceX];
                }
            }
            g.drawRGB(menuPanPixels, 0, width, 0, 0, width, height, true);
        }

        // Small atan2 approximation for older CLDC profiles lacking Math.atan2.
        private float fastAtan2(float y, float x) {
            if (x == 0.0f) {
                if (y > 0.0f) return 1.5707963f;
                if (y < 0.0f) return -1.5707963f;
                return 0.0f;
            }
            float absY = y < 0.0f ? -y : y;
            float angle;
            if (x > 0.0f) {
                float r = (x - absY) / (x + absY);
                angle = 0.78539816f - 0.78539816f * r;
            } else {
                float r = (x + absY) / (absY - x);
                angle = 2.35619449f - 0.78539816f * r;
            }
            return y < 0.0f ? -angle : angle;
        }

        private void update(int dt) {
            if (menuMode) {
                menuPanTime += dt;
                return;
            }
            gameClock += dt;
            if (dead) {
                if (currentGame == 2 || currentGame == 10) updateZombies(dt);
                if (currentGame == 2 || currentGame == 5) updateWorldObjects(dt);
                updateDeathPieces(dt);
                if (deathPieces != null && deathPieces.length > 1 && deathPieces[1] != null) {
                    PhysicsPiece torso = deathPieces[1];
                    updateCameraAt(torso.x, torso.y, torso.z);
                }
                if (gameClock >= respawnAt) respawnPlayer();
                return;
            }
            if (protectionAura != null && gameClock >= invulnerableUntil) {
                try { avatar.removeChild(protectionAura); } catch (Throwable ignored) {}
                protectionAura = null;
                protectionAuraMaterial = null;
            } else if (protectionAura != null) {
                updateProtectionFlash();
            }
            if (currentGame == 2 || currentGame == 10) updateZombies(dt);
            if (currentGame == 2 || currentGame == 5) updateWorldObjects(dt);
            if (dead) return;
            animTime += dt;
            float step = dt * 0.004f;
            float cameraTurn = dt * cameraSensitivity;
            float cameraDirection = invertCamera ? -1.0f : 1.0f;
            if (camLeft) camYaw -= cameraTurn * cameraDirection;
            if (camRight) camYaw += cameraTurn * cameraDirection;
            if (camYaw > 3.14159265f) camYaw -= 6.2831853f;
            if (camYaw < -3.14159265f) camYaw += 6.2831853f;
            if (camUp) camPitch += cameraTurn * cameraDirection;
            if (camDown) camPitch -= cameraTurn * cameraDirection;
            // Classic third-person camera: allow looking up/down, but never flip over.
            if (camPitch > 1.20f) camPitch = 1.20f;
            if (camPitch < -1.20f) camPitch = -1.20f;
            float dx = 0, dz = 0;
            float sin = (float)Math.sin(camYaw), cos = (float)Math.cos(camYaw);
            if (forward) { dx += sin * step; dz -= cos * step; }
            if (back) { dx -= sin * step; dz += cos * step; }
            if (left) { dx -= cos * step; dz -= sin * step; }
            if (right) { dx += cos * step; dz += sin * step; }
            float nextX = px + dx, nextZ = pz + dz;
            // Resolve horizontal movement against every block face, one axis at a time.
            if (isPlayerBlocked(nextX, pz)) nextX = px;
            if (isPlayerBlocked(nextX, nextZ)) nextZ = pz;
            dx = nextX - px; dz = nextZ - pz;
            px = nextX; pz = nextZ;
            // Gravity applies continuously; landing checks cover the floor and obby platforms.
            float previousY = jumpY;
            if (!grounded) {
                jumpY += playerVelocityY * dt;
                playerVelocityY -= 0.000018f * dt;
            }
            resolveLanding(previousY);
            if (isTouchingStudioHazard()) {
                beginDeath();
                return;
            }
            if (!grounded && jumpY < -8.0f) {
                beginDeath();
                return;
            }
            if (lobbyMode && gameClock >= invulnerableUntil && jumpY < 1.1f &&
                    Math.abs(px - 3.0f) < 0.95f && Math.abs(pz + 3.0f) < 0.95f) {
                beginDeath();
                return;
            }
            if (currentGame == 0 || currentGame == 7) {
                float[] finish = OBBY_BLOCKS[OBBY_BLOCKS.length - 1];
                float finishTop = finish[1] + finish[4] * 0.5f;
                if (onPlatform(finish[0], finish[2], finish[3] * 0.5f, finish[5] * 0.5f) &&
                        jumpY >= finishTop - 0.05f)
                    modelStatus = currentGame == 7 ? "Race complete!" : "Obby complete!";
            }
            // Turn only while moving, toward the world-space travel direction.
            if (dx != 0.0f || dz != 0.0f) yaw = fastAtan2(-dx, -dz);
            jumping = !grounded;
            if (avatar != null) {
                avatar.setTranslation(px, jumpY, pz);
                avatar.setOrientation((float)(yaw * 57.29578f), 0, 1, 0);
            }
            updateCamera();
            int wantedAnimation;
            if (jumping) wantedAnimation = 2;
            else if (forward || back || left || right) wantedAnimation = 1;
            else wantedAnimation = 0;
            animation = wantedAnimation;
            animateNoob();
            if (world != null) {
                try { world.animate(animTime); } catch (Throwable t) {}
            }
            updateExtraGame(dt);
            if (currentGame == 2) updateHordeStatus();
        }

        private void resolveLanding(float previousY) {
            float top = Float.NEGATIVE_INFINITY;
            if (currentGame != 4 && Math.abs(px) < 11.5f &&
                    Math.abs(pz) < 11.5f) top = 0.0f;
            if (currentGame >= 4) {
                for (int i=0;i<modeBlockCount;i++) {
                    if (!modeBlockActive[i]) continue;
                    float[] b=modeBlocks[i];
                    float platformTop=b[1]+b[4]*0.5f;
                    if (onPlatform(b[0],b[2],b[3]*0.5f,b[5]*0.5f) &&
                            platformCanSupport(previousY,platformTop))
                        top=Math.max(top,platformTop);
                }
            }
            if (currentGame == 3 && studioTesting) {
                for (int i=0; i<studioObjectCount; i++) {
                    StudioObject object = studioObjects[i];
                    if (object == null || object.type == 2) continue;
                    float platformTop = object.y + object.sy * 0.5f;
                    if (onPlatform(object.x,object.z,object.sx*0.5f,object.sz*0.5f) &&
                            platformCanSupport(previousY,platformTop))
                        top = Math.max(top,platformTop);
                }
            }
            if (currentGame == 0 || currentGame == 2) {
                float[][] platforms = currentGame == 0 ? OBBY_BLOCKS : ZOMBIE_BLOCKS;
                for (int i = 0; i < platforms.length; i++) {
                    float[] b = platforms[i];
                    float platformTop = b[1] + b[4] * 0.5f;
                    if (onPlatform(b[0], b[2], b[3] * 0.5f, b[5] * 0.5f) &&
                            platformCanSupport(previousY, platformTop)) top = Math.max(top, platformTop);
                }
            }
            if (!grounded && playerVelocityY <= 0.0f && top != Float.NEGATIVE_INFINITY &&
                    previousY >= top && jumpY <= top) {
                jumpY = top;
                playerVelocityY = 0.0f;
                grounded = true;
            } else if (grounded) {
                if (top != Float.NEGATIVE_INFINITY && jumpY <= top + 0.05f) {
                    jumpY = top;
                    playerVelocityY = 0.0f;
                } else {
                    grounded = false;
                }
            }
        }

        private boolean platformCanSupport(float previousY, float platformTop) {
            return (grounded && jumpY >= platformTop - 0.05f) ||
                    (!grounded && playerVelocityY <= 0.0f && previousY >= platformTop && jumpY <= platformTop);
        }

        private boolean onPlatform(float x, float z, float halfX, float halfZ) {
            return Math.abs(px - x) <= halfX + 0.22f && Math.abs(pz - z) <= halfZ + 0.22f;
        }

        private void beginDeath() {
            if (dead || gameClock < invulnerableUntil) return;
            dead = true;
            grounded = false;
            jumping = false;
            respawnAt = gameClock + 2600;
            if (avatar != null) world.removeChild(avatar);
            deathPieces = new PhysicsPiece[6];
            float c = (float)Math.cos(yaw), sn = (float)Math.sin(yaw);
            addDeathPiece(0, makeRoundedCylinderHead(), 0, 2.19f, 0, 0.28f, 0.25f, 0.28f, c, sn);
            addDeathPiece(1, makeBox(0,0,0,0.78f,0.95f,0.40f,0x1769AA), 0,1.45f,0, 0.39f,0.475f,0.20f, c,sn);
            addDeathPiece(2, makeBox(0,0,0,0.30f,0.88f,0.40f,0xF5C542), -0.54f,1.48f,0, 0.15f,0.44f,0.20f, c,sn);
            addDeathPiece(3, makeBox(0,0,0,0.30f,0.88f,0.40f,0xF5C542), 0.54f,1.48f,0, 0.15f,0.44f,0.20f, c,sn);
            addDeathPiece(4, makeBox(0,0,0,0.36f,0.78f,0.40f,0x2E9E45), -0.20f,0.52f,0, 0.18f,0.39f,0.20f, c,sn);
            addDeathPiece(5, makeBox(0,0,0,0.36f,0.78f,0.40f,0x2E9E45), 0.20f,0.52f,0, 0.18f,0.39f,0.20f, c,sn);
        }

        private void addDeathPiece(int index, Node node, float ox, float oy, float oz,
                float halfX, float halfY, float halfZ, float c, float sn) {
            float x = px + ox * c + oz * sn;
            float z = pz - ox * sn + oz * c;
            node.setTranslation(x, jumpY + oy, z);
            world.addChild(node);
            float outward = (index - 2.5f) * 0.0012f;
            float direction = index % 2 == 0 ? 1.0f : -1.0f;
            float axisX = index % 3 == 0 ? 1.0f : 0.35f;
            float axisY = index % 3 == 1 ? 1.0f : 0.45f;
            float axisZ = index % 3 == 2 ? 1.0f : 0.60f;
            float axisLength = (float)Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
            axisX /= axisLength; axisY /= axisLength; axisZ /= axisLength;
            deathPieces[index] = new PhysicsPiece(node, x, jumpY + oy, z,
                    outward, 0.0035f + (index % 3) * 0.001f, direction * 0.001f,
                    35 + index * 5, axisX, axisY, axisZ, halfX, halfY, halfZ);
        }

        private void updateDeathPieces(int dt) {
            if (deathPieces == null) return;
            for (int i = 0; i < deathPieces.length; i++) {
                PhysicsPiece p = deathPieces[i];
                if (p == null) continue;
                p.vy -= 0.000018f * dt;
                p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
                p.rotation += p.spin * dt * 0.001f;
                updatePieceExtents(p);
                resolvePieceEnvironment(p);
            }
            for (int i = 0; i < deathPieces.length; i++) {
                PhysicsPiece a = deathPieces[i];
                if (a == null) continue;
                for (int j = i + 1; j < deathPieces.length; j++) {
                    PhysicsPiece b = deathPieces[j];
                    if (b != null) resolvePiecePair(a, b);
                }
            }
            for (int i = 0; i < deathPieces.length; i++) {
                PhysicsPiece p = deathPieces[i];
                if (p == null) continue;
                p.node.setTranslation(p.x, p.y, p.z);
                p.node.setOrientation(p.rotation, p.spinX, p.spinY, p.spinZ);
            }
        }

        private void updatePieceExtents(PhysicsPiece p) {
            float radians = p.rotation * 0.017453293f;
            float c = (float)Math.cos(radians), s = (float)Math.sin(radians);
            float t = 1.0f - c;
            float r00=t*p.spinX*p.spinX+c, r01=t*p.spinX*p.spinY-s*p.spinZ;
            float r02=t*p.spinX*p.spinZ+s*p.spinY;
            float r10=t*p.spinX*p.spinY+s*p.spinZ, r11=t*p.spinY*p.spinY+c;
            float r12=t*p.spinY*p.spinZ-s*p.spinX;
            float r20=t*p.spinX*p.spinZ-s*p.spinY, r21=t*p.spinY*p.spinZ+s*p.spinX;
            float r22=t*p.spinZ*p.spinZ+c;
            p.extentX=Math.abs(r00)*p.halfX+Math.abs(r01)*p.halfY+Math.abs(r02)*p.halfZ;
            p.extentY=Math.abs(r10)*p.halfX+Math.abs(r11)*p.halfY+Math.abs(r12)*p.halfZ;
            p.extentZ=Math.abs(r20)*p.halfX+Math.abs(r21)*p.halfY+Math.abs(r22)*p.halfZ;
        }

        private void resolvePieceEnvironment(PhysicsPiece p) {
            if (currentGame == 0) {
                for (int i = 0; i < OBBY_BLOCKS.length; i++) {
                    float[] b = OBBY_BLOCKS[i];
                    resolvePieceAgainstBox(p,b[0],b[1],b[2],b[3]*0.5f,b[4]*0.5f,b[5]*0.5f);
                }
            } else if (lobbyMode) {
                resolvePieceAgainstBox(p, 3, 0.35f, -3, 0.6f, 0.35f, 0.6f);
            } else if (currentGame == 2) {
                for (int i = 0; i < ZOMBIE_BLOCKS.length; i++) {
                    float[] b = ZOMBIE_BLOCKS[i];
                    resolvePieceAgainstBox(p,b[0],b[1],b[2],b[3]*0.5f,b[4]*0.5f,b[5]*0.5f);
                }
                for (int i = 0; i < worldObjects.length; i++) {
                    WorldObject o = worldObjects[i];
                    if (o != null && o.active && !o.bomb && o.body != p) {
                        PhysicsPiece b = o.body;
                        resolvePieceAgainstBox(p,b.x,b.y,b.z,b.extentX,b.extentY,b.extentZ);
                    }
                }
            } else if (currentGame == 3) {
                for (int i=0; i<studioObjectCount; i++) {
                    StudioObject object = studioObjects[i];
                    if (object != null)
                        resolvePieceAgainstBox(p,object.x,object.y,object.z,
                                object.sx*0.5f,object.sy*0.5f,object.sz*0.5f);
                }
            }
            if (Math.abs(p.x) <= 12.0f + p.extentX && Math.abs(p.z) <= 12.0f + p.extentZ &&
                    p.y - p.extentY < 0.0f) {
                p.y = p.extentY;
                if (p.vy < -0.0005f) p.vy = -p.vy * 0.28f;
                else p.vy = 0.0f;
                p.vx *= 0.86f; p.vz *= 0.86f; p.spin *= 0.82f;
            }
        }

        private void resolvePieceAgainstBox(PhysicsPiece p, float x, float y, float z,
                float hx, float hy, float hz) {
            float dx=p.x-x, dy=p.y-y, dz=p.z-z;
            float ox=p.extentX+hx-Math.abs(dx);
            float oy=p.extentY+hy-Math.abs(dy);
            float oz=p.extentZ+hz-Math.abs(dz);
            if (ox <= 0 || oy <= 0 || oz <= 0) return;
            if (ox <= oy && ox <= oz) {
                float n = dx >= 0 ? 1.0f : -1.0f;
                p.x += n * (ox + 0.001f);
                if (p.vx * n < 0) p.vx = -p.vx * 0.28f;
            } else if (oy <= ox && oy <= oz) {
                float n = dy >= 0 ? 1.0f : -1.0f;
                p.y += n * (oy + 0.001f);
                if (p.vy * n < 0) p.vy = -p.vy * 0.28f;
            } else {
                float n = dz >= 0 ? 1.0f : -1.0f;
                p.z += n * (oz + 0.001f);
                if (p.vz * n < 0) p.vz = -p.vz * 0.28f;
            }
            p.vx *= 0.92f; p.vz *= 0.92f; p.spin *= 0.90f;
        }

        private void resolvePiecePair(PhysicsPiece a, PhysicsPiece b) {
            float dx=a.x-b.x, dy=a.y-b.y, dz=a.z-b.z;
            float ox=a.extentX+b.extentX-Math.abs(dx);
            float oy=a.extentY+b.extentY-Math.abs(dy);
            float oz=a.extentZ+b.extentZ-Math.abs(dz);
            if (ox <= 0 || oy <= 0 || oz <= 0) return;
            float nx=0, ny=0, nz=0, penetration;
            if (ox <= oy && ox <= oz) { nx=dx >= 0 ? 1 : -1; penetration=ox; }
            else if (oy <= ox && oy <= oz) { ny=dy >= 0 ? 1 : -1; penetration=oy; }
            else { nz=dz >= 0 ? 1 : -1; penetration=oz; }
            float push=penetration * 0.5f + 0.001f;
            a.x+=nx*push; a.y+=ny*push; a.z+=nz*push;
            b.x-=nx*push; b.y-=ny*push; b.z-=nz*push;
            float relative=(a.vx-b.vx)*nx+(a.vy-b.vy)*ny+(a.vz-b.vz)*nz;
            if (relative < 0) {
                float impulse=-0.65f*relative;
                a.vx+=impulse*nx; a.vy+=impulse*ny; a.vz+=impulse*nz;
                b.vx-=impulse*nx; b.vy-=impulse*ny; b.vz-=impulse*nz;
            }
            a.spin*=0.94f; b.spin*=0.94f;
        }

        private void resetPlayerSpawn() {
            if (currentGame == 3 && studioTesting) {
                boolean foundSpawn = false;
                for (int i=0; i<studioObjectCount; i++) {
                    StudioObject object = studioObjects[i];
                    if (object != null && object.type == 4) {
                        px=object.x; pz=object.z; jumpY=object.y + object.sy*0.5f;
                        foundSpawn=true; break;
                    }
                }
                if (!foundSpawn) { px=0; pz=0; jumpY=0; }
            } else if ((currentGame == 0 || currentGame == 7) && OBBY_BLOCKS.length > 0) {
                float[] first = OBBY_BLOCKS[0];
                px = first[0]; pz = first[2];
                jumpY = first[1] + first[4] * 0.5f;
            } else if ((currentGame == 4 || currentGame == 6) && modeBlockCount > 0) {
                float[] first=modeBlocks[0];
                px=first[0]; pz=first[2]; jumpY=first[1]+first[4]*0.5f;
            } else if (currentGame == 9) {
                px=0; pz=0; jumpY=0.70f;
            } else if (currentGame == 13) {
                px=0; pz=0; jumpY=0.36f;
            } else if (currentGame == 14) {
                px=0; pz=0; jumpY=0.50f;
            } else {
                px = 0;
                pz = 0;
                jumpY = 0;
            }
            playerVelocityY = 0;
            grounded = true;
        }

        private void respawnPlayer() {
            if (deathPieces != null) {
                for (int i = 0; i < deathPieces.length; i++)
                    if (deathPieces[i] != null) world.removeChild(deathPieces[i].node);
            }
            deathPieces = null;
            try {
                resetPlayerSpawn();
                yaw = 0; grounded = true; jumping = false;
                avatar = new Group();
                activeModel = buildNoob();
                avatar.addChild(activeModel);
                protectionAura = makeProtectionAura();
                avatar.addChild(protectionAura);
                world.addChild(avatar);
                dead = false;
                invulnerableUntil = gameClock + 4000;
                updateCamera();
            } catch (Throwable t) {
                dead = true;
                respawnAt = gameClock + 1000;
                initError = "Respawn: " + t.toString();
            }
        }

        private Group makeProtectionAura() {
            final int rings = 10, segments = 20;
            int[] stripLengths = new int[rings];
            for (int i = 0; i < rings; i++) stripLengths[i] = (segments + 1) * 2;
            short[] shell = new short[rings * (segments + 1) * 2 * 3];
            int out = 0;
            for (int r = 0; r < rings; r++) {
                double a0 = -1.57079632679 + 3.14159265359 * r / rings;
                double a1 = -1.57079632679 + 3.14159265359 * (r + 1) / rings;
                for (int s = 0; s <= segments; s++) {
                    double lon = 6.28318530718 * s / segments;
                    shell[out++] = (short)(Math.cos(a0) * Math.sin(lon) * 1350);
                    shell[out++] = (short)(Math.sin(a0) * 1350);
                    shell[out++] = (short)(Math.cos(a0) * Math.cos(lon) * 1350);
                    shell[out++] = (short)(Math.cos(a1) * Math.sin(lon) * 1350);
                    shell[out++] = (short)(Math.sin(a1) * 1350);
                    shell[out++] = (short)(Math.cos(a1) * Math.cos(lon) * 1350);
                }
            }
            VertexArray va = new VertexArray(rings * (segments + 1) * 2, 3, 2);
            va.set(0, rings * (segments + 1) * 2, shell);
            VertexBuffer vb = new VertexBuffer(); vb.setPositions(va, 0.001f, null);
            TriangleStripArray tris = new TriangleStripArray(0, stripLengths);
            Appearance ap = new Appearance();
            Material mat = new Material();
            mat.setColor(Material.AMBIENT, 0x503399FF);
            mat.setColor(Material.DIFFUSE, 0x503399FF);
            mat.setColor(Material.EMISSIVE, 0x503399FF);
            protectionAuraMaterial = mat;
            ap.setMaterial(mat);
            CompositingMode cm = new CompositingMode();
            cm.setBlending(CompositingMode.ALPHA);
            ap.setCompositingMode(cm);
            PolygonMode pm = new PolygonMode(); pm.setCulling(PolygonMode.CULL_NONE);
            ap.setPolygonMode(pm);
            Mesh shellMesh = new Mesh(vb, new IndexBuffer[] {tris}, new Appearance[] {ap});
            Group aura = new Group();
            aura.addChild(shellMesh);
            aura.setTranslation(0, 1.35f, 0);
            return aura;
        }

        private void updateProtectionFlash() {
            if (protectionAuraMaterial == null) return;
            float pulse = (float)Math.sin(gameClock * 0.018f);
            int alpha = 55 + (int)((pulse + 1.0f) * 75.0f);
            int color = (alpha << 24) | 0x003399FF;
            protectionAuraMaterial.setColor(Material.AMBIENT, color);
            protectionAuraMaterial.setColor(Material.DIFFUSE, color);
            protectionAuraMaterial.setColor(Material.EMISSIVE, color);
        }

        private void updateCamera() {
            updateCameraAt(px, 1.2f + jumpY, pz);
        }

        private void updateCameraAt(float targetX, float targetY, float targetZ) {
            float horizontal = (float)Math.cos(camPitch) * camDistance;
            float cx = targetX - (float)Math.sin(camYaw) * horizontal;
            float cy = targetY + (float)Math.sin(camPitch) * camDistance;
            float cz = targetZ + (float)Math.cos(camYaw) * horizontal;
            // M3G Transform has no setLookAt helper; set camera position and orientation.
            // Camera looks along local -Z. Orbit position is opposite the look direction,
            // so yaw must have the opposite sign to camYaw.
            camera.setOrientation((float)(-camYaw * 57.29578f), 0.0f, 1.0f, 0.0f);
            camera.postRotate((float)(-camPitch * 57.29578f), 1.0f, 0.0f, 0.0f);
            camera.setTranslation(cx, cy, cz);
        }

        protected void paint(Graphics g) {
            if (menuMode) {
                drawMenu(g);
                return;
            }
            g.setColor(0x78BCE8);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (loaded && world != null) {
                boolean bound = false;
                try {
                    g3d.bindTarget(g, true, Graphics3D.DITHER);
                    bound = true;
                    g3d.setViewport(0, 0, getWidth(), getHeight());
                    camera.setPerspective(65.0f, (float)getWidth()/(float)getHeight(), 0.1f, 240.0f);
                    updateSkyCrop();
                    g3d.render(world);
                } catch (Throwable t) {
                    g.setColor(0xFFFFFF);
                    g.drawString("M3G render error", 3, 3, Graphics.TOP|Graphics.LEFT);
                } finally {
                    if (bound) {
                        try { g3d.releaseTarget(); } catch (Throwable t) {}
                    }
                }
            } else {
                g.setColor(0xFFFFFF);
                g.drawString("Roblox3DME - model load failed", 4, 4, Graphics.TOP|Graphics.LEFT);
                g.drawString("Check Java model and faceNEW.png / faceZomb.png", 4, 22, Graphics.TOP|Graphics.LEFT);
            }
            g.setColor(0xFFFFFF);
            g.drawString("ROBLOX 3DME", 3, 2, Graphics.TOP|Graphics.LEFT);
            g.drawString(modelStatus, 3, 20, Graphics.TOP|Graphics.LEFT);
            if (modelError.length() > 0) drawWrapped(g, modelError, 3, 38, getWidth() - 6);
            if (initError.length() > 0) drawWrapped(g, initError, 3, 72, getWidth() - 6);
            if (currentGame == 2) drawInventory(g);
            if (currentGame >= 4) drawExtraHud(g);
            if (currentGame == 3 && !paused) drawStudioHud(g);
            if (paused) drawPauseMenu(g);
        }


        private void drawExtraHud(Graphics g) {
            String label="";
            if(currentGame==4) label="CLIMB! LAVA "+(int)(lavaHeight*10)/10.0f;
            else if(currentGame==5) label="DODGE METEORS  "+Math.max(0,30-(int)((gameClock-modeStartAt)/1000))+"s";
            else if(currentGame==6) label="KEEP MOVING - TILES FALL";
            else if(currentGame==7) label="TIME "+Math.max(0,(int)((gameClock-modeStartAt)/1000))+"s";
            else if(currentGame==8) label="COINS "+modeCollected+"/"+modeGoal;
            else if(currentGame==9) label="HILL "+modeScore+"/20s";
            else if(currentGame==10) label="3 ATTACK  "+modeScore+" KOs";
            else if(currentGame==11) label="FOUND "+(modeGoal-countZombies())+"/"+modeGoal;
            else if(currentGame==12) {
                String[] colors=new String[] {"RED","BLUE","GREEN","YELLOW"};
                label=simonRound>12?"SIMON SAYS COMPLETE":(simonRound==0?"GET READY":"GO TO "+colors[simonTarget]);
            } else if(currentGame==13) label="CASH "+modeCash+"  BUILD "+tycoonLevel+"/9  (3)";
            else if(currentGame==14) label="LIVES "+Math.max(0,3-defenseLeaked)+"  3 ATTACK";
            g.setColor(0xFFFFFF);
            g.drawString(label,getWidth()/2,getHeight()-3,Graphics.BOTTOM|Graphics.HCENTER);
        }

        private void drawInventory(Graphics g) {
            String[] items = new String[] {"Espada", "Bomba", "Pala"};
            int y = getHeight() - 2 * g.getFont().getHeight() - 6;
            g.setColor(0xFFFFFF);
            g.drawString("7/9 CAMBIAR   3 USAR", getWidth() / 2, y,
                    Graphics.BOTTOM | Graphics.HCENTER);
            g.setColor(0xF5C542);
            g.drawString("ITEM: " + items[inventorySelection], getWidth() / 2,
                    y - g.getFont().getHeight(), Graphics.BOTTOM | Graphics.HCENTER);
        }

        private void drawWrapped(Graphics g, String s, int x, int y, int maxWidth) {
            if (s == null || s.length() == 0) return;
            Font font = g.getFont();
            int lineHeight = font.getHeight();
            int start = 0;
            int lineY = y;
            while (start < s.length() && lineY < getHeight() - lineHeight) {
                int end = start;
                int lastSpace = -1;
                while (end < s.length()) {
                    if (s.charAt(end) == ' ') lastSpace = end;
                    if (font.stringWidth(s.substring(start, end + 1)) > maxWidth) break;
                    end++;
                }
                if (end == start) end++;
                else if (end < s.length() && lastSpace >= start) end = lastSpace;
                g.drawString(s.substring(start, end), x, lineY, Graphics.TOP|Graphics.LEFT);
                start = end;
                while (start < s.length() && s.charAt(start) == ' ') start++;
                lineY += lineHeight;
            }
        }

        private Group buildNoob() throws Exception {
            return buildNoob(false);
        }

        private Group buildNoob(boolean zombie) throws Exception {
            Group model = new Group();
            int skin = zombie ? 0x35B64A : 0xF5C542;
            int clothing = zombie ? 0x795438 : 0x1769AA;
            int legs = zombie ? 0x795438 : 0x2E9E45;

            // Zombie copies use the noob body proportions and face with a green/brown palette.
            model.addChild(makeBox(0, 1.45f, 0, 0.78f, 0.95f, 0.40f, clothing));
            model.addChild(makeRoundedCylinderHead(skin));
            model.addChild(makeTexturedFace(zombie ? "faceZomb.png" : "faceNEW.png", skin));
            Mesh modelLeftArm = (Mesh)makeBox(-0.54f, 1.48f, 0, 0.30f, 0.88f, 0.40f, skin);
            Mesh modelRightArm = (Mesh)makeBox(0.54f, 1.48f, 0, 0.30f, 0.88f, 0.40f, skin);
            Mesh modelLeftLeg = (Mesh)makeBox(-0.20f, 0.52f, 0, 0.36f, 0.78f, 0.40f, legs);
            Mesh modelRightLeg = (Mesh)makeBox(0.20f, 0.52f, 0, 0.36f, 0.78f, 0.40f, legs);
            model.addChild(modelLeftArm);
            model.addChild(modelRightArm);
            model.addChild(modelLeftLeg);
            model.addChild(modelRightLeg);
            if (!zombie) {
                leftArm = modelLeftArm; rightArm = modelRightArm;
                leftLeg = modelLeftLeg; rightLeg = modelRightLeg;
                swordVisual = makeSwordVisual();
                swordVisual.setRenderingEnable(false);
                model.addChild(swordVisual);
            }
            return model;
        }

        private Node makeTexturedFace(String resourceName, int fallbackColor) {
            try {
                javax.microedition.lcdui.Image img =
                        loadImageResource("/" + resourceName, "/meblox/" + resourceName);
                if (img == null) {
                    modelError = resourceName + " not found in /meblox/ or JAR root";
                    return makeCurvedTexturedFace(makeSolidAppearance(fallbackColor));
                }

                // Preserve the PNG alpha channel while resizing to JSR-184 power-of-two dimensions.
                Image textureImage = makePowerOfTwoImage(img);
                Image2D image2D = new Image2D(Image2D.RGBA, textureImage);
                Texture2D texture = new Texture2D(image2D);
                texture.setFiltering(Texture2D.FILTER_NEAREST, Texture2D.FILTER_NEAREST);
                texture.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
                texture.setBlending(Texture2D.FUNC_MODULATE);

                Appearance ap = new Appearance();
                ap.setTexture(0, texture);
                Material mat = new Material();
                mat.setColor(Material.AMBIENT, 0xFFFFFFFF);
                mat.setColor(Material.DIFFUSE, 0xFFFFFFFF);
                mat.setColor(Material.EMISSIVE, 0xFFFFFFFF);
                ap.setMaterial(mat);

                // Draw the transparent PNG pixels with source alpha and discard fully transparent texels.
                // Disabling depth writes prevents invisible texels from occluding other geometry.
                CompositingMode compositing = new CompositingMode();
                compositing.setBlending(CompositingMode.ALPHA);
                compositing.setAlphaThreshold(0.01f);
                compositing.setDepthWriteEnable(false);
                ap.setCompositingMode(compositing);

                PolygonMode polygonMode = new PolygonMode();
                polygonMode.setCulling(PolygonMode.CULL_NONE);
                ap.setPolygonMode(polygonMode);
                return makeCurvedTexturedFace(ap);
            } catch (Throwable t) {
                modelError = resourceName + " texture: " + t.toString();
                return makeCurvedTexturedFace(makeSolidAppearance(fallbackColor));
            }
        }

        private Mesh makeCurvedTexturedFace(Appearance ap) {
            final int segments = 8;
            final float radius = 0.291f; // 0.011 units outside the cylinder's 0.28 radius
            short[] positions = new short[(segments + 1) * 2 * 3];
            short[] texCoords = new short[(segments + 1) * 2 * 2];
            int p = 0;
            int uv = 0;
            for (int i = 0; i <= segments; i++) {
                double angle = -0.68 + 1.36 * i / segments;
                float x = (float)(radius * Math.sin(angle));
                float z = (float)(-radius * Math.cos(angle));
                int u = 1000 * i / segments;
                // The enlarged curved strip follows the cylinder with a small radial gap.
                positions[p++] = (short)(x * 1000.0f);
                positions[p++] = 2010;
                positions[p++] = (short)(z * 1000.0f);
                positions[p++] = (short)(x * 1000.0f);
                positions[p++] = 2370;
                positions[p++] = (short)(z * 1000.0f);
                texCoords[uv++] = (short)u;
                texCoords[uv++] = 1000;
                texCoords[uv++] = (short)u;
                texCoords[uv++] = 0;
            }
            VertexArray pos = new VertexArray((segments + 1) * 2, 3, 2);
            pos.set(0, (segments + 1) * 2, positions);
            VertexArray uvArray = new VertexArray((segments + 1) * 2, 2, 2);
            uvArray.set(0, (segments + 1) * 2, texCoords);
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(pos, 0.001f, null);
            vb.setTexCoords(0, uvArray, 0.001f, null);
            TriangleStripArray strip = new TriangleStripArray(0, new int[] {(segments + 1) * 2});
            return new Mesh(vb, new IndexBuffer[] {strip}, new Appearance[] {ap});
        }

        private Appearance makeSolidAppearance(int color) {
            Appearance ap = new Appearance();
            Material mat = new Material();
            mat.setColor(Material.AMBIENT, color);
            mat.setColor(Material.DIFFUSE, color);
            mat.setColor(Material.EMISSIVE, color);
            ap.setMaterial(mat);
            PolygonMode pm = new PolygonMode();
            pm.setCulling(PolygonMode.CULL_NONE);
            ap.setPolygonMode(pm);
            return ap;
        }

        private Image makePowerOfTwoImage(Image source) {
            int sourceWidth = source.getWidth();
            int sourceHeight = source.getHeight();
            int width = 1;
            int height = 1;
            while (width < sourceWidth) width *= 2;
            while (height < sourceHeight) height *= 2;
            if (width == sourceWidth && height == sourceHeight) return source;

            int[] sourcePixels = new int[sourceWidth * sourceHeight];
            source.getRGB(sourcePixels, 0, sourceWidth, 0, 0, sourceWidth, sourceHeight);
            int[] texturePixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                int sourceY = y * sourceHeight / height;
                for (int x = 0; x < width; x++) {
                    int sourceX = x * sourceWidth / width;
                    texturePixels[y * width + x] = sourcePixels[sourceY * sourceWidth + sourceX];
                }
            }
            return Image.createRGBImage(texturePixels, width, height, true);
        }

        private Node makeRoundedCylinderHead() {
            return makeRoundedCylinderHead(0xF5C542);
        }

        private Node makeRoundedCylinderHead(int color) {
            final int segments = 32;
            final int bevelSteps = 6;
            final float halfHeight = 0.25f;
            final float endRadius = 0.20f;
            final float bevelRadius = 0.08f;
            int ringCount = bevelSteps * 2 + 1;
            float[] ringY = new float[ringCount];
            float[] ringRadius = new float[ringCount];
            for (int i = 0; i <= bevelSteps; i++) {
                double angle = 1.57079632679 * i / bevelSteps;
                ringY[i] = -halfHeight + bevelRadius * (1.0f - (float)Math.cos(angle));
                ringRadius[i] = endRadius + bevelRadius * (float)Math.sin(angle);
            }
            for (int i = 1; i <= bevelSteps; i++) {
                double angle = 1.57079632679 * i / bevelSteps;
                int ring = bevelSteps + i;
                ringY[ring] = halfHeight - bevelRadius * (float)Math.cos(angle);
                ringRadius[ring] = endRadius + bevelRadius * (float)Math.cos(angle);
            }

            int bandCount = ringCount - 1;
            int verticesPerBand = (segments + 1) * 2;
            short[] shellCoords = new short[bandCount * verticesPerBand * 3];
            int[] shellStrips = new int[bandCount];
            int out = 0;
            for (int band = 0; band < bandCount; band++) {
                shellStrips[band] = verticesPerBand;
                for (int point = 0; point <= segments; point++) {
                    double angle = 6.283185307179586 * point / segments;
                    float c = (float)Math.cos(angle);
                    float sn = (float)Math.sin(angle);
                    shellCoords[out++] = (short)(ringRadius[band] * sn * 1000.0f);
                    shellCoords[out++] = (short)(ringY[band] * 1000.0f);
                    shellCoords[out++] = (short)(-ringRadius[band] * c * 1000.0f);
                    shellCoords[out++] = (short)(ringRadius[band + 1] * sn * 1000.0f);
                    shellCoords[out++] = (short)(ringY[band + 1] * 1000.0f);
                    shellCoords[out++] = (short)(-ringRadius[band + 1] * c * 1000.0f);
                }
            }

            Group head = new Group();
            head.addChild(makeSolidMesh(shellCoords, shellStrips, color));
            head.addChild(makeCylinderDisk(-halfHeight, endRadius, segments, color));
            head.addChild(makeCylinderDisk(halfHeight, endRadius, segments, color));
            head.setTranslation(0, 2.19f, 0);
            return head;
        }

        private Mesh makeCylinderDisk(float y, float radius, int segments, int color) {
            short[] coords = new short[segments * 3 * 3];
            int[] strips = new int[segments];
            int out = 0;
            for (int i = 0; i < segments; i++) {
                strips[i] = 3;
                double a0 = 6.283185307179586 * i / segments;
                double a1 = 6.283185307179586 * (i + 1) / segments;
                coords[out++] = 0; coords[out++] = (short)(y * 1000.0f); coords[out++] = 0;
                coords[out++] = (short)(radius * Math.sin(a0) * 1000.0);
                coords[out++] = (short)(y * 1000.0f);
                coords[out++] = (short)(-radius * Math.cos(a0) * 1000.0);
                coords[out++] = (short)(radius * Math.sin(a1) * 1000.0);
                coords[out++] = (short)(y * 1000.0f);
                coords[out++] = (short)(-radius * Math.cos(a1) * 1000.0);
            }
            return makeSolidMesh(coords, strips, color);
        }

        private Mesh makeSolidMesh(short[] coords, int[] stripLengths, int color) {
            int vertexCount = coords.length / 3;
            VertexArray vertices = new VertexArray(vertexCount, 3, 2);
            vertices.set(0, vertexCount, coords);
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(vertices, 0.001f, null);
            TriangleStripArray strips = new TriangleStripArray(0, stripLengths);
            Appearance ap = new Appearance();
            Material mat = new Material();
            mat.setColor(Material.AMBIENT, color);
            mat.setColor(Material.DIFFUSE, color);
            mat.setColor(Material.EMISSIVE, color);
            ap.setMaterial(mat);
            PolygonMode pm = new PolygonMode();
            pm.setCulling(PolygonMode.CULL_NONE);
            ap.setPolygonMode(pm);
            return new Mesh(vb, new IndexBuffer[] {strips}, new Appearance[] {ap});
        }

        private void animateNoob() {
            if (leftArm == null || rightArm == null || leftLeg == null || rightLeg == null) return;
            float swing = 0.0f;
            if (animation == 1) swing = (float)Math.sin(animTime * 0.012f) * 32.0f;
            else if (animation == 2) swing = -18.0f;
            boolean swingingSword = currentGame == 2 && gameClock < swordAttackUntil;
            leftArm.setOrientation(swing, 1, 0, 0);
            rightArm.setOrientation(swingingSword ? -35.0f : -swing, 1, 0, 0);
            leftLeg.setOrientation(-swing, 1, 0, 0);
            rightLeg.setOrientation(swingingSword ? 34.0f : swing, 1, 0, 0);
            if (swordVisual != null) swordVisual.setRenderingEnable(swingingSword);
        }

        private Node makeBox(float x, float y, float z, float sx, float sy, float sz, int color) {
            float[] corners = new float[] {
                -sx/2,-sy/2,-sz/2, sx/2,-sy/2,-sz/2, -sx/2,sy/2,-sz/2, sx/2,sy/2,-sz/2,
                -sx/2,-sy/2,sz/2, sx/2,-sy/2,sz/2, -sx/2,sy/2,sz/2, sx/2,sy/2,sz/2
            };
            // JSR-184 defines TriangleStripArray (not TriangleArray).
            // Four vertices per face, six independent strips.
            int[] faceOrder = new int[] {
                0,2,1,3,   // front
                4,5,6,7,   // back
                0,1,4,5,   // lower face
                2,6,3,7,   // upper face
                0,4,2,6,   // left face
                1,3,5,7    // right face
            };
            float[] p = new float[faceOrder.length * 3];
            for (int i = 0; i < faceOrder.length; i++) {
                int src = faceOrder[i] * 3;
                int dst = i * 3;
                p[dst] = corners[src];
                p[dst + 1] = corners[src + 1];
                p[dst + 2] = corners[src + 2];
            }
            VertexArray va = new VertexArray(faceOrder.length, 3, 2);
            short[] coords = new short[p.length];
            for (int i=0;i<p.length;i++) coords[i]=(short)(p[i]*1000);
            va.set(0, faceOrder.length, coords);
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(va, 0.001f, null);
            int[] stripLengths = new int[] {4, 4, 4, 4, 4, 4};
            TriangleStripArray ta = new TriangleStripArray(0, stripLengths);
            Appearance ap = new Appearance();
            Material mat = new Material();
            mat.setColor(Material.AMBIENT, color);
            mat.setColor(Material.DIFFUSE, color);
            mat.setColor(Material.EMISSIVE, color);
            ap.setMaterial(mat);
            Mesh mesh = new Mesh(vb, new IndexBuffer[] { ta }, new Appearance[] { ap });
            mesh.setTranslation(x,y,z);
            return mesh;
        }

        private boolean isRightSoftkey(int keyCode, int action) {
            if (keyCode == -7 || action == GAME_B) return true;
            try {
                String name = getKeyName(keyCode);
                return "Right Softkey".equalsIgnoreCase(name) ||
                        "Right Soft Key".equalsIgnoreCase(name) ||
                        "Soft2".equalsIgnoreCase(name) || "Soft 2".equalsIgnoreCase(name) ||
                        "Softkey2".equalsIgnoreCase(name) || "Softkey 2".equalsIgnoreCase(name) ||
                        "Right Selection".equalsIgnoreCase(name) ||
                        "Options".equalsIgnoreCase(name) || "Menu".equalsIgnoreCase(name);
            } catch (Throwable ignored) { return false; }
        }

        private void clearInput() {
            forward = back = left = right = false;
            camLeft = camRight = camUp = camDown = false;
        }

        private void exitToGameMenu() {
            paused = false;
            configMode = false;
            menuMode = true;
            loaded = false;
            selectedGame = currentGame;
            clearInput();
        }

        private void openPauseMenu() {
            paused = true;
            configMode = false;
            pauseSelection = 0;
            clearInput();
        }

        private void changeCameraSetting(int direction) {
            if (configSelection == 0) {
                sensitivityLevel = (sensitivityLevel + direction + 3) % 3;
                if (sensitivityLevel == 0) cameraSensitivity = 0.0015f;
                else if (sensitivityLevel == 1) cameraSensitivity = 0.0025f;
                else cameraSensitivity = 0.0040f;
            } else if (configSelection == 1) {
                invertCamera = !invertCamera;
            }
        }

        private void handlePauseInput(int keyCode, int action) {
            boolean up = action == UP || keyCode == KEY_NUM2;
            boolean down = action == DOWN || keyCode == KEY_NUM8;
            boolean leftKey = action == LEFT || keyCode == KEY_NUM4;
            boolean rightKey = action == RIGHT || keyCode == KEY_NUM6;
            boolean fire = action == FIRE || keyCode == KEY_NUM5;
            if (configMode) {
                if (up) configSelection = (configSelection + 2) % 3;
                else if (down) configSelection = (configSelection + 1) % 3;
                else if ((leftKey || rightKey) && configSelection < 2)
                    changeCameraSetting(rightKey ? 1 : -1);
                else if (fire) {
                    if (configSelection == 2) configMode = false;
                    else changeCameraSetting(1);
                }
                return;
            }
            if (up) pauseSelection = (pauseSelection + 3) % 4;
            else if (down) pauseSelection = (pauseSelection + 1) % 4;
            else if (fire) {
                if (pauseSelection == 0) {
                    paused = false;
                } else if (pauseSelection == 1) {
                    configMode = true;
                    configSelection = 0;
                } else if (pauseSelection == 2) {
                    try { startGame(currentGame); }
                    catch (Throwable t) { initError = "Restart: " + t.toString(); }
                } else {
                    exitToGameMenu();
                }
            }
        }

        private void drawPauseMenu(Graphics g) {
            int width = getWidth(), height = getHeight();
            if (pauseOverlayPixels == null || pauseOverlayWidth != width || pauseOverlayHeight != height) {
                pauseOverlayWidth = width; pauseOverlayHeight = height;
                pauseOverlayPixels = new int[width * height];
                for (int i = 0; i < pauseOverlayPixels.length; i++) pauseOverlayPixels[i] = 0x9908121E;
            }
            int panelWidth = Math.min(260, Math.max(1, width - 16));
            int rowHeight = Math.max(22, g.getFont().getHeight() + 8);
            int optionCount = configMode ? 3 : 4;
            int panelHeight = Math.min(height - 8, 50 + optionCount * rowHeight + 32);
            int panelX = (width - panelWidth) / 2, panelY = (height - panelHeight) / 2;
            g.drawRGB(pauseOverlayPixels, 0, width, 0, 0, width, height, true);
            g.setColor(0x1A2D40);
            g.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 14, 14);
            g.setColor(0xFFFFFF);
            g.drawString(configMode ? "CONFIGURATION" : "PAUSED", width / 2,
                    panelY + 10, Graphics.TOP | Graphics.HCENTER);
            String[] options;
            int selected;
            if (configMode) {
                String speed = sensitivityLevel == 0 ? "Low" : (sensitivityLevel == 1 ? "Normal" : "High");
                options = new String[] {
                    "Camera speed: " + speed,
                    "Invert camera: " + (invertCamera ? "On" : "Off"),
                    "Back"
                };
                selected = configSelection;
            } else {
                options = new String[] {"Resume", "Configure", "Restart", "Exit to game menu"};
                selected = pauseSelection;
            }
            int firstY = panelY + 38;
            int buttonX = panelX + 8, buttonWidth = panelWidth - 16;
            for (int i = 0; i < options.length; i++) {
                int y = firstY + i * rowHeight;
                if (y + rowHeight > panelY + panelHeight - 16) break;
                g.setColor(i == selected ? 0xF5C542 : 0x263B50);
                g.fillRoundRect(buttonX, y, buttonWidth, rowHeight - 2, 8, 8);
                g.setColor(i == selected ? 0x17202A : 0xFFFFFF);
                g.drawString(options[i], width / 2, y + 2,
                        Graphics.TOP | Graphics.HCENTER);
            }
            g.setColor(0xFFFFFF);
            int footerY = panelY + panelHeight - 2 * g.getFont().getHeight() - 8;
            g.drawString(configMode ? "LEFT/RIGHT: change" : "UP/DOWN: select  FIRE: confirm",
                    width / 2, footerY, Graphics.TOP | Graphics.HCENTER);
            g.drawString(configMode ? "FIRE: select  RIGHT: back" : "RIGHT SOFTKEY: resume",
                    width / 2, footerY + g.getFont().getHeight(),
                    Graphics.TOP | Graphics.HCENTER);
        }

        private void handleStudioInput(int keyCode, int action) {
            if (keyCode == KEY_NUM7 || keyCode == KEY_NUM9) {
                if (studioObjectCount == 0) {
                    studioPalette = (studioPalette +
                            (keyCode == KEY_NUM9 ? 1 : STUDIO_TYPE_NAMES.length - 1)) %
                            STUDIO_TYPE_NAMES.length;
                    modelStatus = "Studio palette: " + STUDIO_TYPE_NAMES[studioPalette];
                } else {
                    if (studioSelected < 0) studioSelected =
                            keyCode == KEY_NUM9 ? 0 : studioObjectCount - 1;
                    else studioSelected = (studioSelected +
                            (keyCode == KEY_NUM9 ? 1 : studioObjectCount - 1)) % studioObjectCount;
                    modelStatus = "Studio object " + (studioSelected + 1);
                }
            } else if (keyCode == KEY_NUM5 || action == FIRE) {
                addStudioObject();
            } else if (keyCode == KEY_NUM2 || keyCode == KEY_NUM4 ||
                    keyCode == KEY_NUM6 || keyCode == KEY_NUM8) {
                moveStudioObject(keyCode);
            } else if (keyCode == KEY_STAR) {
                studioProperty = (studioProperty + 1) % STUDIO_PROPERTY_NAMES.length;
            } else if (keyCode == KEY_NUM1) {
                adjustStudioProperty(-1);
            } else if (keyCode == KEY_POUND) {
                adjustStudioProperty(1);
            } else if (keyCode == KEY_NUM0) {
                deleteStudioObject();
            } else if (action == LEFT) camLeft = true;
            else if (action == RIGHT) camRight = true;
            else if (action == UP) camUp = true;
            else if (action == DOWN) camDown = true;
        }

        private void addStudioObject() {
            if (studioObjectCount >= studioObjects.length) {
                modelStatus = "Studio full - delete an object first";
                return;
            }
            int type = studioPalette;
            float sx=2.0f, sy=2.0f, sz=2.0f;
            int color = STUDIO_COLORS[type % STUDIO_COLORS.length];
            if (type == 1) { sx=4.0f; sy=0.5f; sz=4.0f; }
            else if (type == 2) { sx=2.0f; sy=0.5f; sz=2.0f; }
            else if (type == 3) { sx=1.2f; sy=3.0f; sz=1.2f; }
            else if (type == 4) { sx=2.0f; sy=0.25f; sz=2.0f; }
            int column = studioObjectCount % 5;
            int row = studioObjectCount / 5;
            float x = (column - 2) * 2.6f;
            float z = -2.0f - row * 2.6f;
            float y = sy * 0.5f;
            StudioObject object = new StudioObject(type,x,y,z,sx,sy,sz,color);
            studioObjects[studioObjectCount] = object;
            object.node = makeBox(x,y,z,sx,sy,sz,color);
            world.addChild(object.node);
            studioSelected = studioObjectCount++;
            studioPalette = (studioPalette + 1) % STUDIO_TYPE_NAMES.length;
            modelStatus = "Studio: added " + STUDIO_TYPE_NAMES[type];
        }

        private void moveStudioObject(int keyCode) {
            StudioObject object = getSelectedStudioObject();
            if (object == null) {
                modelStatus = "Studio: add an object with 5";
                return;
            }
            float step = 0.5f;
            if (keyCode == KEY_NUM2) object.z -= step;
            else if (keyCode == KEY_NUM8) object.z += step;
            else if (keyCode == KEY_NUM4) object.x -= step;
            else if (keyCode == KEY_NUM6) object.x += step;
            rebuildStudioObject(object);
        }

        private StudioObject getSelectedStudioObject() {
            return studioSelected >= 0 && studioSelected < studioObjectCount ?
                    studioObjects[studioSelected] : null;
        }

        private void adjustStudioProperty(int direction) {
            StudioObject object = getSelectedStudioObject();
            if (object == null) {
                studioPalette = (studioPalette + direction + STUDIO_TYPE_NAMES.length) %
                        STUDIO_TYPE_NAMES.length;
                modelStatus = "Studio palette: " + STUDIO_TYPE_NAMES[studioPalette];
                return;
            }
            float step = 0.25f * direction;
            switch (studioProperty) {
                case 0: object.x += step; break;
                case 1: object.y += step; break;
                case 2: object.z += step; break;
                case 3: object.sx = clampStudioSize(object.sx + step); break;
                case 4: object.sy = clampStudioSize(object.sy + step); break;
                case 5: object.sz = clampStudioSize(object.sz + step); break;
                default:
                    int colorIndex = 0;
                    for (int i=0; i<STUDIO_COLORS.length; i++)
                        if (STUDIO_COLORS[i] == object.color) colorIndex = i;
                    colorIndex = (colorIndex + direction + STUDIO_COLORS.length) %
                            STUDIO_COLORS.length;
                    object.color = STUDIO_COLORS[colorIndex];
                    break;
            }
            rebuildStudioObject(object);
        }

        private float clampStudioSize(float size) {
            if (size < 0.25f) return 0.25f;
            if (size > 16.0f) return 16.0f;
            return size;
        }

        private void rebuildStudioObject(StudioObject object) {
            if (object.node != null) {
                try { world.removeChild(object.node); } catch (Throwable ignored) {}
            }
            object.node = makeBox(object.x,object.y,object.z,
                    object.sx,object.sy,object.sz,object.color);
            world.addChild(object.node);
            modelStatus = "Studio object " + (studioSelected + 1) +
                    ": " + STUDIO_PROPERTY_NAMES[studioProperty];
        }

        private void deleteStudioObject() {
            if (studioSelected < 0 || studioSelected >= studioObjectCount) return;
            StudioObject object = studioObjects[studioSelected];
            try { world.removeChild(object.node); } catch (Throwable ignored) {}
            for (int i=studioSelected; i<studioObjectCount-1; i++)
                studioObjects[i] = studioObjects[i+1];
            studioObjects[--studioObjectCount] = null;
            if (studioObjectCount == 0) studioSelected = -1;
            else if (studioSelected >= studioObjectCount) studioSelected = studioObjectCount - 1;
            modelStatus = "Studio: object deleted";
        }

        private void syncStudioNodes() {
            for (int i=0; i<studioObjectCount; i++) {
                StudioObject object = studioObjects[i];
                if (object == null) continue;
                object.node = makeBox(object.x,object.y,object.z,
                        object.sx,object.sy,object.sz,object.color);
                world.addChild(object.node);
            }
        }

        private void toggleStudioTest() {
            studioTesting = !studioTesting;
            clearInput();
            if (studioTesting) {
                resetPlayerSpawn();
                if (avatar != null) avatar.setTranslation(px,jumpY,pz);
                invulnerableUntil = gameClock + 1200;
                modelStatus = "Studio - TESTING (3 returns to edit)";
            } else {
                modelStatus = "Studio - Edit mode";
            }
        }

        private boolean isTouchingStudioHazard() {
            if (!studioTesting) return false;
            for (int i=0; i<studioObjectCount; i++) {
                StudioObject object = studioObjects[i];
                if (object == null || object.type != 2) continue;
                boolean vertical = jumpY <= object.y + object.sy * 0.5f + 0.03f &&
                        jumpY + 1.9f > object.y - object.sy * 0.5f;
                if (vertical && Math.abs(px-object.x) < object.sx*0.5f + 0.25f &&
                        Math.abs(pz-object.z) < object.sz*0.5f + 0.25f) return true;
            }
            return false;
        }

        private void drawStudioHud(Graphics g) {
            int line = g.getFont().getHeight();
            int panelHeight = line * 5 + 6;
            int top = getHeight() - panelHeight;
            g.setColor(0x102030);
            g.fillRect(0, top, getWidth(), panelHeight);
            g.setColor(0xFFFFFF);
            StudioObject object = getSelectedStudioObject();
            String first = studioTesting ? "STUDIO TEST - 3: EDIT" :
                    "STUDIO - " + (object == null ? "No object" :
                    STUDIO_TYPE_NAMES[object.type] + " " + (studioSelected + 1) +
                    "/" + studioObjectCount);
            String second = object == null ? "Palette: " + STUDIO_TYPE_NAMES[studioPalette] :
                    STUDIO_PROPERTY_NAMES[studioProperty] + ": " +
                    studioPropertyValue(object);
            g.drawString(first, 3, top + 1, Graphics.TOP | Graphics.LEFT);
            g.drawString(second, 3, top + line + 1, Graphics.TOP | Graphics.LEFT);
            g.drawString("7/9 select  5 add  2/4/6/8 move", 3, top + line*2 + 1,
                    Graphics.TOP | Graphics.LEFT);
            g.drawString("* property  1/# edit  0 delete", 3, top + line*3 + 1,
                    Graphics.TOP | Graphics.LEFT);
            g.drawString("D-pad camera  3 test  Softkey pause", 3, top + line*4 + 1,
                    Graphics.TOP | Graphics.LEFT);
        }

        private String studioPropertyValue(StudioObject object) {
            switch (studioProperty) {
                case 0: return "" + object.x;
                case 1: return "" + object.y;
                case 2: return "" + object.z;
                case 3: return "" + object.sx;
                case 4: return "" + object.sy;
                case 5: return "" + object.sz;
                default: return "0x" + Integer.toHexString(object.color);
            }
        }

        protected void keyPressed(int keyCode) {
            int k = getGameAction(keyCode);
            if (menuMode) {
                int pageSize = getHeight() < 220 ? 4 : 5;
                int pageCount = (GAME_NAMES.length + pageSize - 1) / pageSize;
                int page = selectedGame / pageSize;
                int row = selectedGame % pageSize;
                if (keyCode == KEY_NUM7) {
                    page = (page + pageCount - 1) % pageCount;
                    selectedGame = Math.min(page * pageSize + row, GAME_NAMES.length - 1);
                } else if (keyCode == KEY_NUM9) {
                    page = (page + 1) % pageCount;
                    selectedGame = Math.min(page * pageSize + row, GAME_NAMES.length - 1);
                } else if (k == UP || keyCode == KEY_NUM2)
                    selectedGame = (selectedGame + GAME_NAMES.length - 1) % GAME_NAMES.length;
                else if (k == DOWN || keyCode == KEY_NUM8)
                    selectedGame = (selectedGame + 1) % GAME_NAMES.length;
                else if (keyCode == KEY_NUM5 || k == FIRE) {
                    try {
                        initError = "";
                        startGame(selectedGame);
                    } catch (Throwable t) {
                        menuMode = true;
                        loaded = false;
                        initError = "Game: " + t.toString();
                    }
                }
                repaint();
                return;
            }
            // On some devices key 3 is reported with GAME_B, which is also used
            // by the right softkey. In Horde and Studio, keep numeric 3 reserved for its game action.
            boolean reservedThree = (currentGame == 2 || currentGame == 3 ||
                    currentGame == 10 || currentGame == 13 || currentGame == 14) &&
                    keyCode == KEY_NUM3;
            if (!reservedThree && isRightSoftkey(keyCode, k)) {
                if (paused) { paused = false; configMode = false; }
                else openPauseMenu();
                repaint();
                return;
            }
            if (paused) {
                handlePauseInput(keyCode, k);
                repaint();
                return;
            }
            if (currentGame == 3 && keyCode == KEY_NUM3) {
                toggleStudioTest();
                repaint();
                return;
            }
            if (currentGame == 3 && !studioTesting) {
                handleStudioInput(keyCode, k);
                repaint();
                return;
            }
            if (currentGame == 3 && studioTesting && keyCode == KEY_NUM0) {
                exitToGameMenu();
                repaint();
                return;
            }
            if (keyCode == KEY_NUM0) {
                exitToGameMenu();
                repaint();
                return;
            }
            if (currentGame == 2 &&
                    (keyCode == KEY_NUM7 || keyCode == KEY_NUM9)) {
                inventorySelection = (inventorySelection +
                        (keyCode == KEY_NUM9 ? 1 : 2)) % 3;
                repaint();
                return;
            }
            if (currentGame == 2 && keyCode == KEY_NUM3) {
                useInventoryItem();
                repaint();
                return;
            }
            if ((currentGame == 10 || currentGame == 14) && keyCode == KEY_NUM3) {
                useArenaSword();
                repaint();
                return;
            }
            if (currentGame == 13 && keyCode == KEY_NUM3) {
                useTycoonAction();
                repaint();
                return;
            }
            if (keyCode == KEY_NUM2) forward = true;
            else if (keyCode == KEY_NUM8) back = true;
            else if (keyCode == KEY_NUM4) left = true;
            else if (keyCode == KEY_NUM6) right = true;
            else if (k == LEFT) camLeft = true;
            else if (k == RIGHT) camRight = true;
            else if (k == UP) camUp = true;
            else if (k == DOWN) camDown = true;
            else if ((keyCode == KEY_NUM5 || k == FIRE) && !jumping) {
                if (grounded) {
                    grounded = false;
                    playerVelocityY = 0.0075f;
                    jumping = true;
                }
                animation = 2;
                animateNoob();
            }
            else if (keyCode == KEY_STAR || keyCode == KEY_POUND) {
                camDistance += keyCode == KEY_STAR ? -0.5f : 0.5f;
                if (camDistance < 2) camDistance=2;
                if (camDistance > 9) camDistance=9;
            }
        }
        protected void keyReleased(int keyCode) {
            int k = getGameAction(keyCode);
            if (keyCode == KEY_NUM2) forward = false;
            else if (keyCode == KEY_NUM8) back = false;
            else if (keyCode == KEY_NUM4) left = false;
            else if (keyCode == KEY_NUM6) right = false;
            else if (k == LEFT) camLeft = false;
            else if (k == RIGHT) camRight = false;
            else if (k == UP) camUp = false;
            else if (k == DOWN) camDown = false;
        }
    }
}