package com.packages.ebook;

import java.io.*;
import java.awt.image.BufferedImage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Opens one document per job; callers render off the UI thread. */
final class PdfReader {
    // The APK merges JARs, so PDFBox's ImageIO service file replaces JAI's.
    // Register explicitly to retain JPEG2000 alongside PDFBox's JBIG2 provider.
    private static void registerImages() {
        javax.imageio.spi.IIORegistry.getDefaultInstance().registerServiceProvider(new com.github.jaiimageio.jpeg2000.impl.J2KImageReaderSpi());
    }
    static PDDocument open(File file) throws IOException {
        PDDocument doc=Loader.loadPDF(file, IOUtils.createTempFileOnlyStreamCache());
        if(doc.isEncrypted()){doc.close();throw new IOException("暂不支持加密 PDF");}
        if(doc.getNumberOfPages()<1){doc.close();throw new IOException("PDF 没有页面");}
        return doc;
    }
    static int pageCount(File file)throws IOException {try(PDDocument doc=open(file)){return doc.getNumberOfPages();}}
    static BufferedImage render(File file,int page,int width,int height,double zoom)throws IOException {
        registerImages();
        try(PDDocument doc=open(file)) {
            PDRectangle box=doc.getPage(page).getCropBox();
            double w=box.getWidth(),h=box.getHeight();
            if(doc.getPage(page).getRotation()%180!=0){double t=w;w=h;h=t;}
            if(w<=0||h<=0||!Double.isFinite(w*h))throw new IOException("PDF 页面尺寸无效");
            double scale=Math.min(width/w,height/h)*zoom;
            scale=Math.min(scale,Math.sqrt(8000000.0/(w*h)));
            PDFRenderer renderer=new PDFRenderer(doc);renderer.setSubsamplingAllowed(true);
            return renderer.renderImage(page,(float)scale);
        }
    }
}
