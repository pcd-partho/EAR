package com.myapp.app

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

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

        val balanceText = TextView(this)
        balanceText.text = "Balance: Loading..."
        balanceText.textSize = 28f

        layout.addView(welcome)
        layout.addView(balanceText)
        setContentView(layout)

        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val balance = doc.getDouble("balance") ?: 0.0
                balanceText.text = "Balance: TK$balance"
            }
    }
}
