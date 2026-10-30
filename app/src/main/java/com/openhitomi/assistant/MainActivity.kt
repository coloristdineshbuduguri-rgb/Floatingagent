package com.openhitomi.assistant

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import org.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var home: WebView
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private var apiKey = "" // Memory only; never exposed to the page or written to disk.
    private var provider = "ollama"
    private var endpoint = "http://127.0.0.1:11434"
    private var model = ""
    private val history = JSONArray()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        provider = prefs.getString("provider", "ollama")!!
        endpoint = prefs.getString("endpoint", endpoint)!!
        model = prefs.getString("model", "")!!
        home = WebView(this)
        home.settings.javaScriptEnabled = true
        home.settings.allowFileAccess = false
        home.settings.allowContentAccess = false
        home.addJavascriptInterface(Bridge(), "Hitomi")
        home.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean = true
        }
        setContentView(home)
        home.loadUrl("file:///android_asset/open-hitomi.html")
    }
    private fun reply(s: String) = runOnUiThread {
        if (!isFinishing && !isDestroyed) home.evaluateJavascript("receive(${JSONObject.quote(s)})", null)
    }
    inner class Bridge {
        @JavascriptInterface fun action(name: String) { runOnUiThread {
            when(name) {
                "overlay" -> overlay()
                "stop" -> { stopService(Intent(this@MainActivity, FloatingOverlayService::class.java)); reply("Floating assistant stopped.") }
                "browser" -> ask("In-App Browser", "https://example.com") { browser(it) }
                "termux" -> termux()
                "wallet" -> ask("Solana public address", "") { wallet(it.trim()) }
                "ollama", "xai" -> configure(name)
                "clear" -> { while(history.length()>0) history.remove(0); reply("Chat context cleared.") }
            }
        } }
        @JavascriptInterface fun chat(text: String) {
            if(text.isBlank()) return
            worker.execute {
                try {
                    check(model.isNotBlank()) { "Choose Ollama or xAI and enter a model ID first." }
                    val messages = JSONArray(history.toString()).put(JSONObject().put("role", "user").put("content", text.take(12000)))
                    val body = JSONObject().put("model", model).put("messages", messages).put("stream", false)
                    val response = if(provider == "xai") {
                        check(apiKey.isNotBlank()) { "Enter your xAI API key again; keys are kept in memory only." }
                        post("https://api.x.ai/v1/chat/completions",body,apiKey).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    } else post(endpoint.trimEnd('/')+"/api/chat",body).getJSONObject("message").getString("content")
                    history.put(JSONObject().put("role","user").put("content",text.take(12000)))
                    history.put(JSONObject().put("role","assistant").put("content",response))
                    while(history.length()>20) history.remove(0)
                    reply(response)
                } catch(e: Exception) { reply("Unable to complete: ${e.message}") }
            }
        }
    }
    private fun ask(title: String, initial: String, done: (String)->Unit) {
        val input = EditText(this).apply { setText(initial); setSingleLine() }
        AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancel",null)
            .setPositiveButton("Continue") { _,_->done(input.text.toString()) }.show()
    }
    private fun configure(kind: String) {
        val box = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(30,12,30,12) }
        fun field(hintText: String, value: String) = EditText(this).apply { hint=hintText; setText(value); setSingleLine(); box.addView(this) }
        val address = if(kind=="ollama") field("Ollama URL",endpoint) else null
        val m = field("Model ID (required)",if(provider==kind) model else "")
        val key = if(kind=="xai") field("xAI API key (session only)",apiKey).apply { inputType=129 } else null
        AlertDialog.Builder(this).setTitle(if(kind=="xai") "xAI · API usage may cost money" else "Ollama · local endpoint")
            .setView(box).setNegativeButton("Cancel",null).setPositiveButton("Connect") { _,_->
                val url=address?.text?.toString()?.trim() ?: endpoint
                if(m.text.isBlank() || (kind=="ollama" && !validHttp(url))) { reply("Enter a model ID and a valid HTTP(S) endpoint."); return@setPositiveButton }
                provider=kind; endpoint=url; model=m.text.toString().trim(); apiKey=key?.text?.toString() ?: apiKey
                prefs.edit().putString("provider",provider).putString("endpoint",endpoint).putString("model",model).apply()
                worker.execute { while(history.length()>0) history.remove(0) }
                reply("${if(kind=="xai") "xAI" else "Ollama"} configured. Send a message to test the connection.")
            }.show()
    }
    private fun validHttp(s: String): Boolean = try { val u=Uri.parse(s); u.scheme in listOf("http","https") && !u.host.isNullOrBlank() && u.userInfo==null } catch(_:Exception){false}
    private fun browser(url: String) {
        if(!validHttp(url)) { reply("Enter a valid HTTP(S) URL."); return }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val bar=EditText(this).apply { setText(url); setSingleLine(); inputType=17 }
        val go=Button(this).apply { text="Go" }
        val web=WebView(this).apply {
            settings.javaScriptEnabled=true; settings.allowFileAccess=false; settings.allowContentAccess=false
            settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient=object:WebViewClient(){
                override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest):Boolean = !validHttp(r.url.toString())
            }
        } // Deliberately no JavaScript bridge on untrusted websites.
        go.setOnClickListener { if(validHttp(bar.text.toString())) web.loadUrl(bar.text.toString()) }
        box.addView(bar);box.addView(go);box.addView(web,LinearLayout.LayoutParams(-1,0,1f))
        val dialog=Dialog(this);dialog.setContentView(box);dialog.window?.setLayout(-1,-1)
        dialog.setOnDismissListener { web.stopLoading();web.destroy() };dialog.show();dialog.window?.setLayout(-1,-1);web.loadUrl(url)
    }
    private fun overlay() {
        if(!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
            reply("Allow display over other apps, return, and tap Floating Assistant again.");return
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),10)
        try { startForegroundService(Intent(this,FloatingOverlayService::class.java));reply("Bubble started. Drag to move; tap to open; long-press to stop.") }
        catch(e:Exception){reply("Could not start bubble: ${e.message}")}
    }
    private fun termux() {
        if(checkSelfPermission("com.termux.permission.RUN_COMMAND")!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf("com.termux.permission.RUN_COMMAND"),11)
            reply("Grant Termux command permission and tap Termux again. Install Termux first if needed.");return
        }
        ask("Run in Termux (review command)","pwd") { command ->
            AlertDialog.Builder(this).setTitle("Execute this command?").setMessage(command)
                .setNegativeButton("Cancel",null).setPositiveButton("Run") { _,_->
                    try {
                        startService(Intent("com.termux.RUN_COMMAND").setClassName("com.termux","com.termux.app.RunCommandService")
                            .putExtra("com.termux.RUN_COMMAND_PATH","/data/data/com.termux/files/usr/bin/bash")
                            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("-lc",command))
                            .putExtra("com.termux.RUN_COMMAND_BACKGROUND",false)
                            .putExtra("com.termux.RUN_COMMAND_SESSION_ACTION","0"))
                        reply("Sent to Termux. Output appears in its terminal session.")
                    } catch(e:Exception){reply("Termux unavailable: ${e.message}. Enable allow-external-apps=true in Termux properties.")}
                }.show()
        }
    }
    private fun wallet(address: String) {
        if(!Regex("[1-9A-HJ-NP-Za-km-z]{32,44}").matches(address)){reply("Enter a Solana public address, never a seed phrase.");return}
        reply("Loading public wallet information…")
        worker.execute {
            try {
                fun rpc(method:String,params:JSONArray):JSONObject {
                    val r=post("https://api.mainnet-beta.solana.com",JSONObject().put("jsonrpc","2.0").put("id",1).put("method",method).put("params",params))
                    check(!r.has("error")){r.optJSONObject("error")?.optString("message") ?: "RPC error"};return r.getJSONObject("result")
                }
                val balance=rpc("getBalance",JSONArray().put(address)).getLong("value")/1e9
                val lines=StringBuilder("Address: $address\nBalance: $balance SOL\n\nToken accounts:\n")
                for(program in listOf("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA","TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")) {
                    val values=rpc("getTokenAccountsByOwner",JSONArray().put(address).put(JSONObject().put("programId",program)).put(JSONObject().put("encoding","jsonParsed"))).getJSONArray("value")
                    for(i in 0 until values.length()) {
                        val info=values.getJSONObject(i).getJSONObject("account").getJSONObject("data").getJSONObject("parsed").getJSONObject("info")
                        lines.append("${info.getString("mint")}: ${info.getJSONObject("tokenAmount").getString("uiAmountString")}\n")
                    }
                }
                reply(lines.toString())
            } catch(e:Exception){reply("Wallet lookup failed: ${e.message}. Public RPC may rate-limit requests.")}
        }
    }
    private fun post(url:String,body:JSONObject,key:String=""):JSONObject {
        val c=URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod="POST";c.connectTimeout=15000;c.readTimeout=120000;c.instanceFollowRedirects=false;c.doOutput=true
            c.setRequestProperty("Content-Type","application/json")
            if(key.isNotEmpty()) c.setRequestProperty("Authorization","Bearer $key")
            c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
            val status=c.responseCode
            check(status in 200..299){"HTTP $status; check endpoint, model, credentials, or provider limits."}
            val raw=c.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(raw)
        } finally {c.disconnect()}
    }
    override fun onDestroy() { home.removeJavascriptInterface("Hitomi");home.destroy();worker.shutdownNow();apiKey="";super.onDestroy() }
}
