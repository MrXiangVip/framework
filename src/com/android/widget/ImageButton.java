package com.android.widget;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.util.AttributeSet;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** Clickable APK image, centered and scaled down to fit the padded cell. */
public class ImageButton extends Button {
    private BufferedImage image;

    public ImageButton(Context context) { this(context, null); }
    public ImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        String source = attrs == null ? null : attrs.get("src");
        if (source != null) {
            try (InputStream stream = context.getClassLoader().getResourceAsStream(source)) {
                if (stream == null) throw new IOException("Resource not found");
                image = ImageIO.read(stream);
                if (image == null) throw new IOException("Unsupported image format");
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot load APK image: " + source, e);
            }
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize((image == null ? 0 : image.getWidth()) + 2 * mPadding, widthSpec),
                resolveSize((image == null ? 0 : image.getHeight()) + 2 * mPadding, heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (image == null) return;
        int availableWidth = Math.max(0, getWidth() - 2 * mPadding);
        int availableHeight = Math.max(0, getHeight() - 2 * mPadding);
        double scale = Math.min(1.0, Math.min((double) availableWidth / image.getWidth(),
                (double) availableHeight / image.getHeight()));
        int width = (int) Math.floor(image.getWidth() * scale);
        int height = (int) Math.floor(image.getHeight() * scale);
        canvas.drawImage(image, (getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
    }
}
