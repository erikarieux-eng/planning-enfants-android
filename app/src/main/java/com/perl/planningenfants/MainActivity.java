package com.perl.planningenfants;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends android.app.Activity implements SupabaseSync.UiListener {
    private LinearLayout content;
    private TextView syncBadge;
    private SharedPreferences prefs;
    private String currentTab = "today";
    private final int BLUE=Color.rgb(37,99,235),PINK=Color.rgb(190,24,93),GREEN=Color.rgb(22,163,74),ORANGE=Color.rgb(234,88,12);
    private final int PRIMARY=Color.rgb(79,70,229),PAGE_BG=Color.rgb(246,246,249),CARD_BORDER=Color.rgb(232,232,238),TAB_BG=Color.rgb(237,237,242),TEXT_DARK=Color.rgb(17,24,39),TEXT_MUTED=Color.rgb(107,114,128);
    private final String[] CHILDREN={"Andrew","Shanayss","Kelvyn"};
    private final List<Button> tabButtons=new ArrayList<>();
    private final List<String> tabKeys=new ArrayList<>();
    private Uri pendingPhotoUri;
    private String pendingPhotoChild;
    private static final int REQ_CAMERA=2001,REQ_GALLERY=2002,REQ_CAMERA_PERM=2003;

    @Override protected void onCreate(Bundle b){super.onCreate(b);LocalStore.seedIfNeeded(this);ReminderScheduler.ensureDefaults(this);prefs=getSharedPreferences(ReminderScheduler.PREFS,MODE_PRIVATE);requestNotificationPermission();ReminderScheduler.scheduleAll(this);buildUi();SyncWorker.schedule(this);SupabaseSync.start(this,this);}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(14),dp(14),0);root.setBackgroundColor(PAGE_BG);

        LinearLayout headCard=new LinearLayout(this);headCard.setOrientation(LinearLayout.VERTICAL);headCard.setPadding(dp(16),dp(14),dp(16),dp(14));headCard.setBackground(cardBackground(PRIMARY));headCard.setElevation(dp(3));
        TextView title=new TextView(this);title.setText("Planning famille");title.setTextSize(24);title.setTypeface(Typeface.DEFAULT_BOLD);title.setTextColor(Color.WHITE);headCard.addView(title);
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(0,dp(4),0,0);
        TextView sub=new TextView(this);sub.setText(capitalize(new SimpleDateFormat("EEEE d MMMM",Locale.FRANCE).format(new Date())));sub.setTextSize(13);sub.setTextColor(Color.rgb(224,225,255));head.addView(sub,new LinearLayout.LayoutParams(0,-2,1));
        syncBadge=new TextView(this);syncBadge.setTextSize(11);syncBadge.setTypeface(Typeface.DEFAULT_BOLD);syncBadge.setPadding(dp(10),dp(5),dp(10),dp(5));head.addView(syncBadge);headCard.addView(head);
        root.addView(headCard,paramsWithTop(0));updateSyncBadge();

        HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);hsv.setPadding(0,dp(12),0,0);LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        addTab(tabs,"today","Aujourd'hui",()->showToday());addTab(tabs,"week","Semaine",()->showWeek());addTab(tabs,"add","➕ Ajouter",()->showAdd());addTab(tabs,"pickups","👥 Récup.",()->showPickups());addTab(tabs,"family","☁ Famille",()->showFamily());addTab(tabs,"reminders","🔔 Rappels",()->showReminders());
        hsv.addView(tabs);root.addView(hsv,new LinearLayout.LayoutParams(-1,dp(52)));
        ScrollView scroll=new ScrollView(this);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(0,dp(12),0,dp(30));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);renderCurrent();
    }

    private void addTab(LinearLayout tabs,String key,String label,Runnable r){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12.5f);b.setTypeface(Typeface.DEFAULT_BOLD);b.setPadding(dp(16),0,dp(16),0);
        b.setOnClickListener(v->{r.run();highlightTabs();});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(40));lp.rightMargin=dp(8);b.setLayoutParams(lp);
        tabs.addView(b);tabButtons.add(b);tabKeys.add(key);
    }
    private void highlightTabs(){for(int i=0;i<tabButtons.size();i++){Button b=tabButtons.get(i);boolean active=tabKeys.get(i).equals(currentTab);GradientDrawable g=new GradientDrawable();g.setCornerRadius(dp(20));g.setColor(active?PRIMARY:TAB_BG);b.setBackground(g);b.setTextColor(active?Color.WHITE:TEXT_DARK);}}
    private void renderCurrent(){switch(currentTab){case"week":showWeek();break;case"add":showAdd();break;case"pickups":showPickups();break;case"family":showFamily();break;case"reminders":showReminders();break;default:showToday();}highlightTabs();}

    private boolean visibleChild(String child){return SupabaseSync.isAdmin(this)||SupabaseSync.childProfile(this).isEmpty()||SupabaseSync.childProfile(this).equals(child);}
    private List<PlannerEvent> eventsFor(Calendar date){String iso=iso(date);int day=date.get(Calendar.DAY_OF_WEEK);List<PlannerEvent>out=new ArrayList<>();for(PlannerEvent e:LocalStore.events(this))if(visibleChild(e.child)&&((e.weekly&&e.day==day)||(!e.weekly&&iso.equals(e.dateIso))))out.add(e);Collections.sort(out,Comparator.comparing(x->x.start));return out;}

    private void showToday(){currentTab="today";content.removeAllViews();Calendar c=Calendar.getInstance();addSection("Aujourd'hui");List<PlannerEvent>ev=eventsFor(c);if(ev.isEmpty())addMuted("Aucun cours ou activité prévu.");for(PlannerEvent e:ev)addEventCard(e);
        List<PickupAssignment> pa=pickupsForDate(iso(c));if(!pa.isEmpty()){addSection("Qui récupère ?");for(PickupAssignment a:pa)addPickupCard(a);}if(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)==ScheduleData.TUE||Calendar.getInstance().get(Calendar.DAY_OF_WEEK)==ScheduleData.FRI)addNotice("⚠ Kelvyn : basket 17h30 • Andrew : basket 18h30.");}

    private int weekOffset=0;
    private void showWeek(){currentTab="week";content.removeAllViews();
        Calendar base=Calendar.getInstance();int dow=base.get(Calendar.DAY_OF_WEEK);int delta=Calendar.MONDAY-dow;if(delta>0)delta-=7;base.add(Calendar.DAY_OF_YEAR,delta);base.add(Calendar.DAY_OF_YEAR,weekOffset*7);
        Calendar weekEnd=(Calendar)base.clone();weekEnd.add(Calendar.DAY_OF_YEAR,5);
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setGravity(Gravity.CENTER_VERTICAL);
        Button prev=navArrow("◀");prev.setOnClickListener(v->{weekOffset--;showWeek();});nav.addView(prev,new LinearLayout.LayoutParams(dp(44),dp(40)));
        TextView range=text(capitalize(new SimpleDateFormat("d MMM",Locale.FRANCE).format(base.getTime()))+" – "+capitalize(new SimpleDateFormat("d MMM",Locale.FRANCE).format(weekEnd.getTime())),15,true,TEXT_DARK);range.setGravity(Gravity.CENTER);nav.addView(range,new LinearLayout.LayoutParams(0,-2,1));
        Button next=navArrow("▶");next.setOnClickListener(v->{weekOffset++;showWeek();});nav.addView(next,new LinearLayout.LayoutParams(dp(44),dp(40)));
        content.addView(nav,paramsWithTop(0));
        if(weekOffset!=0){Button today=action("📍 Revenir à cette semaine");today.setTextSize(12);today.setPadding(dp(14),dp(6),dp(14),dp(6));today.setOnClickListener(v->{weekOffset=0;showWeek();});content.addView(today,paramsWithTop(8));}
        for(int i=0;i<6;i++){Calendar d=(Calendar)base.clone();d.add(Calendar.DAY_OF_YEAR,i);addSection(capitalize(new SimpleDateFormat("EEEE d MMM",Locale.FRANCE).format(d.getTime())));List<PlannerEvent>l=eventsFor(d);if(l.isEmpty())addMuted("Rien de prévu.");for(PlannerEvent e:l)addEventCard(e);for(PickupAssignment a:pickupsForDate(iso(d)))addPickupCard(a);}
    }
    private Button navArrow(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(PRIMARY);GradientDrawable g=new GradientDrawable();g.setCornerRadius(dp(20));g.setColor(TAB_BG);b.setBackground(g);b.setPadding(0,0,0,0);return b;}

    private void showAdd(){currentTab="add";content.removeAllViews();addSection("Ajouter au planning");if(!SupabaseSync.isAdmin(this)){addMuted("Sur le téléphone d'un enfant, le planning est en lecture seule.");return;}addMuted("Les événements ajoutés sont synchronisés avec tous les appareils de la famille dès qu'un espace famille est créé.");Button e=action("➕ Ajouter un événement");e.setOnClickListener(v->showAddEventDialog());content.addView(e,paramsWithTop(8));Button p=action("👤 Ajouter une personne autorisée");p.setOnClickListener(v->showAddPersonDialog());content.addView(p,paramsWithTop(8));Button a=action("🚗 Définir qui récupère un enfant");a.setOnClickListener(v->showAssignmentDialog());content.addView(a,paramsWithTop(8));Button photo=action("📷 Importer un emploi du temps (photo)");photo.setOnClickListener(v->startScheduleImport());content.addView(photo,paramsWithTop(8));addMuted("Prends une photo de l'emploi du temps scolaire : les cours détectés te seront proposés avant tout ajout, rien n'est importé automatiquement.");}

    private void showPickups(){currentTab="pickups";content.removeAllViews();addSection("Récupérations");String today=iso(Calendar.getInstance());List<PickupAssignment>todayList=pickupsForDate(today);if(todayList.isEmpty())addMuted("Aucune personne définie pour aujourd'hui.");else for(PickupAssignment a:todayList)addPickupCard(a);
        if(SupabaseSync.isAdmin(this)){Button assign=action("🚗 Définir / changer une récupération");assign.setOnClickListener(v->showAssignmentDialog());content.addView(assign,paramsWithTop(8));}
        addSection("Prochaines récupérations");List<PickupAssignment>all=new ArrayList<>();for(PickupAssignment a:LocalStore.pickups(this))if(visibleChild(a.child)&&a.dateIso.compareTo(today)>=0)all.add(a);Collections.sort(all,Comparator.comparing(x->x.dateIso+x.time));int n=0;for(PickupAssignment a:all){if(!a.dateIso.equals(today)){addPickupCard(a);if(++n>=10)break;}}if(n==0)addMuted("Aucune récupération future enregistrée.");
        if(SupabaseSync.isAdmin(this)){addSection("Personnes autorisées");List<PickupPerson>people=LocalStore.people(this);if(people.isEmpty())addMuted("Aucune personne enregistrée.");for(PickupPerson p:people)addPersonCard(p);Button add=action("➕ Ajouter une personne");add.setOnClickListener(v->showAddPersonDialog());content.addView(add,paramsWithTop(8));}}

    private void showFamily(){currentTab="family";content.removeAllViews();addSection("Synchronisation familiale");
        if(!SupabaseSync.hasFamily(this)){addMuted("Crée ton espace famille sur ton téléphone, puis donne le code à Andrew et Shanayss. Sur leur téléphone, ils choisissent « Rejoindre » et leur profil.");Button create=action("Créer mon espace famille");create.setOnClickListener(v->showCreateFamilyDialog());content.addView(create,paramsWithTop(8));Button join=action("Rejoindre avec un code");join.setOnClickListener(v->showJoinDialog());content.addView(join,paramsWithTop(8));return;}
        addInfo("Espace",SupabaseSync.familyName(this));addInfo("Code famille",SupabaseSync.familyCode(this));addInfo("Rôle",SupabaseSync.isAdmin(this)?"Administratrice":"Enfant — "+SupabaseSync.childProfile(this));Button copy=action("📋 Copier le code famille");copy.setOnClickListener(v->{ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Code famille",SupabaseSync.familyCode(this)));Toast.makeText(this,"Code copié",Toast.LENGTH_SHORT).show();});content.addView(copy,paramsWithTop(8));Button leave=action("Quitter cet espace sur ce téléphone");leave.setOnClickListener(v->confirm("Quitter l'espace famille ?","Les données locales resteront sur ce téléphone, mais la synchronisation sera coupée.",()->{SupabaseSync.leaveFamily(this);showFamily();updateSyncBadge();}));content.addView(leave,paramsWithTop(8));}

    private void showReminders(){currentTab="reminders";content.removeAllViews();addSection("Notifications de rappel");Switch activity=switchRow("🏀🤸🏽‍♀️🎹 Activités","30 min avant par défaut",prefs.getBoolean(ReminderScheduler.KEY_ACTIVITY,true));activity.setOnCheckedChangeListener((b,x)->{prefs.edit().putBoolean(ReminderScheduler.KEY_ACTIVITY,x).apply();ReminderScheduler.scheduleAll(this);});Switch pickup=switchRow("🚗 Sorties / récupérations","20 min avant",prefs.getBoolean(ReminderScheduler.KEY_PICKUP,true));pickup.setOnCheckedChangeListener((b,x)->{prefs.edit().putBoolean(ReminderScheduler.KEY_PICKUP,x).apply();ReminderScheduler.scheduleAll(this);});Switch morning=switchRow("🌅 Départs du matin","Désactivé par défaut",prefs.getBoolean(ReminderScheduler.KEY_MORNING,false));morning.setOnCheckedChangeListener((b,x)->{prefs.edit().putBoolean(ReminderScheduler.KEY_MORNING,x).apply();ReminderScheduler.scheduleAll(this);});addSection("Délai activités");addMinuteButtons(ReminderScheduler.KEY_ACTIVITY_MIN,30);addSection("Délai récupérations");addMinuteButtons(ReminderScheduler.KEY_PICKUP_MIN,20);Button exact=action("Autoriser les alarmes précises");exact.setOnClickListener(v->requestExactAlarmAccess());content.addView(exact,paramsWithTop(10));addMuted("Les changements synchronisés (ex. Mamie remplace Papa) apparaissent sur les autres téléphones en quelques secondes si l'appli est ouverte, et au plus tard 15 minutes après en arrière-plan.");}

    private void showAddEventDialog(){LinearLayout box=dialogBox();Spinner child=spinner(CHILDREN);box.addView(label("Enfant"));box.addView(child);EditText title=input("Ex. Médecin, match, anniversaire…");box.addView(label("Événement"));box.addView(title);String[] types={"Activité","École / cours","Garderie","Autre"};Spinner type=spinner(types);box.addView(label("Type"));box.addView(type);CheckBox weekly=new CheckBox(this);weekly.setText("Répéter chaque semaine");box.addView(weekly);String[] days={"Lundi","Mardi","Mercredi","Jeudi","Vendredi","Samedi","Dimanche"};Spinner day=spinner(days);box.addView(label("Jour si récurrent"));box.addView(day);Calendar selected=Calendar.getInstance();String[] date={iso(selected)},start={"17:00"},end={"18:00"};Button dateB=action("Date : "+date[0]);dateB.setOnClickListener(v->pickDate(selected,s->{date[0]=s;dateB.setText("Date : "+s);}));box.addView(dateB,paramsWithTop(5));Button startB=action("Début : "+start[0]);startB.setOnClickListener(v->pickTime(start[0],s->{start[0]=s;startB.setText("Début : "+s);}));box.addView(startB,paramsWithTop(5));Button endB=action("Fin : "+end[0]);endB.setOnClickListener(v->pickTime(end[0],s->{end[0]=s;endB.setText("Fin : "+s);}));box.addView(endB,paramsWithTop(5));weekly.setOnCheckedChangeListener((b,x)->dateB.setEnabled(!x));new AlertDialog.Builder(this).setTitle("Nouvel événement").setView(box).setNegativeButton("Annuler",null).setPositiveButton("Ajouter",(d,w)->{PlannerEvent e=new PlannerEvent();e.id="custom_"+UUID.randomUUID();e.child=child.getSelectedItem().toString();e.title=title.getText().toString().trim().isEmpty()?"Événement":title.getText().toString().trim();e.start=start[0];e.end=end[0];e.weekly=weekly.isChecked();e.day=e.weekly?calendarDay(day.getSelectedItemPosition()):0;e.dateIso=e.weekly?"":date[0];int ti=type.getSelectedItemPosition();e.kind=ti==0?"ACTIVITY":ti==1?"SCHOOL":ti==2?"GARDERIE":"OTHER";e.updatedAt=System.currentTimeMillis();SupabaseSync.saveEvent(this,e);showAdd();Toast.makeText(this,"Événement ajouté",Toast.LENGTH_SHORT).show();}).show();}

    private void showAddPersonDialog(){LinearLayout box=dialogBox();EditText name=input("Prénom et nom");EditText relation=input("Ex. Mamie, Papa, Tante…");EditText phone=input("Téléphone (facultatif)");box.addView(label("Nom"));box.addView(name);box.addView(label("Lien"));box.addView(relation);box.addView(label("Téléphone"));box.addView(phone);box.addView(label("Peut récupérer"));CheckBox a=new CheckBox(this);a.setText("Andrew");CheckBox s=new CheckBox(this);s.setText("Shanayss");CheckBox k=new CheckBox(this);k.setText("Kelvyn");a.setChecked(true);s.setChecked(true);k.setChecked(true);box.addView(a);box.addView(s);box.addView(k);new AlertDialog.Builder(this).setTitle("Personne autorisée").setView(box).setNegativeButton("Annuler",null).setPositiveButton("Ajouter",(d,w)->{if(name.getText().toString().trim().isEmpty()){Toast.makeText(this,"Le nom est obligatoire",Toast.LENGTH_SHORT).show();return;}PickupPerson p=new PickupPerson();p.id="person_"+UUID.randomUUID();p.name=name.getText().toString().trim();p.relation=relation.getText().toString().trim();p.phone=phone.getText().toString().trim();if(a.isChecked())p.children.add("Andrew");if(s.isChecked())p.children.add("Shanayss");if(k.isChecked())p.children.add("Kelvyn");p.updatedAt=System.currentTimeMillis();SupabaseSync.savePerson(this,p);showPickups();}).show();}

    // ---------------- Import d'emploi du temps par photo ----------------

    private void startScheduleImport(){
        Spinner child=spinner(CHILDREN);LinearLayout box=dialogBox();box.addView(label("Cet emploi du temps concerne"));box.addView(child);
        new AlertDialog.Builder(this).setTitle("Importer une photo").setView(box).setNegativeButton("Annuler",null).setPositiveButton("Continuer",(d,w)->{
            pendingPhotoChild=child.getSelectedItem().toString();
            String[] options={"📷 Prendre une photo","🖼 Choisir dans la galerie"};
            new AlertDialog.Builder(this).setTitle("Photo de l'emploi du temps").setItems(options,(dd,which)->{if(which==0)launchCamera();else launchGallery();}).show();
        }).show();
    }

    private void launchCamera(){
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_CAMERA_PERM);return;}
        try{
            File dir=new File(getCacheDir(),"images");if(!dir.exists())dir.mkdirs();
            File f=new File(dir,"schedule_"+System.currentTimeMillis()+".jpg");
            pendingPhotoUri=FileProvider.getUriForFile(this,"com.perl.planningenfants.fileprovider",f);
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,pendingPhotoUri);i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i,REQ_CAMERA);
        }catch(Exception e){Toast.makeText(this,"Appareil photo indisponible.",Toast.LENGTH_SHORT).show();}
    }
    private void launchGallery(){Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");startActivityForResult(i,REQ_GALLERY);}

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_CAMERA_PERM){if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)launchCamera();else Toast.makeText(this,"Autorisation caméra refusée.",Toast.LENGTH_SHORT).show();}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK)return;
        try{
            Uri uri=requestCode==REQ_CAMERA?pendingPhotoUri:(data!=null?data.getData():null);
            if(uri==null)return;
            if(requestCode==REQ_GALLERY||requestCode==REQ_CAMERA)analyzeSchedulePhoto(uri);
        }catch(Exception e){Toast.makeText(this,"Impossible de lire la photo.",Toast.LENGTH_SHORT).show();}
    }

    private void analyzeSchedulePhoto(Uri uri){
        ProgressDialog pd=new ProgressDialog(this);pd.setMessage("Analyse de la photo en cours…");pd.setCancelable(false);pd.show();
        new Thread(()->{
            try{
                InputStream is=getContentResolver().openInputStream(uri);
                Bitmap bmp=BitmapFactory.decodeStream(is);if(is!=null)is.close();
                if(bmp==null){runOnUiThread(()->{pd.dismiss();Toast.makeText(this,"Image illisible.",Toast.LENGTH_SHORT).show();});return;}
                int maxDim=1600;
                if(bmp.getWidth()>maxDim||bmp.getHeight()>maxDim){float scale=Math.min((float)maxDim/bmp.getWidth(),(float)maxDim/bmp.getHeight());bmp=Bitmap.createScaledBitmap(bmp,Math.round(bmp.getWidth()*scale),Math.round(bmp.getHeight()*scale),true);}
                ByteArrayOutputStream bos=new ByteArrayOutputStream();bmp.compress(Bitmap.CompressFormat.JPEG,85,bos);
                String base64=Base64.encodeToString(bos.toByteArray(),Base64.NO_WRAP);
                SupabaseSync.parseSchedulePhoto(this,base64,"image/jpeg",(ok,entries,msg)->runOnUiThread(()->{
                    pd.dismiss();
                    if(!ok){Toast.makeText(this,msg,Toast.LENGTH_LONG).show();return;}
                    if(entries.length()==0){Toast.makeText(this,"Aucun cours détecté sur cette photo.",Toast.LENGTH_LONG).show();return;}
                    showImportReviewDialog(entries);
                }));
            }catch(Exception e){runOnUiThread(()->{pd.dismiss();Toast.makeText(this,"Erreur de lecture de l'image.",Toast.LENGTH_SHORT).show();});}
        }).start();
    }

    private void showImportReviewDialog(JSONArray entries){
        LinearLayout box=dialogBox();box.addView(label(entries.length()+" cours détectés pour "+pendingPhotoChild+" — décoche ce qui est incorrect :"));
        List<CheckBox> boxes=new ArrayList<>();
        String[] dayLabels={"Lun","Mar","Mer","Jeu","Ven","Sam"};
        for(int i=0;i<entries.length();i++){
            JSONObject o=entries.optJSONObject(i);if(o==null)continue;
            String day=o.optString("day","MON");int dayIdx=java.util.Arrays.asList("MON","TUE","WED","THU","FRI","SAT").indexOf(day);
            String dayLabel=dayIdx>=0?dayLabels[dayIdx]:day;
            CheckBox cb=new CheckBox(this);cb.setChecked(true);cb.setText(dayLabel+" "+o.optString("start","")+"-"+o.optString("end","")+"  "+o.optString("title",""));cb.setTextSize(14);
            box.addView(cb,paramsWithTop(4));boxes.add(cb);
        }
        new AlertDialog.Builder(this).setTitle("Vérifier avant import").setView(box).setNegativeButton("Annuler",null).setPositiveButton("Importer",(d,w)->{
            int imported=0;
            for(int i=0;i<entries.length()&&i<boxes.size();i++){
                if(!boxes.get(i).isChecked())continue;
                JSONObject o=entries.optJSONObject(i);if(o==null)continue;
                String day=o.optString("day","MON");int dayIdx=java.util.Arrays.asList("MON","TUE","WED","THU","FRI","SAT").indexOf(day);
                int[] calDays={Calendar.MONDAY,Calendar.TUESDAY,Calendar.WEDNESDAY,Calendar.THURSDAY,Calendar.FRIDAY,Calendar.SATURDAY};
                PlannerEvent e=new PlannerEvent();e.id="custom_"+UUID.randomUUID();e.child=pendingPhotoChild;e.title=o.optString("title","Cours");
                e.start=o.optString("start","08:00");e.end=o.optString("end","09:00");e.weekly=true;e.day=dayIdx>=0?calDays[dayIdx]:Calendar.MONDAY;e.dateIso="";
                String kind=o.optString("kind","SCHOOL");e.kind=("SCHOOL".equals(kind)||"ACTIVITY".equals(kind)||"GARDERIE".equals(kind))?kind:"OTHER";
                e.updatedAt=System.currentTimeMillis();SupabaseSync.saveEvent(this,e);imported++;
            }
            Toast.makeText(this,imported+" cours importés.",Toast.LENGTH_LONG).show();showAdd();
        }).show();
    }

    private void showAssignmentDialog(){
        new AlertDialog.Builder(this).setTitle("Quel enfant ?").setItems(CHILDREN,(d,which)->showAssignmentForChild(CHILDREN[which])).show();
    }

    private void showAssignmentForChild(String childName){
        List<PickupPerson> authorized=new ArrayList<>();
        for(PickupPerson p:LocalStore.people(this))if(p.children.contains(childName))authorized.add(p);
        if(authorized.isEmpty()){Toast.makeText(this,"Aucune personne autorisée pour "+childName+". Ajoute-la d'abord.",Toast.LENGTH_LONG).show();showAddPersonDialog();return;}
        LinearLayout box=dialogBox();
        List<String>names=new ArrayList<>();for(PickupPerson p:authorized)names.add(p.name+(p.relation.isEmpty()?"":" — "+p.relation));
        Spinner person=spinner(names.toArray(new String[0]));box.addView(label("Personne prévue pour "+childName));box.addView(person);
        Calendar selected=Calendar.getInstance();String[]date={iso(selected)},time={"16:00"};
        Button db=action("Date : "+date[0]);db.setOnClickListener(v->pickDate(selected,s->{date[0]=s;db.setText("Date : "+s);}));box.addView(db,paramsWithTop(5));
        Button tb=action("Heure : "+time[0]);tb.setOnClickListener(v->pickTime(time[0],s->{time[0]=s;tb.setText("Heure : "+s);}));box.addView(tb,paramsWithTop(5));
        EditText note=input("Note facultative");box.addView(label("Note"));box.addView(note);
        new AlertDialog.Builder(this).setTitle("Qui récupère "+childName+" ?").setView(box).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{PickupPerson pp=authorized.get(person.getSelectedItemPosition());PickupAssignment x=new PickupAssignment();x.id="pickup_"+date[0]+"_"+childName.toLowerCase(Locale.ROOT);x.dateIso=date[0];x.child=childName;x.time=time[0];x.personId=pp.id;x.personName=pp.name;x.note=note.getText().toString().trim();x.responseToken=UUID.randomUUID().toString().replace("-","");x.responseStatus="pending";x.updatedAt=System.currentTimeMillis();SupabaseSync.savePickup(this,x);showPickups();Toast.makeText(this,"Récupération enregistrée",Toast.LENGTH_SHORT).show();}).show();
    }

    private void showCreateFamilyDialog(){EditText name=input("Nom de l'espace famille");name.setText("Famille");new AlertDialog.Builder(this).setTitle("Créer l'espace famille").setView(name).setNegativeButton("Annuler",null).setPositiveButton("Créer",(d,w)->{Toast.makeText(this,"Création…",Toast.LENGTH_SHORT).show();SupabaseSync.createFamily(this,name.getText().toString().trim().isEmpty()?"Famille":name.getText().toString().trim(),(ok,msg)->runOnUiThread(()->{if(ok){showFamily();updateSyncBadge();new AlertDialog.Builder(this).setTitle("Espace créé").setMessage("Code à transmettre aux enfants :\n\n"+msg+"\n\nIls choisissent Rejoindre puis leur prénom.").setPositiveButton("OK",null).show();}else Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}));}).show();}

    private void showJoinDialog(){LinearLayout box=dialogBox();EditText code=input("Code famille");Spinner profile=spinner(CHILDREN);box.addView(label("Code"));box.addView(code);box.addView(label("Ce téléphone appartient à"));box.addView(profile);new AlertDialog.Builder(this).setTitle("Rejoindre la famille").setView(box).setNegativeButton("Annuler",null).setPositiveButton("Rejoindre",(d,w)->SupabaseSync.joinFamily(this,code.getText().toString(),profile.getSelectedItem().toString(),(ok,msg)->runOnUiThread(()->{Toast.makeText(this,msg,Toast.LENGTH_LONG).show();if(ok){updateSyncBadge();showFamily();}}))).show();}

    private List<PickupAssignment> pickupsForDate(String date){List<PickupAssignment>out=new ArrayList<>();for(PickupAssignment a:LocalStore.pickups(this))if(date.equals(a.dateIso)&&visibleChild(a.child))out.add(a);Collections.sort(out,Comparator.comparing(x->x.time));return out;}
    private void addEventCard(PlannerEvent e){LinearLayout card=card(childColor(e.child));TextView top=text(e.start+" – "+e.end+"   "+e.child,16,true,childColor(e.child));card.addView(top);TextView t=text(e.title,15,false,Color.rgb(31,41,55));card.addView(t);if(!e.weekly){TextView d=text("📅 "+e.dateIso,12,false,Color.GRAY);card.addView(d);}if(SupabaseSync.isAdmin(this)&&e.id.startsWith("custom_")){card.setOnLongClickListener(v->{confirm("Supprimer cet événement ?",e.child+" • "+e.title,()->SupabaseSync.deleteEvent(this,e.id));return true;});}content.addView(card,paramsWithTop(7));}
    private void addPickupCard(PickupAssignment a){LinearLayout card=card(ORANGE);card.addView(text(a.time+"   "+a.child,16,true,ORANGE));PickupPerson p=personById(a.personId);String who="🚗 "+a.personName;if(p!=null&&!p.relation.isEmpty())who+=" • "+p.relation;card.addView(text(who,15,true,Color.rgb(31,41,55)));if(p!=null&&!p.phone.isEmpty())card.addView(text("☎ "+p.phone,13,false,Color.rgb(55,65,81)));card.addView(text("📅 "+a.dateIso+(a.note.isEmpty()?"":" • "+a.note),12,false,Color.GRAY));
        if(SupabaseSync.isAdmin(this)){
            if("accepted".equals(a.responseStatus))card.addView(text("✅ Accepté par "+a.personName,12,true,GREEN),paramsWithTop(4));
            else if("declined".equals(a.responseStatus))card.addView(text("❌ Refusé par "+a.personName,12,true,PINK),paramsWithTop(4));
            else if(SupabaseSync.hasFamily(this)&&!a.responseToken.isEmpty())card.addView(text("⏳ En attente de réponse",12,false,TEXT_MUTED),paramsWithTop(4));
            if(SupabaseSync.hasFamily(this)&&!a.responseToken.isEmpty()){Button share=new Button(this);share.setText("📤 Envoyer le lien");share.setAllCaps(false);share.setTextSize(12);share.setTextColor(Color.WHITE);GradientDrawable g=new GradientDrawable();g.setCornerRadius(dp(16));g.setColor(PRIMARY);share.setBackground(g);share.setPadding(dp(12),dp(4),dp(12),dp(4));share.setOnClickListener(v->sharePickupLink(a));card.addView(share,paramsWithTop(6));}
            card.setOnLongClickListener(v->{confirm("Supprimer cette récupération ?",a.child+" • "+a.personName,()->SupabaseSync.deletePickup(this,a.id));return true;});
        }
        content.addView(card,paramsWithTop(7));
    }

    private void sharePickupLink(PickupAssignment a){
        String link="https://erikarieux-eng.github.io/planning-enfants-android/web/recuperation.html?token="+a.responseToken;
        String msg="Bonjour "+a.personName+", peux-tu récupérer "+a.child+" le "+a.dateIso+" à "+a.time+" ?\n"+link;
        Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,msg);
        startActivity(Intent.createChooser(i,"Envoyer le lien à "+a.personName));
    }
    private PickupPerson personById(String id){for(PickupPerson p:LocalStore.people(this))if(p.id.equals(id))return p;return null;}
    private void addPersonCard(PickupPerson p){LinearLayout card=card(Color.rgb(99,102,241));card.addView(text(p.name+(p.relation.isEmpty()?"":" • "+p.relation),16,true,Color.rgb(67,56,202)));String c=p.children.isEmpty()?"Aucun enfant":String.join(" • ",p.children);card.addView(text(c+(p.phone.isEmpty()?"":"\n☎ "+p.phone),13,false,Color.rgb(55,65,81)));if(SupabaseSync.isAdmin(this)){card.setOnLongClickListener(v->{confirm("Supprimer cette personne ?",p.name,()->SupabaseSync.deletePerson(this,p.id));return true;});}content.addView(card,paramsWithTop(7));}

    private LinearLayout card(int accent){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(14),dp(12),dp(14),dp(12));x.setBackground(cardBackground(Color.WHITE,accent));x.setElevation(dp(2));return x;}
    private TextView text(String s,int size,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private void addSection(String s){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(14),0,dp(6));View dot=new View(this);GradientDrawable dg=new GradientDrawable();dg.setShape(GradientDrawable.OVAL);dg.setColor(PRIMARY);dot.setBackground(dg);LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(dp(8),dp(8));dlp.rightMargin=dp(8);row.addView(dot,dlp);row.addView(text(s,17,true,TEXT_DARK));content.addView(row);}
    private void addMuted(String s){TextView t=text(s,13,false,TEXT_MUTED);t.setPadding(dp(16),dp(3),0,dp(7));content.addView(t);}
    private void addNotice(String s){TextView t=text(s,13,false,Color.rgb(124,45,18));t.setPadding(dp(12),dp(10),dp(12),dp(10));t.setBackground(cardBackground(Color.rgb(255,247,237),ORANGE));content.addView(t,paramsWithTop(8));}
    private void addInfo(String k,String v){TextView t=text(k+" : "+v,15,"Code famille".equals(k),Color.rgb(31,41,55));t.setPadding(0,dp(4),0,dp(4));content.addView(t);}
    private Button action(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setPadding(dp(18),dp(10),dp(18),dp(10));GradientDrawable g=new GradientDrawable();g.setCornerRadius(dp(24));g.setColor(PRIMARY);b.setBackground(g);b.setElevation(dp(1));return b;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);return e;}
    private TextView label(String s){TextView t=text(s,13,true,Color.rgb(55,65,81));t.setPadding(0,dp(8),0,0);return t;}
    private LinearLayout dialogBox(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),dp(4),dp(20),0);return l;}
    private Spinner spinner(String[] items){Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,items);s.setAdapter(a);return s;}

    private Switch switchRow(String title,String sub,boolean checked){LinearLayout box=card(Color.rgb(229,231,235));Switch sw=new Switch(this);sw.setText(title);sw.setTextSize(16);sw.setTypeface(Typeface.DEFAULT_BOLD);sw.setChecked(checked);box.addView(sw);box.addView(text(sub,13,false,Color.rgb(107,114,128)));content.addView(box,paramsWithTop(8));return sw;}
    private void addMinuteButtons(String key,int def){LinearLayout row=new LinearLayout(this);int cur=prefs.getInt(key,def);for(int v:new int[]{10,15,20,30,45,60}){boolean sel=v==cur;Button b=new Button(this);b.setText(v+"m");b.setAllCaps(false);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(sel?Color.WHITE:TEXT_DARK);GradientDrawable g=new GradientDrawable();g.setCornerRadius(dp(18));g.setColor(sel?PRIMARY:TAB_BG);b.setBackground(g);b.setOnClickListener(x->{prefs.edit().putInt(key,v).apply();ReminderScheduler.scheduleAll(this);showReminders();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);lp.rightMargin=dp(6);row.addView(b,lp);}content.addView(row);}

    private interface StringResult{void done(String value);}private void pickDate(Calendar seed,StringResult r){new DatePickerDialog(this,(v,y,m,d)->r.done(String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d)),seed.get(Calendar.YEAR),seed.get(Calendar.MONTH),seed.get(Calendar.DAY_OF_MONTH)).show();}
    private void pickTime(String initial,StringResult r){String[]p=initial.split(":");new TimePickerDialog(this,(v,h,m)->r.done(String.format(Locale.ROOT,"%02d:%02d",h,m)),Integer.parseInt(p[0]),Integer.parseInt(p[1]),true).show();}
    private int calendarDay(int pos){return new int[]{Calendar.MONDAY,Calendar.TUESDAY,Calendar.WEDNESDAY,Calendar.THURSDAY,Calendar.FRIDAY,Calendar.SATURDAY,Calendar.SUNDAY}[pos];}
    private String iso(Calendar c){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(c.getTime());}
    private int childColor(String child){if("Andrew".equals(child))return BLUE;if("Shanayss".equals(child))return PINK;return GREEN;}
    private GradientDrawable cardBackground(int fill,int accent){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(16));g.setStroke(dp(1),blend(accent,Color.WHITE,0.55f));return g;}
    private GradientDrawable cardBackground(int fill){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(16));return g;}
    private int blend(int color,int with,float ratio){float ir=1f-ratio;int r=Math.round(Color.red(color)*ir+Color.red(with)*ratio);int gr=Math.round(Color.green(color)*ir+Color.green(with)*ratio);int bl=Math.round(Color.blue(color)*ir+Color.blue(with)*ratio);return Color.rgb(r,gr,bl);}
    private String capitalize(String s){return s==null||s.isEmpty()?s:Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private LinearLayout.LayoutParams paramsWithTop(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void confirm(String title,String msg,Runnable yes){new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setNegativeButton("Annuler",null).setPositiveButton("Oui",(d,w)->{yes.run();renderCurrent();}).show();}

    private void updateSyncBadge(){if(syncBadge==null)return;boolean synced=SupabaseSync.hasFamily(this);syncBadge.setText(synced?"☁ Synchro":"● Local");syncBadge.setTextColor(Color.WHITE);GradientDrawable g=new GradientDrawable();g.setCornerRadius(dp(14));g.setColor(synced?Color.rgb(22,163,74):blend(Color.WHITE,PRIMARY,0.35f));syncBadge.setBackground(g);}
    @Override public void onSyncChanged(){runOnUiThread(()->{updateSyncBadge();renderCurrent();});}
    @Override public void onSyncMessage(String m){runOnUiThread(()->{updateSyncBadge();if(m!=null&&m.startsWith("Erreur"))Toast.makeText(this,m,Toast.LENGTH_LONG).show();});}

    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1001);}
    private void requestExactAlarmAccess(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S){AlarmManager am=(AlarmManager)getSystemService(Context.ALARM_SERVICE);if(am!=null&&!am.canScheduleExactAlarms()){try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}return;}}Toast.makeText(this,"Alarmes précises déjà autorisées.",Toast.LENGTH_SHORT).show();}
    @Override protected void onResume(){super.onResume();ReminderScheduler.scheduleAll(this);}
}
