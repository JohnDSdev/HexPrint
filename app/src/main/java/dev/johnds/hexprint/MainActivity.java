package dev.johnds.hexprint;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.util.Locale;

public class MainActivity extends Activity {
  private static final int PICK_PDF = 43;
  private static final int BG = 0xff080a0e, PANEL = 0xff161922;
  private static final int TEXT = 0xfff5f6fa, MUTED = 0xffa5acb9, ACCENT = 0xff8eaaff;
  private SharedPreferences pref;
  private Uri pdfUri;
  private int pageCount = 0, paperWidth = 612, paperHeight = 792;
  private double xFactor = 1, yFactor = 1;
  private EditText docScale, measuredX, measuredY;
  private TextView docInfo, corrInfo;
  private ImageView preview;
  private LinearLayout root;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
    pref = getSharedPreferences("hexprint",MODE_PRIVATE);
    xFactor = Double.longBitsToDouble(pref.getLong("xf",Double.doubleToLongBits(1)));
    yFactor = Double.longBitsToDouble(pref.getLong("yf",Double.doubleToLongBits(1)));
    paperWidth = pref.getInt("pw",612);
    paperHeight = pref.getInt("ph",792);
    buildScreen();
    String old = pref.getString("uri",null);
    if (old != null) {
      try { loadPdf(Uri.parse(old)); } catch (Exception ignored) {}
    }
  }

  private void buildScreen() {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
    root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(18),dp(18),dp(18),dp(35)); scroll.addView(root);
    setContentView(scroll);
    TextView title = label("HexPrint",30,TEXT,true); root.addView(title);
    root.addView(label("Precision PDF printing",14,MUTED,false));
    root.addView(space(22));

    LinearLayout fileCard = card();
    fileCard.addView(label("1  PDF document",17,TEXT,true));
    fileCard.addView(space(10));
    Button open = button("Choose PDF", true);
    open.setOnClickListener(v -> {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType("application/pdf");
      startActivityForResult(intent,PICK_PDF);
    });
    fileCard.addView(open);
    docInfo = label("No PDF selected",13,MUTED,false); fileCard.addView(docInfo);
    preview = new ImageView(this);
    preview.setAdjustViewBounds(true); preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
    LinearLayout.LayoutParams iv = new LinearLayout.LayoutParams(-1,dp(185));
    iv.topMargin=dp(9); fileCard.addView(preview,iv);
    root.addView(fileCard);
    root.addView(space(14));

    LinearLayout sizeCard = card();
    sizeCard.addView(label("2  Paper & PDF scale",17,TEXT,true));
    sizeCard.addView(space(10));
    Button letter = button("US Letter   8.5 x 11 in",false);
    letter.setOnClickListener(v -> setPaper(612,792));
    sizeCard.addView(letter);
    Button a4 = button("A4   210 x 297 mm",false);
    a4.setOnClickListener(v -> setPaper(595,842));
    sizeCard.addView(a4);
    Button rotate = button("Rotate paper orientation",false);
    rotate.setOnClickListener(v -> setPaper(paperHeight,paperWidth));
    sizeCard.addView(rotate);
    sizeCard.addView(label("PDF size (%)",13,MUTED,false));
    docScale = entry("100");
    docScale.setText(pref.getString("scale","100"));
    sizeCard.addView(docScale);
    sizeCard.addView(label("100 = source PDF's original physical dimensions. No fit-to-page is applied by HexPrint.",12,MUTED,false));
    root.addView(sizeCard);
    root.addView(space(14));

    LinearLayout cal = card();
    cal.addView(label("3  Printer calibration",17,TEXT,true));
    cal.addView(space(7));
    cal.addView(label("Use the same printer and print settings every time. Start with a raw test; measure both lines in mm using calipers.",13,MUTED,false));
    Button raw = button("Print RAW 100 mm test",false);
    raw.setOnClickListener(v -> doPrint(PrecisionPrinter.Mode.RAW_CALIBRATION));
    cal.addView(raw);
    cal.addView(label("Measured horizontal line (mm)",13,MUTED,false));
    measuredX = entry("e.g. 98.5"); cal.addView(measuredX);
    cal.addView(label("Measured vertical line (mm)",13,MUTED,false));
    measuredY = entry("e.g. 99.2"); cal.addView(measuredY);
    Button save = button("Save raw calibration",true);
    save.setOnClickListener(v -> updateCalibration(false));
    cal.addView(save);
    Button verify = button("Print CORRECTED verification",false);
    verify.setOnClickListener(v -> doPrint(PrecisionPrinter.Mode.CORRECTED_CALIBRATION));
    cal.addView(verify);
    Button refine = button("Refine using verification measurements",false);
    refine.setOnClickListener(v -> updateCalibration(true));
    cal.addView(refine);
    Button reset = button("Reset calibration to 100%",false);
    reset.setOnClickListener(v -> { xFactor=1; yFactor=1; persistFactors(); showCorrection(); toast("Calibration reset"); });
    cal.addView(reset);
    corrInfo = label("",13,ACCENT,true); cal.addView(corrInfo);
    showCorrection();
    root.addView(cal); root.addView(space(16));

    Button print = button("PRINT PDF AT ACTUAL SIZE",true);
    print.setOnClickListener(v -> doPrint(PrecisionPrinter.Mode.DOCUMENT));
    root.addView(print);
    root.addView(space(8));
    root.addView(label("Important: Android's print service may still resize a job. Use Borders, avoid Fit to Page, and verify physical dimensions after printing.",12,MUTED,false));
  }

  private void setPaper(int w,int h) {
    paperWidth=w; paperHeight=h;
    pref.edit().putInt("pw",w).putInt("ph",h).apply();
    toast("Paper: "+w+" x "+h+" PDF points");
  }

  private void doPrint(PrecisionPrinter.Mode mode) {
    if (mode==PrecisionPrinter.Mode.DOCUMENT && pdfUri==null) { toast("Choose a PDF first"); return; }
    double scale=100.0;
    try {
      scale=Double.parseDouble(docScale.getText().toString().trim());
      if (!Double.isFinite(scale) || scale<=0 || scale>400) throw new NumberFormatException();
    } catch (NumberFormatException e) { toast("PDF scale must be between 0 and 400%"); return; }
    pref.edit().putString("scale",docScale.getText().toString()).apply();
    try {
      new PrecisionPrinter(this,pdfUri,pageCount,paperWidth,paperHeight,
        scale,xFactor,yFactor,mode).print();
    } catch (Exception e) { toast("Printer error: "+e.getMessage()); }
  }

  private void updateCalibration(boolean refine) {
    try {
      double mx=Double.parseDouble(measuredX.getText().toString().trim());
      double my=Double.parseDouble(measuredY.getText().toString().trim());
      if (!(mx>75 && mx<125 && my>75 && my<125)) { toast("Enter measured lengths between 75 and 125 mm"); return; }
      double nx=ScaleMath.correctionFromMeasurement(100,mx), ny=ScaleMath.correctionFromMeasurement(100,my);
      xFactor=refine ? xFactor*nx : nx;
      yFactor=refine ? yFactor*ny : ny;
      if (xFactor<0.8 || xFactor>1.2 || yFactor<0.8 || yFactor>1.2) {
        toast("Correction is unusually large: inspect print settings"); return;
      }
      persistFactors(); showCorrection();
      measuredX.setText(""); measuredY.setText("");
      toast("Calibration saved. Print verification sheet next.");
    } catch(Exception ex) { toast("Enter both measured lengths in mm"); }
  }

  private void persistFactors() {
    pref.edit().putLong("xf",Double.doubleToLongBits(xFactor))
      .putLong("yf",Double.doubleToLongBits(yFactor)).apply();
  }
  private void showCorrection() {
    if (corrInfo!=null) corrInfo.setText(String.format(Locale.US,
      "Saved correction: horizontal %.3f%%  |  vertical %.3f%%",xFactor*100,yFactor*100));
  }

  @Override protected void onActivityResult(int request,int result,Intent data) {
    super.onActivityResult(request,result,data);
    if (request==PICK_PDF && result==RESULT_OK && data!=null && data.getData()!=null) {
      Uri uri=data.getData();
      try {
        getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch(SecurityException ignored) {}
      loadPdf(uri);
    }
  }
  private void loadPdf(Uri uri) {
    try (ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r")) {
      if (fd==null) throw new IOException("Cannot read PDF");
      try(PdfRenderer r=new PdfRenderer(fd)) {
        if (r.getPageCount()<1) throw new IOException("Empty PDF");
        pageCount=r.getPageCount(); pdfUri=uri;
        pref.edit().putString("uri",uri.toString()).apply();
        try(PdfRenderer.Page p=r.openPage(0)) {
          double wid=p.getWidth()*25.4/72.0, hei=p.getHeight()*25.4/72.0;
          docInfo.setText(String.format(Locale.US,
            "%d page(s)  |  first page %.1f x %.1f mm",pageCount,wid,hei));
          float rate=Math.min(760f/p.getWidth(),500f/p.getHeight());
          int bw=Math.max(1,(int)(p.getWidth()*rate)), bh=Math.max(1,(int)(p.getHeight()*rate));
          Bitmap bm=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);
          bm.eraseColor(Color.WHITE);
          p.render(bm,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
          preview.setImageBitmap(bm);
        }
      }
    } catch (Exception ex) { toast("PDF error: "+ex.getMessage()); }
  }

  private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
  private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density*n); }
  private View space(int height) {
    View v=new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(height))); return v;
  }
  private TextView label(String text,int sp,int color,boolean strong) {
    TextView t=new TextView(this); t.setText(text); t.setTextSize(sp); t.setTextColor(color);
    t.setPadding(0,dp(4),0,dp(4));
    if (strong) t.setTypeface(null,android.graphics.Typeface.BOLD);
    return t;
  }
  private LinearLayout card() {
    LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
    l.setPadding(dp(16),dp(14),dp(16),dp(16));
    GradientDrawable b=new GradientDrawable();
    b.setColor(PANEL); b.setCornerRadius(dp(22)); b.setStroke(dp(1),0xff303543); l.setBackground(b);
    return l;
  }
  private Button button(String name,boolean primary) {
    Button b=new Button(this); b.setAllCaps(false); b.setText(name); b.setTextSize(14);
    b.setTextColor(primary ? BG : TEXT);
    GradientDrawable d=new GradientDrawable();
    d.setColor(primary ? ACCENT : 0xff292f3d); d.setCornerRadius(dp(13));
    if (!primary) d.setStroke(dp(1),0xff475067);
    b.setBackground(d);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));
    lp.topMargin=dp(8); lp.bottomMargin=dp(4);
    b.setLayoutParams(lp);
    return b;
  }
  private EditText entry(String hint) {
    EditText e=new EditText(this); e.setSingleLine(true); e.setTextColor(TEXT); e.setHintTextColor(MUTED);
    e.setTextSize(16); e.setHint(hint);
    e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    e.setPadding(dp(14),0,dp(14),0);
    GradientDrawable d=new GradientDrawable(); d.setColor(0xff0f121a);
    d.setStroke(dp(1),0xff434a5c); d.setCornerRadius(dp(12)); e.setBackground(d);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(46)); lp.bottomMargin=dp(10);
    e.setLayoutParams(lp); return e;
  }
}
