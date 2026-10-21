package meblox;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.m3g.*;

/**
 * Roblox3DME: minimal JSR-184 playground/controller.
 * Put noobidle.m3g, noobrun.m3g, noobjump.m3g and face.png
 * in the /meblox/ resource folder in the JAR. Intended for MIDP 2.0 + JSR-184 devices.
 */
public final class main extends MIDlet {
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
        private final main midlet;
        private Thread thread;
        private boolean running;
        private Graphics3D g3d = Graphics3D.getInstance();
        private World world;
        private Camera camera;
        private Group avatar;
        private Object3D idleObject, runObject, jumpObject;
        private Group activeModel;
        private Mesh leftArm, rightArm, leftLeg, rightLeg;
        private int animation = 0; // 0 idle, 1 run, 2 jump
        private float px = 0, pz = 0, yaw = 0;
        private float camYaw = 0, camPitch = 0.22f;
        private float camDistance = 5.0f;
        private float jumpY = 0, jumpVelocity = 0;
        private long lastTime;
        private int animTime;
        private boolean forward, back, left, right, camLeft, camRight, camUp, camDown, jumping;
        private boolean loaded;
        private String modelStatus = "Models: not loaded";
        private String modelError = "";
        private String initError = "";

        Game(main m) { midlet = m; setFullScreenMode(true); }
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
            world = new World();
            world.setBackground(new Background());
            Background bg = world.getBackground();
            bg.setColor(0x78BCE8);
            bg.setDepthClearEnable(true);
            bg.setColorClearEnable(true);

            camera = new Camera();
            camera.setPerspective(65.0f, 1.0f, 0.1f, 100.0f);
            world.addChild(camera);
            world.setActiveCamera(camera);

            // Large, simple green test platform.
            world.addChild(makeBox(0, -0.25f, 0, 24, 0.5f, 24, 0x54A64B));
            // Colored stepping blocks to test depth, scale, and movement.
            world.addChild(makeBox(0, 0.25f, -5, 2, 1, 2, 0xD94B45));
            world.addChild(makeBox(3, 0.5f, -8, 2, 2, 2, 0xE3C44D));
            world.addChild(makeBox(-3, 0.75f, -8, 2, 3, 2, 0x4779D1));
            world.addChild(makeBox(0, 1.5f, -11, 3, 4, 3, 0xB56AC8));

            // Build the avatar procedurally: no external .m3g files are needed.
            // Only face.png is loaded as a texture; if missing, the face falls back to white.
            avatar = buildNoob();
            world.addChild(avatar);
            modelStatus = "Procedural Noob: OK | 2/8 move, 5 jump";
            loaded = true;
        }

        private Object3D loadModel(String preferredPath, String fallbackPath) {
            String[] paths = new String[] { preferredPath, fallbackPath };
            String lastError = "";
            for (int p = 0; p < paths.length; p++) {
                String path = paths[p];
                java.io.InputStream check = null;
                try {
                    // Verify that the resource is actually inside the packaged MIDlet JAR.
                    check = getClass().getResourceAsStream(path);
                    if (check == null) {
                        lastError = path + ": resource not found in JAR";
                        continue;
                    }
                    check.close();
                    check = null;

                    Object3D[] objects = Loader.load(path);
                    if (objects == null || objects.length == 0) {
                        lastError = path + ": Loader returned no objects";
                        continue;
                    }

                    // Root order is unspecified; prefer a Group, then any renderable Node.
                    for (int i = 0; i < objects.length; i++) {
                        if (objects[i] instanceof Group && !(objects[i] instanceof World))
                            return objects[i];
                    }
                    for (int i = 0; i < objects.length; i++) {
                        if (objects[i] instanceof Node && !(objects[i] instanceof Camera))
                            return objects[i];
                    }
                    lastError = path + ": no renderable Node among " + objects.length + " roots";
                } catch (Throwable t) {
                    lastError = path + ": " + t.toString();
                } finally {
                    if (check != null) {
                        try { check.close(); } catch (Throwable ignored) {}
                    }
                }
            }
            modelError = lastError;
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
            activateTracks(obj);
        }

        // Exporters may attach animation tracks to child nodes instead of the root.
        private void activateTracks(Object3D obj) {
            try {
                int n = obj.getAnimationTrackCount();
                for (int i = 0; i < n; i++) {
                    AnimationController c = obj.getAnimationTrack(i).getController();
                    if (c != null) {
                        c.setActiveInterval(0, 0x7fffffff);
                        c.setSpeed(1.0f, animTime);
                        c.setPosition(0.0f, animTime);
                    }
                }
                if (obj instanceof Group) {
                    Group group = (Group)obj;
                    for (int i = 0; i < group.getChildCount(); i++)
                        activateTracks(group.getChild(i));
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
            animTime += dt;
            float step = dt * 0.004f;
            float cameraTurn = dt * 0.0025f;
            if (camLeft) camYaw -= cameraTurn;
            if (camRight) camYaw += cameraTurn;
            if (camUp) camPitch += cameraTurn;
            if (camDown) camPitch -= cameraTurn;
            // Classic third-person camera: allow looking up/down, but never flip over.
            if (camPitch > 1.20f) camPitch = 1.20f;
            if (camPitch < -1.20f) camPitch = -1.20f;
            float dx = 0, dz = 0;
            float sin = (float)Math.sin(camYaw), cos = (float)Math.cos(camYaw);
            if (forward) { dx += sin * step; dz -= cos * step; }
            if (back) { dx -= sin * step; dz += cos * step; }
            if (left) { dx -= cos * step; dz -= sin * step; }
            if (right) { dx += cos * step; dz += sin * step; }
            px += dx; pz += dz;
            // Roblox-style character facing: follow the camera's horizontal heading,
            // even while standing still. Movement remains camera-relative.
            yaw = camYaw;
            if (jumping) {
                jumpY += jumpVelocity * dt;
                jumpVelocity -= 0.00003f * dt;
                if (jumpY <= 0) {
                    jumpY = 0;
                    jumpVelocity = 0;
                    jumping = false;
                }
            }
            if (avatar != null) {
                avatar.setTranslation(px, jumpY, pz);
                avatar.setOrientation((float)(yaw * 57.29578f), 0, 1, 0);
            }
            updateCamera();
            if (jumping) animation = 2;
            else if (forward || back || left || right) animation = 1;
            else animation = 0;
            animateNoob();
            if (world != null) {
                try { world.animate(animTime); } catch (Throwable t) {}
            }
        }

        private void updateCamera() {
            float horizontal = (float)Math.cos(camPitch) * camDistance;
            float cx = px - (float)Math.sin(camYaw) * horizontal;
            float cy = 1.2f + jumpY + (float)Math.sin(camPitch) * camDistance;
            float cz = pz + (float)Math.cos(camYaw) * horizontal;
            // M3G Transform has no setLookAt helper; set camera position and orientation.
            // Camera looks along local -Z. Orbit position is opposite the look direction,
            // so yaw must have the opposite sign to camYaw.
            camera.setOrientation((float)(-camYaw * 57.29578f), 0.0f, 1.0f, 0.0f);
            camera.postRotate((float)(-camPitch * 57.29578f), 1.0f, 0.0f, 0.0f);
            camera.setTranslation(cx, cy, cz);
        }

        protected void paint(Graphics g) {
            g.setColor(0x78BCE8);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (loaded && world != null) {
                boolean bound = false;
                try {
                    g3d.bindTarget(g, true, Graphics3D.DITHER);
                    bound = true;
                    g3d.setViewport(0, 0, getWidth(), getHeight());
                    camera.setPerspective(65.0f, (float)getWidth()/(float)getHeight(), 0.1f, 100.0f);
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
                g.drawString("Check .m3g resources", 4, 22, Graphics.TOP|Graphics.LEFT);
            }
            g.setColor(0xFFFFFF);
            g.drawString("ROBLOX 3DME | DPAD: CAMERA", 3, 2, Graphics.TOP|Graphics.LEFT);
            g.drawString("2/8 move  4/6 strafe  5 jump  */# zoom", 3, getHeight()-18, Graphics.TOP|Graphics.LEFT);
            g.drawString(modelStatus, 3, 20, Graphics.TOP|Graphics.LEFT);
            if (modelError.length() > 0) drawWrapped(g, modelError, 3, 38, getWidth() - 6);
            if (initError.length() > 0) drawWrapped(g, initError, 3, 72, getWidth() - 6);
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
            Group model = new Group();

            // Classic blocky noob proportions, in local coordinates.
            model.addChild(makeBox(0, 1.45f, 0, 0.78f, 0.95f, 0.42f, 0x1769AA)); // classic blue torso
            model.addChild(makeBox(0, 2.18f, 0, 0.68f, 0.62f, 0.62f, 0xF5C542)); // classic yellow head
            model.addChild(makeTexturedFace()); // front face decal
            leftArm = (Mesh)makeBox(-0.52f, 1.48f, 0, 0.24f, 0.88f, 0.28f, 0xF5C542);
            rightArm = (Mesh)makeBox(0.52f, 1.48f, 0, 0.24f, 0.88f, 0.28f, 0xF5C542);
            leftLeg = (Mesh)makeBox(-0.21f, 0.52f, 0, 0.30f, 0.78f, 0.34f, 0x2E9E45);
            rightLeg = (Mesh)makeBox(0.21f, 0.52f, 0, 0.30f, 0.78f, 0.34f, 0x2E9E45);
            model.addChild(leftArm);
            model.addChild(rightArm);
            model.addChild(leftLeg);
            model.addChild(rightLeg);
            return model;
        }

        private Node makeTexturedFace() {
            try {
                javax.microedition.lcdui.Image img = null;
                java.io.InputStream in = getClass().getResourceAsStream("/meblox/face.png");
                if (in == null) in = getClass().getResourceAsStream("/face.png");
                if (in != null) {
                    try { img = javax.microedition.lcdui.Image.createImage(in); }
                    finally { try { in.close(); } catch (Throwable ignored) {} }
                }
                if (img == null) return makeBox(0, 2.18f, -0.316f, 0.48f, 0.40f, 0.012f, 0xFFFFFF);

                Image2D image2D = new Image2D(Image2D.RGBA, img);
                Texture2D texture = new Texture2D(image2D);
                texture.setFiltering(Texture2D.FILTER_NEAREST, Texture2D.FILTER_NEAREST);
                texture.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
                Appearance ap = new Appearance();
                ap.setTexture(0, texture);
                Material mat = new Material();
                mat.setColor(Material.AMBIENT, 0xFFFFFFFF);
                mat.setColor(Material.DIFFUSE, 0xFFFFFFFF);
                mat.setColor(Material.EMISSIVE, 0xFFFFFFFF);
                ap.setMaterial(mat);

                // Quad on the front (-Z) side of the head, with full-image UVs.
                short[] p = new short[] {-270,-210,0, 270,-210,0, -270,210,0, 270,210,0};
                VertexArray pos = new VertexArray(4, 3, 2);
                pos.set(0, 4, p);
                short[] uv = new short[] {0,1000, 1000,1000, 0,0, 1000,0};
                VertexArray texCoords = new VertexArray(4, 2, 2);
                texCoords.set(0, 4, uv);
                VertexBuffer vb = new VertexBuffer();
                vb.setPositions(pos, 0.001f, null);
                vb.setTexCoords(0, texCoords, 0.001f, null);
                TriangleStripArray indices = new TriangleStripArray(0, new int[] {4});
                Mesh face = new Mesh(vb, new IndexBuffer[] {indices}, new Appearance[] {ap});
                face.setTranslation(0, 2.18f, -0.317f);
                return face;
            } catch (Throwable t) {
                return makeBox(0, 2.18f, -0.316f, 0.48f, 0.40f, 0.012f, 0xFFFFFF);
            }
        }

        private void animateNoob() {
            if (leftArm == null || rightArm == null || leftLeg == null || rightLeg == null) return;
            float swing = 0.0f;
            if (animation == 1) swing = (float)Math.sin(animTime * 0.012f) * 32.0f;
            else if (animation == 2) swing = -18.0f;
            leftArm.setOrientation(swing, 1, 0, 0);
            rightArm.setOrientation(-swing, 1, 0, 0);
            leftLeg.setOrientation(-swing, 1, 0, 0);
            rightLeg.setOrientation(swing, 1, 0, 0);
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

        protected void keyPressed(int keyCode) {
            int k = getGameAction(keyCode);
            if (keyCode == KEY_NUM2) forward = true;
            else if (keyCode == KEY_NUM8) back = true;
            else if (keyCode == KEY_NUM4) left = true;
            else if (keyCode == KEY_NUM6) right = true;
            else if (k == LEFT) camLeft = true;
            else if (k == RIGHT) camRight = true;
            else if (k == UP) camUp = true;
            else if (k == DOWN) camDown = true;
            else if ((keyCode == KEY_NUM5 || k == FIRE) && !jumping) {
                jumping = true;
                jumpVelocity = 0.008f;
                switchAnimation(2);
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