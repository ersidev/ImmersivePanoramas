package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class CustomCubeMapTexture {
    private static final int FACE_COUNT = 6;

    private final ResourceLocation id;
    private final TextureManager textureManager;
    private boolean loaded;

    public CustomCubeMapTexture(ResourceLocation id, TextureManager textureManager) {
        this.id = id;
        this.textureManager = textureManager;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public synchronized boolean loadFromDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }

        NativeImage[] faces = new NativeImage[FACE_COUNT];
        try {
            for (int i = 0; i < faces.length; i++) {
                Path file = dir.resolve(PanoramaStorage.faceFileName(i));
                if (!Files.isRegularFile(file)) {
                    return false;
                }
                try (InputStream in = Files.newInputStream(file)) {
                    faces[i] = NativeImage.read(in);
                }
            }
            uploadFaces(faces);
            return true;
        } catch (Exception e) {
            ModLogger.error("Failed to load custom panorama from directory {}", dir, e);
            return false;
        } finally {
            PanoramaStorage.closeImages(faces);
        }
    }

    synchronized void uploadFaces(NativeImage[] faces) {
        if (faces.length != FACE_COUNT) {
            throw new IllegalArgumentException("A panorama must have six faces");
        }
        int faceSize = faces[0].getWidth();
        if (faceSize != faces[0].getHeight()) {
            throw new IllegalArgumentException("Panorama faces must be square");
        }
        for (NativeImage face : faces) {
            if (face.getWidth() != faceSize || face.getHeight() != faceSize) {
                throw new IllegalArgumentException("Panorama faces must have matching dimensions");
            }
        }

        FaceTexture[] textures = new FaceTexture[FACE_COUNT];
        try {
            for (int i = 0; i < textures.length; i++) {
                textures[i] = new FaceTexture();
                PanoramaStorage.makeOpaque(faces[i]);
                textures[i].upload(faces[i]);
            }

            for (int i = 0; i < textures.length; i++) {
                textureManager.register(this.id.withSuffix("_" + i + ".png"), textures[i]);
                textures[i] = null;
            }
            this.loaded = true;
        } finally {
            for (FaceTexture texture : textures) {
                if (texture != null) {
                    texture.releaseId();
                }
            }
        }
    }

    private static class FaceTexture extends AbstractTexture {
        private void upload(NativeImage face) {
            TextureUtil.prepareImage(getId(), face.getWidth(), face.getHeight());
            face.upload(0, 0, 0, 0, 0, face.getWidth(), face.getHeight(), true, true, false, false);
        }

        @Override
        public void load(ResourceManager resourceManager) {}
    }
}
