package com.packages.ebook;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Stores independent, normalized reading copies, so source files may be moved later. */
final class BookLibrary {
    static final long MAX_SOURCE = 32L * 1024 * 1024;
    static final class Book {
        final String id, title, text, format;
        final File assets;
        Book(String id,String title,String text,String format) { this(id,title,text,format,null); }
        Book(String id,String title,String text,String format,File assets) { this.id=id;this.title=title;this.text=text;this.format=format;this.assets=assets; }
    }
    private final File directory;
    BookLibrary(File directory) { this.directory=directory; }
    List<Book> load() {
        List<Book> books=new ArrayList<>();
        File[] files=directory.listFiles((d,n)->n.matches("[a-f0-9]{64}\\.properties"));
        if(files==null)return books;
        Arrays.sort(files,Comparator.comparing(File::getName));
        for(File file:files)try(InputStream in=new FileInputStream(file)) {
            Properties p=new Properties();p.load(in);
            String id=file.getName().replace(".properties","");
            if("PDF".equals(p.getProperty("format"))) {
                File pdf=new File(directory,id+".pdf");
                int count=Integer.parseInt(p.getProperty("pages","0"));
                if(pdf.isFile()&&count>0)books.add(new Book(id,p.getProperty("title","未命名"),""+count,"PDF",pdf));
                continue;
            }
            File textFile=new File(directory,id+".txt");
            if(textFile.length()>EpubReader.MAX_TEXT)continue;
            String text=utf8(Files.readAllBytes(textFile.toPath()));
            if(!text.trim().isEmpty())books.add(new Book(id,p.getProperty("title","未命名"),text,p.getProperty("format","TXT"),new File(directory,id+"-assets")));
        }catch(IOException | NumberFormatException ignored) { /* A damaged entry must not hide other books. */ }
        return books;
    }
    static boolean supported(String name) {
        String lower=name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf")||lower.endsWith(".txt")||lower.endsWith(".epub")||lower.endsWith(".md")||lower.endsWith(".markdown");
    }
    Book importFile(File source)throws IOException {
        if(!source.isFile()||!source.canRead())throw new IOException("文件不存在或无法读取");
        String name=source.getName(),lower=name.toLowerCase(Locale.ROOT);
        if(!supported(name))throw new IOException("请选择 TXT、EPUB、Markdown 或 PDF 文件");
        if(lower.endsWith(".pdf"))return importPdf(source);
        if(source.length()>MAX_SOURCE)throw new IOException("文件过大，最大支持 32 MB");
        byte[] original;
        try(InputStream in=new FileInputStream(source)){original=EpubReader.readLimited(in,(int)MAX_SOURCE);}
        String id=digest(original),title=name.substring(0,name.lastIndexOf('.')),text,format;
        boolean markdown=lower.endsWith(".md")||lower.endsWith(".markdown");
        if(markdown) {text=utf8(original);format="MD";id=digest(("markdown:"+id).getBytes(StandardCharsets.UTF_8));}
        else if(lower.endsWith(".txt")) {text=utf8(original);format="TXT";}
        else { EpubReader.Result result=EpubReader.read(source);text=result.text;title=result.title.isEmpty()?title:result.title;format="EPUB"; }
        text=text.replace("\r\n","\n").replace('\r','\n');
        if(text.startsWith("\ufeff"))text=text.substring(1);
        if(text.indexOf('\0')>=0)throw new IOException("文件不是可阅读的文本");
        if(text.trim().isEmpty())throw new IOException("书籍中没有可阅读的正文");
        if(text.getBytes(StandardCharsets.UTF_8).length>EpubReader.MAX_TEXT)throw new IOException("正文过大，最大支持 8 MB");
        Files.createDirectories(directory.toPath());
        File assets=new File(directory,id+"-assets");
        if(markdown) {
            title=MarkdownReader.title(text,title);
            if(!assets.exists()) {
                Path staging=Files.createTempDirectory(directory.toPath(),"assets-");
                try { MarkdownReader.copyImages(text,source.getCanonicalFile().getParentFile(),staging.toFile());Files.move(staging,assets.toPath()); }
                finally { if(Files.exists(staging))try(java.util.stream.Stream<Path> paths=Files.walk(staging)){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.deleteIfExists(path);} }
            }
        }
        writeAtomic(new File(directory,id+".txt"),text.getBytes(StandardCharsets.UTF_8));
        Properties p=new Properties();p.setProperty("title",title);p.setProperty("format",format);
        ByteArrayOutputStream meta=new ByteArrayOutputStream();p.store(meta,"Imported ebook");
        writeAtomic(new File(directory,id+".properties"),meta.toByteArray());
        return new Book(id,title,text,format,assets);
    }
    private Book importPdf(File source)throws IOException {
        if(source.length()>256L*1024*1024)throw new IOException("PDF 最大支持 256 MB");
        Files.createDirectories(directory.toPath());
        Path temp=Files.createTempFile(directory.toPath(),"pdf-",".tmp");
        try {
            MessageDigest hash;
            try{hash=MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
            try(InputStream in=new FileInputStream(source);OutputStream out=Files.newOutputStream(temp)) {
                byte[] buffer=new byte[65536];int n;long total=0;
                while((n=in.read(buffer))!=-1){total+=n;if(total>256L*1024*1024)throw new IOException("PDF 最大支持 256 MB");hash.update(buffer,0,n);out.write(buffer,0,n);}
            }
            int count=PdfReader.pageCount(temp.toFile());
            StringBuilder hex=new StringBuilder();for(byte b:hash.digest())hex.append(String.format("%02x",b&255));
            String id=hex.toString(),title=source.getName().substring(0,source.getName().lastIndexOf('.'));
            File target=new File(directory,id+".pdf");
            Files.move(temp,target.toPath(),StandardCopyOption.REPLACE_EXISTING);
            Properties meta=new Properties();meta.setProperty("title",title);meta.setProperty("format","PDF");meta.setProperty("pages",""+count);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();meta.store(bytes,"Imported PDF");
            writeAtomic(new File(directory,id+".properties"),bytes.toByteArray());
            return new Book(id,title,""+count,"PDF",target);
        }finally{Files.deleteIfExists(temp);}
    }
    private static void writeAtomic(File target,byte[] data)throws IOException {
        File temp=File.createTempFile("import-",".tmp",target.getParentFile());
        try {Files.write(temp.toPath(),data);Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}finally{temp.delete();}
    }
    static String utf8(byte[] bytes)throws IOException {
        try {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException e){throw new IOException("文本编码不是有效的 UTF-8，请先转换编码",e);}
    }
    private static String digest(byte[] bytes)throws IOException {
        try {StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format("%02x",b&255));return s.toString();}
        catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
    }
}
