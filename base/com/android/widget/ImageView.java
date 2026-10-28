package com.android.widget;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.util.AttributeSet;
import com.android.view.View;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import javax.imageio.ImageIO;

/**
 * Android-style image layout on this framework's BufferedImage software renderer.
 * Resources are APK paths, not Android integer IDs; matrices are affine AWT transforms.
 * Drawable state/levels, animated drawables, content providers and HTTP loading are not supported.
 */
public class ImageView extends View {
    public enum ScaleType { MATRIX, FIT_XY, FIT_START, FIT_CENTER, FIT_END, CENTER, CENTER_CROP, CENTER_INSIDE }
    public enum TintMode { SRC_IN, SRC_ATOP }
    private BufferedImage image, filtered;
    private String resource;
    private URI uri;
    private ScaleType scaleType = ScaleType.FIT_CENTER;
    private final AffineTransform matrix = new AffineTransform();
    private boolean adjustViewBounds, cropToPadding, baselineAlignBottom;
    private int maxWidth = Integer.MAX_VALUE, maxHeight = Integer.MAX_VALUE;
    private int imageAlpha = 255, baseline = -1;
    private int paddingLeft, paddingTop, paddingRight, paddingBottom;
    private Integer tint, colorFilter;
    private TintMode tintMode = TintMode.SRC_IN;

    public ImageView(Context context) { this(context, null); }
    public ImageView(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public ImageView(Context context, AttributeSet attrs, int style) { this(context, attrs, style, 0); }
    public ImageView(Context context, AttributeSet attrs, int style, int res) {
        super(context, attrs, style, res);
        paddingLeft = paddingTop = paddingRight = paddingBottom = mPadding;
        if (attrs == null) return;
        setPadding(attrs.dimension("paddingLeft", mPadding), attrs.dimension("paddingTop", mPadding),
                attrs.dimension("paddingRight", mPadding), attrs.dimension("paddingBottom", mPadding));
        setAdjustViewBounds(flag(attrs, "adjustViewBounds", false));
        setMaxWidth(attrs.dimension("maxWidth", maxWidth));
        setMaxHeight(attrs.dimension("maxHeight", maxHeight));
        setCropToPadding(flag(attrs, "cropToPadding", false));
        setBaselineAlignBottom(flag(attrs, "baselineAlignBottom", false));
        if (attrs.get("baseline") != null) setBaseline(attrs.dimension("baseline", 0));
        if (attrs.get("scaleType") != null) setScaleType(parseScaleType(attrs.get("scaleType")));
        if (attrs.get("imageAlpha") != null) setImageAlpha(Integer.parseInt(attrs.get("imageAlpha")));
        if (attrs.get("tintMode") != null) {
            setImageTintMode(TintMode.valueOf(attrs.get("tintMode").toUpperCase(Locale.ROOT)));
        } else if (attrs.get("tint") != null) {
            // Android XML tint uses SRC_ATOP for legacy layouts.
            setImageTintMode(TintMode.SRC_ATOP);
        }
        if (attrs.get("tint") != null) setImageTint(attrs.color("tint", 0));
        if (attrs.get("src") != null) setImageResource(attrs.get("src"));
    }
    private static boolean flag(AttributeSet attrs, String name, boolean fallback) {
        String value = attrs.get(name);
        if (value == null) return fallback;
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Invalid " + name + ": " + value);
    }
    private static ScaleType parseScaleType(String value) {
        if (value.matches("[0-7]")) return ScaleType.values()[Integer.parseInt(value)];
        return ScaleType.valueOf(value.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT));
    }

    public BufferedImage getImageBitmap() { return image; }
    public String getImageResource() { return resource; }
    public URI getImageURI() { return uri; }
    public void setImageBitmap(BufferedImage bitmap) { replaceImage(bitmap, null, null); }
    /** Loads a classloader path or @drawable/name / @mipmap/name. Null clears the image. */
    public void setImageResource(String source) {
        if (source == null || source.isEmpty() || "@null".equals(source)) { setImageBitmap(null); return; }
        BufferedImage bitmap = loadResource(source);
        replaceImage(bitmap, source, null);
    }
    private BufferedImage loadResource(String source) {
        String path = source;
        boolean reference = source.startsWith("@drawable/") || source.startsWith("@mipmap/");
        if (reference) path = "res/" + source.substring(1);
        if (path.startsWith("/")) path = path.substring(1);
        String[] suffixes = reference ? new String[]{"", ".png", ".jpg", ".jpeg", ".gif", ".bmp"} : new String[]{""};
        for (String suffix : suffixes) {
            try (InputStream input = getContext().getClassLoader().getResourceAsStream(path + suffix)) {
                if (input != null) return decode(input, source);
            } catch (IOException error) { throw new IllegalArgumentException("Cannot load image: " + source, error); }
        }
        throw new IllegalArgumentException("Image resource not found: " + source);
    }
    /** Synchronous local file loading; URI schemes requiring network/providers are unsupported. */
    public void setImageURI(URI source) {
        if (source == null) { setImageBitmap(null); return; }
        if (source.getScheme() != null && !"file".equalsIgnoreCase(source.getScheme()))
            throw new IllegalArgumentException("Only local file image URIs are supported: " + source.getScheme());
        File file = source.getScheme() == null ? new File(source.getPath()) : new File(source);
        try (InputStream input = new FileInputStream(file)) {
            replaceImage(decode(input, source.toString()), null, source);
        } catch (IOException error) { throw new IllegalArgumentException("Cannot load image: " + source, error); }
    }
    private static BufferedImage decode(InputStream input, String source) throws IOException {
        BufferedImage bitmap = ImageIO.read(input);
        if (bitmap == null) throw new IOException("Unsupported image: " + source);
        return bitmap;
    }
    private void replaceImage(BufferedImage bitmap, String source, URI imageUri) {
        int oldWidth = image == null ? 0 : image.getWidth(), oldHeight = image == null ? 0 : image.getHeight();
        image = bitmap; resource = source; uri = imageUri; filtered = null;
        if (oldWidth != (image == null ? 0 : image.getWidth()) || oldHeight != (image == null ? 0 : image.getHeight())) requestLayout();
        invalidate();
    }
    public ScaleType getScaleType() { return scaleType; }
    public void setScaleType(ScaleType type) {
        Objects.requireNonNull(type, "scaleType");
        if (scaleType != type) { scaleType = type; requestLayout(); invalidate(); }
    }
    /** Copies the matrix. It only controls rendering when ScaleType.MATRIX is selected. */
    public void setImageMatrix(AffineTransform transform) {
        AffineTransform next = transform == null ? new AffineTransform() : new AffineTransform(transform);
        double[] values = new double[6]; next.getMatrix(values);
        for (double value : values) if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite image matrix");
        if (!matrix.equals(next)) { matrix.setTransform(next); invalidate(); }
    }
    /** Effective image transform in padded content coordinates; callers receive a copy. */
    public AffineTransform getImageMatrix() { return imageTransform(); }
    public boolean getAdjustViewBounds() { return adjustViewBounds; }
    public void setAdjustViewBounds(boolean adjust) {
        adjustViewBounds = adjust;
        if (adjust) setScaleType(ScaleType.FIT_CENTER);
        requestLayout(); invalidate();
    }
    public int getMaxWidth() { return maxWidth; }
    public void setMaxWidth(int width) { maxWidth = Math.max(0, width); requestLayout(); }
    public int getMaxHeight() { return maxHeight; }
    public void setMaxHeight(int height) { maxHeight = Math.max(0, height); requestLayout(); }
    public boolean getCropToPadding() { return cropToPadding; }
    public void setCropToPadding(boolean crop) { cropToPadding = crop; invalidate(); }
    public int getImageAlpha() { return imageAlpha; }
    public void setImageAlpha(int alpha) { imageAlpha = Math.max(0, Math.min(255, alpha)); invalidate(); }
    public int getBaseline() { return baselineAlignBottom ? getMeasuredHeight() : baseline; }
    public void setBaseline(int value) { baseline = value; requestLayout(); }
    public boolean getBaselineAlignBottom() { return baselineAlignBottom; }
    public void setBaselineAlignBottom(boolean align) { baselineAlignBottom = align; requestLayout(); }
    public int getPaddingLeft() { return paddingLeft; }
    public int getPaddingTop() { return paddingTop; }
    public int getPaddingRight() { return paddingRight; }
    public int getPaddingBottom() { return paddingBottom; }
    @Override public void setPadding(int padding) { setPadding(padding, padding, padding, padding); }
    public void setPadding(int left, int top, int right, int bottom) {
        paddingLeft = Math.max(0, left); paddingTop = Math.max(0, top);
        paddingRight = Math.max(0, right); paddingBottom = Math.max(0, bottom);
        mPadding = paddingLeft; requestLayout(); invalidate();
    }
    /** Constant color tint; unlike Android ColorStateList, it does not vary with View state. */
    public Integer getImageTint() { return tint; }
    public void setImageTint(Integer color) { tint = color; filtered = null; invalidate(); }
    public TintMode getImageTintMode() { return tintMode; }
    public void setImageTintMode(TintMode mode) { tintMode = Objects.requireNonNull(mode, "tintMode"); filtered = null; invalidate(); }
    public Integer getColorFilter() { return colorFilter; }
    /** The Android int overload's SRC_ATOP color filter, applied instead of a tint. */
    public void setColorFilter(int color) { colorFilter = color; filtered = null; invalidate(); }
    public void clearColorFilter() { colorFilter = null; filtered = null; invalidate(); }

    private static int add(int a, int b) { return (int) Math.min(Integer.MAX_VALUE, (long) a + b); }
    private static int pixels(double value) { return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value)); }
    private static int limited(int desired, int maximum, int spec) {
        return MeasureSpec.getMode(spec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(spec) : resolveSize(Math.min(desired, maximum), spec);
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int iw = image == null ? 0 : image.getWidth(), ih = image == null ? 0 : image.getHeight();
        int hp = add(paddingLeft, paddingRight), vp = add(paddingTop, paddingBottom);
        boolean freeWidth = MeasureSpec.getMode(widthSpec) != MeasureSpec.EXACTLY;
        boolean freeHeight = MeasureSpec.getMode(heightSpec) != MeasureSpec.EXACTLY;
        int width, height;
        if (adjustViewBounds && image != null && (freeWidth || freeHeight)) {
            width = limited(add(iw, hp), maxWidth, widthSpec);
            height = limited(add(ih, vp), maxHeight, heightSpec);
            if (!freeWidth) {
                height = limited(add(pixels(Math.max(0, width - hp) * (double) ih / iw), vp), maxHeight, heightSpec);
            } else if (!freeHeight) {
                width = limited(add(pixels(Math.max(0, height - vp) * (double) iw / ih), hp), maxWidth, widthSpec);
            } else {
                double ratio = Math.min(Math.max(0, width - hp) / (double) iw, Math.max(0, height - vp) / (double) ih);
                width = limited(add(pixels(iw * ratio), hp), maxWidth, widthSpec);
                height = limited(add(pixels(ih * ratio), vp), maxHeight, heightSpec);
            }
        } else {
            width = resolveSize(Math.max(add(iw, hp), getSuggestedMinimumWidth()), widthSpec);
            height = resolveSize(Math.max(add(ih, vp), getSuggestedMinimumHeight()), heightSpec);
        }
        setMeasuredDimension(width, height);
    }
    private int contentWidth() { return Math.max(0, getWidth() - add(paddingLeft, paddingRight)); }
    private int contentHeight() { return Math.max(0, getHeight() - add(paddingTop, paddingBottom)); }
    private AffineTransform imageTransform() {
        if (scaleType == ScaleType.MATRIX) return new AffineTransform(matrix);
        AffineTransform result = new AffineTransform();
        if (image == null) return result;
        double iw = image.getWidth(), ih = image.getHeight(), w = contentWidth(), h = contentHeight();
        double sx = w / iw, sy = h / ih, x = 0, y = 0;
        if (scaleType != ScaleType.FIT_XY) {
            double scale = scaleType == ScaleType.CENTER ? 1 : scaleType == ScaleType.CENTER_CROP ? Math.max(sx, sy) : Math.min(sx, sy);
            if (scaleType == ScaleType.CENTER_INSIDE) scale = Math.min(1, scale);
            sx = sy = scale;
            double alignment = scaleType == ScaleType.FIT_START ? 0 : scaleType == ScaleType.FIT_END ? 1 : .5;
            x = (w - iw * sx) * alignment; y = (h - ih * sy) * alignment;
            if (scaleType == ScaleType.CENTER || scaleType == ScaleType.CENTER_CROP || scaleType == ScaleType.CENTER_INSIDE) { x = Math.round(x); y = Math.round(y); }
        }
        result.translate(x, y); result.scale(sx, sy); return result;
    }
    private BufferedImage tintedImage() {
        Integer color = colorFilter != null ? colorFilter : tint;
        if (color == null) return image;
        if (filtered != null) return filtered;
        TintMode mode = colorFilter != null ? TintMode.SRC_ATOP : tintMode;
        int w = image.getWidth(), h = image.getHeight(), alpha = color >>> 24;
        int[] data = image.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < data.length; i++) {
            int pixel = data[i], a = pixel >>> 24;
            if (mode == TintMode.SRC_IN) data[i] = ((a * alpha + 127) / 255 << 24) | (color & 0xffffff);
            else {
                int rgb = 0;
                for (int shift = 0; shift <= 16; shift += 8) {
                    int channel = (((color >> shift & 255) * alpha) + ((pixel >> shift & 255) * (255 - alpha)) + 127) / 255;
                    rgb |= channel << shift;
                }
                data[i] = (a << 24) | rgb;
            }
        }
        filtered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); filtered.setRGB(0, 0, w, h, data, 0, w); return filtered;
    }
    @Override protected void onDraw(Canvas canvas) {
        if (image == null || imageAlpha == 0 || contentWidth() == 0 || contentHeight() == 0) return;
        canvas.save();
        try {
            canvas.clipRect(0, 0, getWidth(), getHeight());
            if (cropToPadding) canvas.clipRect(paddingLeft, paddingTop, contentWidth(), contentHeight());
            canvas.translate(paddingLeft, paddingTop);
            canvas.drawImage(tintedImage(), imageTransform(), imageAlpha);
        } finally { canvas.restore(); }
    }
}
