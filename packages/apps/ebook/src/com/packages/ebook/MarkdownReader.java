package com.packages.ebook;

import com.android.graphics.FontManager;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** Small documented Markdown subset; never executes HTML or fetches remote resources. */
final class MarkdownReader {
    static final Pattern IMAGE=Pattern.compile("!\\[([^]\\n]*)\\]\\((<[^>\\n]+>|[^)\\s]+)(?:\\s+[\"'][^\"'\\n]*[\"'])?\\)");
    private static final Pattern HEADING=Pattern.compile("^ {0,3}(#{1,6})\\s+(.+?)(?:\\s+#+)?$");
    private static final Pattern LIST=Pattern.compile("^\\s*(?:[-+*]|[0-9]+[.)])\\s+(.+)$");
    static final class Block {
        final String kind,text;final int level;
        Block(String kind,String text,int level){this.kind=kind;this.text=text;this.level=level;}
    }
    static final class Span {
        final String text;final int style;
        Span(String text,int style){this.text=text;this.style=style;}
    }
    static final class Line {
        final List<Span> spans; final int size,indent,height;final String kind;
        final BufferedImage image;final int imageWidth;
        Line(List<Span> spans,int size,int indent,int height,String kind,BufferedImage image,int imageWidth){this.spans=spans;this.size=size;this.indent=indent;this.height=height;this.kind=kind;this.image=image;this.imageWidth=imageWidth;}
        String plain(){StringBuilder s=new StringBuilder();for(Span span:spans)s.append(span.text);return s.toString();}
        void draw(Graphics2D g,int x,int y,int width,boolean night){
            if("rule".equals(kind)){g.setColor(new Color(night?0x56616b:0xd7cfc0));g.drawLine(x,y+height/2,x+width,y+height/2);return;}
            if(image!=null){g.drawImage(image,x+(width-imageWidth)/2,y,imageWidth,height-12,null);return;}
            if("code".equals(kind)||"table".equals(kind)){g.setColor(new Color(night?0x2b333b:0xeee8dc));g.fillRect(x,y,width,height);}
            if("quote".equals(kind)){g.setColor(new Color(0xa79a87));g.fillRect(x,y+2,3,height-4);}
            g.setColor(new Color(night?0xd9d2c5:0x403b34));float cursor=x+indent;
            for(Span span:spans){if(span.text.isEmpty())continue;java.awt.font.TextLayout layout=FontManager.layout(span.text,size,span.style);layout.draw(g,cursor,y+size+3);cursor+=layout.getAdvance();}
        }
    }
    static List<Block> parse(String source){
        String[] lines=source.replace("\r\n","\n").replace('\r','\n').replace("\ufeff","").split("\n",-1);
        List<Block> blocks=new ArrayList<>();StringBuilder paragraph=new StringBuilder();String fence=null;
        for(int i=0;i<lines.length;i++){
            String line=lines[i],trim=line.trim();
            if(fence!=null){if(trim.matches(Pattern.quote(fence.charAt(0)+"")+"{"+fence.length()+",}\\s*"))fence=null;else blocks.add(new Block("code",line.replace("\t","    "),0));continue;}
            if(trim.startsWith("```")||trim.startsWith("~~~")){flush(blocks,paragraph);int n=0;while(n<trim.length()&&trim.charAt(n)==trim.charAt(0))n++;fence=trim.substring(0,n);continue;}
            if(trim.isEmpty()){flush(blocks,paragraph);continue;}
            Matcher heading=HEADING.matcher(line);
            if(heading.matches()){flush(blocks,paragraph);blocks.add(new Block("heading",heading.group(2),heading.group(1).length()));continue;}
            if(i+1<lines.length&&lines[i+1].trim().matches("(?:={3,}|-{3,})")){flush(blocks,paragraph);blocks.add(new Block("heading",trim,lines[++i].trim().charAt(0)=='='?1:2));continue;}
            if(trim.matches("(?:\\*\\s*){3,}|(?:-\\s*){3,}|(?:_\\s*){3,}")){flush(blocks,paragraph);blocks.add(new Block("rule","",0));continue;}
            if(line.contains("|")&&i+1<lines.length&&tableDivider(lines[i+1])){
                flush(blocks,paragraph);blocks.add(new Block("tableHead",tableText(line),0));i++;
                while(i+1<lines.length&&lines[i+1].contains("|")&&!lines[i+1].trim().isEmpty())blocks.add(new Block("table",tableText(lines[++i]),0));continue;
            }
            if(trim.startsWith(">")){flush(blocks,paragraph);blocks.add(new Block("quote",trim.replaceFirst("^>\\s?",""),0));continue;}
            Matcher list=LIST.matcher(line);
            if(list.matches()){flush(blocks,paragraph);String prefix=trim.matches("^[0-9].*")?trim.substring(0,trim.indexOf(' '))+" ":"• ";blocks.add(new Block("list",prefix+list.group(1),0));continue;}
            Matcher image=IMAGE.matcher(line);
            if(image.find()){
                flush(blocks,paragraph);int offset=0;do{if(image.start()>offset)blocks.add(new Block("paragraph",line.substring(offset,image.start()),0));blocks.add(new Block("image",image.group(),0));offset=image.end();}while(image.find());
                if(offset<line.length())blocks.add(new Block("paragraph",line.substring(offset),0));continue;
            }
            // Preserve physical lines: page-per-file textbooks and hard line breaks remain readable.
            if(paragraph.length()>0)paragraph.append('\n');paragraph.append(line);
        }
        flush(blocks,paragraph);return blocks;
    }
    private static boolean tableDivider(String s){return s.trim().matches("\\|?\\s*:?-{3,}:?\\s*(?:\\|\\s*:?-{3,}:?\\s*)+\\|?");}
    private static String tableText(String s){return s.trim().replaceFirst("^\\|","").replaceFirst("\\|$","").replaceAll("\\s*\\|\\s*","  │  ").trim();}
    private static void flush(List<Block> blocks,StringBuilder p){if(p.length()>0){blocks.add(new Block("paragraph",p.toString(),0));p.setLength(0);}}
    static String title(String source,String fallback){for(Block b:parse(source))if(b.kind.equals("heading"))return plain(inline(b.text));return fallback;}
    private static String plain(List<Span> spans){StringBuilder s=new StringBuilder();for(Span span:spans)s.append(span.text);return s.toString();}
    static List<Span> inline(String s){List<Span> result=new ArrayList<>();inline(s,Font.PLAIN,result,0);return result;}
    private static void inline(String s,int style,List<Span> result,int depth){
        if(depth>12){result.add(new Span(s,style));return;}
        StringBuilder literal=new StringBuilder();
        for(int i=0;i<s.length();){
            char c=s.charAt(i);
            if(c=='\\'&&i+1<s.length()){literal.append(s.charAt(i+1));i+=2;continue;}
            if(c=='['){int close=s.indexOf("](",i+1),end=close<0?-1:s.indexOf(')',close+2);if(end>=0){append(result,literal,style);inline(s.substring(i+1,close),style,result,depth+1);i=end+1;continue;}}
            String marker=null;int extra=0;
            if(s.startsWith("**",i)||s.startsWith("__",i)){marker=s.substring(i,i+2);extra=Font.BOLD;}
            else if(c=='*'||c=='_'){marker=""+c;extra=Font.ITALIC;}
            else if(c=='`')marker="`";
            if(marker!=null){int end=s.indexOf(marker,i+marker.length());if(end>i+marker.length()){
                append(result,literal,style);String inner=s.substring(i+marker.length(),end);
                if(marker.equals("`"))result.add(new Span(inner,style));else inline(inner,style|extra,result,depth+1);
                i=end+marker.length();continue;
            }}
            literal.append(c);i++;
        }
        append(result,literal,style);
    }
    private static void append(List<Span> out,StringBuilder literal,int style){if(literal.length()>0){out.add(new Span(literal.toString(),style));literal.setLength(0);}}
    static String imageTarget(Matcher m){String path=m.group(2);return path.startsWith("<")?path.substring(1,path.length()-1):path;}
    static File localImage(File root,String target)throws IOException {
        try{
            URI uri=new URI(target.replace(" ","%20"));if(uri.isAbsolute()||uri.getRawAuthority()!=null||uri.getQuery()!=null)return null;
            String path=uri.getPath();if(path==null||path.startsWith("/")||path.contains("\\"))return null;
            File file=new File(root,path).getCanonicalFile();if(!file.toPath().startsWith(root.getCanonicalFile().toPath()))return null;
            return file;
        }catch(java.net.URISyntaxException e){return null;}
    }
    /** Copy only explicitly referenced raster images, into an independent per-book folder. */
    static void copyImages(String markdown,File sourceRoot,File destination)throws IOException{
        Matcher m=IMAGE.matcher(markdown);long total=0;Set<String> copied=new HashSet<>();
        while(m.find()){
            String target=imageTarget(m);if(!copied.add(target))continue;
            if(copied.size()>256)throw new IOException("Markdown 插图数量超过 256 张");
            File source=localImage(sourceRoot,target),output=localImage(destination,target);
            if(source==null||output==null||!source.isFile()||!raster(source.getName()))continue;
            if(source.length()>8*1024*1024)continue;
            total+=source.length();if(total>32L*1024*1024)throw new IOException("Markdown 插图总大小超过 32 MB");
            Files.createDirectories(output.getParentFile().toPath());
            try(InputStream in=new FileInputStream(source)){Files.write(output.toPath(),EpubReader.readLimited(in,8*1024*1024));}
        }
    }
    private static boolean raster(String name){String n=name.toLowerCase(Locale.ROOT);return n.endsWith(".png")||n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".gif");}
    private static BufferedImage image(File file){
        if(file==null||!file.isFile()||!raster(file.getName()))return null;
        try(ImageInputStream in=ImageIO.createImageInputStream(file)){
            Iterator<ImageReader> readers=ImageIO.getImageReaders(in);if(!readers.hasNext())return null;
            ImageReader r=readers.next();try{r.setInput(in);int w=r.getWidth(0),h=r.getHeight(0);if(w<=0||h<=0||w>20000||h>20000)return null;
                ImageReadParam p=r.getDefaultReadParam();int sample=Math.max(1,Math.max(w,h)/1200);p.setSourceSubsampling(sample,sample,0,0);return r.read(0,p);
            }finally{r.dispose();}
        }catch(IOException|RuntimeException e){return null;}
    }
    static List<List<Line>> layout(String markdown,File assets,int width,int height,int size){
        List<List<Line>> pages=new ArrayList<>();List<Line> current=new ArrayList<>();pages.add(current);int used=0;
        Map<String,BufferedImage> imageCache=new HashMap<>();long pixels=0;
        for(Block block:parse(markdown)){
            List<Line> lines=new ArrayList<>();int textSize=block.kind.equals("heading")?size+Math.max(2,12-block.level*2):size;
            String kind=block.kind.equals("tableHead")?"table":block.kind;
            int indent=kind.equals("quote")?16:kind.equals("list")?8:kind.equals("code")||kind.equals("table")?8:0;
            if(kind.equals("image")){
                Matcher m=IMAGE.matcher(block.text);m.find();BufferedImage image=null;
                String target=imageTarget(m);
                if(imageCache.containsKey(target))image=imageCache.get(target);
                else {
                    try{image=image(localImage(assets,target));}catch(IOException ignored){}
                    if(image!=null){long count=(long)image.getWidth()*image.getHeight();if(pixels+count>16000000)image=null;else pixels+=count;}
                    imageCache.put(target,image);
                }
                if(image!=null){double scale=Math.min(1,Math.min(width/(double)image.getWidth(),Math.max(1,height-24)/(double)image.getHeight()));
                    lines.add(new Line(Collections.emptyList(),size,0,(int)(image.getHeight()*scale)+12,"image",image,Math.max(1,(int)(image.getWidth()*scale))));
                    if(!m.group(1).isEmpty())wrap(lines,inline(m.group(1)),Math.max(12,size-4),16,width,"caption");
                }else wrap(lines,Collections.singletonList(new Span("[插图："+(m.group(1).isEmpty()?imageTarget(m):m.group(1))+" · 缺失或暂不支持]",Font.ITALIC)),size,0,width,"caption");
            }else if(kind.equals("rule"))lines.add(new Line(Collections.emptyList(),size,0,20,"rule",null,0));
            else {
                List<Span> spans=kind.equals("code")?Collections.singletonList(new Span(block.text,Font.PLAIN)):inline(block.text);
                if(block.kind.equals("heading")||block.kind.equals("tableHead")){List<Span> bold=new ArrayList<>();for(Span s:spans)bold.add(new Span(s.text,s.style|Font.BOLD));spans=bold;}
                wrap(lines,spans,textSize,indent,width,kind);
            }
            if(!kind.equals("code")&&!kind.equals("table"))lines.add(new Line(Collections.emptyList(),size,0,8,"gap",null,0));
            for(Line line:lines){if(used+line.height>height&&!current.isEmpty()){current=new ArrayList<>();pages.add(current);used=0;}current.add(line);used+=line.height;}
        }
        return pages;
    }
    private static void wrap(List<Line> out,List<Span> spans,int size,int indent,int width,String kind){
        List<Span> line=new ArrayList<>();StringBuilder run=new StringBuilder();int runStyle=Font.PLAIN;float used=0;
        for(Span span:spans)for(int i=0;i<span.text.length();){int cp=span.text.codePointAt(i);i+=Character.charCount(cp);String ch=new String(Character.toChars(cp));
            float advance=cp=='\n'?0:FontManager.layout(ch,size,span.style).getAdvance();
            if(cp=='\n'||(used+advance>width-indent&&used>0)){append(line,run,runStyle);out.add(new Line(line,size,indent,size+13,kind,null,0));line=new ArrayList<>();used=0;if(cp=='\n')continue;}
            if(runStyle!=span.style){append(line,run,runStyle);runStyle=span.style;}run.append(ch);used+=advance;
        }
        append(line,run,runStyle);out.add(new Line(line,size,indent,size+13,kind,null,0));
    }
}
