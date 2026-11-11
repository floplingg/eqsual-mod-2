package com.eqsual.cosmetics.client.render;

import com.eqsual.cosmetics.EqsualCosmetics;
import com.eqsual.cosmetics.cosmetic.CosmeticDef;
import com.eqsual.cosmetics.cosmetic.CosmeticRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A real 3D cosmetic model made of textured cuboids, baked once from the catalogue JSON.
 * Static parts are fully pre-transformed; only animated parts are re-transformed per frame,
 * and each cosmetic is drawn with at most three draw submissions (solid / translucent / glowing).
 *
 * <p>Model space is in pixels: origin at the centre of the top of the head, +Y up, +Z towards the face.
 */
public final class CosmeticModel {
    public static final Identifier ATLAS = Identifier.fromNamespaceAndPath(EqsualCosmetics.MOD_ID, "textures/cosmetic/atlas.png");
    private static final Map<String, CosmeticModel> CACHE = new HashMap<>();

    // Face corner selectors (from=0 / to=1 per axis), ordered top-left, top-right, bottom-right, bottom-left
    // as seen from outside the box. Must match tools/gen_cosmetics.py.
    private static final String[] FACES = {"up", "down", "north", "south", "west", "east"};
    private static final int[][][] FACE_CORNERS = {
            {{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}}, // up
            {{0, 0, 1}, {1, 0, 1}, {1, 0, 0}, {0, 0, 0}}, // down
            {{1, 1, 0}, {0, 1, 0}, {0, 0, 0}, {1, 0, 0}}, // north (-Z, back)
            {{0, 1, 1}, {1, 1, 1}, {1, 0, 1}, {0, 0, 1}}, // south (+Z, front)
            {{0, 1, 0}, {0, 1, 1}, {0, 0, 1}, {0, 0, 0}}, // west
            {{1, 1, 1}, {1, 1, 0}, {1, 0, 0}, {1, 0, 1}}, // east
    };

    private final List<Part> solid = new ArrayList<>();
    private final List<Part> translucent = new ArrayList<>();
    private final List<Part> glowing = new ArrayList<>();

    public static CosmeticModel get(CosmeticDef def) {
        return CACHE.computeIfAbsent(def.id(), id -> new CosmeticModel(def.parts()));
    }

    private CosmeticModel(JsonArray parts) {
        float atlas = CosmeticRegistry.atlasSize();
        for (JsonElement el : parts) {
            Part p = Part.parse(el.getAsJsonObject(), atlas);
            if (p.glow) glowing.add(p);
            else if (p.translucent) translucent.add(p);
            else solid.add(p);
        }
    }

    /**
     * Submits this model for drawing. The pose stack must already be in cosmetic model space.
     *
     * @param time       animation time in ticks (entity age + partial tick)
     * @param animations whether animated parts move
     * @param glow       whether glowing parts are drawn emissive/full-bright
     */
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                       float time, boolean animations, boolean glow) {
        float t = animations ? time : 0f;
        if (!solid.isEmpty()) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(ATLAS),
                    (pose, vc) -> emit(solid, pose, vc, light, overlay, t));
        }
        List<Part> clear = translucent;
        if (!glow && !glowing.isEmpty()) {
            clear = new ArrayList<>(translucent);
            clear.addAll(glowing);
        }
        if (!clear.isEmpty()) {
            List<Part> list = clear;
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(ATLAS),
                    (pose, vc) -> emit(list, pose, vc, light, overlay, t));
        }
        if (glow && !glowing.isEmpty()) {
            RenderType emissive = RenderTypes.entityTranslucentEmissive(ATLAS);
            collector.submitCustomGeometry(poseStack, emissive,
                    (pose, vc) -> emit(glowing, pose, vc, LightTexture.FULL_BRIGHT, overlay, t));
        }
    }

    private static void emit(List<Part> parts, PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, float t) {
        float[] tmp = new float[24];
        for (Part p : parts) {
            float[] c = p.animated() ? p.animatedCorners(t, tmp) : p.corners;
            for (int f = 0; f < 6; f++) {
                int[][] fc = FACE_CORNERS[f];
                int i0 = idx(fc[0]), i1 = idx(fc[1]), i2 = idx(fc[2]), i3 = idx(fc[3]);
                // normal = (BL - TL) x (TR - TL)
                float ax = c[i3 * 3] - c[i0 * 3], ay = c[i3 * 3 + 1] - c[i0 * 3 + 1], az = c[i3 * 3 + 2] - c[i0 * 3 + 2];
                float bx = c[i1 * 3] - c[i0 * 3], by = c[i1 * 3 + 1] - c[i0 * 3 + 1], bz = c[i1 * 3 + 2] - c[i0 * 3 + 2];
                float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1.0e-6f) continue; // degenerate (zero-thickness) face
                nx /= len; ny /= len; nz /= len;
                float[] uv = p.uv[f];
                // counter-clockwise from outside: TL, BL, BR, TR
                vertex(vc, pose, c, i0, uv[0], uv[1], light, overlay, nx, ny, nz);
                vertex(vc, pose, c, i3, uv[0], uv[3], light, overlay, nx, ny, nz);
                vertex(vc, pose, c, i2, uv[2], uv[3], light, overlay, nx, ny, nz);
                vertex(vc, pose, c, i1, uv[2], uv[1], light, overlay, nx, ny, nz);
            }
        }
    }

    private static int idx(int[] sel) { return sel[0] | (sel[1] << 1) | (sel[2] << 2); }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float[] c, int i, float u, float v,
                               int light, int overlay, float nx, float ny, float nz) {
        vc.addVertex(pose, c[i * 3], c[i * 3 + 1], c[i * 3 + 2])
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /** One textured cuboid. */
    private static final class Part {
        final float[] corners = new float[24];   // 8 corners after static transform (index = x | y<<1 | z<<2)
        final float[][] uv = new float[6][];     // per face: u0, v0, u1, v1 (normalised)
        boolean glow, translucent;
        Anim anim;
        float px, py, pz;                          // pivot

        boolean animated() { return anim != null; }

        static Part parse(JsonObject o, float atlas) {
            Part p = new Part();
            float[] from = floats(o.getAsJsonArray("from"));
            float[] to = floats(o.getAsJsonArray("to"));
            float[] pivot = o.has("pivot") ? floats(o.getAsJsonArray("pivot")) : new float[]{0, 0, 0};
            p.px = pivot[0]; p.py = pivot[1]; p.pz = pivot[2];
            p.glow = o.has("glow") && o.get("glow").getAsBoolean();
            p.translucent = o.has("translucent") && o.get("translucent").getAsBoolean();
            Matrix4f m = new Matrix4f();
            if (o.has("rot")) {
                float[] r = floats(o.getAsJsonArray("rot"));
                m.translate(pivot[0], pivot[1], pivot[2])
                        .rotateZ(r[2] * Mth.DEG_TO_RAD)
                        .rotateY(r[1] * Mth.DEG_TO_RAD)
                        .rotateX(r[0] * Mth.DEG_TO_RAD)
                        .translate(-pivot[0], -pivot[1], -pivot[2]);
            }
            Vector3f v = new Vector3f();
            for (int i = 0; i < 8; i++) {
                v.set((i & 1) != 0 ? to[0] : from[0], (i & 2) != 0 ? to[1] : from[1], (i & 4) != 0 ? to[2] : from[2]);
                m.transformPosition(v);
                p.corners[i * 3] = v.x;
                p.corners[i * 3 + 1] = v.y;
                p.corners[i * 3 + 2] = v.z;
            }
            JsonObject uvs = o.getAsJsonObject("uv");
            for (int f = 0; f < 6; f++) {
                float[] raw = floats(uvs.getAsJsonArray(FACES[f]));
                p.uv[f] = new float[]{raw[0] / atlas, raw[1] / atlas, raw[2] / atlas, raw[3] / atlas};
            }
            if (o.has("anim")) p.anim = Anim.parse(o.getAsJsonObject("anim"));
            return p;
        }

        float[] animatedCorners(float t, float[] out) {
            Matrix4f m = anim.matrix(t, px, py, pz);
            Vector3f v = new Vector3f();
            for (int i = 0; i < 8; i++) {
                v.set(corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2]);
                m.transformPosition(v);
                out[i * 3] = v.x;
                out[i * 3 + 1] = v.y;
                out[i * 3 + 2] = v.z;
            }
            return out;
        }
    }

    /** Procedural part animations: spin, sway, twitch, flicker, bob and halo (spin + bob). */
    private record Anim(String type, float speed, float amp, float phase, float bob, float bobSpeed) {
        static Anim parse(JsonObject o) {
            return new Anim(o.get("type").getAsString(),
                    num(o, "speed", 1f), num(o, "amp", 0f), num(o, "phase", 0f),
                    num(o, "bob", 0f), num(o, "bobSpeed", 0f));
        }

        Matrix4f matrix(float t, float px, float py, float pz) {
            Matrix4f m = new Matrix4f();
            switch (type) {
                case "spin" -> m.translate(px, 0, pz).rotateY(t * speed * Mth.DEG_TO_RAD).translate(-px, 0, -pz);
                case "halo" -> m.translate(0, Mth.sin(t * bobSpeed) * bob, 0).rotateY(t * speed * Mth.DEG_TO_RAD);
                case "sway" -> m.translate(px, py, pz)
                        .rotateZ(Mth.sin(t * speed + phase) * amp * Mth.DEG_TO_RAD)
                        .rotateX(Mth.sin(t * speed * 0.7f + phase) * amp * 0.4f * Mth.DEG_TO_RAD)
                        .translate(-px, -py, -pz);
                case "twitch" -> {
                    float s = Math.max(0f, Mth.sin(t * speed + phase));
                    float k = s * s * s * s;
                    k = k * k * k; // short, sharp flick once per cycle
                    m.translate(px, py, pz).rotateX(-k * amp * Mth.DEG_TO_RAD).translate(-px, -py, -pz);
                }
                case "flicker" -> {
                    float f = Mth.sin(t * speed + phase) * 0.6f + Mth.sin(t * speed * 2.3f + phase * 1.7f) * 0.4f;
                    float sy = 1f + amp * f;
                    float sxz = 1f - amp * 0.35f * f;
                    m.translate(px, py, pz).scale(sxz, sy, sxz).translate(-px, -py, -pz);
                }
                case "bob" -> m.translate(0, Mth.sin(t * speed + phase) * amp, 0);
                default -> { }
            }
            return m;
        }

        private static float num(JsonObject o, String k, float def) {
            return o.has(k) ? o.get(k).getAsFloat() : def;
        }
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
        return out;
    }
}
