package com.packages.ebook;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;

/** Reflowable EPUB 2/3 text reader; follows OPF spine rather than ZIP ordering. */
final class EpubReader {
    static final int MAX_TEXT=8*1024*1024;
    private static final int MAX_ENTRY=8*1024*1024,MAX_TOTAL=32*1024*1024;
    static final class Result {final String title,text;Result(String title,String text){this.title=title;this.text=text;}}
    private final ZipFile zip;
    private int consumed;
    private EpubReader(ZipFile zip){this.zip=zip;}
    static byte[] readLimited(InputStream in,int limit)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){if(n>limit-out.size())throw new IOException("书籍解压内容超过大小限制");out.write(buffer,0,n);}return out.toByteArray();
    }
    private byte[] entry(String path)throws IOException {
        ZipEntry e=zip.getEntry(path);if(e==null||e.isDirectory())throw new IOException("EPUB 缺少文件："+path);
        if(e.getSize()>MAX_ENTRY)throw new IOException("EPUB 章节过大");
        try(InputStream in=zip.getInputStream(e)){byte[] b=readLimited(in,Math.min(MAX_ENTRY,MAX_TOTAL-consumed));consumed+=b.length;return b;}
    }
    private Document xml(String path)throws IOException {
        try {
            DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);
            f.setFeature("http://xml.org/sax/features/external-general-entities",false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            f.setXIncludeAware(false);f.setExpandEntityReferences(false);
            DocumentBuilder b=f.newDocumentBuilder();b.setEntityResolver((publicId,systemId)->new InputSource(new StringReader("")));
            b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler(){@Override public void fatalError(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw e;}});
            return b.parse(new ByteArrayInputStream(entry(path)));
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("EPUB 元数据无法解析："+path,e);}
    }
    static Result read(File file)throws IOException {
        try(ZipFile zip=new ZipFile(file)) {return new EpubReader(zip).parse();}
        catch(ZipException e){throw new IOException("EPUB 压缩包已损坏",e);}
    }
    private Result parse()throws IOException {
        if(zip.getEntry("META-INF/encryption.xml")!=null)throw new IOException("暂不支持加密或 DRM EPUB");
        Document container=xml("META-INF/container.xml");
        NodeList roots=container.getElementsByTagNameNS("*","rootfile");
        if(roots.getLength()==0)throw new IOException("EPUB 缺少 OPF 入口");
        Element root=(Element)roots.item(0);
        for(int i=0;i<roots.getLength();i++)if("application/oebps-package+xml".equals(((Element)roots.item(i)).getAttribute("media-type"))){root=(Element)roots.item(i);break;}
        String opf=resolve("",root.getAttribute("full-path"));Document doc=xml(opf);
        String title="";NodeList titles=doc.getElementsByTagNameNS("*","title");if(titles.getLength()>0)title=titles.item(0).getTextContent().trim();
        Map<String,Element> manifest=new HashMap<>();NodeList items=doc.getElementsByTagNameNS("*","item");
        for(int i=0;i<items.getLength();i++){Element item=(Element)items.item(i);manifest.put(item.getAttribute("id"),item);}
        NodeList spine=doc.getElementsByTagNameNS("*","itemref");if(spine.getLength()==0)throw new IOException("EPUB 没有阅读顺序");
        StringBuilder text=new StringBuilder();
        for(int i=0;i<spine.getLength();i++) {
            Element ref=(Element)spine.item(i);if("no".equals(ref.getAttribute("linear")))continue;
            Element item=manifest.get(ref.getAttribute("idref"));if(item==null)throw new IOException("EPUB 章节索引不完整");
            String type=item.getAttribute("media-type");
            if(!"application/xhtml+xml".equals(type)&&!"text/html".equals(type))throw new IOException("暂不支持该 EPUB 的章节格式："+type);
            String chapter=html(entry(resolve(opf,item.getAttribute("href"))));
            if(text.length()+chapter.length()>MAX_TEXT)throw new IOException("EPUB 正文过大");
            if(!chapter.trim().isEmpty())text.append(chapter.trim()).append("\n\n");
        }
        return new Result(title,text.toString());
    }
    private static String resolve(String base,String href)throws IOException {
        try {
            URI uri=new URI(href);if(uri.isAbsolute()||uri.getRawAuthority()!=null||uri.getRawQuery()!=null)throw new IOException("EPUB 包含不支持的外部资源路径");
            String path=uri.getPath();if(path==null||path.isEmpty()||path.startsWith("/")||path.contains("\\"))throw new IOException("EPUB 章节路径无效");
            String parent=base.contains("/")?base.substring(0,base.lastIndexOf('/')+1):"";
            Deque<String> parts=new ArrayDeque<>();for(String p:(parent+path).split("/")){if(p.isEmpty()||p.equals("."))continue;if(p.equals("..")){if(parts.isEmpty())throw new IOException("EPUB 路径超出书籍目录");parts.removeLast();}else parts.addLast(p);}
            return String.join("/",parts);
        }catch(java.net.URISyntaxException e){throw new IOException("EPUB 路径无效",e);}
    }
    private static String html(byte[] bytes)throws IOException {
        // XML declaration is authoritative for XHTML; UTF-8 is the normal default.
        String header=new String(bytes,0,Math.min(bytes.length,200),StandardCharsets.ISO_8859_1);
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("<\\?xml[^>]*encoding\\s*=\\s*['\"]([^'\"]+)['\"]",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(header);
        java.nio.charset.Charset charset=StandardCharsets.UTF_8;
        if(bytes.length>=2&&((bytes[0]==(byte)0xff&&bytes[1]==(byte)0xfe)||(bytes[0]==(byte)0xfe&&bytes[1]==(byte)0xff)))charset=StandardCharsets.UTF_16;
        else if(m.find())try{charset=java.nio.charset.Charset.forName(m.group(1));}catch(Exception e){throw new IOException("EPUB 章节编码不支持",e);}
        String markup=new String(bytes,charset).replace("\ufeff", "").replaceAll("(?s)<\\?.*?\\?>", "");
        final StringBuilder text=new StringBuilder();
        new ParserDelegator().parse(new StringReader(markup),new HTMLEditorKit.ParserCallback(){
            int hidden;
            private boolean hiddenTag(HTML.Tag t){return t==HTML.Tag.HEAD||t==HTML.Tag.SCRIPT||t==HTML.Tag.STYLE;}
            private void newline(){if(text.length()>0&&text.charAt(text.length()-1)!='\n')text.append('\n');}
            @Override public void handleStartTag(HTML.Tag t,MutableAttributeSet a,int p){if(hiddenTag(t))hidden++;else if(hidden==0&&t.isBlock())newline();}
            @Override public void handleEndTag(HTML.Tag t,int p){if(hiddenTag(t)){hidden=Math.max(0,hidden-1);}else if(hidden==0&&t.isBlock())newline();}
            @Override public void handleSimpleTag(HTML.Tag t,MutableAttributeSet a,int p){if(hidden==0&&(t==HTML.Tag.BR||t==HTML.Tag.HR))newline();}
            @Override public void handleText(char[] data,int p){if(hidden==0)text.append(data);}
        },true);
        return text.toString().replace('\u00a0',' ').replace("\ufeff","");
    }
}
