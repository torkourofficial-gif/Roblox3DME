import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.m3g.*;

/**
 * Roblox3DME: minimal JSR-184 playground/controller.
 * Put noobidle.m3g, noobrun.m3g, noobjump.m3g and face.png
 * in the MIDlet resource root. Intended for MIDP 2.0 + JSR-184 devices.
 */
public final class Roblox3DME extends MIDlet {
    private Game game;
    protected void startApp() {
        if (game == null) game = new Game(this);
        Display.getDisplay(this).setCurrent(game);
        game.start();
    }
    protected void pauseApp() { if (game != null) game.stop(); }
    protected void destroyApp(boolean unconditional) { if (game != null) game.stop(); }
    void quit() { destroyApp(true); notifyDestroyed(); }

    private static final class Game extends Canvas implements Runnable {
        private final Roblox3DME midlet;
        private Thread thread;
        private boolean running;
        private Graphics3D g3d = Graphics3D.getInstance();
        private World world;
        private Camera camera;
        private Group avatar;
        private Object3D idleObject, runObject, jumpObject;
        private Group activeModel;
        private int animation = 0; // 0 idle, 1 run, 2 jump
        private float px = 0, pz = 0, yaw = 0;
        private float camYaw = 0, camPitch = 0.22f;
        private float camDistance = 5.0f;
        private long lastTime;
        private int animTime;
        private boolean forward, back, left, right, camLeft, camRight, camUp, camDown, jumping;
        private boolean loaded;

        Game(Roblox3DME m) { midlet = m; setFullScreenMode(true); }
        synchronized void start() {
            if (running) return;
            running = true;
            thread = new Thread(this);
            thread.start();
        }
        synchronized void stop() { running = false; thread = null; }

        public void run() {
            try { init3D(); } catch (Throwable t) { loaded = false; }
            lastTime = System.currentTimeMillis();
            while (running) {
                long now = System.currentTimeMillis();
                int dt = (int)(now - lastTime);
                if (dt < 1) dt = 1;
                if (dt > 100) dt = 100;
                lastTime = now;
                update(dt);
                renderFrame();
                try { Thread.sleep(25); } catch (InterruptedException e) {}
            }
        }

        private void init3D() throws Exception {
            world = new World();
            world.setBackground(new Background());
            Background bg = world.getBackground();
            bg.setColor(0x78BCE8);
            bg.setDepthClearEnable(true);
            bg.setColorClearEnable(true);

            camera = new Camera();
            camera.setPerspective(65.0f, 1.0f, 0.1f, 100.0f);
            world.addChild(camera);

            // Large, simple green test platform.
            world.addChild(makeBox(0, -0.25f, 0, 24, 0.5f, 24, 0x54A64B));
            // Colored stepping blocks to test depth, scale, and movement.
            world.addChild(makeBox(0, 0.25f, -5, 2, 1, 2, 0xD94B45));
            world.addChild(makeBox(3, 0.5f, -8, 2, 2, 2, 0xE3C44D));
            world.addChild(makeBox(-3, 0.75f, -8, 2, 3, 2, 0x4779D1));
            world.addChild(makeBox(0, 1.5f, -11, 3, 4, 3, 0xB56AC8));

            idleObject = loadModel("/noobidle.m3g");
            runObject = loadModel("/noobrun.m3g");
            jumpObject = loadModel("/noobjump.m3g");
            activeModel = asGroup(idleObject);
            if (activeModel != null) {
                avatar = new Group();
                avatar.addChild(activeModel);
                world.addChild(avatar);
                playAnimation(idleObject, 0);
            }
            loaded = true;
        }

        private Object3D loadModel(String path) {
            try {
                Object3D[] objects = Loader.load(getClass().getResourceAsStream(path));
                if (objects != null && objects.length > 0) {
                    for (int i = 0; i < objects.length; i++)
                        if (objects[i] instanceof Group) return objects[i];
                    return objects[0];
                }
            } catch (Throwable t) {}
            return null;
        }

        private Group asGroup(Object3D o) {
            if (o instanceof Group) return (Group)o;
            if (o instanceof Node) {
                Group wrapper = new Group();
                wrapper.addChild((Node)o);
                return wrapper;
            }
            return null;
        }

        private void playAnimation(Object3D obj, int index) {
            if (obj == null) return;
            try {
                int n = obj.getAnimationTrackCount();
                for (int i = 0; i < n; i++) {
                    AnimationTrack track = obj.getAnimationTrack(i);
                    AnimationController c = track.getController();
                    if (c != null) {
                        c.setActiveInterval(0, 0x7fffffff);
                        c.setSpeed(1.0f, animTime);
                        c.setPosition(0.0f, animTime);
                    }
                }
            } catch (Throwable t) {}
        }

        private void switchAnimation(int next) {
            if (next == animation) return;
            Object3D chosen = next == 1 ? runObject : (next == 2 ? jumpObject : idleObject);
            Group replacement = asGroup(chosen);
            if (replacement == null || avatar == null) { animation = next; return; }
            try {
                if (activeModel != null) avatar.removeChild(activeModel);
                activeModel = replacement;
                avatar.addChild(activeModel);
                playAnimation(chosen, next);
            } catch (Throwable t) {}
            animation = next;
        }

        private void update(int dt) {
            animTime += dt;
            float step = dt * 0.004f;
            float cameraTurn = dt * 0.0025f;
            if (camLeft) camYaw -= cameraTurn;
            if (camRight) camYaw += cameraTurn;
            if (camUp) camPitch += cameraTurn;
            if (camDown) camPitch -= cameraTurn;
            if (camPitch > 0.95f) camPitch = 0.95f;
            if (camPitch < -0.35f) camPitch = -0.35f;
            float dx = 0, dz = 0;
            float sin = (float)Math.sin(camYaw), cos = (float)Math.cos(camYaw);
            if (forward) { dx += sin * step; dz -= cos * step; }
            if (back) { dx -= sin * step; dz += cos * step; }
            if (left) { dx -= cos * step; dz -= sin * step; }
            if (right) { dx += cos * step; dz += sin * step; }
            px += dx; pz += dz;
            if (dx != 0 || dz != 0) yaw = (float)Math.atan2(dx, -dz);
            if (avatar != null) {
                avatar.setTranslation(px, jumping ? 0.8f : 0.0f, pz);
                avatar.setOrientation((float)(yaw * 57.29578f), 0, 1, 0);
            }
            updateCamera();
            if (jumping) switchAnimation(2);
            else if (forward || back || left || right) switchAnimation(1);
            else switchAnimation(0);
            if (world != null) {
                try { world.animate(animTime); } catch (Throwable t) {}
            }
        }

        private void updateCamera() {
            float horizontal = (float)Math.cos(camPitch) * camDistance;
            float cx = px - (float)Math.sin(camYaw) * horizontal;
            float cy = 1.2f + (float)Math.sin(camPitch) * camDistance;
            float cz = pz + (float)Math.cos(camYaw) * horizontal;
            Transform view = new Transform();
            view.setLookAt(cx, cy, cz, px, 1.0f, pz, 0.0f, 1.0f, 0.0f);
            camera.setTransform(view);
        }

        private void renderFrame() {
            Graphics g = getGraphics();
            if (g == null) return;
            g.setColor(0x78BCE8); g.fillRect(0, 0, getWidth(), getHeight());
            if (loaded && world != null) {
                try {
                    g3d.bindTarget(g, true, Graphics3D.DITHER);
                    g3d.setViewport(0, 0, getWidth(), getHeight());
                    camera.setPerspective(65.0f, (float)getWidth()/(float)getHeight(), 0.1f, 100.0f);
                    g3d.render(world);
                } catch (Throwable t) {
                    g.setColor(0xFFFFFF); g.drawString("M3G render error", 3, 3, Graphics.TOP|Graphics.LEFT);
                } finally {
                    try { g3d.releaseTarget(); } catch (Throwable t) {}
                }
            } else {
                g.setColor(0xFFFFFF);
                g.drawString("Roblox3DME - loading model failed", 4, 4, Graphics.TOP|Graphics.LEFT);
                g.drawString("Check .m3g resource names", 4, 22, Graphics.TOP|Graphics.LEFT);
            }
            g.setColor(0xFFFFFF);
            g.drawString("ROBLOX 3DME | DPAD: CAMERA", 3, 2, Graphics.TOP|Graphics.LEFT);
            g.drawString("2/8 move  4/6 strafe  5 jump  */# zoom", 3, getHeight()-18, Graphics.TOP|Graphics.LEFT);
            flushGraphics();
        }

        private Node makeBox(float x, float y, float z, float sx, float sy, float sz, int color) {
            float[] p = new float[] {
                -sx/2,-sy/2,-sz/2, sx/2,-sy/2,-sz/2, -sx/2,sy/2,-sz/2, sx/2,sy/2,-sz/2,
                -sx/2,-sy/2,sz/2, sx/2,-sy/2,sz/2, -sx/2,sy/2,sz/2, sx/2,sy/2,sz/2
            };
            int[] indices = new int[] {
                0,2,1, 1,2,3, 4,5,6, 5,7,6,
                0,1,4, 1,5,4, 2,6,3, 3,6,7,
                0,4,2, 2,4,6, 1,3,5, 3,7,5
            };
            VertexArray va = new VertexArray(8, 3, 2);
            short[] coords = new short[p.length];
            for (int i=0;i<p.length;i++) coords[i]=(short)(p[i]*1000);
            va.set(0, 8, coords);
            VertexBuffer vb = new VertexBuffer();
            vb.setPositions(va, 0.001f, null);
            TriangleArray ta = new TriangleArray(0, indices);
            Appearance ap = new Appearance();
            Material mat = new Material();
            mat.setColor(Material.AMBIENT, color);
            mat.setColor(Material.DIFFUSE, color);
            mat.setColor(Material.EMISSIVE, color);
            mat.setLightingEnable(false);
            ap.setMaterial(mat);
            Mesh mesh = new Mesh(vb, new IndexBuffer[] { ta }, new Appearance[] { ap });
            mesh.setTranslation(x,y,z);
            return mesh;
        }

        protected void keyPressed(int keyCode) {
            int k = getGameAction(keyCode);
            if (keyCode == KEY_NUM2) forward = true;
            else if (keyCode == KEY_NUM8) back = true;
            else if (k == LEFT) camLeft = true;
            else if (k == RIGHT) camRight = true;
            else if (keyCode == KEY_NUM4) left = true;
            else if (keyCode == KEY_NUM6) right = true;
            else if (k == UP) camUp = true;
            else if (k == DOWN) camDown = true;
            else if (keyCode == KEY_NUM5 || k == FIRE) { jumping = true; switchAnimation(2); }
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
            else if (k == LEFT) camLeft = false;
            else if (k == RIGHT) camRight = false;
            else if (keyCode == KEY_NUM4) left = false;
            else if (keyCode == KEY_NUM6) right = false;
            else if (k == UP) camUp = false;
            else if (k == DOWN) camDown = false;
            else if (keyCode == KEY_NUM5 || k == FIRE) jumping = false;
        }
    }
}