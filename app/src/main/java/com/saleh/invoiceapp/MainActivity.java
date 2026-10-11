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
  static class InvoiceItem { String name; double qty,total; InvoiceItem(String n,double q,double t){name=n;qty=q;total=t;} }
  ArrayList<InvoiceItem> invoiceItems = new ArrayList<>();
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
    TextView title=text("فواتيري حق البيع والشراء",24);title.setTextColor(Color.rgb(23,74,61));title.setGravity(Gravity.CENTER);title.setTypeface(null,1);root.addView(title);
    TextView subtitle=text("بيع وشراء • حسابات العملاء • طباعة وPDF وصورة",13);subtitle.setGravity(Gravity.CENTER);root.addView(subtitle);
    customer=field("اسم العميل (اختياري)", android.text.InputType.TYPE_CLASS_TEXT);root.addView(customer);
    LinearLayout first=new LinearLayout(this);first.setOrientation(LinearLayout.HORIZONTAL);
    total=field("الإجمالي",android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);first.addView(total,new LinearLayout.LayoutParams(0,dp(52),1));
    qty=field("الكمية",android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);first.addView(qty,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(first);
    LinearLayout second=new LinearLayout(this);second.setOrientation(LinearLayout.HORIZONTAL);
    item=field("اسم الصنف",android.text.InputType.TYPE_CLASS_TEXT);second.addView(item,new LinearLayout.LayoutParams(0,dp(52),2));
    unit=text("سعر الوحدة —",15);unit.setGravity(Gravity.CENTER);second.addView(unit,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(second);
    total.addTextChangedListener(watch());qty.addTextChangedListener(watch());
    Button add=button("إضافة الصنف");root.addView(add);add.setOnClickListener(v->addItem());
    rows=text("الأصناف المسجلة — اضغط على أي صف لتعديله",14);rows.setPadding(0,dp(8),0,dp(8));root.addView(rows);
    renderInvoiceRows(root);
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
  void addItem(){try{double t=Double.parseDouble(total.getText().toString().replace(",","")),q=Double.parseDouble(qty.getText().toString().replace(",",""));String n=item.getText().toString().trim();if(n.isEmpty()||q<=0||t<0){toast("أدخل الصنف والكمية والإجمالي");return;}invoiceItems.add(new InvoiceItem(n,q,t));rebuildInvoice();total.setText("");qty.setText("");item.setText("");refreshInvoiceRows();}catch(Exception e){toast("تحقق من الإجمالي والكمية");}}
  void rebuildInvoice(){invoice.setLength(0);invoiceTotal=0;rowCount=invoiceItems.size();for(InvoiceItem it:invoiceItems){double u=it.qty==0?0:it.total/it.qty;invoice.append(money(it.total)).append("  |  ").append(it.qty).append("  |  ").append(it.name).append("  |  ").append(money(u)).append("\\n");invoiceTotal+=it.total;}if(rows!=null)rows.setText("الأصناف المسجلة ("+rowCount+") — اضغط على الصف للتعديل");if(summary!=null)summary.setText("الإجمالي: "+money(invoiceTotal)+"  •  الأصناف: "+rowCount);}
  void renderInvoiceRows(LinearLayout host){if(host!=null){LinearLayout table=new LinearLayout(this);table.setOrientation(1);table.setTag("invoice_items_table");host.addView(table,new LinearLayout.LayoutParams(-1,-2));populateInvoiceTable(table);}}
  
  void editInvoiceItem(int index){if(index<0||index>=invoiceItems.size())return;InvoiceItem it=invoiceItems.get(index);LinearLayout form=column();EditText eTotal=field("الإجمالي",android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);eTotal.setText(String.valueOf(it.total));EditText eQty=field("الكمية / العدد",android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);eQty.setText(String.valueOf(it.qty));EditText eName=field("تفاصيل الصنف",android.text.InputType.TYPE_CLASS_TEXT);eName.setText(it.name);form.addView(eTotal);form.addView(eQty);form.addView(eName);new AlertDialog.Builder(this).setTitle("تعديل الصنف").setView(form).setNegativeButton("إلغاء",null).setNeutralButton("حذف الصنف",(d,w)->{invoiceItems.remove(index);rebuildInvoice();refreshInvoiceRows();}).setPositiveButton("حفظ التعديل",(d,w)->{try{double t=Double.parseDouble(eTotal.getText().toString().replace(",","")),q=Double.parseDouble(eQty.getText().toString().replace(",",""));String n=eName.getText().toString().trim();if(q<=0||t<0||n.isEmpty()){toast("تحقق من التفاصيل والكمية والإجمالي");return;}it.total=t;it.qty=q;it.name=n;rebuildInvoice();refreshInvoiceRows();toast("تم تعديل الصنف");}catch(Exception ex){toast("تحقق من القيم المدخلة");}}).show();}
  void refreshInvoiceRows(){View v=findTable(getWindow().getDecorView());if(v instanceof LinearLayout){LinearLayout table=(LinearLayout)v;table.removeAllViews();populateInvoiceTable(table);}}
  View findTable(View v){if("invoice_items_table".equals(v.getTag()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View r=findTable(g.getChildAt(i));if(r!=null)return r;}}return null;}
  void populateInvoiceTable(LinearLayout table){LinearLayout header=new LinearLayout(this);header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);header.setBackgroundColor(Color.rgb(23,74,61));String[] labels={"الإجمالي","الكمية","التفاصيل","سعر الوحدة"};float[] weights={1.05f,.8f,1.55f,1.05f};for(int i=0;i<labels.length;i++){TextView t=text(labels[i],11);t.setTextColor(Color.WHITE);t.setTypeface(null,1);t.setGravity(Gravity.CENTER);header.addView(t,new LinearLayout.LayoutParams(0,dp(38),weights[i]));}table.addView(header);for(int i=0;i<invoiceItems.size();i++){final int ix=i;InvoiceItem it=invoiceItems.get(i);LinearLayout row=new LinearLayout(this);row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);row.setGravity(Gravity.CENTER_VERTICAL);row.setBackgroundColor(i%2==0?Color.WHITE:Color.rgb(235,241,237));String[] values={money(it.total),String.valueOf(it.qty),it.name,money(it.qty==0?0:it.total/it.qty)};for(int j=0;j<values.length;j++){TextView t=text(values[j],12);t.setGravity(Gravity.CENTER);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(t,new LinearLayout.LayoutParams(0,dp(46),weights[j]));}row.setOnClickListener(v->editInvoiceItem(ix));table.addView(row);View line=new View(this);line.setBackgroundColor(Color.rgb(190,205,197));table.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}}

  String money(double d){return String.format(Locale.US,"%,.2f",d);}
  String date(){return new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(new Date());}
  String receiptText(){String c=customer.getText().toString().trim();if(c.isEmpty())c="عميل نقدي";double p=0;try{p=Double.parseDouble(paid.getText().toString());}catch(Exception e){}return "        بقالة / فاتورتي\n------------------------------\nالعميل: "+c+"\nالتاريخ: "+date()+"\n------------------------------\n"+invoice+"------------------------------\nالإجمالي: "+money(invoiceTotal)+"\nالمدفوع: "+money(p)+"\nالمتبقي: "+money(invoiceTotal-p)+"\n";}
  void saveInvoice(){if(rowCount==0){toast("أضف صنفاً أولاً");return;}try{double p=Double.parseDouble(paid.getText().toString().isEmpty()?"0":paid.getText().toString());android.content.ContentValues v=new android.content.ContentValues();v.put("customer",customer.getText().toString().trim().isEmpty()?"عميل نقدي":customer.getText().toString().trim());v.put("date",date());v.put("total",invoiceTotal);v.put("paid",p);v.put("details",receiptText());db.insert("invoices",null,v);toast("تم حفظ الفاتورة في حساب العميل");}catch(Exception e){toast("تعذر الحفظ؛ تحقق من المبلغ المدفوع");}}
  void showHistory(){
    ArrayList<String> names=new ArrayList<>();
    Cursor c=db.rawQuery("SELECT DISTINCT customer FROM invoices WHERE customer IS NOT NULL AND TRIM(customer)<>'' ORDER BY customer COLLATE NOCASE",null);
    while(c.moveToNext()) names.add(c.getString(0)); c.close();
    LinearLayout root=column(); root.setBackgroundColor(Color.rgb(247,248,244));
    LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
    Button back=button("رجوع للفواتير"); back.setLayoutParams(new LinearLayout.LayoutParams(0,dp(46),1)); back.setOnClickListener(v->buildUi());
    TextView title=text("العملاء",23); title.setTypeface(null,1); title.setGravity(Gravity.CENTER); title.setLayoutParams(new LinearLayout.LayoutParams(0,dp(46),1));
    head.addView(back); head.addView(title); root.addView(head);
    EditText search=field("بحث عن عميل",android.text.InputType.TYPE_CLASS_TEXT); root.addView(search);
    LinearLayout list=new LinearLayout(this); list.setOrientation(1); root.addView(list,new LinearLayout.LayoutParams(-1,-2));
    Runnable refresh=()->{list.removeAllViews();String q=search.getText().toString().trim();for(String name:names){if(!name.contains(q))continue;Cursor x=db.rawQuery("SELECT COUNT(*),COALESCE(SUM(total-paid),0) FROM invoices WHERE customer=?",new String[]{name});int count=0;double bal=0;if(x.moveToFirst()){count=x.getInt(0);bal=x.getDouble(1);}x.close();LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(7),dp(10),dp(7));row.setBackgroundColor(Color.WHITE);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(62));rp.setMargins(0,dp(2),0,dp(2));row.setLayoutParams(rp);row.setOrientation(LinearLayout.HORIZONTAL);TextView nm=text(name,17);nm.setTypeface(null,1);TextView info=text(count+" عملية",12);TextView balance=text("الرصيد: "+money(bal),14);balance.setGravity(Gravity.CENTER);LinearLayout left=new LinearLayout(this);left.setOrientation(1);left.addView(nm);left.addView(info);row.addView(left,new LinearLayout.LayoutParams(0,-2,1));row.addView(balance,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->showCustomerAccount(name));list.addView(row);}};
    search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){refresh.run();}public void afterTextChanged(android.text.Editable e){}});
    refresh.run(); ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
  }
  void showCustomerAccount(String name){
    LinearLayout root=column();root.setBackgroundColor(Color.rgb(247,248,244));
    Button back=button("← العملاء");root.addView(back);back.setOnClickListener(v->showHistory());
    TextView title=text("حساب العميل: "+name,21);title.setTypeface(null,1);title.setGravity(Gravity.CENTER);root.addView(title);
    Cursor sum=db.rawQuery("SELECT COALESCE(SUM(total-paid),0),COUNT(*) FROM invoices WHERE customer=?",new String[]{name});double balance=0;int count=0;if(sum.moveToFirst()){balance=sum.getDouble(0);count=sum.getInt(1);}sum.close();
    TextView overview=text("الرصيد التراكمي: "+money(balance)+"    •    العمليات: "+count,16);overview.setTypeface(null,1);root.addView(overview);
    LinearLayout header=new LinearLayout(this);header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);header.setBackgroundColor(Color.rgb(23,74,61));String[] labels={"التاريخ","مبلغ العملية","تفاصيل العملية","الرصيد"};float[] weights={1.15f,1f,1.65f,1f};for(int i=0;i<labels.length;i++){TextView t=new TextView(this);t.setText(labels[i]);t.setTextColor(Color.WHITE);t.setTextSize(11);t.setGravity(Gravity.CENTER);t.setTypeface(null,1);header.addView(t,new LinearLayout.LayoutParams(0,dp(42),weights[i]));}root.addView(header);
    LinearLayout list=new LinearLayout(this);list.setOrientation(1);list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    Cursor cur=db.rawQuery("SELECT id,date,total,paid,details FROM invoices WHERE customer=? ORDER BY id ASC",new String[]{name});double running=0;ArrayList<String[]> ops=new ArrayList<>();while(cur.moveToNext()){double amount=cur.getDouble(2)-cur.getDouble(3);running+=amount;String dt=cur.getString(1)==null?"":cur.getString(1);String detail=cur.getString(4)==null?"":cur.getString(4).replace("\n"," ").replace("\r"," ");ops.add(new String[]{cur.getString(0),dt,money(amount),detail,money(running)});}cur.close();
    for(int i=ops.size()-1;i>=0;i--){String[] op=ops.get(i);LinearLayout row=new LinearLayout(this);row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(3),dp(2),dp(3));row.setBackgroundColor(i%2==0?Color.WHITE:Color.rgb(235,241,237));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(54));rp.setMargins(0,dp(1),0,0);row.setLayoutParams(rp);String[] vals={op[1],op[2],op[3],op[4]};for(int j=0;j<vals.length;j++){TextView t=text(vals[j],10);t.setGravity(Gravity.CENTER);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(t,new LinearLayout.LayoutParams(0,-1,weights[j]));}row.setOnClickListener(v->operationDialog(name,op));list.addView(row);}
    root.addView(list);ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
  }
  void operationDialog(String name,String[] op){
    new AlertDialog.Builder(this).setTitle("عملية رقم "+op[0]).setMessage("العميل: "+name+"\nالتاريخ: "+op[1]+"\nمبلغ العملية: "+op[2]+"\nالرصيد التراكمي: "+op[4]+"\n\n"+op[3]).setNegativeButton("إغلاق",null).setPositiveButton("طباعة العملية",(d,w)->printText("كشف عملية\nالعميل: "+name+"\nالتاريخ: "+op[1]+"\nمبلغ العملية: "+op[2]+"\nالتفاصيل: "+op[3]+"\nالرصيد التراكمي: "+op[4])).show();
  }
  void printText(String content){
    if(Build.VERSION.SDK_INT>=19){PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);pm.print("عملية العميل",new android.print.PrintDocumentAdapter(){public void onLayout(android.print.PrintAttributes a,android.print.PrintAttributes b,CancellationSignal s,LayoutResultCallback cb,Bundle e){cb.onLayoutFinished(new android.print.PrintDocumentInfo.Builder("operation.pdf").setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(),true);}public void onWrite(PageRange[] pages,ParcelFileDescriptor fd,CancellationSignal s,WriteResultCallback cb){PdfDocument doc=new PdfDocument();try{String[] lines=content.split("\\n");Paint p=new Paint(3);p.setColor(Color.BLACK);p.setTextSize(20);int h=Math.max(300,lines.length*30+40);PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(432,h,1).create());int y=30;for(String line:lines){page.getCanvas().drawText(line,12,y,p);y+=30;}doc.finishPage(page);doc.writeTo(new FileOutputStream(fd.getFileDescriptor()));cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});}catch(Exception ex){cb.onWriteFailed(ex.toString());}finally{doc.close();}}},new android.print.PrintAttributes.Builder().setMediaSize(android.print.PrintAttributes.MediaSize.UNKNOWN_PORTRAIT).setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS).build());}else toast("الطباعة تحتاج Android 4.4 أو أحدث");
  }
  void paymentDialog(boolean incoming){LinearLayout l=column();EditText name=field("اسم العميل",android.text.InputType.TYPE_CLASS_TEXT);EditText amount=field("المبلغ",android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);EditText note=field("البيان (اختياري)",android.text.InputType.TYPE_CLASS_TEXT);l.addView(name);l.addView(amount);l.addView(note);new AlertDialog.Builder(this).setTitle(incoming?"سند قبض":"سند صرف").setView(l).setNegativeButton("إلغاء",null).setPositiveButton("حفظ", (d,w)->{try{ContentValues v=new ContentValues();v.put("customer",name.getText().toString());v.put("date",date()+" • "+(incoming?"سند قبض":"سند صرف")+" • "+note.getText());v.put("total",0);v.put("paid",Double.parseDouble(amount.getText().toString()));v.put("details",(incoming?"سند قبض":"سند صرف")+"\n"+name.getText()+"\nالمبلغ: "+amount.getText()+"\n"+note.getText());db.insert("invoices",null,v);toast("تم حفظ السند");}catch(Exception e){toast("أدخل مبلغاً صحيحاً");}}).show();}
  Bitmap render(){int width=576;String content=receiptText();String[] lines=content.split("\n");Paint p=new Paint(3);p.setColor(Color.BLACK);p.setTextSize(26);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));int lineH=34;Bitmap b=Bitmap.createBitmap(width,Math.max(150,lines.length*lineH+40),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);int y=35;for(String line:lines){c.drawText(line,12,y,p);y+=lineH;}return b;}
  void exportPdf(){if(rowCount==0){toast("أضف صنفاً أولاً");return;}try{PdfDocument doc=new PdfDocument();Bitmap b=render();PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(432,b.getHeight()/2,1).create());page.getCanvas().drawBitmap(b,null,new Rect(0,0,432,b.getHeight()/2),null);doc.finishPage(page);String name="Invoice_"+System.currentTimeMillis()+".pdf";if(Build.VERSION.SDK_INT>=29){ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,"application/pdf");v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/فاتورتي");Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);OutputStream o=getContentResolver().openOutputStream(u);doc.writeTo(o);o.close();}else{File dir=new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"");dir.mkdirs();FileOutputStream o=new FileOutputStream(new File(dir,name));doc.writeTo(o);o.close();}doc.close();b.recycle();toast("تم تصدير PDF إلى مجلد فاتورتي");}catch(Exception e){toast("تعذر إنشاء PDF: "+e.getMessage());}}
  void exportImage(){if(rowCount==0){toast("أضف صنفاً أولاً");return;}try{Bitmap b=render();ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,"Invoice_"+System.currentTimeMillis()+".png");v.put(MediaStore.Images.Media.MIME_TYPE,"image/png");if(Build.VERSION.SDK_INT>=29)v.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/فاتورتي");Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);OutputStream o=getContentResolver().openOutputStream(u);b.compress(Bitmap.CompressFormat.PNG,100,o);o.close();b.recycle();toast("تم حفظ صورة الفاتورة");}catch(Exception e){toast("تعذر حفظ الصورة");}}
  void printInvoice(){if(rowCount==0){toast("أضف صنفاً أولاً");return;}if(Build.VERSION.SDK_INT>=19){PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);pm.print("فاتورة",new ReceiptAdapter(),new android.print.PrintAttributes.Builder().setMediaSize(android.print.PrintAttributes.MediaSize.UNKNOWN_PORTRAIT).setResolution(new android.print.PrintAttributes.Resolution("thermal","Thermal",203,203)).setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS).build());}else toast("الطباعة تحتاج Android 4.4 أو أحدث");}
  class ReceiptAdapter extends android.print.PrintDocumentAdapter {Bitmap bmp;public void onLayout(android.print.PrintAttributes oldA,android.print.PrintAttributes newA,CancellationSignal s,LayoutResultCallback cb,Bundle extras){bmp=render();cb.onLayoutFinished(new android.print.PrintDocumentInfo.Builder("invoice.pdf").setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(),true);}public void onWrite(PageRange[] pages,ParcelFileDescriptor fd,CancellationSignal s,WriteResultCallback cb){try{PdfDocument d=new PdfDocument();PdfDocument.Page p=d.startPage(new PdfDocument.PageInfo.Builder(432,Math.max(300,bmp.getHeight()/2),1).create());p.getCanvas().drawBitmap(bmp,null,new Rect(0,0,432,bmp.getHeight()/2),null);d.finishPage(p);d.writeTo(new FileOutputStream(fd.getFileDescriptor()));d.close();cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});}catch(Exception e){cb.onWriteFailed(e.toString());}}}
  void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
