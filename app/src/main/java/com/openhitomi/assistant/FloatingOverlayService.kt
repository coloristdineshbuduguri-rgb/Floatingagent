package com.openhitomi.assistant
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.TextView
import kotlin.math.abs

class FloatingOverlayService : Service() {
    private var bubble: TextView?=null
    private lateinit var manager: WindowManager
    override fun onBind(intent:Intent?) = null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="STOP"){stopSelf();return START_NOT_STICKY}
        if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY}
        val channel="hitomi_overlay"
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"Floating assistant",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,1,Intent(this,FloatingOverlayService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n=Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("OpenHitomi is floating")
            .setContentText("Tap the bubble to chat. Stop it here anytime.").setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()
        if(Build.VERSION.SDK_INT>=34) startForeground(7,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(7,n)
        if(bubble==null) showBubble()
        return START_NOT_STICKY
    }
    private fun showBubble() {
        manager=getSystemService(WindowManager::class.java)
        val size=(72*resources.displayMetrics.density).toInt()
        val p=WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START;x=20;y=200 }
        val v=TextView(this).apply {
            text="•ᴗ•";textSize=25f;gravity=Gravity.CENTER;setTextColor(Color.rgb(53,58,95));typeface=Typeface.DEFAULT_BOLD
            contentDescription="Open Hitomi; drag to move, tap to open, hold to stop"
            background=GradientDrawable().apply { shape=GradientDrawable.OVAL;setColor(Color.rgb(225,226,255));setStroke(5,Color.WHITE) }
            elevation=12f
        }
        var sx=0f;var sy=0f;var ox=0;var oy=0;var moved=false;var held=false
        val handler=Handler(Looper.getMainLooper())
        val hold=Runnable {held=true;stopSelf()}
        v.setOnClickListener { startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        v.setOnTouchListener { _,event ->
            when(event.action) {
                MotionEvent.ACTION_DOWN->{sx=event.rawX;sy=event.rawY;ox=p.x;oy=p.y;moved=false;held=false;handler.postDelayed(hold,700);true}
                MotionEvent.ACTION_MOVE->{
                    if(abs(event.rawX-sx)>10 || abs(event.rawY-sy)>10){moved=true;handler.removeCallbacks(hold)}
                    val bounds=resources.displayMetrics
                    p.x=(ox+(event.rawX-sx).toInt()).coerceIn(0,(bounds.widthPixels-size).coerceAtLeast(0))
                    p.y=(oy+(event.rawY-sy).toInt()).coerceIn(0,(bounds.heightPixels-size-60).coerceAtLeast(0))
                    if(bubble!=null) manager.updateViewLayout(v,p);true
                }
                MotionEvent.ACTION_UP->{handler.removeCallbacks(hold);if(!moved && !held) v.performClick();true}
                MotionEvent.ACTION_CANCEL->{handler.removeCallbacks(hold);true}
                else->false
            }
        }
        try {manager.addView(v,p);bubble=v} catch(_:Exception){stopSelf()}
    }
    override fun onDestroy(){bubble?.let{manager.removeView(it)};bubble=null;super.onDestroy()}
}
