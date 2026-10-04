package com.myapp.app

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionHistoryActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val scroll = ScrollView(this)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 80, 60, 60)

        val title = TextView(this)
        title.text = "Transaction History"
        title.textSize = 26f
        layout.addView(title)

        val listContainer = LinearLayout(this)
        listContainer.orientation = LinearLayout.VERTICAL
        listContainer.setPadding(0, 30, 0, 0)
        layout.addView(listContainer)

        scroll.addView(layout)
        setContentView(scroll)

        val uid = auth.currentUser?.uid ?: return

        db.collection("transactions")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { result ->
                val docs = result.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }

                if (docs.isEmpty()) {
                    val none = TextView(this)
                    none.text = "No transactions yet."
                    listContainer.addView(none)
                    return@addOnSuccessListener
                }

                val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

                for (doc in docs) {
                    val type = doc.getString("type") ?: ""
                    val amount = doc.getDouble("amount") ?: 0.0
                    val status = doc.getString("status") ?: "pending"
                    val createdAt = doc.getLong("createdAt") ?: 0L
                    val dateStr = sdf.format(Date(createdAt))

                    val item = TextView(this)
                    item.textSize = 16f
                    item.setPadding(0, 15, 0, 15)

                    val typeLabel = if (type == "deposit") "Deposit" else "Withdraw"
                    val statusLabel = status.replaceFirstChar { it.uppercase() }

                    item.text = "$typeLabel - TK$amount\nStatus: $statusLabel\n$dateStr"

                    listContainer.addView(item)
                }
            }
    }
}