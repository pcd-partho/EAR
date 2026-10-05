package com.myapp.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var balanceText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 100, 60, 60)

        val welcome = TextView(this)
        welcome.text = "Welcome, ${auth.currentUser?.email}"
        welcome.textSize = 20f

        balanceText = TextView(this)
        balanceText.text = "Balance: Loading..."
        balanceText.textSize = 28f
        balanceText.setPadding(0, 20, 0, 40)

        val playGameBtn = Button(this)
        playGameBtn.text = "Play Crash Game"
        playGameBtn.setOnClickListener {
            startActivity(Intent(this, GameActivity::class.java))
        }

        val addMoneyBtn = Button(this)
        addMoneyBtn.text = "Add Money"
        addMoneyBtn.setOnClickListener {
            startActivity(Intent(this, AddMoneyActivity::class.java))
        }

        val withdrawBtn = Button(this)
        withdrawBtn.text = "Withdraw"
        withdrawBtn.setOnClickListener {
            startActivity(Intent(this, WithdrawActivity::class.java))
        }

        val historyBtn = Button(this)
        historyBtn.text = "Transaction History"
        historyBtn.setOnClickListener {
            startActivity(Intent(this, TransactionHistoryActivity::class.java))
        }

        val adminBtn = Button(this)
        adminBtn.text = "Admin Panel"
        adminBtn.visibility = View.GONE
        adminBtn.setOnClickListener {
            startActivity(Intent(this, AdminActivity::class.java))
        }

        val logoutBtn = Button(this)
        logoutBtn.text = "Logout"
        logoutBtn.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        layout.addView(welcome)
        layout.addView(balanceText)
        layout.addView(playGameBtn)
        layout.addView(addMoneyBtn)
        layout.addView(withdrawBtn)
        layout.addView(historyBtn)
        layout.addView(adminBtn)
        layout.addView(logoutBtn)
        setContentView(layout)

        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val balance = doc.getDouble("balance") ?: 0.0
                balanceText.text = "Balance: TK$balance"

                val isAdmin = doc.getBoolean("isAdmin") ?: false
                if (isAdmin) {
                    adminBtn.visibility = View.VISIBLE
                }
            }
    }

    override fun onResume() {
        super.onResume()
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val balance = doc.getDouble("balance") ?: 0.0
                balanceText.text = "Balance: TK$balance"
            }
    }
}