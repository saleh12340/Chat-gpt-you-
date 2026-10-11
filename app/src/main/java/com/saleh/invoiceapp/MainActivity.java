package com.saleh.invoiceapp;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
  EditText total, qty, item, customer, paid;
  TextView unit, rows, summary;
  SQLiteDatabase db;
  StringBuilder invoice = new StringBuilder();
  double invoiceTotal=0; int rowCount=0;
  int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
  public void onCreate(Bundle b){super.onCreate(b); getWindow().setStatusBarColor(Color.rgb(23,54,48)); getWindow().setNavigationBarColor(Color.BLACK);
    db=openOrCreateDatabase("invoice_book.db",MODE_PRIVATE,null);
    db.execSQL("CREATE TABLE IF NOT EXISTS invoices(id INTEGER PRIMARY KEY AUTOINCREMENT, customer TEXT, date TEXT, total REAL, paid REAL, details TEXT)");
    buildUi();
  }
  TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(28,43,39));return t;}
  EditText field(String hint,int type){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setTextSize(16);e.setInputType(type);e.setPadding(dp(10),dp(5),dp(10),dp(5));e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(180,193,186)));return e;}
  LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(12),dp(8),dp(12),dp(8));return l;}
  void buildUi(){
    ScrollView scroll=new ScrollView(this); LinearLayout root=column();root.setBackgroundColor(Color.rgb(247,248,244));scroll.addView(root);
    TextView title=text("فاتورتي الحرارية",24);title.setTextColor(Color.rgb(23,74,61));title.setGravity(Gravity.CENTER);title.setTypeface(null,1);root.addView(title);
    TextView subtitle=text("إدخال سريع • حساب عميل • PDF • صورة • طباعة",13);subtitle.setGravity(Gravity.CENTER);root.addView(subtitle);
    customer=field("اسم العميل (اختياري)", android.text.InputType.TYPE_CLASS_TEXT);root.addView(customer);
    LinearLayout first=new LinearLayout(this);first.setOrientation(LinearLayout.HORIZONTAL);
    total=field("الإجمالي",android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);first.addView(total,new LinearLayout.LayoutParams(0,dp(52),1));
    qty=field("الكمية",android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);first.addView(qty,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(first);
    LinearLayout second=new LinearLayout(this);second.setOrientation(LinearLayout.HORIZONTAL);
    item=field("اسم الصنف",android.text.InputType.TYPE_CLASS_TEXT);second.addView(item,new LinearLayout.LayoutParams(0,dp(52),2));
    unit=text("سعر الوحدة —",15);unit.setGravity(Gravity.CENTER);second.addView(unit,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(second);
    total.addTextChangedListener(watch());qty.addTextChangedListener(watch());
    Button add=button("إضافة الصنف");root.addView(add);add.setOnClickListener(v->addItem());
    rows=text("تفاصيل الفاتورة",15);rows.setPadding(0,dp(8),0,dp(8));root.addView(rows);
    summary=text("الإجمالي: 0",18);summary.setTypeface(null,1);root.addView(summary);
    paid=field("المبلغ المدفوع",android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);root.addView(paid);
    LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);
    Button save=button("حفظ الفاتورة في حساب العميل");actions.addView(save);save.setOnClickListener(v->saveInvoice());
    Button pdf=button("تصدير PDF عالي الدقة");actions.addView(pdf);pdf.setOnClickListener(v->exportPdf());
    Button image=button("حفظ صورة الفاتورة");actions.addView(image);image.setOnClickListener(v->exportImage());
    Button print=button("طباعة حرارية / اختيار الطابعة");actions.addView(print);print.setOnClickListener(v->printInvoice());
    Button history=button("فواتير وعمليات العملاء");actions.addView(history);history.setOnClickListener(v->showHistory());
    Button receipt=button("تسجيل سند قبض");actions.addView(receipt);receipt.setOnClickListener(v->paymentDialog(true));
    Button expense=button("تسجيل سند صرف");actions.addView(expense);expense.setOnClickListener(v->paymentDialog(false));
    root.addView(actions);setContentView(scroll);
  }
  android.text.TextWatcher watch(){return new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int st,int before,int count){try{double t=Double.parseDouble(total.getText().toString()),q=Double.parseDouble(qty.getText().toString());unit.setText(q==0?"سعر الوحدة —":String.format(Locale.US,"%.2f",t/q));}catch(Exception e){unit.setText("سعر الوحدة —");}}public void afterTextChanged(android.text.Editable e){}};}
  Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(23,74,61)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(46));p.setMargins(0,dp(3),0,dp(3));b.setLayoutParams(p);return b;}
  void addItem(){try{double t=Double.parseDouble(total.getText().toString()),q=Double.parseDouble(qty.getText().toString());String n=item.getText().toString().trim();if(n.isEmpty()||q<=0||t<0){toast("أدخل الصنف والكمية والإجمالي");return;}double u=t/q;invoice.append(n).append("  |  ").append(q).append(" × ").append(String.format(Locale.US,"%.2f",u)).append(" = ").append(String.format(Locale.US,"%.2f",t)).append("\n");invoiceTotal+=t;rowCount++;rows.setText(invoice.toString());summary.setText("الإجمالي: "+money(invoiceTotal)+"  •  الأصناف: "+rowCount);total.setText("");qty.setText("");item.setText("");}catch(Exception e){toast("تحقق من الإجمالي والكمية");}}
  String money(double d){return String.format(Locale.US,"%,.2f",d);}
  String date(){return new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(new Date());}
  String receiptText(){String c=customer.getText().toString().trim();if(c.isEmpty())c="عميل نقدي";double p=0;try{p=Double.parseDouble(paid.getText().toString());}catch(Exception e){}return "        بقالة / فاتورتي\n------------------------------\nالعميل: "+c+"\nالتاريخ: "+date()+"\n------------------------------\n"+invoice+"------------------------------\nالإجمالي: "+money(invoiceTotal)+"\nالمدفوع: "+money(p)+"\nالمتبقي: "+money(invoiceTotal-p)+"\n";}
  void saveInvoice(){if(rowCount==0){toast("أضف صنفاً أولاً");return;}try{double p=Double.parseDouble(paid.getText().toString().isEmpty()?"0":paid.getText().toString());android.content.ContentValues v=new android.content.ContentValues();v.put("customer",customer.getText().toString().trim().isEmpty()?"عميل نقدي":customer.getText().toString().trim());v.put("date",date());v.put("total",invoiceTotal);v.put("paid",p);v.put("details",receiptText());db.insert("invoices",null,v);toast("تم حفظ الفاتورة في حساب العميل");}catch(Exception e){toast("تعذر الحفظ؛ تحقق من المبلغ المدفوع");}}
  void showHistory(){Cursor c=db.rawQuery("SELECT id,customer,date,total,paid FROM invoices ORDER BY id DESC",null);StringBuilder s=new StringBuilder("الفواتير والعمليات\n\n");while(c.moveToNext())s.append("#").append(c.getInt(0)).append(" • ").append(c.getString(1)).append("\n").append(c.getString(2)).append("\nالإجمالي: ").append(money(c.getDouble(3))).append(" | المتبقي: ").append(money(c.getDouble(3)-c.getDouble(4))).append("\n----------------\n");c.close();new AlertDialog.Builder(this).setTitle("حسابات العملاء").setMessage(s.length()>20?s.toString():"لا توجد فواتير محفوظة").setPositiveButton("إغلاق",null).show();}
  void paymentDialog(boolean incoming){LinearLayout l=column();EditText name=field("اسم العميل",android.text.InputType.TYPE_CLASS_TEXT);EditText amount=field("المبلغ",android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);EditText note=field("البيان (اختياري)",android.text.InputType.TYPE_CLASS_TEXT);l.addView(name);l.addView(amount);l.addView(note);new AlertDialog.Builder(this).setTitle(incoming?"سند قبض":"سند صرف").setView(l).setNegativeButton("إلغاء",null).setPositiveButton("حفظ", (d,w)->{try{ContentValues v=new ContentValues();v.put("customer",name.getText().toString());v.put("date",date()+" • "+(incoming?"سند قبض":"سند صرف")+" • "+note.getText());v.put("total",0);v.put("paid",Double.parseDouble(amount.getText().toString()));v.put("details",(incoming?"سند قبض":"سند صرف")+"\n"+name.getText()+"\nالمبلغ: "+amount.getText()+"\n"+note.getText());db.insert("invoices",null,v);toast("تم حفظ السند");}catch(Exception e){toast("أدخل مبلغاً صحيحاً");}}).show();}
  Bitmap render(){int width=576;String content=receiptText();String[] lines=content.split("\n");Paint p=new Paint(3);p.setColor(Color.BLACK);p.setTextSize(26);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));int lineH=34;Bitmap b=Bitmap.createBitmap(width,Math.max(150,lines.length*lineH+40),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);int y=35;for(String line:lines){c.drawText(line,12,y,p);y+=lineH;}return b;}
  void exportPdf(){if(rowCount==0){toast("أضف صنفاً أولاً");return;}try{PdfDocument doc=new PdfDocument();Bitmap b=render();PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(432,b.getHeight()/2,1).create());page.getCanvas().drawBitmap(b,null,new Rect(0,0,432,b.getHeight()/2),null);doc.finishPage(page);String name="Invoice_"+System.currentTimeMillis()+".pdf";if(Build.VERSION.SDK_INT>=29){ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,"application/pdf");v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/فاتورتي");Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);OutputStream o=getContentResolver().openOutputStream(u);doc.writeTo(o);o.close();}else{File dir=new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"");dir.mkdirs();FileOutputStream o=new FileOutputStream(new File(dir,name));doc.writeTo(o);o.close();}doc.close();b.recycle();toast("تم تصدير PDF إلى مجلد فاتورتي");}catch(Exception e){toast("تعذر إنشاء PDF: "+e.getMessage());}}
  void exportImage(){if(rowCount==0){toast("أضف صنفاً أولاً");return;}try{Bitmap b=render();ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,"Invoice_"+System.currentTimeMillis()+".png");v.put(MediaStore.Images.Media.MIME_TYPE,"image/png");if(Build.VERSION.SDK_INT>=29)v.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/فاتورتي");Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);OutputStream o=getContentResolver().openOutputStream(u);b.compress(Bitmap.CompressFormat.PNG,100,o);o.close();b.recycle();toast("تم حفظ صورة الفاتورة");}catch(Exception e){toast("تعذر حفظ الصورة");}}
  void printInvoice(){if(rowCount==0){toast("أضف صنفاً أولاً");return;}if(Build.VERSION.SDK_INT>=19){PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);pm.print("فاتورة",new ReceiptAdapter(),new android.print.PrintAttributes.Builder().setMediaSize(android.print.PrintAttributes.MediaSize.UNKNOWN_PORTRAIT).setResolution(new android.print.PrintAttributes.Resolution("thermal","Thermal",203,203)).setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS).build());}else toast("الطباعة تحتاج Android 4.4 أو أحدث");}
  class ReceiptAdapter extends android.print.PrintDocumentAdapter {Bitmap bmp;public void onLayout(android.print.PrintAttributes oldA,android.print.PrintAttributes newA,CancellationSignal s,LayoutResultCallback cb,Bundle extras){bmp=render();cb.onLayoutFinished(new android.print.PrintDocumentInfo.Builder("invoice.pdf").setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(),true);}public void onWrite(PageRange[] pages,ParcelFileDescriptor fd,CancellationSignal s,WriteResultCallback cb){try{PdfDocument d=new PdfDocument();PdfDocument.Page p=d.startPage(new PdfDocument.PageInfo.Builder(432,Math.max(300,bmp.getHeight()/2),1).create());p.getCanvas().drawBitmap(bmp,null,new Rect(0,0,432,bmp.getHeight()/2),null);d.finishPage(p);d.writeTo(new FileOutputStream(fd.getFileDescriptor()));d.close();cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});}catch(Exception e){cb.onWriteFailed(e.toString());}}}
  void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
