package meblox;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.m3g.*;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.rms.RecordStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;

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

    private static final class Game extends Canvas implements Runnable, CommandListener {
        private final main midlet;
        private Thread thread;
        private Player musicPlayer;
        private java.io.InputStream musicInput;
        private int musicTrack=-1;
        private int[] gameMusicOrder = {1,2,3};
        private int gameMusicIndex=3, lastGameMusicTrack;
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
        // 0 = random 3-5, 1-5 = exact count, 6 = no bots.
        private int botCountSetting;
        private static final int BOT_COUNT_RANDOM=0, BOT_COUNT_OFF=6;
        private static final String SETTINGS_STORE="Roblox3DMESettings";
        private int currentGame;
        private boolean paused, configMode;
        private int pauseSelection, configSelection;
        private boolean menuSettings, controlMapMode, awaitingControlKey;
        private boolean lanMenu;
        private int lanMenuSelection;
        private String lanMessage="";
        private LanSession lanSession;
        private volatile LanSession pendingLanSession;
        private volatile int pendingLanGame=-1;
        private volatile String pendingLanError;
        private LanSession.PlayerState[] lanPlayers;
        private Group[] lanAvatars = new Group[5];
        private TextBox lanAddressBox;
        private final Command lanConnectCommand=new Command("Connect",Command.OK,1);
        private final Command lanCancelCommand=new Command("Cancel",Command.CANCEL,2);
        private int controlSelection;
        private String settingsMessage = "";
        private static final int CONTROL_FORWARD=0, CONTROL_BACK=1, CONTROL_LEFT=2,
                CONTROL_RIGHT=3, CONTROL_JUMP=4, CONTROL_USE=5, CONTROL_PREVIOUS=6,
                CONTROL_NEXT=7;
        private static final String[] CONTROL_NAMES = {
            "Move forward", "Move back", "Move left", "Move right",
            "Jump", "Use item / build", "Previous item", "Next item"
        };
        private int[] mappedControls = {
            KEY_NUM2, KEY_NUM8, KEY_NUM4, KEY_NUM6, KEY_NUM5,
            KEY_NUM3, KEY_NUM7, KEY_NUM9
        };
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
        private static final String[] GAME_NAMES = LevelCatalog.names();
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
        private Texture2D cachedPlayerFaceTexture, cachedZombieFaceTexture;
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
        private int playerHealth=100;
        private boolean grounded = true, dead, lobbyMode;
        private long gameClock, respawnAt, invulnerableUntil;
        private PhysicsPiece[] deathPieces;
        private Zombie[] zombies = new Zombie[12];
        private PlayerBot[] playerBots = new PlayerBot[5];
        private int playerBotCount, randomState;
        private String botDialogue="";
        private long botDialogueUntil,nextBotDialogueAt;
        private int talkingBot=-1;
        private int projectedScreenX,projectedScreenY;
        private WorldObject[] worldObjects = new WorldObject[80];
        private MeteorBurst[] meteorBursts = new MeteorBurst[4];
        private Group protectionAura;
        private Material protectionAuraMaterial;
        private String modelStatus = "Select a game";
        private String modelError = "";
        private String initError = "";

        private static final int OBBY_PLATFORM_COUNT = 81;
        private static final float[][] OBBY_BLOCKS = new ObbyLevel().blocks();

        private static final float[][] ZOMBIE_BLOCKS = new ZombieHordeLevel().blocks();

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
            boolean alive = true, hidden, fleeing;
            Zombie(Group model, float x, float z) {
                this.model = model; this.x = x; this.z = z;
            }
        }

        private static final String[] BOT_DIALOGUES = {
            "Vamos por aqui.",
            "Buena partida!",
            "Creo que vi un zombie.",
            "Necesito una espada.",
            "Salta, puedes hacerlo!",
            "Por aqui parece seguro.",
            "Uy, casi me caigo!",
            "A defender la base!",
            "Te cubro!",
            "Voy por ese enemigo.",
            "Esa bomba va a explotar!",
            "Mejor sigamos moviendonos.",
            "El obby esta larguisimo.",
            "Creo que encontre un camino.",
            "Eso estuvo cerca!",
            "Ya reapareci, listo!",
            "Vamos equipo!",
            "Nos vemos en la meta.",
            "Alguien vio mi pala?",
            "No voy a caer otra vez!"
        };
        private static final float[] HIDE_FLEE_COS={1.0f,0.707107f,0.707107f,0.0f,0.0f,-0.707107f,-0.707107f,-1.0f};
        private static final float[] HIDE_FLEE_SIN={0.0f,0.707107f,-0.707107f,1.0f,-1.0f,0.707107f,-0.707107f,0.0f};
        private static final float[] HIDE_SPAWN_X={-15.65f,15.65f,-15.65f,15.65f,-1.25f};
        private static final float[] HIDE_SPAWN_Z={-13.15f,-13.15f,14.35f,14.35f,-17.15f};
        private static final float TYCOON_PLAYER_X=0.0f,TYCOON_PLAYER_Z=-7.0f;
        private static final float[] TYCOON_BOT_X={-7.0f,0.0f,7.0f};
        private static final float[] TYCOON_BOT_Z={6.0f,7.0f,6.0f};
        private static final float[] SIMON_PAD_X={-4.0f,4.0f,-4.0f,4.0f};
        private static final float[] SIMON_PAD_Z={-4.0f,-4.0f,4.0f,4.0f};
        private static final int[] BOT_SKIN_COLORS =
                {0xF5C542,0xC68642,0xF1C27D,0x8D5524,0xE0AC69,0xD9A066};
        private static final int[] BOT_SHIRT_COLORS =
                {0x1769AA,0xC43C35,0x7F58A5,0x2C8C65,0x303F9F,0xD87C22,0x444444};
        private static final int[] BOT_LEG_COLORS =
                {0x2E9E45,0x333333,0x365E9D,0x6D4635,0x6B3E76,0xB8A23A};

        private static final class PlayerBot {
            Group model, aura;
            float x,y,z,spawnX,spawnZ,targetX,targetZ,velocityY,yaw;
            PhysicsPiece[] deathPieces;
            long nextThink,nextJump,nextAttack,respawnAt,invulnerableUntil,auraUntil;
            int skinColor, shirtColor, legColor, health=100, simonRoundSeen;
            int tycoonLevel,tycoonCash;
            long simonMoveAt,tycoonAt,tycoonIncomeAt;
            boolean simonMoving,alive=true,grounded=true;
            Mesh swordArm, leftArm, rightArm, leftLeg, rightLeg;
            Group sword;
            long attackUntil;
            PlayerBot(float x,float z) {
                this.x=x; this.z=z; spawnX=x; spawnZ=z; targetX=x; targetZ=z;
            }
        }

        private static final class WorldObject {
            PhysicsPiece body;
            boolean bomb, botOwned, meteor, active = true;
            Group fireTrail;
            long explodeAt, removeAt;
            WorldObject(PhysicsPiece body, boolean bomb, long explodeAt, long removeAt) {
                this.body = body; this.bomb = bomb;
                this.explodeAt = explodeAt; this.removeAt = removeAt;
            }
        }

        private static final class MeteorBurst {
            Group model;
            Mesh outer, inner;
            long removeAt;
            MeteorBurst(Group model,Mesh outer,Mesh inner,long removeAt) {
                this.model=model;this.outer=outer;this.inner=inner;this.removeAt=removeAt;
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
            loadSettings();
            setFullScreenMode(true);
        }

        private void loadSettings() {
            RecordStore store=null;
            DataInputStream input=null;
            try {
                store=RecordStore.openRecordStore(SETTINGS_STORE,true);
                if(store.getNumRecords()<1)return;
                byte[] data=store.getRecord(1);
                input=new DataInputStream(new ByteArrayInputStream(data));
                int version=input.readInt();
                if(version!=1)return;
                int loadedSensitivity=input.readInt();
                boolean loadedInvert=input.readBoolean();
                float loadedDistance=input.readFloat();
                int loadedBots=input.readInt();
                int controlCount=input.readInt();
                if(loadedSensitivity<0||loadedSensitivity>2||
                        loadedDistance<2.0f||loadedDistance>9.0f||
                        loadedBots<BOT_COUNT_RANDOM||loadedBots>BOT_COUNT_OFF||
                        controlCount!=mappedControls.length)return;
                int[] loadedControls=new int[controlCount];
                for(int i=0;i<controlCount;i++)loadedControls[i]=input.readInt();
                for(int i=0;i<loadedControls.length;i++) {
                    if(loadedControls[i]<KEY_NUM1||loadedControls[i]>KEY_NUM9)return;
                    for(int j=0;j<i;j++)if(loadedControls[i]==loadedControls[j])return;
                }
                sensitivityLevel=loadedSensitivity;
                cameraSensitivity=sensitivityLevel==0?0.0015f:
                        (sensitivityLevel==1?0.0025f:0.0040f);
                invertCamera=loadedInvert;
                camDistance=loadedDistance;
                botCountSetting=loadedBots;
                for(int i=0;i<mappedControls.length;i++)mappedControls[i]=loadedControls[i];
            } catch(Throwable ignored) {
                // Keep safe defaults when RMS is unavailable or the record is corrupt.
            } finally {
                if(input!=null)try{input.close();}catch(Throwable ignored){}
                if(store!=null)try{store.closeRecordStore();}catch(Throwable ignored){}
            }
        }

        private boolean saveSettings() {
            RecordStore store=null;
            ByteArrayOutputStream bytes=null;
            DataOutputStream output=null;
            try {
                bytes=new ByteArrayOutputStream();
                output=new DataOutputStream(bytes);
                output.writeInt(1);
                output.writeInt(sensitivityLevel);
                output.writeBoolean(invertCamera);
                output.writeFloat(camDistance);
                output.writeInt(botCountSetting);
                output.writeInt(mappedControls.length);
                for(int i=0;i<mappedControls.length;i++)output.writeInt(mappedControls[i]);
                output.flush();
                byte[] data=bytes.toByteArray();
                store=RecordStore.openRecordStore(SETTINGS_STORE,true);
                if(store.getNumRecords()<1)store.addRecord(data,0,data.length);
                else store.setRecord(1,data,0,data.length);
                return true;
            } catch(Throwable t) {
                settingsMessage="Settings could not be saved";
                return false;
            } finally {
                if(output!=null)try{output.close();}catch(Throwable ignored){}
                if(bytes!=null)try{bytes.close();}catch(Throwable ignored){}
                if(store!=null)try{store.closeRecordStore();}catch(Throwable ignored){}
            }
        }

        public void commandAction(Command command, Displayable displayable) {
            if (command == lanCancelCommand) {
                Display.getDisplay(midlet).setCurrent(this);
                lanMenu = true;
                repaint();
                return;
            }
            if (command == lanConnectCommand && lanAddressBox != null) {
                final String address = lanAddressBox.getString();
                Display.getDisplay(midlet).setCurrent(this);
                lanMenu = false;
                lanMessage = "Connecting to " + address + "...";
                final Game self = this;
                Thread connect = new Thread(new Runnable() {
                    public void run() {
                        try {
                            LanSession session = LanSession.join(address);
                            pendingLanSession = session;
                            pendingLanGame = session.getGameId();
                        } catch (Throwable t) {
                            pendingLanError = "LAN: " + t.toString();
                        }
                        self.repaint();
                    }
                });
                connect.start();
            }
        }
        synchronized void start() {
            if (running) return;
            running = true;
            thread = new Thread(this);
            thread.start();
        }
        synchronized void stop() {
            running = false;
            thread = null;
            stopMusic();
        }

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
            playMusic(3);
        }

        private synchronized void playMusic(int track) {
            if(track<1||track>3)return;
            if(musicTrack==track&&musicPlayer!=null)return;
            stopMusic();
            java.io.InputStream in=null;
            try {
                String resource="/meblox/R"+track+".mid";
                in=getClass().getResourceAsStream(resource);
                if(in==null)in=midlet.getClass().getResourceAsStream(resource);
                if(in==null) {
                    resource="/R"+track+".mid";
                    in=getClass().getResourceAsStream(resource);
                    if(in==null)in=midlet.getClass().getResourceAsStream(resource);
                }
                if(in==null)throw new java.io.IOException("Missing /meblox/R"+track+
                        ".mid and /R"+track+".mid");
                Player next=Manager.createPlayer(in,"audio/midi");
                next.realize();
                next.setLoopCount(-1);
                next.prefetch();
                next.start();
                musicInput=in;
                musicPlayer=next;
                musicTrack=track;
            } catch(Throwable t) {
                if(in!=null)try{in.close();}catch(Throwable ignored){}
                musicPlayer=null;
                musicInput=null;
                musicTrack=-1;
                modelError="Music R"+track+": "+t.toString();
            }
        }

        private synchronized void stopMusic() {
            Player old=musicPlayer;
            musicPlayer=null;
            if(old!=null) {
                try{old.stop();}catch(Throwable ignored){}
                try{old.close();}catch(Throwable ignored){}
            }
            java.io.InputStream in=musicInput;
            musicInput=null;
            if(in!=null)try{in.close();}catch(Throwable ignored){}
            musicTrack=-1;
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
            lanMenu = false;
            currentGame = game;
            if (game == 3) studioTesting = false;
            menuMode = false;
            paused = false;
            configMode = false;
            menuSettings = false;
            controlMapMode = false;
            awaitingControlKey = false;
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
            playerBots = new PlayerBot[5]; playerBotCount = 0;
            randomState = (int)(System.currentTimeMillis() ^ (game * 7919L));
            worldObjects = new WorldObject[80];
            grounded = true;
            dead = false;
            playerHealth=100;
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
            modeCash=25; tycoonLevel=0; simonTarget=0; simonRound=0;
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
            // Obby, falling tiles, and their time trial course should float over the void.
            if (game != 0 && game != 4 && game != 6 && game != 7)
                world.addChild(makeBox(0, -0.25f, 0,
                        game == 11 ? 44 : 24, 0.5f, game == 11 ? 44 : 24, 0x54A64B));
            meteorBursts = new MeteorBurst[4];
            if (game == 0) {
                // An 80-platform zigzag course ending on an enlarged green finish.
                for (int i = 0; i < OBBY_BLOCKS.length; i++) {
                    float[] b = OBBY_BLOCKS[i];
                    world.addChild(makeBox(b[0],b[1],b[2],b[3],b[4],b[5],(int)b[6]));
                }
            } else if (game == 1) {
                // Red touch-to-die test block in the baseplate lobby.
                float[] hazard=new BaseplateLevel().blocks()[0];
                world.addChild(makeBox(hazard[0],hazard[1],hazard[2],hazard[3],hazard[4],hazard[5],(int)hazard[6]));
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
                if(game==13){px=TYCOON_PLAYER_X;pz=TYCOON_PLAYER_Z;jumpY=0;}
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
            spawnPlayerBots();
            playMusic(chooseGameMusicTrack());
        }

        private int chooseGameMusicTrack() {
            if (gameMusicIndex >= gameMusicOrder.length) {
                for (int i = 0; i < gameMusicOrder.length; i++) gameMusicOrder[i] = i + 1;
                for (int i = gameMusicOrder.length - 1; i > 0; i--) {
                    int j = nextRandom(i + 1);
                    int swap = gameMusicOrder[i];
                    gameMusicOrder[i] = gameMusicOrder[j];
                    gameMusicOrder[j] = swap;
                }
                if (gameMusicOrder[0] == lastGameMusicTrack) {
                    int swap = gameMusicOrder[0];
                    gameMusicOrder[0] = gameMusicOrder[1];
                    gameMusicOrder[1] = swap;
                }
                gameMusicIndex = 0;
            }
            lastGameMusicTrack = gameMusicOrder[gameMusicIndex++];
            return lastGameMusicTrack;
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
            GameLevel level = LevelCatalog.get(game);
            float[][] blocks = level.blocks();
            for (int i = 0; i < blocks.length; i++) {
                float[] b = blocks[i];
                addModeBlock(b[0],b[1],b[2],b[3],b[4],b[5],(int)b[6]);
            }
            if (game == 4) {
                lavaNode=makeBox(0,lavaHeight-0.12f,0,24,0.24f,24,0xF04B25);
                world.addChild(lavaNode);
            } else if (game == 8) {
                for (int i=0;i<12;i++) {
                    float x=-9.0f+(i%4)*6.0f, z=-8.0f+(i/4)*7.5f;
                    addToken(x,0.55f,z);
                }
                modeGoal=tokenCount;
            }
        }

        private int nextRandom(int limit) {
            randomState=randomState*1103515245+12345;
            return limit<=0?0:((randomState>>>8)&0x7fffffff)%limit;
        }
        private int configuredBotCount(int maximum) {
            if(botCountSetting==BOT_COUNT_OFF)return 0;
            if(botCountSetting==BOT_COUNT_RANDOM)
                return Math.min(maximum,3+nextRandom(3));
            return Math.min(maximum,botCountSetting);
        }
        private void spawnPlayerBots() {
            if(currentGame==11){playerBotCount=0;return;}
            playerBotCount=configuredBotCount(currentGame==13?3:5);
            botDialogue="";
            talkingBot=-1;
            botDialogueUntil=0;
            nextBotDialogueAt=gameClock+1400+nextRandom(2400);
            int skinStart=nextRandom(BOT_SKIN_COLORS.length);
            int shirtStart=nextRandom(BOT_SHIRT_COLORS.length);
            int legStart=nextRandom(BOT_LEG_COLORS.length);
            for(int i=0;i<playerBotCount;i++) {
                float x,z;
                if(currentGame==13){
                    x=TYCOON_BOT_X[i];z=TYCOON_BOT_Z[i];
                }else if((currentGame==0||currentGame==7)&&OBBY_BLOCKS.length>0){
                    x=OBBY_BLOCKS[0][0]+(i-2)*0.48f;z=OBBY_BLOCKS[0][2]+1.0f;
                }else if((currentGame==4||currentGame==6)&&modeBlockCount>0){
                    x=modeBlocks[0][0]+(i-2)*0.42f;z=modeBlocks[0][2]+0.85f;
                }else{x=(i-2)*1.25f;z=2.0f+(i%2);}
                PlayerBot bot=new PlayerBot(x,z);
                bot.skinColor=BOT_SKIN_COLORS[(i+skinStart)%BOT_SKIN_COLORS.length];
                bot.shirtColor=BOT_SHIRT_COLORS[(i+shirtStart)%BOT_SHIRT_COLORS.length];
                bot.legColor=BOT_LEG_COLORS[(i+legStart)%BOT_LEG_COLORS.length];
                bot.y=botGroundTop(x,z);
                if(currentGame==13){
                    bot.tycoonCash=12;
                    bot.tycoonAt=gameClock+4000+nextRandom(3000);
                    bot.tycoonIncomeAt=gameClock+2200;
                    bot.nextThink=gameClock+500+nextRandom(1000);
                }
                playerBots[i]=bot;addPlayerBotModel(bot,true);
            }
        }
        private void addPlayerBotModel(PlayerBot bot,boolean protectedSpawn) {
            try{
                Mesh l=leftArm,r=rightArm,ll=leftLeg,rl=rightLeg;Group sword=swordVisual;
                bot.model=buildBotNoob(bot);
                bot.leftArm=leftArm;bot.rightArm=rightArm;bot.leftLeg=leftLeg;bot.rightLeg=rightLeg;
                bot.swordArm=rightArm;bot.sword=swordVisual;
                leftArm=l;rightArm=r;leftLeg=ll;rightLeg=rl;swordVisual=sword;
                if(bot.sword!=null)bot.sword.setRenderingEnable(false);
                bot.model.setTranslation(bot.x,bot.y,bot.z);world.addChild(bot.model);
                bot.alive=true;bot.health=100;
                if(protectedSpawn){bot.aura=makeBotAura();bot.model.addChild(bot.aura);
                    bot.invulnerableUntil=gameClock+4000;bot.auraUntil=bot.invulnerableUntil;}
            }catch(Throwable t){
                bot.model=null;bot.aura=null;bot.alive=false;bot.respawnAt=gameClock+10000;
                initError="Player bot: "+t.toString();
            }
        }
        private Group buildBotNoob(PlayerBot bot) {
            Group model=new Group();
            model.addChild(makeBox(0,1.45f,0,0.78f,0.95f,0.40f,bot.shirtColor));
            model.addChild(makeRoundedCylinderHead(bot.skinColor));
            model.addChild(makeTexturedFace("faceNEW.png",bot.skinColor));
            Mesh la=(Mesh)makeBox(-0.54f,1.48f,0,0.30f,0.88f,0.40f,bot.skinColor);
            Mesh ra=(Mesh)makeBox(0.54f,1.48f,0,0.30f,0.88f,0.40f,bot.skinColor);
            Mesh ll=(Mesh)makeBox(-0.20f,0.52f,0,0.36f,0.78f,0.40f,bot.legColor);
            Mesh rl=(Mesh)makeBox(0.20f,0.52f,0,0.36f,0.78f,0.40f,bot.legColor);
            model.addChild(la);model.addChild(ra);model.addChild(ll);model.addChild(rl);
            leftArm=la;rightArm=ra;leftLeg=ll;rightLeg=rl;
            Group weapon=new Group();
            weapon.addChild(makeBox(0,0.39f,0,0.12f,0.66f,0.06f,0xDCE9F2));
            weapon.addChild(makeBox(0,0.03f,0,0.32f,0.07f,0.10f,0xB68A42));
            weapon.addChild(makeBox(0,-0.10f,0,0.10f,0.22f,0.10f,0x654321));
            weapon.setTranslation(0.56f,1.02f,-0.08f);
            weapon.setRenderingEnable(false);model.addChild(weapon);swordVisual=weapon;
            return model;
        }
        private void killPlayerBot(PlayerBot bot) {
            if(bot==null||!bot.alive||gameClock<bot.invulnerableUntil)return;
            bot.health-=35;
            if(bot.health>0){bot.invulnerableUntil=gameClock+850;return;}
            bot.health=0;
            bot.alive=false;bot.grounded=false;bot.respawnAt=gameClock+2600;
            if(talkingBot>=0&&talkingBot<playerBotCount&&playerBots[talkingBot]==bot){
                botDialogue="";talkingBot=-1;botDialogueUntil=0;
            }
            if(bot.model!=null){
                try{world.removeChild(bot.model);}catch(Throwable ignored){}
                spawnBotDeathPieces(bot);
            }
            bot.model=null;bot.aura=null;
        }
        private void spawnBotDeathPieces(PlayerBot bot) {
            bot.deathPieces=new PhysicsPiece[6];
            float c=(float)Math.cos(bot.yaw),sn=(float)Math.sin(bot.yaw);
            addBotDeathPiece(bot,0,makeRoundedCylinderHead(bot.skinColor),0,2.19f,0,0.28f,0.25f,0.28f,c,sn);
            addBotDeathPiece(bot,1,makeBox(0,0,0,0.78f,0.95f,0.40f,bot.shirtColor),0,1.45f,0,0.39f,0.475f,0.20f,c,sn);
            addBotDeathPiece(bot,2,makeBox(0,0,0,0.30f,0.88f,0.40f,bot.skinColor),-0.54f,1.48f,0,0.15f,0.44f,0.20f,c,sn);
            addBotDeathPiece(bot,3,makeBox(0,0,0,0.30f,0.88f,0.40f,bot.skinColor),0.54f,1.48f,0,0.15f,0.44f,0.20f,c,sn);
            addBotDeathPiece(bot,4,makeBox(0,0,0,0.36f,0.78f,0.40f,bot.legColor),-0.20f,0.52f,0,0.18f,0.39f,0.20f,c,sn);
            addBotDeathPiece(bot,5,makeBox(0,0,0,0.36f,0.78f,0.40f,bot.legColor),0.20f,0.52f,0,0.18f,0.39f,0.20f,c,sn);
        }
        private void addBotDeathPiece(PlayerBot bot,int index,Node node,float ox,float oy,float oz,
                float halfX,float halfY,float halfZ,float c,float sn) {
            addDeathPieceTo(bot.deathPieces,index,node,bot.x,bot.y,bot.z,
                    ox,oy,oz,halfX,halfY,halfZ,c,sn);
        }
        private void clearBotDeathPieces(PlayerBot bot) {
            if(bot==null||bot.deathPieces==null)return;
            for(int i=0;i<bot.deathPieces.length;i++){
                PhysicsPiece p=bot.deathPieces[i];
                if(p!=null)try{world.removeChild(p.node);}catch(Throwable ignored){}
            }
            bot.deathPieces=null;
        }
        private void updateTycoonBot(PlayerBot bot,int dt,int baseIndex){
            float baseX=TYCOON_BOT_X[baseIndex],baseZ=TYCOON_BOT_Z[baseIndex];
            if(gameClock>=bot.tycoonIncomeAt){
                bot.tycoonCash+=2;
                bot.tycoonIncomeAt=gameClock+2200;
            }
            if(bot.tycoonLevel<9&&gameClock>=bot.tycoonAt){
                int cost=5+bot.tycoonLevel*3;
                if(bot.tycoonCash>=cost){
                    bot.tycoonCash-=cost;
                    int level=++bot.tycoonLevel;
                    int column=(level-1)%3,row=(level-1)/3;
                    float x=baseX+(column-1)*1.5f;
                    float z=baseZ+(row-1)*1.5f;
                    float sy=0.48f+row*0.16f;
                    int color=BOT_SHIRT_COLORS[baseIndex%BOT_SHIRT_COLORS.length];
                    addModeBlock(x,sy*0.5f+0.035f,z,0.70f,sy,0.70f,color);
                    modeStatus("Tycoon bot "+(baseIndex+1)+" upgraded to "+level);
                }
                bot.tycoonAt=gameClock+4500+nextRandom(2200);
            }
            if(gameClock>=bot.nextThink){
                bot.nextThink=gameClock+1100+nextRandom(1100);
                bot.targetX=baseX+(nextRandom(49)-24)*0.055f;
                bot.targetZ=baseZ+(nextRandom(49)-24)*0.055f;
            }
            float dx=bot.targetX-bot.x,dz=bot.targetZ-bot.z;
            float distance=(float)Math.sqrt(dx*dx+dz*dz);
            if(distance>0.08f){
                float step=Math.min(dt*0.0008f,distance);
                float moveX=dx/distance*step,moveZ=dz/distance*step;
                float nx=bot.x+moveX,nz=bot.z+moveZ;
                if(!isWorldBlocked(nx,bot.z,bot.y,1.9f))bot.x=nx;
                if(!isWorldBlocked(bot.x,nz,bot.y,1.9f))bot.z=nz;
                if(moveX!=0||moveZ!=0){
                    bot.yaw=fastAtan2(-moveX,-moveZ);
                    bot.model.setOrientation(bot.yaw*57.29578f,0,1,0);
                }
            }
            bot.model.setTranslation(bot.x,bot.y,bot.z);
        }

        private void updatePlayerBots(int dt) {
            if(playerBots==null||world==null)return;
            updateBotDialogue();
            for(int i=0;i<playerBotCount;i++){
                PlayerBot bot=playerBots[i];if(bot==null)continue;
                if(!bot.alive){
                    updateBotDeathPieces(bot,dt);
                    if(gameClock>=bot.respawnAt){
                        clearBotDeathPieces(bot);
                        bot.x=bot.spawnX;bot.z=bot.spawnZ;
                        bot.y=botGroundTop(bot.x,bot.z);bot.velocityY=0;bot.grounded=true;bot.yaw=0;
                        addPlayerBotModel(bot,true);
                    }
                    continue;
                }
                if(bot.model==null){bot.alive=false;bot.respawnAt=gameClock+10000;continue;}
                if(bot.attackUntil>0&&gameClock>=bot.attackUntil){
                    if(bot.swordArm!=null)bot.swordArm.setOrientation(0,1,0,0);
                    if(bot.sword!=null)bot.sword.setRenderingEnable(false);
                    bot.model.setScale(1,1,1);bot.attackUntil=0;
                }
                if(bot.aura!=null){
                    float pulse=1.0f+(float)Math.sin(gameClock*0.012f)*0.045f;
                    bot.aura.setScale(pulse,pulse,pulse);
                    if(gameClock>=bot.auraUntil){try{bot.model.removeChild(bot.aura);}catch(Throwable ignored){}
                        bot.aura=null;}
                }

                if(currentGame==13){
                    updateTycoonBot(bot,dt,i);
                    continue;
                }
                boolean simonActive=currentGame==12&&simonRound>0&&simonRound<=12;
                if(simonActive&&bot.simonRoundSeen!=simonRound){
                    bot.simonRoundSeen=simonRound;
                    bot.simonMoveAt=gameClock+700+nextRandom(2300);
                    bot.simonMoving=false;
                    bot.targetX=bot.x;bot.targetZ=bot.z;
                }
                boolean attackPlayer=currentGame==10;
                boolean attackZombies=currentGame==2||currentGame==14;
                int targetZombie=attackZombies?nearestLivingZombie(bot.x,bot.z):-1;
                float targetX=attackPlayer?px:(targetZombie>=0?zombies[targetZombie].x:bot.spawnX);
                float targetZ=attackPlayer?pz:(targetZombie>=0?zombies[targetZombie].z:bot.spawnZ);
                float dx=targetX-bot.x,dz=targetZ-bot.z,d=(float)Math.sqrt(dx*dx+dz*dz);
                boolean armed=attackPlayer||(attackZombies&&targetZombie>=0);
                if(armed&&d<4.2f&&gameClock>=bot.nextAttack&&gameClock>=bot.invulnerableUntil){
                    bot.nextAttack=gameClock+2300+nextRandom(1200);
                    if(d<2.3f){
                        bot.yaw=fastAtan2(-dx,-dz);
                        bot.model.setOrientation(bot.yaw*57.29578f,0,1,0);
                        animatePlayerBotWeapon(bot);
                        if(attackPlayer){
                            if(!dead&&gameClock>=invulnerableUntil)damagePlayer(35);
                        }else if(targetZombie>=0&&zombies[targetZombie]!=null&&zombies[targetZombie].alive){
                            Zombie target=zombies[targetZombie];
                            target.alive=false;
                            try{world.removeChild(target.model);}catch(Throwable ignored){}
                            if(currentGame!=2){modeScore++;modeStatus("Enemy down: "+modeScore);}
                            if(currentGame==2)updateHordeStatus();
                        }
                    }else if(nextRandom(2)==0){
                        float inv=1.0f/d;
                        Node bomb=makeBombModel();
                        addWorldObject(bomb,bot.x+dx*inv*0.8f,bot.y+0.45f,
                                bot.z+dz*inv*0.8f,dx*inv*0.0015f,0,dz*inv*0.0015f,
                                0.34f,0.44f,0.34f,0,true,2600);
                        for(int q=0;q<worldObjects.length;q++)
                            if(worldObjects[q]!=null&&worldObjects[q].active&&worldObjects[q].body.node==bomb)
                                worldObjects[q].botOwned=true;
                        animatePlayerBotWeapon(bot);
                    }
                }
                if(simonActive&&!bot.simonMoving&&gameClock>=bot.simonMoveAt){
                    bot.simonMoving=true;
                    bot.targetX=SIMON_PAD_X[simonTarget]+(i-2)*0.10f;
                    bot.targetZ=SIMON_PAD_Z[simonTarget]+((i%2==0)?-0.10f:0.10f);
                }
                if(gameClock>=bot.nextThink||(armed&&d>3.0f)){
                    bot.nextThink=gameClock+900+nextRandom(1400);
                    if(simonActive){
                        if(bot.simonMoving){
                            bot.targetX=SIMON_PAD_X[simonTarget]+(i-2)*0.10f;
                            bot.targetZ=SIMON_PAD_Z[simonTarget]+((i%2==0)?-0.10f:0.10f);
                        }else{bot.targetX=bot.x;bot.targetZ=bot.z;}
                    }else if(armed){bot.targetX=targetX;bot.targetZ=targetZ;}
                    else{bot.targetX=bot.spawnX+(nextRandom(160)-80)*0.1f;
                        bot.targetZ=bot.spawnZ+(nextRandom(160)-80)*0.1f;}
                    if(bot.grounded&&gameClock>=bot.nextJump&&
                            (nextRandom(4)==0||currentGame==0||currentGame==7)){
                        bot.velocityY=0.0068f;bot.grounded=false;
                        bot.nextJump=gameClock+1300+nextRandom(1000);}
                }
                float tx=bot.targetX-bot.x,tz=bot.targetZ-bot.z;
                float dest=(float)Math.sqrt(tx*tx+tz*tz),mx=0,mz=0;
                if(dest>0.18f){
                    float move=Math.min(dt*0.00115f,dest);mx=tx/dest*move;mz=tz/dest*move;
                    float nx=bot.x+mx,nz=bot.z+mz;
                    if(!isWorldBlocked(nx,bot.z,bot.y,1.9f))bot.x=nx;
                    else if(bot.grounded&&gameClock>=bot.nextJump){
                        bot.velocityY=0.0075f;bot.grounded=false;bot.nextJump=gameClock+900;}
                    if(!isWorldBlocked(bot.x,nz,bot.y,1.9f))bot.z=nz;
                    if(mx!=0||mz!=0){
                        bot.yaw=fastAtan2(-mx,-mz);
                        bot.model.setOrientation(bot.yaw*57.29578f,0,1,0);
                    }
                }

                float previousY=bot.y;
                if(!bot.grounded){
                    bot.y+=bot.velocityY*dt;bot.velocityY-=0.000018f*dt;
                }
                botResolveLanding(bot,previousY);
                if(bot.y < -8.0f ||
                        (currentGame==4&&bot.y<lavaHeight+0.85f&&Math.abs(bot.x)<12.0f&&Math.abs(bot.z)<12.0f) ||
                        (currentGame==3&&studioTesting&&botTouchesStudioHazard(bot)) ||
                        (currentGame==1&&gameClock>=bot.invulnerableUntil&&bot.y<1.1f&&
                                Math.abs(bot.x-3.0f)<0.95f&&Math.abs(bot.z+3.0f)<0.95f)){
                    killPlayerBot(bot);continue;
                }
                if(bot.leftArm!=null&&dest>0.18f&&gameClock>=bot.attackUntil){
                    float gait=(float)Math.sin(gameClock*0.016f)*24.0f;
                    bot.leftArm.setOrientation(gait,1,0,0);bot.rightArm.setOrientation(-gait,1,0,0);
                    bot.leftLeg.setOrientation(-gait,1,0,0);bot.rightLeg.setOrientation(gait,1,0,0);
                }
                bot.model.setTranslation(bot.x,bot.y,bot.z);
            }
        }
        private int nearestLivingZombie(float x,float z) {
            int target=-1;float best=Float.MAX_VALUE;
            if(zombies==null)return -1;
            for(int i=0;i<zombies.length;i++){
                Zombie enemy=zombies[i];if(enemy==null||!enemy.alive)continue;
                float dx=enemy.x-x,dz=enemy.z-z,dist=dx*dx+dz*dz;
                if(dist<best){best=dist;target=i;}
            }
            return target;
        }
        private boolean botOnPlatform(float x,float z,float bx,float bz,float halfX,float halfZ) {
            return Math.abs(x-bx)<=halfX+0.22f&&Math.abs(z-bz)<=halfZ+0.22f;
        }
        private void botResolveLanding(PlayerBot bot,float previousY) {
            float top=Float.NEGATIVE_INFINITY;
            if(currentGame!=4&&currentGame!=0&&currentGame!=6&&currentGame!=7&&
                    Math.abs(bot.x)<11.5f&&Math.abs(bot.z)<11.5f)top=0.0f;
            if(currentGame>=4){
                for(int i=0;i<modeBlockCount;i++){
                    if(!modeBlockActive[i])continue;
                    float[] b=modeBlocks[i];float platformTop=b[1]+b[4]*0.5f;
                    if(botOnPlatform(bot.x,bot.z,b[0],b[2],b[3]*0.5f,b[5]*0.5f)&&
                            ((bot.grounded&&bot.y>=platformTop-0.05f)||
                            (!bot.grounded&&bot.velocityY<=0&&previousY>=platformTop&&bot.y<=platformTop)))
                        top=Math.max(top,platformTop);
                }
            }
            if(currentGame==3&&studioTesting){
                for(int i=0;i<studioObjectCount;i++){
                    StudioObject o=studioObjects[i];if(o==null||o.type==2)continue;
                    float platformTop=o.y+o.sy*0.5f;
                    if(botOnPlatform(bot.x,bot.z,o.x,o.z,o.sx*0.5f,o.sz*0.5f)&&
                            ((bot.grounded&&bot.y>=platformTop-0.05f)||
                            (!bot.grounded&&bot.velocityY<=0&&previousY>=platformTop&&bot.y<=platformTop)))
                        top=Math.max(top,platformTop);
                }
            }
            if(currentGame==0||currentGame==2){
                float[][] platforms=currentGame==0?OBBY_BLOCKS:ZOMBIE_BLOCKS;
                for(int i=0;i<platforms.length;i++){
                    float[] b=platforms[i];float platformTop=b[1]+b[4]*0.5f;
                    if(botOnPlatform(bot.x,bot.z,b[0],b[2],b[3]*0.5f,b[5]*0.5f)&&
                            ((bot.grounded&&bot.y>=platformTop-0.05f)||
                            (!bot.grounded&&bot.velocityY<=0&&previousY>=platformTop&&bot.y<=platformTop)))
                        top=Math.max(top,platformTop);
                }
            }
            if(!bot.grounded&&bot.velocityY<=0&&top!=Float.NEGATIVE_INFINITY&&
                    previousY>=top&&bot.y<=top){
                bot.y=top;bot.velocityY=0;bot.grounded=true;
            }else if(bot.grounded){
                if(top!=Float.NEGATIVE_INFINITY&&bot.y<=top+0.05f){bot.y=top;bot.velocityY=0;}
                else bot.grounded=false;
            }
        }
        private boolean botTouchesStudioHazard(PlayerBot bot) {
            for(int i=0;i<studioObjectCount;i++){
                StudioObject o=studioObjects[i];if(o==null||o.type!=2)continue;
                boolean vertical=bot.y<=o.y+o.sy*0.5f+0.03f&&bot.y+1.9f>o.y-o.sy*0.5f;
                if(vertical&&Math.abs(bot.x-o.x)<o.sx*0.5f+0.25f&&Math.abs(bot.z-o.z)<o.sz*0.5f+0.25f)return true;
            }
            return false;
        }
        private void updateBotDialogue() {
            if(botDialogue.length()>0&&gameClock<botDialogueUntil&&talkingBot>=0&&
                    talkingBot<playerBotCount&&playerBots[talkingBot]!=null&&playerBots[talkingBot].alive)return;
            botDialogue="";
            talkingBot=-1;
            if(gameClock<nextBotDialogueAt||playerBotCount<=0)return;
            int first=nextRandom(playerBotCount);
            for(int offset=0;offset<playerBotCount;offset++){
                int index=(first+offset)%playerBotCount;
                PlayerBot bot=playerBots[index];
                if(bot==null||!bot.alive)continue;
                talkingBot=index;
                botDialogue=BOT_DIALOGUES[nextRandom(BOT_DIALOGUES.length)];
                botDialogueUntil=gameClock+3000;
                nextBotDialogueAt=botDialogueUntil+2400+nextRandom(4600);
                return;
            }
            nextBotDialogueAt=gameClock+1000;
        }

        private void animatePlayerBotWeapon(PlayerBot bot) {
            bot.attackUntil=gameClock+450;
            if(bot.swordArm!=null)bot.swordArm.setOrientation(-42,1,0,0);
            if(bot.sword!=null)bot.sword.setRenderingEnable(true);
        }

        private float botGroundTop(float x,float z) {
            float top=currentGame==4?-20.0f:0.0f;
            if(currentGame==0||currentGame==7)for(int i=0;i<OBBY_BLOCKS.length;i++){
                float[] b=OBBY_BLOCKS[i];
                if(Math.abs(x-b[0])<b[3]*0.5f+0.2f&&Math.abs(z-b[2])<b[5]*0.5f+0.2f)
                    if(b[1]+b[4]*0.5f>top)top=b[1]+b[4]*0.5f;}
            for(int i=0;i<modeBlockCount;i++)if(modeBlockActive[i]){
                float[] b=modeBlocks[i];
                if(Math.abs(x-b[0])<b[3]*0.5f+0.2f&&Math.abs(z-b[2])<b[5]*0.5f+0.2f)
                    if(b[1]+b[4]*0.5f>top)top=b[1]+b[4]*0.5f;}
            return top;
        }
        private Group makeBotAura() {
            Group aura=new Group();aura.addChild(makeAuraMesh(0x503399FF));
            aura.setTranslation(0,1.35f,0);return aura;
        }
        private Mesh makeAuraMesh(int color) {
            final int rings=8,segments=16;int[] strips=new int[rings];
            for(int i=0;i<rings;i++)strips[i]=(segments+1)*2;
            short[] shell=new short[rings*(segments+1)*2*3];int out=0;
            for(int r=0;r<rings;r++){
                double a0=-1.57079632679+3.14159265359*r/rings;
                double a1=-1.57079632679+3.14159265359*(r+1)/rings;
                for(int q=0;q<=segments;q++){
                    double lon=6.28318530718*q/segments;
                    shell[out++]=(short)(Math.cos(a0)*Math.sin(lon)*1350);
                    shell[out++]=(short)(Math.sin(a0)*1350);
                    shell[out++]=(short)(Math.cos(a0)*Math.cos(lon)*1350);
                    shell[out++]=(short)(Math.cos(a1)*Math.sin(lon)*1350);
                    shell[out++]=(short)(Math.sin(a1)*1350);
                    shell[out++]=(short)(Math.cos(a1)*Math.cos(lon)*1350);}
            }
            VertexArray va=new VertexArray(rings*(segments+1)*2,3,2);
            va.set(0,rings*(segments+1)*2,shell);
            VertexBuffer vb=new VertexBuffer();vb.setPositions(va,0.001f,null);
            TriangleStripArray tris=new TriangleStripArray(0,strips);
            Appearance ap=new Appearance();Material mat=new Material();
            mat.setColor(Material.AMBIENT,color);mat.setColor(Material.DIFFUSE,color);
            mat.setColor(Material.EMISSIVE,color);ap.setMaterial(mat);
            CompositingMode cm=new CompositingMode();cm.setBlending(CompositingMode.ALPHA);
            ap.setCompositingMode(cm);PolygonMode pm=new PolygonMode();
            pm.setCulling(PolygonMode.CULL_NONE);ap.setPolygonMode(pm);
            return new Mesh(vb,new IndexBuffer[]{tris},new Appearance[]{ap});
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
            int count=configuredBotCount(3);
            if(count>0)spawnSimpleBot(0,-8,-8);
            if(count>1)spawnSimpleBot(1,8,-8);
            if(count>2)spawnSimpleBot(2,0,9);
        }

        private void spawnHideBot(int slot,float x,float z) {
            if(slot<0||slot>=zombies.length)return;
            try {
                Group model=buildNoob(false);
                model.setTranslation(x,0,z);
                model.setRenderingEnable(true);
                world.addChild(model);
                zombies[slot]=new Zombie(model,x,z);
            } catch(Throwable t) {
                initError="Hide bot: "+t.toString();
            }
        }

        private void spawnSeekBots() {
            zombies=new Zombie[6];
            int count=configuredBotCount(5);
            int[] order={0,1,2,3,4};
            for(int i=order.length-1;i>0;i--){
                int j=nextRandom(i+1),swap=order[i];
                order[i]=order[j];order[j]=swap;
            }
            for(int i=0;i<count;i++)
                spawnHideBot(i,HIDE_SPAWN_X[order[i]],HIDE_SPAWN_Z[order[i]]);
            for(int i=0;i<count;i++) if(zombies[i]!=null) {
                zombies[i].hidden=true;
                zombies[i].fleeing=false;
                try{world.removeChild(zombies[i].model);}catch(Throwable ignored){}
            }
            modeGoal=count;
            modeStartAt=gameClock;
            modeEventAt=gameClock+20000;
            modeStatus("HIDE THE BOTS - 20s");
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
                    damagePlayer(35); return;
                }
                if (jumpY>8.0f) modeStatus("Lava escaped!");
            } else if (currentGame==5) {
                if (gameClock>=modeEventAt) {
                    modeEventAt=gameClock+850;
                    long seed=gameClock/850L+modeScore*17L;
                    float x=(float)((seed*37L)%190L)/10.0f-9.5f;
                    float z=(float)((seed*61L)%190L)/10.0f-9.5f;
                    spawnMeteor(x,z);
                }
                for(int i=0;i<worldObjects.length;i++) {
                    WorldObject o=worldObjects[i];
                    if(o==null||!o.active) continue;
                    float dx=o.body.x-px,dz=o.body.z-pz;
                    if(gameClock>=invulnerableUntil&&o.body.y<jumpY+1.6f&&
                            o.body.y>jumpY-0.5f&&dx*dx+dz*dz<1.0f) {
                        removeWorldObject(o); damagePlayer(35); return;
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
                updateSeekGame(dt);
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

        private void updateSeekGame(int dt) {
            if(modeEventAt>0&&gameClock<modeEventAt)return;
            if(modeEventAt>0){
                for(int i=0;i<zombies.length;i++){
                    Zombie bot=zombies[i];
                    if(bot==null||!bot.alive)continue;
                    bot.hidden=false;
                    bot.model.setRenderingEnable(true);
                    try{world.addChild(bot.model);}catch(Throwable ignored){}
                }
                modeEventAt=0;
                modeStatus("Find the bots! Turn the camera to spot them.");
            }
            for(int i=0;i<zombies.length;i++) {
                Zombie z=zombies[i];
                if(z==null||!z.alive)continue;
                float dx=px-z.x,dz=pz-z.z;
                float distanceSquared=dx*dx+dz*dz;
                if(!z.fleeing&&gameClock>=z.attackAt){
                    z.attackAt=gameClock+180;
                    if(isBotVisibleInCamera(z)){
                        z.fleeing=true;
                        modeStatus("Spotted! Chase the bot!");
                    }
                }
                if(z.fleeing){
                    float awayX=z.x-px,awayZ=z.z-pz;
                    float length=(float)Math.sqrt(awayX*awayX+awayZ*awayZ);
                    if(length<0.01f){awayX=nextRandom(2)==0?1.0f:-1.0f;awayZ=0;length=1;}
                    awayX/=length;awayZ/=length;
                    float step=dt*0.0032f;
                    float moveX=0,moveZ=0,bestDot=-2.0f;
                    for(int d=0;d<HIDE_FLEE_COS.length;d++){
                        float c=HIDE_FLEE_COS[d],sn=HIDE_FLEE_SIN[d];
                        float dirX=awayX*c-awayZ*sn,dirZ=awayX*sn+awayZ*c;
                        float nx=z.x+dirX*step,nz=z.z+dirZ*step;
                        if(Math.abs(nx)>20.0f||Math.abs(nz)>20.0f||
                                isWorldBlocked(nx,z.z,0.0f,1.9f)||
                                isWorldBlocked(z.x,nz,0.0f,1.9f))continue;
                        float dot=dirX*awayX+dirZ*awayZ;
                        if(dot>bestDot){bestDot=dot;moveX=dirX;moveZ=dirZ;}
                    }
                    if(bestDot>-1.0f){
                        z.x+=moveX*step;z.z+=moveZ*step;
                        z.model.setTranslation(z.x,0,z.z);
                        z.model.setOrientation(fastAtan2(-moveX,-moveZ)*57.29578f,0,1,0);
                    }
                    dx=px-z.x;dz=pz-z.z;distanceSquared=dx*dx+dz*dz;
                }
                if(distanceSquared<1.15f*1.15f){
                    z.alive=false;
                    try{world.removeChild(z.model);}catch(Throwable ignored){}
                    modeStatus("Found "+(modeGoal-countZombies())+"/"+modeGoal);
                }
            }
            if(modeGoal-countZombies()>=modeGoal)modeStatus("Everyone found!");
        }

        private boolean isBotVisibleInCamera(Zombie bot){
            float cp=(float)Math.cos(camPitch),sp=(float)Math.sin(camPitch);
            float cameraX=px-(float)Math.sin(camYaw)*cp*camDistance;
            float cameraY=1.2f+jumpY+sp*camDistance;
            float cameraZ=pz+(float)Math.cos(camYaw)*cp*camDistance;
            float dx=bot.x-cameraX,dz=bot.z-cameraZ;
            float forwardX=(float)Math.sin(camYaw),forwardZ=-(float)Math.cos(camYaw);
            float rightX=(float)Math.cos(camYaw),rightZ=(float)Math.sin(camYaw);
            float forward=dx*forwardX+dz*forwardZ;
            if(forward<=0.2f)return false;
            float aspect=(float)getWidth()/(float)Math.max(1,getHeight());
            float halfWidth=(float)Math.tan(1.134464f*0.5f)*aspect;
            float lateral=Math.abs(dx*rightX+dz*rightZ);
            if(lateral>forward*halfWidth)return false;
            float distance=(float)Math.sqrt(dx*dx+dz*dz);
            if(distance>28.0f)return false;
            int steps=(int)(distance*2.0f);
            if(steps<2)steps=2;
            if(steps>48)steps=48;
            for(int i=2;i<steps-1;i++){
                float t=(float)i/(float)steps;
                float x=cameraX+dx*t,z=cameraZ+dz*t;
                float y=cameraY+(1.1f-cameraY)*t;
                if(isWorldBlocked(x,z,y-0.35f,0.7f))return false;
            }
            return true;
        }

        private void updateSimonGame() {
            if(simonRound>12||gameClock<modeEventAt)return;
            if(simonRound>0){
                if(!isOnSimonPad(px,pz,simonTarget)){
                    damagePlayer(35);
                    if(dead)return;
                }else modeScore++;
                for(int i=0;i<playerBotCount;i++){
                    PlayerBot bot=playerBots[i];
                    if(bot!=null&&bot.alive&&!isOnSimonPad(bot.x,bot.z,simonTarget))
                        killPlayerBot(bot);
                }
                if(simonRound>=12){
                    simonRound=13;
                    modeStatus("Simon Says complete!");
                    return;
                }
            }
            simonTarget=nextRandom(4);
            simonRound++;
            modeEventAt=gameClock+6200;
            for(int i=0;i<playerBotCount;i++){
                PlayerBot bot=playerBots[i];
                if(bot==null||!bot.alive)continue;
                bot.simonRoundSeen=simonRound;
                bot.simonMoveAt=gameClock+700+nextRandom(2300);
                bot.simonMoving=false;
                bot.targetX=bot.x;bot.targetZ=bot.z;
            }
            String[] color=new String[] {"RED","BLUE","GREEN","YELLOW"};
            modeStatus("SIMON SAYS: "+color[simonTarget]+"! Move!");
        }

        private boolean isOnSimonPad(float x,float z,int target) {
            if(target<0||target>=SIMON_PAD_X.length)return false;
            return Math.abs(x-SIMON_PAD_X[target])<=1.85f&&
                    Math.abs(z-SIMON_PAD_Z[target])<=1.85f;
        }

        private void useTycoonAction() {
            if(currentGame!=13)return;
            if(Math.abs(px-TYCOON_PLAYER_X)>2.4f||
                    Math.abs(pz-TYCOON_PLAYER_Z)>2.4f){
                modeStatus("Return to your yellow base to build");
                return;
            }
            if(tycoonLevel>=9){modeStatus("Your base is fully upgraded!");return;}
            int cost=5+tycoonLevel*3;
            if(modeCash<cost){modeStatus("Need "+cost+" cash");return;}
            modeCash-=cost;
            int level=++tycoonLevel;
            int column=(level-1)%3,row=(level-1)/3;
            float x=TYCOON_PLAYER_X+(column-1)*1.5f;
            float z=TYCOON_PLAYER_Z+(row-1)*1.5f;
            float sy=0.48f+row*0.16f;
            addModeBlock(x,sy*0.5f+0.035f,z,0.70f,sy,0.70f,STUDIO_COLORS[level%STUDIO_COLORS.length]);
            modeStatus("Your base upgraded "+level+"/9 - cash "+modeCash);
            if(tycoonLevel>=9)modeStatus("Your base is fully upgraded!");
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
                    if(defenseLeaked>=3) {damagePlayer(35);return;}
                } else {
                    float step=dt*0.00065f;
                    z.x+=dx/d*step; z.z+=dz/d*step;
                    z.model.setTranslation(z.x,0,z.z);
                }
                if(z.alive&&gameClock>=z.attackAt){
                    for(int b=0;b<playerBotCount;b++){
                        PlayerBot bot=playerBots[b];
                        if(bot==null||!bot.alive||gameClock<bot.invulnerableUntil)continue;
                        float bx=bot.x-z.x,bz=bot.z-z.z;
                        if(bx*bx+bz*bz<0.9f*0.9f){
                            z.attackAt=gameClock+900;killPlayerBot(bot);break;
                        }
                    }
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
                        damagePlayer(35);
                        return;
                    }
                }
                if(currentGame==2&&gameClock>=z.attackAt){
                    for(int b=0;b<playerBotCount;b++){
                        PlayerBot bot=playerBots[b];
                        if(bot==null||!bot.alive||gameClock<bot.invulnerableUntil)continue;
                        float bx=bot.x-z.x,bz=bot.z-z.z;
                        if(bx*bx+bz*bz<0.9f*0.9f){
                            z.attackAt=gameClock+900;killPlayerBot(bot);break;
                        }
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
                    if(currentGame==13&&b[4]<=0.20f&&b[1]+b[4]*0.5f<=0.05f)continue;
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
            updateMeteorBursts();
            if (worldObjects == null) return;
            for (int i = 0; i < worldObjects.length; i++) {
                WorldObject o = worldObjects[i];
                if (o == null || !o.active) continue;
                if (!o.bomb && gameClock >= o.removeAt) {
                    removeWorldObject(o);
                    continue;
                }
                PhysicsPiece p = o.body;
                if(o.meteor&&o.fireTrail!=null){
                    float flicker=0.86f+(float)Math.sin(gameClock*0.028f+i*1.7f)*0.12f;
                    o.fireTrail.setScale(flicker,1.0f+(1.0f-flicker),flicker);
                }
                p.vy -= 0.000018f * dt;
                p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
                p.rotation += p.spin * dt * 0.001f;
                updatePieceExtents(p);
                if(o.meteor&&p.vy<0.0f&&p.y-p.extentY<=0.0f){
                    float impactX=p.x,impactZ=p.z;
                    removeWorldObject(o);
                    spawnMeteorBurst(impactX,0.45f,impactZ);
                    damageAtMeteorImpact(impactX,impactZ);
                    continue;
                }
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

        private WorldObject addWorldObject(Node node, float x, float y, float z,
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
            return worldObjects[slot];
        }

        private void spawnMeteor(float x,float z) {
            Group model=new Group();
            model.addChild(makeBox(0,0,0,0.72f,0.72f,0.72f,0xA94732));
            Group flame=new Group();
            Mesh outer=makeAuraMesh(0xDFFF5018);
            outer.setTranslation(0,-0.45f,0);outer.setScale(0.30f,0.48f,0.30f);
            Mesh inner=makeAuraMesh(0xEFFFCC32);
            inner.setTranslation(0,-0.73f,0);inner.setScale(0.19f,0.36f,0.19f);
            flame.addChild(outer);flame.addChild(inner);model.addChild(flame);
            WorldObject meteor=addWorldObject(model,x,8.5f,z,0,0,0,
                    0.72f,0.72f,0.72f,0,false,0);
            if(meteor!=null){meteor.meteor=true;meteor.fireTrail=flame;}
        }

        private void spawnMeteorBurst(float x,float y,float z) {
            int slot=-1;
            for(int i=0;i<meteorBursts.length;i++)
                if(meteorBursts[i]==null||gameClock>=meteorBursts[i].removeAt){slot=i;break;}
            if(slot<0){
                slot=0;
                for(int i=1;i<meteorBursts.length;i++)
                    if(meteorBursts[i].removeAt<meteorBursts[slot].removeAt)slot=i;
                try{world.removeChild(meteorBursts[slot].model);}catch(Throwable ignored){}
            }
            Group burst=new Group();
            Mesh outer=makeAuraMesh(0xCFFF5418);
            Mesh inner=makeAuraMesh(0xDFFFC72D);
            inner.setScale(0.52f,0.52f,0.52f);
            burst.addChild(outer);burst.addChild(inner);
            burst.setTranslation(x,y,z);burst.setScale(0.08f,0.08f,0.08f);
            world.addChild(burst);
            meteorBursts[slot]=new MeteorBurst(burst,outer,inner,gameClock+360);
        }

        private void updateMeteorBursts() {
            if(meteorBursts==null)return;
            for(int i=0;i<meteorBursts.length;i++){
                MeteorBurst burst=meteorBursts[i];if(burst==null)continue;
                if(gameClock>=burst.removeAt){
                    try{world.removeChild(burst.model);}catch(Throwable ignored){}
                    meteorBursts[i]=null;continue;
                }
                float progress=1.0f-(burst.removeAt-gameClock)/360.0f;
                float grow=0.08f+progress*0.62f;
                burst.outer.setScale(grow,grow,grow);
                burst.inner.setScale(grow*0.52f,grow*0.52f,grow*0.52f);
            }
        }

        private void damageAtMeteorImpact(float x,float z) {
            float radiusSquared=1.55f*1.55f;
            float dx=px-x,dz=pz-z;
            if(!dead&&gameClock>=invulnerableUntil&&dx*dx+dz*dz<=radiusSquared)
                damagePlayer(35);
            for(int i=0;i<playerBotCount;i++){
                PlayerBot bot=playerBots[i];
                if(bot==null||!bot.alive||gameClock<bot.invulnerableUntil)continue;
                dx=bot.x-x;dz=bot.z-z;
                if(dx*dx+dz*dz<=radiusSquared)killPlayerBot(bot);
            }
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
            for(int i=0;i<playerBotCount;i++){
                PlayerBot bot=playerBots[i];
                if(bot==null||!bot.alive||gameClock<bot.invulnerableUntil)continue;
                float dx=bot.x-bx,dz=bot.z-bz;
                if(dx*dx+dz*dz<=radiusSquared)killPlayerBot(bot);
            }
            float playerX = px - bx, playerZ = pz - bz;
            if (!bomb.botOwned && !dead && gameClock >= invulnerableUntil &&
                    playerX * playerX + playerZ * playerZ <= radiusSquared) damagePlayer(35);
            updateHordeStatus();
        }

        private void useInventoryItem() {
            if(dead||(currentGame!=2&&currentGame!=10&&currentGame!=14))return;
            int itemCount=currentGame==2?3:2;if(inventorySelection>=itemCount)inventorySelection=0;
            if(inventorySelection==0){
                if(gameClock<swordAttackUntil)return;swordAttackUntil=gameClock+600;
                float fx=(float)Math.sin(yaw),fz=-(float)Math.cos(yaw);
                int target=-1;float closest=2.55f*2.55f;
                for(int i=0;i<zombies.length;i++){Zombie z=zombies[i];if(z==null||!z.alive)continue;
                    float dx=z.x-px,dz=z.z-pz,d=dx*dx+dz*dz;
                    if(d>closest||dx*fx+dz*fz<0.10f)continue;target=i;closest=d;}
                if(target>=0){zombies[target].alive=false;
                    try{world.removeChild(zombies[target].model);}catch(Throwable ignored){}
                    if(currentGame!=2){modeScore++;modeStatus("Enemy down: "+modeScore);}}
                else for(int i=0;i<playerBotCount;i++){PlayerBot bot=playerBots[i];
                    if(bot==null||!bot.alive||gameClock<bot.invulnerableUntil)continue;
                    float dx=bot.x-px,dz=bot.z-pz,d=dx*dx+dz*dz;
                    if(d>closest||dx*fx+dz*fz<0.10f)continue;
                    killPlayerBot(bot);modeScore++;if(currentGame!=2)modeStatus("Enemy down: "+modeScore);break;}
                animateNoob();if(currentGame==2)updateHordeStatus();
            }else if(inventorySelection==1){
                float fx=(float)Math.sin(yaw),fz=-(float)Math.cos(yaw);
                addWorldObject(makeBombModel(),px+fx*0.85f,jumpY+0.45f,pz+fz*0.85f,
                        fx*0.0018f,0,fz*0.0018f,0.34f,0.44f,0.34f,0,true,2600);
            }else if(currentGame==2){
                float fx=(float)Math.sin(yaw),fz=-(float)Math.cos(yaw);
                float rx=(float)Math.cos(yaw),rz=(float)Math.sin(yaw);
                float cx=px+fx*1.75f,cz=pz+fz*1.75f;
                for(int row=0;row<2;row++)for(int col=-1;col<=1;col++){
                    Group block=new Group();block.addChild(makeBox(0,0,0,0.88f,0.82f,0.36f,row==0?0x777777:0x969696));
                    addWorldObject(block,cx+rx*col*0.86f,jumpY+1.5f+row*0.84f,cz+rz*col*0.86f,
                            0,0,0,0.88f,0.82f,0.36f,yaw,false,0);}
            }
        }
        private Group makeBombModel(){
            Group bomb=new Group();bomb.addChild(makeBox(0,0,0,0.34f,0.34f,0.34f,0xE52B2B));
            bomb.addChild(makeBox(0,0.21f,0,0.12f,0.10f,0.12f,0x303030));return bomb;
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
            if (lanMenu) {
                drawLanPanel(g);
                return;
            }
            if (menuSettings) {
                drawSettingsPanel(g, false);
                return;
            }
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
            g.drawString("# LAN  * SETTINGS  5 START",
                    getWidth() / 2, getHeight() - font.getHeight() * 2 - 10,
                    Graphics.TOP | Graphics.HCENTER);
            if (modelError.length() > 0)
                g.drawString(modelError, 4, 4, Graphics.TOP | Graphics.LEFT);
            if (initError.length() > 0)
                g.drawString(initError, 4, g.getFont().getHeight() + 6, Graphics.TOP | Graphics.LEFT);
        }

        private void drawLanPanel(Graphics g) {
            g.setColor(0x101820);
            g.fillRect(0, 0, getWidth(), getHeight());
            drawMenuPanorama(g);
            if (scaledLogo != null)
                g.drawImage(scaledLogo, (getWidth()-scaledLogo.getWidth())/2,
                        Math.max(5,getHeight()/18), Graphics.TOP|Graphics.LEFT);
            int y = getHeight()/2 - 30;
            String[] options = {"Host selected game", "Join by host IP", "Back"};
            for (int i = 0; i < options.length; i++) {
                int rowY = y + i * 25;
                g.setColor(i == lanMenuSelection ? 0xF5C542 : 0x203040);
                g.fillRoundRect(12,rowY,getWidth()-24,21,8,8);
                g.setColor(i == lanMenuSelection ? 0x17202A : 0xFFFFFF);
                g.drawString(options[i],getWidth()/2,rowY+3,Graphics.TOP|Graphics.HCENTER);
            }
            Font font = g.getFont();
            g.setColor(0xFFFFFF);
            g.drawString("Selected: " + GAME_NAMES[selectedGame],getWidth()/2,
                    y-20,Graphics.TOP|Graphics.HCENTER);
            g.drawString("UP/DOWN SELECT  5 OK  # BACK",getWidth()/2,
                    getHeight()-font.getHeight()-6,Graphics.TOP|Graphics.HCENTER);
            if (lanMessage.length() > 0)
                g.drawString(lanMessage,getWidth()/2,getHeight()-font.getHeight()*2-8,
                        Graphics.TOP|Graphics.HCENTER);
            if (modelError.length() > 0)
                g.drawString(modelError,2,2,Graphics.TOP|Graphics.LEFT);
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

        private void updateLanPlayers() {
            if (lanSession == null) return;
            if (!lanSession.isHost() && !lanSession.isConnected()) {
                lanSession = null;
                lanMessage = "LAN connection lost";
                modelStatus = lanMessage;
                for (int i=0;i<lanAvatars.length;i++) {
                    if (lanAvatars[i] != null && world != null) {
                        try { world.removeChild(lanAvatars[i]); } catch (Throwable ignored) {}
                        lanAvatars[i] = null;
                    }
                }
                return;
            }
            lanSession.publish(px, jumpY, pz, yaw, playerHealth, !dead);
            if (lanPlayers == null) lanPlayers = new LanSession.PlayerState[5];
            lanSession.copySnapshot(lanPlayers);
            LanSession.PlayerState[] states = lanPlayers;
            int local = lanSession.getLocalSlot();
            for (int i = 0; i < states.length; i++) {
                if (i == local || states[i] == null || !states[i].connected || !states[i].alive) {
                    if (lanAvatars[i] != null && world != null) {
                        try { world.removeChild(lanAvatars[i]); } catch (Throwable ignored) {}
                        lanAvatars[i] = null;
                    }
                    continue;
                }
                if (lanAvatars[i] == null && world != null) {
                    Mesh oldLeft=leftArm,oldRight=rightArm,oldLegL=leftLeg,oldLegR=rightLeg;
                    Group oldSword=swordVisual;
                    try { lanAvatars[i]=buildNoob(false); }
                    catch (Throwable t) { continue; }
                    leftArm=oldLeft; rightArm=oldRight; leftLeg=oldLegL; rightLeg=oldLegR;
                    swordVisual=oldSword;
                    world.addChild(lanAvatars[i]);
                }
                if (lanAvatars[i] != null) {
                    lanAvatars[i].setTranslation(states[i].x,states[i].y,states[i].z);
                    lanAvatars[i].setOrientation(states[i].yaw*57.29578f,0,1,0);
                }
            }
        }

        private void update(int dt) {
            if (pendingLanError != null) {
                lanMessage = pendingLanError;
                modelError = lanMessage;
                pendingLanError = null;
            }
            if (pendingLanSession != null && pendingLanGame >= 0) {
                lanSession = pendingLanSession;
                pendingLanSession = null;
                int joinedGame = pendingLanGame;
                pendingLanGame = -1;
                try {
                    selectedGame = joinedGame;
                    startGame(joinedGame);
                    lanMessage = "Connected to LAN host";
                } catch (Throwable t) {
                    lanSession.close(); lanSession = null;
                    lanMessage = "LAN game: " + t.toString();
                    modelError = lanMessage;
                }
            }
            if (menuMode) {
                menuPanTime += dt;
                return;
            }
            updateLanPlayers();
            gameClock += dt;
            if (dead) {
                if (currentGame == 2 || currentGame == 10) updateZombies(dt);
                if (currentGame == 2 || currentGame == 5 || currentGame == 10 || currentGame == 14) updateWorldObjects(dt);
                updatePlayerBots(dt);
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
            if (currentGame == 2 || currentGame == 5 || currentGame == 14) updateWorldObjects(dt);
            updatePlayerBots(dt);
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
            resolveHideSeekCeiling(previousY);
            resolveLanding(previousY);
            if (isTouchingStudioHazard()) {
                damagePlayer(35);
                return;
            }
            if (!grounded && jumpY < -8.0f) {
                damagePlayer(35);
                return;
            }
            if (lobbyMode && gameClock >= invulnerableUntil && jumpY < 1.1f &&
                    Math.abs(px - 3.0f) < 0.95f && Math.abs(pz + 3.0f) < 0.95f) {
                damagePlayer(35);
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
            float floorLimit = currentGame == 11 ? 21.5f : 11.5f;
            if (currentGame != 4 && currentGame != 0 && currentGame != 6 &&
                    currentGame != 7 && Math.abs(px) < floorLimit &&
                    Math.abs(pz) < floorLimit) top = 0.0f;
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

        private void resolveHideSeekCeiling(float previousY){
            if(currentGame!=11||grounded||playerVelocityY<=0.0f)return;
            float oldHead=previousY+1.9f,newHead=jumpY+1.9f;
            for(int i=0;i<modeBlockCount;i++){
                if(!modeBlockActive[i])continue;
                float[] b=modeBlocks[i];
                float underside=b[1]-b[4]*0.5f;
                if(onPlatform(b[0],b[2],b[3]*0.5f,b[5]*0.5f)&&
                        oldHead<=underside&&newHead>=underside){
                    jumpY=underside-1.9f;
                    playerVelocityY=0.0f;
                    return;
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

        private void damagePlayer(int amount) {
            if(dead||gameClock<invulnerableUntil)return;
            if(amount<1)amount=1;
            playerHealth-=amount;
            if(playerHealth<=0){
                playerHealth=0;
                beginDeath();
            }else{
                invulnerableUntil=gameClock+850;
                modeStatus("Health "+playerHealth+"/100");
            }
        }

        private void beginDeath() {
            if (dead || gameClock < invulnerableUntil) return;
            playerHealth=0;
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
            addDeathPieceTo(deathPieces,index,node,px,jumpY,pz,ox,oy,oz,
                    halfX,halfY,halfZ,c,sn);
        }

        private void addDeathPieceTo(PhysicsPiece[] pieces,int index,Node node,
                float centerX,float centerY,float centerZ,float ox,float oy,float oz,
                float halfX,float halfY,float halfZ,float c,float sn) {
            float x=centerX+ox*c+oz*sn;
            float z=centerZ-ox*sn+oz*c;
            node.setTranslation(x,centerY+oy,z);
            world.addChild(node);
            float outward=(index-2.5f)*0.0012f;
            float direction=index%2==0?1.0f:-1.0f;
            float axisX=index%3==0?1.0f:0.35f;
            float axisY=index%3==1?1.0f:0.45f;
            float axisZ=index%3==2?1.0f:0.60f;
            float axisLength=(float)Math.sqrt(axisX*axisX+axisY*axisY+axisZ*axisZ);
            axisX/=axisLength;axisY/=axisLength;axisZ/=axisLength;
            pieces[index]=new PhysicsPiece(node,x,centerY+oy,z,
                    outward,0.0035f+(index%3)*0.001f,direction*0.001f,
                    35+index*5,axisX,axisY,axisZ,halfX,halfY,halfZ);
        }

        private void updateDeathPieces(int dt) {
            updatePhysicsPieces(deathPieces,dt);
        }

        private void updateBotDeathPieces(PlayerBot bot,int dt) {
            if(bot!=null)updatePhysicsPieces(bot.deathPieces,dt);
        }

        private void updatePhysicsPieces(PhysicsPiece[] pieces,int dt) {
            if(pieces==null)return;
            for(int i=0;i<pieces.length;i++){
                PhysicsPiece p=pieces[i];if(p==null)continue;
                p.vy-=0.000018f*dt;
                p.x+=p.vx*dt;p.y+=p.vy*dt;p.z+=p.vz*dt;
                p.rotation+=p.spin*dt*0.001f;
                updatePieceExtents(p);
                resolvePieceEnvironment(p);
            }
            for(int i=0;i<pieces.length;i++){
                PhysicsPiece a=pieces[i];if(a==null)continue;
                for(int j=i+1;j<pieces.length;j++){
                    PhysicsPiece b=pieces[j];if(b!=null)resolvePiecePair(a,b);
                }
            }
            for(int i=0;i<pieces.length;i++){
                PhysicsPiece p=pieces[i];if(p==null)continue;
                p.node.setTranslation(p.x,p.y,p.z);
                p.node.setOrientation(p.rotation,p.spinX,p.spinY,p.spinZ);
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
            if (currentGame != 0 && currentGame != 6 && currentGame != 7 &&
                    Math.abs(p.x) <= 12.0f + p.extentX && Math.abs(p.z) <= 12.0f + p.extentZ &&
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
                px=TYCOON_PLAYER_X;pz=TYCOON_PLAYER_Z;jumpY=0.0f;
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
                playerHealth=100;
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
            if(currentGame==11&&modeEventAt>gameClock&&modeEventAt>0){
                g.setColor(0x000000);
                g.fillRect(0,0,getWidth(),getHeight());
                g.setColor(0xFFFFFF);
                g.drawString("HIDE THE BOTS",getWidth()/2,getHeight()/2-g.getFont().getHeight(),
                        Graphics.TOP|Graphics.HCENTER);
                g.drawString(""+((modeEventAt-gameClock+999)/1000),
                        getWidth()/2,getHeight()/2,Graphics.TOP|Graphics.HCENTER);
            }
            g.setColor(0xFFFFFF);
            g.drawString("ROBLOX 3DME", 3, 2, Graphics.TOP|Graphics.LEFT);
            g.drawString(modelStatus, 3, 20, Graphics.TOP|Graphics.LEFT);
            if (modelError.length() > 0) drawWrapped(g, modelError, 3, 38, getWidth() - 6);
            if (initError.length() > 0) drawWrapped(g, initError, 3, 72, getWidth() - 6);
            if (currentGame == 2 || currentGame == 10 || currentGame == 14) drawInventory(g);
            if (currentGame >= 4) drawExtraHud(g);
            if (currentGame == 3 && !paused) drawStudioHud(g);
            if (!paused) {
                drawHealthBars(g);
                drawBotDialogue(g);
            }
            if (paused) drawPauseMenu(g);
        }


        private boolean projectWorldToScreen(float x,float y,float z) {
            if(getWidth()<=0||getHeight()<=0)return false;
            float targetX=px,targetY=1.2f+jumpY,targetZ=pz;
            if(dead&&deathPieces!=null&&deathPieces.length>1&&deathPieces[1]!=null){
                PhysicsPiece torso=deathPieces[1];
                targetX=torso.x;targetY=torso.y;targetZ=torso.z;
            }
            float cp=(float)Math.cos(camPitch),sp=(float)Math.sin(camPitch);
            float horizontal=cp*camDistance;
            float cameraX=targetX-(float)Math.sin(camYaw)*horizontal;
            float cameraY=targetY+sp*camDistance;
            float cameraZ=targetZ+(float)Math.cos(camYaw)*horizontal;
            float forwardX=(float)Math.sin(camYaw)*cp,forwardY=-sp;
            float forwardZ=-(float)Math.cos(camYaw)*cp;
            float rightX=(float)Math.cos(camYaw),rightZ=(float)Math.sin(camYaw);
            float upX=(float)Math.sin(camYaw)*sp,upY=cp;
            float upZ=-(float)Math.cos(camYaw)*sp;
            float relX=x-cameraX,relY=y-cameraY,relZ=z-cameraZ;
            float depth=relX*forwardX+relY*forwardY+relZ*forwardZ;
            if(depth<=0.2f)return false;
            float scale=(getHeight()*0.5f)/(float)Math.tan(1.134464f*0.5f);
            projectedScreenX=(int)(getWidth()*0.5f+
                    (relX*rightX+relZ*rightZ)*scale/depth);
            projectedScreenY=(int)(getHeight()*0.5f-
                    (relX*upX+relY*upY+relZ*upZ)*scale/depth);
            return projectedScreenX>=-getWidth()/2&&projectedScreenX<=getWidth()*3/2&&
                    projectedScreenY>=0&&projectedScreenY<=getHeight();
        }

        private void drawHealthBars(Graphics g) {
            for(int i=0;i<playerBotCount;i++){
                PlayerBot bot=playerBots[i];
                if(bot==null||!bot.alive)continue;
                if(projectWorldToScreen(bot.x,bot.y+2.58f,bot.z))
                    drawCharacterHealthBar(g,projectedScreenX,projectedScreenY,bot.health,34,5);
            }
            if(!dead&&projectWorldToScreen(px,jumpY+2.65f,pz))
                drawCharacterHealthBar(g,projectedScreenX,projectedScreenY,playerHealth,48,6);
        }

        private void drawCharacterHealthBar(Graphics g,int centerX,int centerY,int value,int width,int height) {
            if(value<0)value=0;if(value>100)value=100;
            int x=centerX-width/2,y=centerY-height/2;
            g.setColor(0x202020);g.fillRect(x-1,y-1,width+2,height+2);
            g.setColor(0x5A2020);g.fillRect(x,y,width,height);
            int fill=width*value/100;
            if(value>60)g.setColor(0x35D05A);
            else if(value>30)g.setColor(0xE6C832);
            else g.setColor(0xE34343);
            if(fill>0)g.fillRect(x,y,fill,height);
            g.setColor(0xFFFFFF);g.drawRect(x-1,y-1,width+1,height+1);
        }

        private void drawBotDialogue(Graphics g) {
            if(botDialogue.length()==0||gameClock>=botDialogueUntil||talkingBot<0||
                    talkingBot>=playerBotCount||playerBots[talkingBot]==null||!playerBots[talkingBot].alive)return;
            PlayerBot bot=playerBots[talkingBot];
            if(!projectWorldToScreen(bot.x,bot.y+3.0f,bot.z))return;
            int screenX=projectedScreenX,screenY=projectedScreenY;
            Font font=g.getFont();
            String label=botDialogue;
            int maxTextWidth=Math.max(1,getWidth()-24);
            if(font.stringWidth(label)>maxTextWidth){
                while(label.length()>0&&font.stringWidth(label+"...")>maxTextWidth)
                    label=label.substring(0,label.length()-1);
                label+="...";
            }
            int boxWidth=Math.min(getWidth()-8,font.stringWidth(label)+14);
            int boxHeight=font.getHeight()+8;
            int boxX=screenX-boxWidth/2;
            if(boxX<4)boxX=4;
            if(boxX+boxWidth>getWidth()-4)boxX=getWidth()-4-boxWidth;
            int boxY=screenY-boxHeight-11;
            if(boxY<30)boxY=30;
            if(boxY+boxHeight>getHeight()-2)boxY=getHeight()-boxHeight-2;
            int tailX=boxX+boxWidth/2;
            g.setColor(0xFFFFFF);g.drawLine(tailX,boxY+boxHeight-1,screenX,screenY-3);
            g.fillRoundRect(boxX,boxY,boxWidth,boxHeight,10,10);
            g.setColor(0x303030);g.drawRoundRect(boxX,boxY,boxWidth,boxHeight,10,10);
            g.drawString(label,boxX+7,boxY+4,Graphics.TOP|Graphics.LEFT);
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
            else if(currentGame==11) label=modeEventAt>gameClock?
                    "HIDEOUTS "+((modeEventAt-gameClock+999)/1000)+"s":
                    "FOUND "+(modeGoal-countZombies())+"/"+modeGoal;
            else if(currentGame==12) {
                String[] colors=new String[] {"RED","BLUE","GREEN","YELLOW"};
                label=simonRound>12?"SIMON SAYS COMPLETE":(simonRound==0?"GET READY":
                        "GO TO "+colors[simonTarget]+" "+Math.max(0,(int)((modeEventAt-gameClock)/1000))+"s");
            } else if(currentGame==13) label="CASH "+modeCash+" YOU "+tycoonLevel+"/9  3 BUILD";
            else if(currentGame==14) label="LIVES "+Math.max(0,3-defenseLeaked)+"  3 ATTACK";
            g.setColor(0xFFFFFF);
            g.drawString(label,getWidth()/2,getHeight()-3,Graphics.BOTTOM|Graphics.HCENTER);
        }

        private void drawInventory(Graphics g) {
            String[] items = currentGame==2 ? new String[] {"Espada","Bomba","Pala"} :
                    new String[] {"Espada","Bomba"};
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
                Texture2D texture;
                boolean zombie = "faceZomb.png".equals(resourceName);
                texture = zombie ? cachedZombieFaceTexture : cachedPlayerFaceTexture;
                if (texture == null) {
                    Image img = loadImageResource("/" + resourceName, "/meblox/" + resourceName);
                    if (img == null) {
                        modelError = resourceName + " not found in /meblox/ or JAR root";
                        return makeCurvedTexturedFace(makeSolidAppearance(fallbackColor));
                    }
                    Image textureImage = makePowerOfTwoImage(img);
                    Image2D image2D = new Image2D(Image2D.RGBA, textureImage);
                    texture = new Texture2D(image2D);
                    texture.setFiltering(Texture2D.FILTER_NEAREST, Texture2D.FILTER_NEAREST);
                    texture.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
                    texture.setBlending(Texture2D.FUNC_MODULATE);
                    if (zombie) cachedZombieFaceTexture = texture;
                    else cachedPlayerFaceTexture = texture;
                }

                Appearance ap = new Appearance();
                ap.setTexture(0, texture);
                Material mat = new Material();
                mat.setColor(Material.AMBIENT, 0xFFFFFFFF);
                mat.setColor(Material.DIFFUSE, 0xFFFFFFFF);
                mat.setColor(Material.EMISSIVE, 0xFFFFFFFF);
                ap.setMaterial(mat);
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
            boolean swingingSword = (currentGame == 2 || currentGame == 10 || currentGame == 14) &&
                    gameClock < swordAttackUntil;
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
            if (lanSession != null) { lanSession.close(); lanSession = null; }
            lanMenu = false;
            lanMessage = "";
            lanPlayers = null;
            for (int i=0;i<lanAvatars.length;i++) lanAvatars[i]=null;
            paused = false;
            configMode = false;
            menuMode = true;
            playMusic(3);
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

        private void drawSettingsPanel(Graphics g, boolean pausedPanel) {
            int width=getWidth(),height=getHeight();
            if(pauseOverlayPixels==null||pauseOverlayWidth!=width||pauseOverlayHeight!=height) {
                pauseOverlayWidth=width;pauseOverlayHeight=height;
                pauseOverlayPixels=new int[width*height];
                for(int i=0;i<pauseOverlayPixels.length;i++)pauseOverlayPixels[i]=0x9908121E;
            }
            g.drawRGB(pauseOverlayPixels,0,width,0,0,width,height,true);
            int rowHeight=Math.max(18,g.getFont().getHeight()+5);
            int optionCount=controlMapMode?Math.min(8,Math.max(1,(height-88)/rowHeight)):
                    Math.min(5,Math.max(1,(height-88)/rowHeight));
            int panelWidth=Math.min(270,Math.max(1,width-12));
            int panelHeight=Math.min(height-8,48+optionCount*rowHeight+34);
            int panelX=(width-panelWidth)/2,panelY=(height-panelHeight)/2;
            g.setColor(0x1A2D40);
            g.fillRoundRect(panelX,panelY,panelWidth,panelHeight,14,14);
            g.setColor(0xFFFFFF);
            g.drawString(controlMapMode?"MAP BUTTONS":"SETTINGS",width/2,panelY+8,
                    Graphics.TOP|Graphics.HCENTER);
            if(controlMapMode) {
                int first=Math.max(0,Math.min(controlSelection-(optionCount/2),
                        CONTROL_NAMES.length-optionCount));
                for(int row=0;row<optionCount;row++) {
                    int index=first+row,y=panelY+31+row*rowHeight;
                    g.setColor(index==controlSelection?0xF5C542:0x263B50);
                    g.fillRoundRect(panelX+7,y,panelWidth-14,rowHeight-2,7,7);
                    g.setColor(index==controlSelection?0x17202A:0xFFFFFF);
                    g.drawString(CONTROL_NAMES[index]+": "+controlKeyName(mappedControls[index]),
                            width/2,y+1,Graphics.TOP|Graphics.HCENTER);
                }
            } else {
                String speed=sensitivityLevel==0?"Low":(sensitivityLevel==1?"Normal":"High");
                String bots=botCountSetting==BOT_COUNT_RANDOM?"Random (3-5)":
                        (botCountSetting==BOT_COUNT_OFF?"Off":""+botCountSetting);
                String[] options={"Camera sensitivity: "+speed,
                    "Invert camera: "+(invertCamera?"On":"Off"),
                    "Bots: "+bots, "Map buttons", "Back"};
                int first=Math.max(0,Math.min(configSelection-(optionCount/2),
                        options.length-optionCount));
                for(int row=0;row<optionCount;row++) {
                    int index=first+row,y=panelY+31+row*rowHeight;
                    g.setColor(index==configSelection?0xF5C542:0x263B50);
                    g.fillRoundRect(panelX+7,y,panelWidth-14,rowHeight-2,7,7);
                    g.setColor(index==configSelection?0x17202A:0xFFFFFF);
                    g.drawString(options[index],width/2,y+1,Graphics.TOP|Graphics.HCENTER);
                }
            }
            g.setColor(0xFFFFFF);
            String help=controlMapMode?(awaitingControlKey?"Press 1-9 to assign":"UP/DOWN select  FIRE map"):
                    "UP/DOWN select  LEFT/RIGHT change";
            g.drawString(help,width/2,panelY+panelHeight-g.getFont().getHeight()*2-6,
                    Graphics.TOP|Graphics.HCENTER);
            g.drawString(settingsMessage.length()>0?settingsMessage:
                    (controlMapMode?"* BACK":"FIRE select  * BACK"),
                    width/2,panelY+panelHeight-g.getFont().getHeight()-4,
                    Graphics.TOP|Graphics.HCENTER);
        }

        private String controlKeyName(int keyCode) {
            for(int i=1;i<=9;i++)if(keyCode==KEY_NUM0+i)return ""+i;
            return "?";
        }

        private void handleSettingsInput(int keyCode,int action) {
            boolean up=action==UP||keyCode==KEY_NUM2;
            boolean down=action==DOWN||keyCode==KEY_NUM8;
            boolean leftKey=action==LEFT||keyCode==KEY_NUM4;
            boolean rightKey=action==RIGHT||keyCode==KEY_NUM6;
            boolean fire=action==FIRE||keyCode==KEY_NUM5;
            if(controlMapMode) {
                if(awaitingControlKey) {
                    int keyIndex=-1;
                    for(int i=1;i<=9;i++)if(keyCode==KEY_NUM0+i)keyIndex=i;
                    if(keyIndex<0) {
                        settingsMessage="Choose a number from 1 to 9";
                        return;
                    }
                    int assigned=KEY_NUM0+keyIndex;
                    for(int i=0;i<mappedControls.length;i++) {
                        if(i!=controlSelection&&mappedControls[i]==assigned) {
                            settingsMessage="That button is already assigned";
                            return;
                        }
                    }
                    mappedControls[controlSelection]=assigned;
                    awaitingControlKey=false;
                    settingsMessage=saveSettings()?"Assigned "+keyIndex+" - saved":
                            "Assigned, but could not save";
                    return;
                }
                if(keyCode==KEY_STAR||keyCode==KEY_POUND||keyCode==KEY_NUM0) {
                    controlMapMode=false;
                    settingsMessage="";
                    return;
                }
                if(up)controlSelection=(controlSelection+CONTROL_NAMES.length-1)%CONTROL_NAMES.length;
                else if(down)controlSelection=(controlSelection+1)%CONTROL_NAMES.length;
                else if(fire) {
                    awaitingControlKey=true;
                    settingsMessage="Press a number from 1 to 9";
                }
                return;
            }
            if(keyCode==KEY_STAR||keyCode==KEY_POUND) {
                if(menuSettings)menuSettings=false;
                else configMode=false;
                settingsMessage="";
                return;
            }
            if(up)configSelection=(configSelection+4)%5;
            else if(down)configSelection=(configSelection+1)%5;
            else if((leftKey||rightKey)&&configSelection<3) {
                changeCameraSetting(rightKey?1:-1);
            } else if(fire) {
                if(configSelection<3)changeCameraSetting(1);
                else if(configSelection==3) {
                    controlMapMode=true;
                    controlSelection=0;
                    awaitingControlKey=false;
                    settingsMessage="";
                } else {
                    if(menuSettings)menuSettings=false;
                    else configMode=false;
                }
            }
        }

        private void changeCameraSetting(int direction) {
            if (configSelection == 0) {
                sensitivityLevel = (sensitivityLevel + direction + 3) % 3;
                if (sensitivityLevel == 0) cameraSensitivity = 0.0015f;
                else if (sensitivityLevel == 1) cameraSensitivity = 0.0025f;
                else cameraSensitivity = 0.0040f;
            } else if (configSelection == 1) {
                invertCamera = !invertCamera;
            } else if (configSelection == 2) {
                botCountSetting=(botCountSetting+direction+7)%7;
            }
            settingsMessage=saveSettings()?"Saved":"Settings could not be saved";
        }

        private void handlePauseInput(int keyCode, int action) {
            boolean up = action == UP || keyCode == KEY_NUM2;
            boolean down = action == DOWN || keyCode == KEY_NUM8;
            boolean fire = action == FIRE || keyCode == KEY_NUM5;
            if (configMode) {
                handleSettingsInput(keyCode,action);
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
                    controlMapMode = false;
                    awaitingControlKey = false;
                    settingsMessage = "";
                } else if (pauseSelection == 2) {
                    try { startGame(currentGame); }
                    catch (Throwable t) { initError = "Restart: " + t.toString(); }
                } else {
                    exitToGameMenu();
                }
            }
        }

        private void drawPauseMenu(Graphics g) {
            if(configMode) {
                drawSettingsPanel(g,true);
                return;
            }
            int width = getWidth(), height = getHeight();
            if (pauseOverlayPixels == null || pauseOverlayWidth != width || pauseOverlayHeight != height) {
                pauseOverlayWidth = width; pauseOverlayHeight = height;
                pauseOverlayPixels = new int[width * height];
                for (int i = 0; i < pauseOverlayPixels.length; i++) pauseOverlayPixels[i] = 0x9908121E;
            }
            int panelWidth = Math.min(260, Math.max(1, width - 16));
            int rowHeight = Math.max(22, g.getFont().getHeight() + 8);
            int optionCount = 4;
            int panelHeight = Math.min(height - 8, 50 + optionCount * rowHeight + 32);
            int panelX = (width - panelWidth) / 2, panelY = (height - panelHeight) / 2;
            g.drawRGB(pauseOverlayPixels, 0, width, 0, 0, width, height, true);
            g.setColor(0x1A2D40);
            g.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 14, 14);
            g.setColor(0xFFFFFF);
            g.drawString("PAUSED", width / 2,
                    panelY + 10, Graphics.TOP | Graphics.HCENTER);
            String[] options;
            int selected;
            options = new String[] {"Resume", "Configure", "Restart", "Exit to game menu"};
            selected = pauseSelection;
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
            g.drawString("UP/DOWN: select  FIRE: confirm",
                    width / 2, footerY, Graphics.TOP | Graphics.HCENTER);
            g.drawString("RIGHT SOFTKEY: resume",
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
                if(menuSettings) {
                    handleSettingsInput(keyCode,k);
                    repaint();
                    return;
                }
                if(keyCode==KEY_POUND && !lanMenu) {
                    lanMenu=true;
                    lanMenuSelection=0;
                    repaint();
                    return;
                }
                if(keyCode==KEY_STAR) {
                    menuSettings=true;
                    configSelection=0;
                    controlMapMode=false;
                    awaitingControlKey=false;
                    settingsMessage="";
                    repaint();
                    return;
                }
                if (lanMenu) {
                    if (k == UP || keyCode == KEY_NUM2)
                        lanMenuSelection = (lanMenuSelection + 2) % 3;
                    else if (k == DOWN || keyCode == KEY_NUM8)
                        lanMenuSelection = (lanMenuSelection + 1) % 3;
                    else if (keyCode == KEY_POUND) lanMenu = false;
                    else if (keyCode == KEY_NUM5 || k == FIRE) {
                        if (lanMenuSelection == 0) {
                            try {
                                initError = "";
                                startGame(selectedGame);
                                lanSession = LanSession.host(selectedGame);
                                lanPlayers = null;
                                lanMessage = "Host " + lanSession.getLocalAddress() + ":" +
                                        LanSession.PORT + " (same Wi-Fi)";
                                modelStatus = "LAN host: " + lanMessage;
                            } catch (Throwable t) {
                                lanMessage = "LAN host: " + t.toString();
                                modelError = lanMessage;
                                menuMode = true;
                                lanMenu = true;
                            }
                        } else if (lanMenuSelection == 1) {
                            lanAddressBox = new TextBox("Join LAN game",
                                    "", 32, TextField.ANY);
                            lanAddressBox.addCommand(lanConnectCommand);
                            lanAddressBox.addCommand(lanCancelCommand);
                            lanAddressBox.setCommandListener(this);
                            Display.getDisplay(midlet).setCurrent(lanAddressBox);
                            return;
                        } else lanMenu = false;
                    }
                    repaint();
                    return;
                }
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
            // by the right softkey. Reserve the mapped action key so softkey detection stays reliable.
            boolean reservedThree = (currentGame == 3 && keyCode == KEY_NUM3) ||
                    ((currentGame == 2 || currentGame == 10 ||
                    currentGame == 13 || currentGame == 14) &&
                    keyCode == mappedControls[CONTROL_USE]);
            if (!reservedThree && isRightSoftkey(keyCode, k)) {
                if (paused && configMode) {
                    configMode = false;
                    controlMapMode = false;
                    awaitingControlKey = false;
                } else if (paused) paused = false;
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
            if ((currentGame == 2 || currentGame == 10 || currentGame == 14) &&
                    (keyCode == mappedControls[CONTROL_PREVIOUS] || keyCode == mappedControls[CONTROL_NEXT])) {
                int n=currentGame==2?3:2;
                inventorySelection=(inventorySelection+(keyCode==mappedControls[CONTROL_NEXT]?1:n-1))%n;
                repaint(); return;
            }
            if ((currentGame == 2 || currentGame == 10 || currentGame == 14) &&
                    keyCode == mappedControls[CONTROL_USE]) {
                useInventoryItem(); repaint(); return;
            }
            if (currentGame == 13 && keyCode == mappedControls[CONTROL_USE]) {
                useTycoonAction();
                repaint();
                return;
            }
            if (keyCode == mappedControls[CONTROL_FORWARD]) forward = true;
            else if (keyCode == mappedControls[CONTROL_BACK]) back = true;
            else if (keyCode == mappedControls[CONTROL_LEFT]) left = true;
            else if (keyCode == mappedControls[CONTROL_RIGHT]) right = true;
            else if (k == LEFT) camLeft = true;
            else if (k == RIGHT) camRight = true;
            else if (k == UP) camUp = true;
            else if (k == DOWN) camDown = true;
            else if ((keyCode == mappedControls[CONTROL_JUMP] || k == FIRE) && !jumping) {
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
                saveSettings();
            }
        }
        protected void keyReleased(int keyCode) {
            int k = getGameAction(keyCode);
            if (keyCode == mappedControls[CONTROL_FORWARD]) forward = false;
            else if (keyCode == mappedControls[CONTROL_BACK]) back = false;
            else if (keyCode == mappedControls[CONTROL_LEFT]) left = false;
            else if (keyCode == mappedControls[CONTROL_RIGHT]) right = false;
            else if (k == LEFT) camLeft = false;
            else if (k == RIGHT) camRight = false;
            else if (k == UP) camUp = false;
            else if (k == DOWN) camDown = false;
        }
    }
}