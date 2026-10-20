package com.packages.mqchat;

import com.android.graphics.Canvas;
import java.awt.*;
import java.awt.font.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.AttributedString;
import java.util.*;

/** APK-local physical fonts, independent of the host's CJK font installation.
 * Measurement, line breaking, input carets and both drawing paths share the same glyph runs. */
public final class ChatText {
    private static final FontRenderContext METRICS=new FontRenderContext(null,true,true);
    private final Font chinese,emoji;
    private final Font system=new Font(Font.DIALOG,Font.PLAIN,14);
    private final Map<String,TextLayout> layouts=new LinkedHashMap<String,TextLayout>(128,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,TextLayout> entry){return size()>256;}
    };
    public ChatText(ClassLoader loader){
        chinese=load(loader,"res/fonts/NotoSansCJKsc-Regular.otf");
        emoji=load(loader,"res/fonts/NotoEmoji-Regular.ttf");
    }
    private static Font load(ClassLoader loader,String path){
        try(InputStream stream=loader.getResourceAsStream(path)){
            if(stream==null)throw new IOException("Missing bundled font: "+path);
            return Font.createFont(Font.TRUETYPE_FONT,stream);
        }catch(IOException|FontFormatException error){throw new IllegalStateException("Cannot load MQChat font: "+path,error);}
    }
    private Font choose(int code){return chinese.canDisplay(code)?chinese:emoji.canDisplay(code)?emoji:system;}
    public boolean hasBundledGlyph(int code){return chinese.canDisplay(code)||emoji.canDisplay(code);}
    private TextLayout layout(String value,int size){
        String key=size+":"+value;TextLayout cached=layouts.get(key);if(cached!=null)return cached;
        AttributedString attributed=new AttributedString(value);int start=0;Font previous=null;
        for(int offset=0;offset<value.length();){int code=value.codePointAt(offset);Font font=choose(code);
            if(previous!=null&&font!=previous){attributed.addAttribute(TextAttribute.FONT,previous.deriveFont((float)size),start,offset);start=offset;}
            previous=font;offset+=Character.charCount(code);
        }
        attributed.addAttribute(TextAttribute.FONT,previous.deriveFont((float)size),start,value.length());
        TextLayout result=new TextLayout(attributed.getIterator(),METRICS);layouts.put(key,result);return result;
    }
    public float width(String value,int size){return value.isEmpty()?0:layout(value,size).getAdvance();}
    public void draw(Graphics2D graphics,String value,int x,int baseline,int size,int color){
        if(value.isEmpty())return;graphics.setColor(new Color(color));layout(value,size).draw(graphics,x,baseline);
    }
    /** Rendering to a transparent tile preserves the framework Canvas's existing translation and clip. */
    public void draw(Canvas canvas,String value,int x,int baseline,int size,int color){
        if(value.isEmpty())return;TextLayout line=layout(value,size);Rectangle bounds=line.getPixelBounds(METRICS,0,0);
        if(bounds.width<=0||bounds.height<=0)return;
        BufferedImage tile=new BufferedImage(bounds.width+2,bounds.height+2,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=tile.createGraphics();
        try{
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setColor(new Color(color,true));line.draw(graphics,1-bounds.x,1-bounds.y);
        }finally{graphics.dispose();}
        canvas.drawImage(tile,x+bounds.x-1,baseline+bounds.y-1,tile.getWidth(),tile.getHeight());
    }
}