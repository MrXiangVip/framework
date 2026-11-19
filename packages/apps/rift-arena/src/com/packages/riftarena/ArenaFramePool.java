package com.packages.riftarena;

import java.awt.image.BufferedImage;

/** Three owned images: published, UI reader and render writer may be different slots. */
final class ArenaFramePool {
    private final BufferedImage[] images=new BufferedImage[3];
    private final int[] readers=new int[3];
    private BufferedImage published,writing;
    ArenaFramePool(int width,int height){for(int i=0;i<images.length;i++)images[i]=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);}
    synchronized BufferedImage beginWrite(){
        if(writing!=null)throw new IllegalStateException("Writer already owns a buffer");
        for(int i=0;i<images.length;i++)if(images[i]!=published&&readers[i]==0){writing=images[i];return writing;}
        return null; // Skip a render rather than overwrite a reader or allocate another image.
    }
    synchronized void publish(BufferedImage image){if(image!=writing)throw new IllegalStateException("Not the writer");published=image;writing=null;}
    synchronized void retain(BufferedImage image){int i=index(image);if(image==writing)throw new IllegalStateException("Reading unfinished frame");readers[i]++;}
    synchronized void release(BufferedImage image){int i=index(image);if(readers[i]<=0)throw new IllegalStateException("Unbalanced reader");readers[i]--;}
    private int index(BufferedImage image){for(int i=0;i<images.length;i++)if(images[i]==image)return i;throw new IllegalArgumentException("Foreign frame");}
}
