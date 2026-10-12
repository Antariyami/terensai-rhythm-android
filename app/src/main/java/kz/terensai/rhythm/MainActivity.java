package kz.terensai.rhythm;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String[] STAGES = {
        "Встреча гостей", "Коллективное парение №1", "Отдых №1",
        "Коллективное парение №2", "Отдых №2",
        "Коллективное парение №3", "Завершение"
    };
    private static final long PROGRAM_MS = 3L * 60 * 60 * 1000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private TextView clock, remaining, heading, stopwatch, progress, journal;
    private Button start, pause, next, reset;
    private long programStarted = 0;
    private long stageStarted = 0;
    private long stageElapsed = 0;
    private boolean paused = false;
    private int stage = 0;
    private String history = "";
    private final int bg = Color.rgb(23,33,30), panel = Color.rgb(41,58,52);
    private final int cream = Color.rgb(212,189,139), mint = Color.rgb(155,221,171);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", new Locale("ru"));

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        prefs = getSharedPreferences("rhythm_state_v1", MODE_PRIVATE);
        restore();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));
        scroll.addView(root);
        setContentView(scroll);
        label(root,"TERENSAI · РИТМ ПАРЕНИЯ",16,cream);
        label(root,"Текущее время",13,0xFFB3C0B8);
        clock=label(root,"--:--:--",38,Color.WHITE);
        label(root,"До завершения программы «Танысу»",13,0xFFB3C0B8);
        remaining=label(root,"03:00:00",34,mint);
        label(root,"Активный этап",13,cream);
        heading=label(root,"Подготовка",22,Color.WHITE);
        stopwatch=label(root,"00:00",52,Color.WHITE);
        progress=label(root,"Программа ещё не начата",14,0xFFB3C0B8);
        start=button(root,"Начать программу Танысу",cream);
        start.setOnClickListener(v->startProgram());
        pause=button(root,"Пауза",panel);
        pause.setOnClickListener(v->togglePause());
        next=button(root,"Завершить этап →",cream);
        next.setOnClickListener(v->completeStage());
        reset=button(root,"Завершить программу / Сбросить",panel);
        reset.setOnClickListener(v->new android.app.AlertDialog.Builder(this)
            .setTitle("Завершить и очистить сессию?")
            .setMessage("Текущий журнал будет очищен. Это действие нельзя отменить.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Сбросить",(d,w)->reset()).show());
        label(root,"Журнал этапов",18,cream);
        journal=label(root,"Пока нет записей",14,0xFFB3C0B8);
        update();
        handler.postDelayed(tick,300);
    }
    private final Runnable tick=new Runnable() {
        public void run(){update();handler.postDelayed(this,500);}
    };
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
    private TextView label(LinearLayout p,String value,int size,int color){
        TextView t=new TextView(this);
        t.setText(value);t.setTextColor(color);t.setTextSize(size);
        t.setPadding(0,dp(12),0,dp(12));t.setGravity(Gravity.CENTER_VERTICAL);
        p.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;
    }
    private Button button(LinearLayout p,String text,int color){
        Button b=new Button(this);b.setText(text);b.setTextSize(17);
        b.setAllCaps(false);b.setTextColor(color==cream?bg:Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(62));
        lp.bottomMargin=dp(12);p.addView(b,lp);return b;
    }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private String fmt(long millis){
        long sec=Math.max(0,millis)/1000;
        return String.format(Locale.ROOT,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);
    }
    private void startProgram(){
        if(programStarted>0)return;
        programStarted=System.currentTimeMillis();
        stageStarted=programStarted;stageElapsed=0;paused=false;stage=0;
        history="";persist();update();
    }
    private long elapsed(long now){
        return stageElapsed+(paused||stageStarted==0?0:Math.max(0,now-stageStarted));
    }
    private void togglePause(){
        if(programStarted==0||stage>=STAGES.length)return;
        long now=System.currentTimeMillis();
        if(paused){paused=false;stageStarted=now;}
        else {stageElapsed=elapsed(now);paused=true;}
        persist();update();
    }
    private void completeStage(){
        if(programStarted==0||stage>=STAGES.length)return;
        long now=System.currentTimeMillis();
        history += timeFormat.format(new Date(now))+"  "+STAGES[stage]+" — "+fmt(elapsed(now))+"\\n";
        stage++;
        stageElapsed=0;stageStarted=now;paused=false;
        persist();update();
    }
    private void reset(){
        programStarted=0;stageStarted=0;stageElapsed=0;stage=0;paused=false;history="";
        persist();update();
    }
    private void update(){
        if(clock==null)return;
        long now=System.currentTimeMillis();
        clock.setText(timeFormat.format(new Date(now)));
        remaining.setText(fmt(programStarted==0?PROGRAM_MS:PROGRAM_MS-Math.max(0,now-programStarted)));
        heading.setText(programStarted==0?"Готов к запуску":stage<STAGES.length?STAGES[stage]:"Все этапы завершены");
        stopwatch.setText(fmt(elapsed(now)));
        progress.setText(programStarted==0?"Программа не начата":stage>=STAGES.length?
          "Этапы закончены":(paused?"Пауза · ":"Идёт · ")+"Этап "+(stage+1)+" из "+STAGES.length);
        journal.setText(history.isEmpty()?"Пока нет записей":history.replace("\\n","\n"));
        start.setEnabled(programStarted==0);
        pause.setEnabled(programStarted!=0&&stage<STAGES.length);
        pause.setText(paused?"Продолжить":"Пауза");
        next.setEnabled(programStarted!=0&&stage<STAGES.length);
    }
    private void persist(){
        prefs.edit().putLong("program",programStarted).putLong("stageStart",stageStarted)
         .putLong("stageElapsed",stageElapsed).putInt("stage",stage)
         .putBoolean("paused",paused).putString("history",history).apply();
    }
    private void restore(){
        programStarted=prefs.getLong("program",0);
        stageStarted=prefs.getLong("stageStart",0);
        stageElapsed=prefs.getLong("stageElapsed",0);
        stage=prefs.getInt("stage",0);
        if(stage<0||stage>STAGES.length)stage=0;
        paused=prefs.getBoolean("paused",false);
        history=prefs.getString("history","");
    }
}
