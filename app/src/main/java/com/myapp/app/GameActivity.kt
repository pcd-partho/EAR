package com.myapp.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class RoundState(
    val status: String,
    val nonce: Long,
    val bettingStartTime: Long,
    val bettingDuration: Long,
    val runningStartTime: Long?,
    val crashedTime: Long?,
    val serverSeed: String,
    val serverSeedHash: String,
    val crashPoint: Double
)

data class BetEntry(
    val uid: String,
    val nonce: Long,
    val displayName: String,
    val amount: Double,
    val cashedOutAt: Double?,
    val payout: Double?
)

class GameActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var currentRound: RoundState? = null
    private var cachedBets: List<BetEntry> = emptyList()
    private var roundListener: ListenerRegistration? = null
    private var betsListener: ListenerRegistration? = null
    private val handler = Handler(Looper.getMainLooper())

    private val tickRunnable = object : Runnable {
        override fun run() {
            checkRoundTransitions()
            handler.postDelayed(this, 1000)
        }
    }

    companion object {
        private const val BETTING_DURATION_MS = 30000L
        private const val CLIENT_SEED = "player-seed-001"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                refreshBalance()
                startListeners()
            }
        }

        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onResume() {
        super.onResume()
        handler.post(tickRunnable)
        if (::webView.isInitialized) refreshBalance()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        roundListener?.remove()
        betsListener?.remove()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ================= Firestore listeners =================

    private fun startListeners() {
        val roundRef = db.collection("rounds").document("current")
        roundListener = roundRef.addSnapshotListener { snap, e ->
            if (e != null) return@addSnapshotListener
            if (snap == null || !snap.exists()) {
                createInitialRoundIfMissing()
                return@addSnapshotListener
            }
            currentRound = RoundState(
                status = snap.getString("status") ?: "betting",
                nonce = snap.getLong("nonce") ?: 0L,
                bettingStartTime = snap.getLong("bettingStartTime") ?: 0L,
                bettingDuration = snap.getLong("bettingDuration") ?: BETTING_DURATION_MS,
                runningStartTime = snap.getLong("runningStartTime"),
                crashedTime = snap.getLong("crashedTime"),
                serverSeed = snap.getString("serverSeed") ?: "",
                serverSeedHash = snap.getString("serverSeedHash") ?: "",
                crashPoint = snap.getDouble("crashPoint") ?: 1.0
            )
            pushRoundStateToWebView()
            pushBetsFeedToWebView()
            pushMyBetState()
        }

        val betsRef = db.collection("rounds").document("current").collection("bets")
        betsListener = betsRef.addSnapshotListener { snap, e ->
            if (e != null || snap == null) return@addSnapshotListener
            cachedBets = snap.documents.mapNotNull { d ->
                val uid = d.getString("uid") ?: return@mapNotNull null
                BetEntry(
                    uid = uid,
                    nonce = d.getLong("nonce") ?: 0L,
                    displayName = d.getString("displayName") ?: "player",
                    amount = d.getDouble("amount") ?: 0.0,
                    cashedOutAt = d.getDouble("cashedOutAt"),
                    payout = d.getDouble("payout")
                )
            }
            pushBetsFeedToWebView()
            pushMyBetState()
        }
    }

    // ================= Round lifecycle =================

    private fun createInitialRoundIfMissing() {
        val ref = db.collection("rounds").document("current")
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) {
                val newNonce = 1L
                val serverSeed = randomHex(32)
                val serverSeedHash = sha256Hex(serverSeed)
                val crashPoint = computeCrashPoint(serverSeed, CLIENT_SEED, newNonce)
                tx.set(ref, mapOf(
                    "status" to "betting",
                    "nonce" to newNonce,
                    "bettingStartTime" to System.currentTimeMillis(),
                    "bettingDuration" to BETTING_DURATION_MS,
                    "runningStartTime" to null,
                    "crashedTime" to null,
                    "serverSeed" to serverSeed,
                    "serverSeedHash" to serverSeedHash,
                    "crashPoint" to crashPoint
                ))
            }
        }
    }

    private fun checkRoundTransitions() {
        val round = currentRound ?: return
        val now = System.currentTimeMillis()
        when (round.status) {
            "betting" -> {
                if (now >= round.bettingStartTime + round.bettingDuration) {
                    transitionToRunning(round)
                }
            }
            "running" -> {
                val rst = round.runningStartTime ?: return
                val elapsed = (now - rst) / 1000.0
                val mult = Math.pow(1.0618, elapsed * 10)
                if (mult >= round.crashPoint) {
                    transitionToCrashed(round)
                }
            }
            "crashed" -> {
                val ct = round.crashedTime ?: return
                if (now >= ct + 5000) {
                    startNewRound(round.nonce)
                }
            }
        }
    }

    private fun transitionToRunning(expected: RoundState) {
        val ref = db.collection("rounds").document("current")
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (snap.getString("status") == "betting" &&
                snap.getLong("bettingStartTime") == expected.bettingStartTime) {
                tx.update(ref, mapOf(
                    "status" to "running",
                    "runningStartTime" to System.currentTimeMillis()
                ))
            }
        }
    }

    private fun transitionToCrashed(expected: RoundState) {
        val ref = db.collection("rounds").document("current")
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (snap.getString("status") == "running" &&
                snap.getLong("runningStartTime") == expected.runningStartTime) {
                tx.update(ref, mapOf(
                    "status" to "crashed",
                    "crashedTime" to System.currentTimeMillis()
                ))
            }
        }
    }

    private fun startNewRound(oldNonce: Long) {
        val ref = db.collection("rounds").document("current")
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val nonceNow = snap.getLong("nonce") ?: 0L
            if (snap.getString("status") == "crashed" && nonceNow == oldNonce) {
                val newNonce = nonceNow + 1
                val serverSeed = randomHex(32)
                val serverSeedHash = sha256Hex(serverSeed)
                val crashPoint = computeCrashPoint(serverSeed, CLIENT_SEED, newNonce)
                tx.set(ref, mapOf(
                    "status" to "betting",
                    "nonce" to newNonce,
                    "bettingStartTime" to System.currentTimeMillis(),
                    "bettingDuration" to BETTING_DURATION_MS,
                    "runningStartTime" to null,
                    "crashedTime" to null,
                    "serverSeed" to serverSeed,
                    "serverSeedHash" to serverSeedHash,
                    "crashPoint" to crashPoint
                ))
            }
        }
    }

    // ================= WebView push helpers =================

    private fun pushRoundStateToWebView() {
        val round = currentRound ?: return
        val json = JSONObject()
        json.put("status", round.status)
        json.put("nonce", round.nonce)
        json.put("bettingStartTime", round.bettingStartTime)
        json.put("bettingDuration", round.bettingDuration)
        json.put("runningStartTime", round.runningStartTime ?: JSONObject.NULL)
        json.put("crashPoint", round.crashPoint)
        json.put("serverSeedHash", round.serverSeedHash)
        json.put("serverSeed", if (round.status == "crashed") round.serverSeed else JSONObject.NULL)
        runOnUiThread {
            webView.evaluateJavascript("window.updateRound($json);", null)
        }
    }

    private fun pushBetsFeedToWebView() {
        val round = currentRound ?: return
        val arr = JSONArray()
        for (b in cachedBets) {
            if (b.nonce != round.nonce) continue
            val obj = JSONObject()
            obj.put("name", b.displayName)
            obj.put("amount", b.amount)
            obj.put("cashedOutAt", b.cashedOutAt ?: JSONObject.NULL)
            obj.put("payout", b.payout ?: JSONObject.NULL)
            arr.put(obj)
        }
        runOnUiThread {
            webView.evaluateJavascript("window.updateBetsFeed($arr);", null)
        }
    }

    private fun pushMyBetState() {
        val round = currentRound ?: return
        val uid = auth.currentUser?.uid ?: return
        val myBet = cachedBets.find { it.uid == uid && it.nonce == round.nonce }
        val hasBet = myBet != null
        val cashedOut = myBet?.cashedOutAt != null
        runOnUiThread {
            webView.evaluateJavascript("window.setMyBetState($hasBet, $cashedOut);", null)
        }
    }

    private fun refreshBalance() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val bal = doc.getDouble("balance") ?: 0.0
                runOnUiThread {
                    webView.evaluateJavascript("window.setBalance($bal);", null)
                }
            }
    }

    private fun maskName(email: String): String {
        val name = email.substringBefore("@")
        return if (name.length <= 4) {
            name.take(1) + "***"
        } else {
            name.take(2) + "***" + name.takeLast(2)
        }
    }

    // ================= JS bridge =================

    inner class WebAppInterface {

        @JavascriptInterface
        fun placeBet(amount: Double) {
            runOnUiThread {
                val round = currentRound
                val uid = auth.currentUser?.uid
                if (round == null || uid == null) return@runOnUiThread
                if (round.status != "betting") {
                    Toast.makeText(this@GameActivity, "Betting closed for this round.", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (amount <= 0) return@runOnUiThread

                val userRef = db.collection("users").document(uid)
                val betRef = db.collection("rounds").document("current").collection("bets").document(uid)

                db.runTransaction { tx ->
                    val userSnap = tx.get(userRef)
                    val balance = userSnap.getDouble("balance") ?: 0.0
                    val betSnap = tx.get(betRef)
                    if (betSnap.exists() && betSnap.getLong("nonce") == round.nonce) {
                        throw Exception("Already bet this round")
                    }
                    if (balance < amount) {
                        throw Exception("Insufficient balance")
                    }
                    tx.update(userRef, "balance", balance - amount)
                    val betData = mapOf(
                        "uid" to uid,
                        "nonce" to round.nonce,
                        "displayName" to maskName(auth.currentUser?.email ?: "player"),
                        "amount" to amount,
                        "cashedOutAt" to null,
                        "payout" to null,
                        "timestamp" to System.currentTimeMillis()
                    )
                    tx.set(betRef, betData)
                }.addOnSuccessListener {
                    refreshBalance()
                }.addOnFailureListener { e ->
                    Toast.makeText(this@GameActivity, e.message ?: "Bet failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun cashOut() {
            runOnUiThread {
                val round = currentRound
                val uid = auth.currentUser?.uid
                if (round == null || uid == null) return@runOnUiThread
                if (round.status != "running" || round.runningStartTime == null) {
                    Toast.makeText(this@GameActivity, "Round not running.", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val elapsed = (System.currentTimeMillis() - round.runningStartTime) / 1000.0
                var multiplier = Math.pow(1.0618, elapsed * 10)
                if (multiplier > round.crashPoint) multiplier = round.crashPoint

                val userRef = db.collection("users").document(uid)
                val betRef = db.collection("rounds").document("current").collection("bets").document(uid)

                db.runTransaction { tx ->
                    val betSnap = tx.get(betRef)
                    if (!betSnap.exists() || betSnap.getLong("nonce") != round.nonce) {
                        throw Exception("No active bet")
                    }
                    if (betSnap.getDouble("cashedOutAt") != null) {
                        throw Exception("Already cashed out")
                    }
                    val amount = betSnap.getDouble("amount") ?: 0.0
                    val payout = amount * multiplier
                    val userSnap = tx.get(userRef)
                    val balance = userSnap.getDouble("balance") ?: 0.0
                    tx.update(userRef, "balance", balance + payout)
                    tx.update(betRef, mapOf(
                        "cashedOutAt" to multiplier,
                        "payout" to payout
                    ))
                }.addOnSuccessListener {
                    refreshBalance()
                }.addOnFailureListener { e ->
                    Toast.makeText(this@GameActivity, e.message ?: "Cash out failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ================= Provably fair helpers =================

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }

    private fun hmacSha256Hex(keyHex: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(hexToBytes(keyHex), "HmacSHA256")
        mac.init(keySpec)
        val bytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(message: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(message.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun randomHex(bytes: Int = 32): String {
        val arr = ByteArray(bytes)
        SecureRandom().nextBytes(arr)
        return arr.joinToString("") { "%02x".format(it) }
    }

    private fun computeCrashPoint(serverSeed: String, clientSeed: String, nonce: Long): Double {
        val hmacHex = hmacSha256Hex(serverSeed, "$clientSeed:$nonce")
        val hStr = hmacHex.substring(0, 13)
        val h = java.math.BigInteger(hStr, 16).toLong()
        if (h % 33 == 0L) return 1.00
        val e = Math.pow(2.0, 52.0)
        val result = Math.floor((100 * e - h) / (e - h)) / 100
        return maxOf(1.00, result)
    }
}