package meblox;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.m3g.*;

/**
 * Roblox3DME: minimal JSR-184 playground/controller.
 * The character is generated in Java; put face.png in /meblox/ (or the JAR root).
 * Intended for MIDP 2.0 + JSR-184 devices.
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
        private Group activeModel;
        private Mesh leftArm, rightArm, leftLeg, rightLeg;
        private int animation = 0; // 0 idle, 1 run, 2 jump
        private float px = 0, pz = 0, yaw = 0;
        private float camYaw = 0, camPitch = 0.22f;
        private float camDistance = 5.0f;
        private float jumpY = 0;
        private long lastTime;
        private int animTime;
        private boolean forward, back, left, right, camLeft, camRight, camUp, camDown, jumping;
        private boolean loaded;
        private boolean menuMode = true;
        private int selectedGame;
        private Image logoSource, titleBackgroundSource, skySource;
        private Image scaledLogo;
        private int[] menuPanPixels;
        private float menuPanScale;
        private float menuPanWidth;
        private Image2D skyBackgroundImage;
        private Background sceneBackground;
        private int cachedMenuWidth = -1, cachedMenuHeight = -1;
        private long menuPanTime;
        private float playerVelocityY;
        private boolean grounded = true, dead, lobbyMode;
        private long gameClock, respawnAt, invulnerableUntil;
        private PhysicsPiece[] deathPieces;
        private Group protectionAura;
        private Material protectionAuraMaterial;
        private String modelStatus = "Select a game";
        private String modelError = "";
        private String initError = "";

        private static final class PhysicsPiece {
            Node node;
            float x, y, z, vx, vy, vz, rotation, spin, spinX, spinY, spinZ;
            PhysicsPiece(Node n, float px, float py, float pz,
                    float velX, float velY, float velZ, float spinRate,
                    float axisX, float axisY, float axisZ) {
                node = n; x = px; y = py; z = pz;
                vx = velX; vy = velY; vz = velZ; spin = spinRate;
                spinX = axisX; spinY = axisY; spinZ = axisZ;
            }
        }

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
            menuMode = false;
            loaded = false;
            px = 0;
            pz = 0;
            yaw = 0;
            jumpY = 0;
            playerVelocityY = 0;
            jumping = false;
            forward = back = left = right = false;
            playerVelocityY = 0;
            grounded = true;
            dead = false;
            lobbyMode = game == 1;
            gameClock = 0;
            invulnerableUntil = 0;
            deathPieces = null;

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
            camera.setPerspective(65.0f, 1.0f, 0.1f, 100.0f);
            world.addChild(camera);
            world.setActiveCamera(camera);

            // Both selections share the same green baseplate.
            world.addChild(makeBox(0, -0.25f, 0, 24, 0.5f, 24, 0x54A64B));
            if (game == 0) {
                // Obby course: stepping blocks are only present in this selection.
                world.addChild(makeBox(0, 0.25f, -5, 2, 1, 2, 0xD94B45));
                world.addChild(makeBox(3, 0.5f, -8, 2, 2, 2, 0xE3C44D));
                world.addChild(makeBox(-3, 0.75f, -8, 2, 3, 2, 0x4779D1));
                world.addChild(makeBox(0, 1.5f, -11, 3, 4, 3, 0xB56AC8));
            } else {
                // Red touch-to-die test block in the baseplate lobby.
                world.addChild(makeBox(3, 0.35f, -3, 1.2f, 0.7f, 1.2f, 0xE32636));
            }

            avatar = new Group();
            activeModel = buildNoob();
            avatar.addChild(activeModel);
            world.addChild(avatar);
            protectionAura = null;
            animation = 0;
            animateNoob();
            updateCamera();
            modelStatus = game == 0 ? "Obby" : "Baseplate";
            loaded = true;
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
            if (width <= 0 || height <= 0 ||
                    (width == cachedMenuWidth && height == cachedMenuHeight)) return;
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
            int buttonHeight = Math.max(28, getHeight() / 10);
            int gap = Math.max(8, buttonHeight / 4);
            int startY = getHeight() / 2 - buttonHeight - gap / 2;
            String[] games = new String[] {"Obby", "Baseplate"};
            Font font = g.getFont();
            for (int i = 0; i < games.length; i++) {
                int y = startY + i * (buttonHeight + gap);
                g.setColor(i == selectedGame ? 0xF5C542 : 0x203040);
                g.fillRoundRect((getWidth() - buttonWidth) / 2, y,
                        buttonWidth, buttonHeight, 10, 10);
                g.setColor(i == selectedGame ? 0x17202A : 0xFFFFFF);
                g.drawString(games[i], getWidth() / 2, y + (buttonHeight - font.getHeight()) / 2,
                        Graphics.TOP | Graphics.HCENTER);
            }
            g.setColor(0xFFFFFF);
            g.drawString("UP/DOWN - FIRE TO PLAY", getWidth() / 2,
                    getHeight() - font.getHeight() - 8, Graphics.TOP | Graphics.HCENTER);
            if (modelError.length() > 0)
                g.drawString(modelError, 4, 4, Graphics.TOP | Graphics.LEFT);
            if (initError.length() > 0)
                g.drawString(initError, 4, font.getHeight() + 6, Graphics.TOP | Graphics.LEFT);
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
                updateDeathPieces(dt);
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
            animTime += dt;
            float step = dt * 0.004f;
            float cameraTurn = dt * 0.0025f;
            if (camLeft) camYaw -= cameraTurn;
            if (camRight) camYaw += cameraTurn;
            if (camYaw > 3.14159265f) camYaw -= 6.2831853f;
            if (camYaw < -3.14159265f) camYaw += 6.2831853f;
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
            // Gravity applies continuously; landing checks cover the floor and obby platforms.
            float previousY = jumpY;
            if (!grounded) {
                jumpY += playerVelocityY * dt;
                playerVelocityY -= 0.000018f * dt;
            }
            resolveLanding(previousY);
            if (!grounded && jumpY < -8.0f) {
                beginDeath();
                return;
            }
            if (lobbyMode && gameClock >= invulnerableUntil && jumpY < 1.1f &&
                    Math.abs(px - 3.0f) < 0.95f && Math.abs(pz + 3.0f) < 0.95f) {
                beginDeath();
                return;
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
        }

        private void resolveLanding(float previousY) {
            float top = Float.NEGATIVE_INFINITY;
            if (Math.abs(px) < 11.5f && Math.abs(pz) < 11.5f) top = 0.0f;
            if (!lobbyMode) {
                if (onPlatform(0, -5, 1, 1) && platformCanSupport(previousY, 0.75f)) top = Math.max(top, 0.75f);
                if (onPlatform(3, -8, 1, 1) && platformCanSupport(previousY, 1.5f)) top = Math.max(top, 1.5f);
                if (onPlatform(-3, -8, 1, 1) && platformCanSupport(previousY, 2.25f)) top = Math.max(top, 2.25f);
                if (onPlatform(0, -11, 1.5f, 1.5f) && platformCanSupport(previousY, 3.5f)) top = Math.max(top, 3.5f);
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
            addDeathPiece(0, makeRoundedCylinderHead(), 0, 2.19f, 0, 0xF5C542, c, sn);
            addDeathPiece(1, makeBox(0,0,0,0.78f,0.95f,0.40f,0x1769AA), 0,1.45f,0,0x1769AA,c,sn);
            addDeathPiece(2, makeBox(0,0,0,0.30f,0.88f,0.40f,0xF5C542), -0.54f,1.48f,0,0xF5C542,c,sn);
            addDeathPiece(3, makeBox(0,0,0,0.30f,0.88f,0.40f,0xF5C542), 0.54f,1.48f,0,0xF5C542,c,sn);
            addDeathPiece(4, makeBox(0,0,0,0.36f,0.78f,0.40f,0x2E9E45), -0.20f,0.52f,0,0x2E9E45,c,sn);
            addDeathPiece(5, makeBox(0,0,0,0.36f,0.78f,0.40f,0x2E9E45), 0.20f,0.52f,0,0x2E9E45,c,sn);
        }

        private void addDeathPiece(int index, Node node, float ox, float oy, float oz,
                int color, float c, float sn) {
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
                    35 + index * 5, axisX, axisY, axisZ);
        }

        private void updateDeathPieces(int dt) {
            if (deathPieces == null) return;
            for (int i = 0; i < deathPieces.length; i++) {
                PhysicsPiece p = deathPieces[i];
                if (p == null) continue;
                p.vy -= 0.000018f * dt;
                p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
                if (p.y < 0.0f) {
                    p.y = 0.0f;
                    if (p.vy < -0.0005f) p.vy = -p.vy * 0.28f;
                    else p.vy = 0.0f;
                    p.vx *= 0.86f; p.vz *= 0.86f;
                }
                p.rotation += p.spin * dt * 0.001f;
                p.node.setTranslation(p.x, p.y, p.z);
                p.node.setOrientation(p.rotation, p.spinX, p.spinY, p.spinZ);
            }
        }

        private void respawnPlayer() {
            if (deathPieces != null) {
                for (int i = 0; i < deathPieces.length; i++)
                    if (deathPieces[i] != null) world.removeChild(deathPieces[i].node);
            }
            deathPieces = null;
            try {
                px = 0; pz = 0; jumpY = 0; playerVelocityY = 0;
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
                    camera.setPerspective(65.0f, (float)getWidth()/(float)getHeight(), 0.1f, 100.0f);
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
                g.drawString("Check Java model and face.png", 4, 22, Graphics.TOP|Graphics.LEFT);
            }
            g.setColor(0xFFFFFF);
            g.drawString("ROBLOX 3DME", 3, 2, Graphics.TOP|Graphics.LEFT);
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
            model.addChild(makeBox(0, 1.45f, 0, 0.78f, 0.95f, 0.40f, 0x1769AA)); // classic blue torso
            model.addChild(makeRoundedCylinderHead()); // upright rounded cylindrical yellow head
            model.addChild(makeTexturedFace()); // curved face.png decal on the front (-Z)
            leftArm = (Mesh)makeBox(-0.54f, 1.48f, 0, 0.30f, 0.88f, 0.40f, 0xF5C542);
            rightArm = (Mesh)makeBox(0.54f, 1.48f, 0, 0.30f, 0.88f, 0.40f, 0xF5C542);
            leftLeg = (Mesh)makeBox(-0.20f, 0.52f, 0, 0.36f, 0.78f, 0.40f, 0x2E9E45);
            rightLeg = (Mesh)makeBox(0.20f, 0.52f, 0, 0.36f, 0.78f, 0.40f, 0x2E9E45);
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
                if (img == null) {
                    modelError = "face.png not found in /meblox/ or JAR root";
                    return makeCurvedTexturedFace(makeSolidAppearance(0xF5C542));
                }

                // JSR-184 textures require power-of-two dimensions; face.png is 126x128.
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
                PolygonMode polygonMode = new PolygonMode();
                polygonMode.setCulling(PolygonMode.CULL_NONE);
                ap.setPolygonMode(polygonMode);

                return makeCurvedTexturedFace(ap);
            } catch (Throwable t) {
                modelError = "face.png texture: " + t.toString();
                return makeCurvedTexturedFace(makeSolidAppearance(0xF5C542));
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
            head.addChild(makeSolidMesh(shellCoords, shellStrips, 0xF5C542));
            head.addChild(makeCylinderDisk(-halfHeight, endRadius, segments, 0xF5C542));
            head.addChild(makeCylinderDisk(halfHeight, endRadius, segments, 0xF5C542));
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
            if (menuMode) {
                if (k == UP || keyCode == KEY_NUM2) selectedGame = 0;
                else if (k == DOWN || keyCode == KEY_NUM8) selectedGame = 1;
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
            if (keyCode == KEY_NUM0) {
                menuMode = true;
                loaded = false;
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