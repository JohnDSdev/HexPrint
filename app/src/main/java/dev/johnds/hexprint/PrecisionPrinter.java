package dev.johnds.hexprint;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

public final class PrecisionPrinter {
  public enum Mode { DOCUMENT, RAW_CALIBRATION, CORRECTED_CALIBRATION }
  private final Context context;
  private final Uri source;
  private final int pageCount;
  private final int paperWidthPt;
  private final int paperHeightPt;
  private final double docPercent;
  private final double factorX;
  private final double factorY;
  private final Mode mode;

  public PrecisionPrinter(Context ctx, Uri src, int pages, int w, int h,
      double documentPercent, double x, double y, Mode m) {
    context = ctx; source = src; pageCount = pages;
    paperWidthPt = w; paperHeightPt = h;
    docPercent = documentPercent; factorX = x; factorY = y; mode = m;
  }

  public void print() {
    PrintAttributes.MediaSize media =
        (paperWidthPt == 595 || paperHeightPt == 595)
            ? (paperWidthPt > paperHeightPt ? PrintAttributes.MediaSize.ISO_A4.asLandscape()
                                            : PrintAttributes.MediaSize.ISO_A4)
            : (paperWidthPt > paperHeightPt ? PrintAttributes.MediaSize.NA_LETTER.asLandscape()
                                            : PrintAttributes.MediaSize.NA_LETTER);
    PrintAttributes attrs = new PrintAttributes.Builder()
        .setMediaSize(media)
        .setResolution(new PrintAttributes.Resolution("300", "300 dpi", 300, 300))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
        .build();
    String job = mode == Mode.DOCUMENT ? "HexPrint 1:1 PDF" : "HexPrint dimensional calibration";
    ((PrintManager) context.getSystemService(Context.PRINT_SERVICE))
        .print(job, adapter(job), attrs);
  }

  private PrintDocumentAdapter adapter(String jobName) {
    return new PrintDocumentAdapter() {
      @Override public void onLayout(PrintAttributes oldAttributes,
          PrintAttributes newAttributes, CancellationSignal cancellationSignal,
          LayoutResultCallback callback, android.os.Bundle extras) {
        if (cancellationSignal.isCanceled()) { callback.onLayoutCancelled(); return; }
        int total = mode == Mode.DOCUMENT ? pageCount : 1;
        callback.onLayoutFinished(new PrintDocumentInfo.Builder(jobName + ".pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(total).build(), true);
      }

      @Override public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
          CancellationSignal cancellationSignal, WriteResultCallback callback) {
        new Thread(() -> {
          PdfDocument output = new PdfDocument();
          try {
            if (mode == Mode.DOCUMENT) makeDocument(output, cancellationSignal);
            else makeCalibration(output, mode == Mode.CORRECTED_CALIBRATION);
            if (cancellationSignal.isCanceled()) { callback.onWriteCancelled(); return; }
            try (FileOutputStream stream = new FileOutputStream(destination.getFileDescriptor())) {
              output.writeTo(stream);
            }
            callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
          } catch (Exception ex) {
            callback.onWriteFailed(ex.getMessage() == null ? "Unable to generate PDF" : ex.getMessage());
          } finally {
            output.close();
            try { destination.close(); } catch (IOException ignored) { }
          }
        }, "HexPrint-PDF").start();
      }
    };
  }

  private void makeDocument(PdfDocument dest, CancellationSignal cancel) throws IOException {
    if (source == null) throw new IOException("No PDF selected");
    try (ParcelFileDescriptor fd = context.getContentResolver().openFileDescriptor(source, "r")) {
      if (fd == null) throw new IOException("Unable to open PDF");
      try (PdfRenderer renderer = new PdfRenderer(fd)) {
        int total = renderer.getPageCount();
        for (int i = 0; i < total; i++) {
          if (cancel.isCanceled()) return;
          try (PdfRenderer.Page sourcePage = renderer.openPage(i)) {
            PdfDocument.Page page = dest.startPage(new PdfDocument.PageInfo.Builder(paperWidthPt,paperHeightPt,i+1).create());
            Canvas canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);
            double requestedW = ScaleMath.scaledDimension(sourcePage.getWidth(),docPercent,factorX);
            double requestedH = ScaleMath.scaledDimension(sourcePage.getHeight(),docPercent,factorY);
            float l = (float)((paperWidthPt - requestedW)/2.0);
            float t = (float)((paperHeightPt - requestedH)/2.0);
            float r = (float)(l + requestedW);
            float b = (float)(t + requestedH);

            // Rasterize at approximately 300 dpi, capped to avoid huge memory allocations.
            double ratio = Math.min(300.0 / 72.0,
                Math.min(3000.0 / sourcePage.getWidth(),3000.0 / sourcePage.getHeight()));
            int bmpW = Math.max(1,(int)Math.round(sourcePage.getWidth()*ratio));
            int bmpH = Math.max(1,(int)Math.round(sourcePage.getHeight()*ratio));
            Bitmap bitmap = Bitmap.createBitmap(bmpW,bmpH,Bitmap.Config.ARGB_8888);
            try {
              bitmap.eraseColor(Color.WHITE);
              sourcePage.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
              Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
              canvas.drawBitmap(bitmap,null,new RectF(l,t,r,b),imagePaint);
            } finally { bitmap.recycle(); }
            dest.finishPage(page);
          }
        }
      }
    }
  }

  private void makeCalibration(PdfDocument dest, boolean corrected) {
    double x = corrected ? factorX : 1.0;
    double y = corrected ? factorY : 1.0;
    PdfDocument.Page page = dest.startPage(new PdfDocument.PageInfo.Builder(paperWidthPt,paperHeightPt,1).create());
    Canvas c = page.getCanvas();
    c.drawColor(Color.WHITE);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(Color.BLACK);
    p.setStrokeWidth(0.8f);
    p.setStyle(Paint.Style.STROKE);

    // All marks stay safely inside non-borderless printer margins.
    float w = paperWidthPt, h = paperHeightPt;
    float hundredX = (float)(ScaleMath.mmToPoints(100)*x);
    float hundredY = (float)(ScaleMath.mmToPoints(100)*y);
    float boxW = (float)(ScaleMath.mmToPoints(110)*x);
    float boxH = (float)(ScaleMath.mmToPoints(70)*y);
    float lineY = Math.max(95, h*0.17f);
    float startX = (w - hundredX)/2;
    c.drawLine(startX,lineY,startX+hundredX,lineY,p);
    c.drawLine(startX,lineY-7,startX,lineY+7,p);
    c.drawLine(startX+hundredX,lineY-7,startX+hundredX,lineY+7,p);

    float vertX = w*0.16f;
    float vertTop = h*0.28f;
    c.drawLine(vertX,vertTop,vertX,vertTop+hundredY,p);
    c.drawLine(vertX-7,vertTop,vertX+7,vertTop,p);
    c.drawLine(vertX-7,vertTop+hundredY,vertX+7,vertTop+hundredY,p);

    float bx = (w-boxW)/2f;
    float by = Math.max(h*0.60f, h-boxH-45);
    // Shift if it would otherwise leave the bottom printable area.
    if (by+boxH > h-32) by = h-boxH-32;
    c.drawRect(bx,by,bx+boxW,by+boxH,p);

    p.setStyle(Paint.Style.FILL);
    p.setTextSize(15);
    c.drawText(corrected ? "HEXPRINT - CORRECTED VERIFICATION" : "HEXPRINT - RAW CALIBRATION",36,42,p);
    p.setTextSize(11);
    c.drawText("Print on the matching paper using Borders, not Borderless.",36,61,p);
    c.drawText("Measure line lengths center-to-center between tick marks.",36,78,p);
    c.drawText("100 mm - HORIZONTAL",startX,lineY-14,p);
    c.drawText("100 mm",vertX+16,vertTop+18,p);
    c.drawText("VERTICAL",vertX+16,vertTop+32,p);
    c.drawText("110 x 70 mm test rectangle",bx,by-10,p);
    c.drawText(String.format(Locale.US,"X %.4fx  |  Y %.4fx",x,y),36,h-16,p);
    dest.finishPage(page);
  }
}
