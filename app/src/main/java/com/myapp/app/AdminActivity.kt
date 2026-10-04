package com.myapp.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var numbersContainer: LinearLayout
    private lateinit var pendingContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val uid = auth.currentUser?.uid
        if (uid == null) {
            finish()
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val isAdmin = doc.getBoolean("isAdmin") ?: false
                if (!isAdmin) {
                    Toast.makeText(this, "Access denied", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    buildUI()
                }
            }
    }

    private fun buildUI() {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 80, 60, 60)

        val title = TextView(this)
        title.text = "Admin Panel"
        title.textSize = 26f
        layout.addView(title)

        val numbersTitle = TextView(this)
        numbersTitle.text = "\nPayment Numbers"
        numbersTitle.textSize = 20f
        layout.addView(numbersTitle)

        numbersContainer = LinearLayout(this)
        numbersContainer.orientation = LinearLayout.VERTICAL
        layout.addView(numbersContainer)

        val methodInput = EditText(this)
        methodInput.hint = "Method (bKash / Nagad)"
        layout.addView(methodInput)

        val numberInput = EditText(this)
        numberInput.hint = "Number"
        layout.addView(numberInput)

        val addNumberBtn = Button(this)
        addNumberBtn.text = "Add Number"
        layout.addView(addNumberBtn)

        addNumberBtn.setOnClickListener {
            val method = methodInput.text.toString().trim()
            val number = numberInput.text.toString().trim()

            if (method.isEmpty() || number.isEmpty()) {
                Toast.makeText(this, "Fill both fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val data = hashMapOf(
                "method" to method,
                "number" to number,
                "createdAt" to System.currentTimeMillis()
            )

            db.collection("paymentNumbers").add(data)
                .addOnSuccessListener {
                    methodInput.text.clear()
                    numberInput.text.clear()
                    Toast.makeText(this, "Number added", Toast.LENGTH_SHORT).show()
                    loadPaymentNumbers()
                }
        }

        val pendingTitle = TextView(this)
        pendingTitle.text = "\nPending Requests"
        pendingTitle.textSize = 20f
        layout.addView(pendingTitle)

        pendingContainer = LinearLayout(this)
        pendingContainer.orientation = LinearLayout.VERTICAL
        layout.addView(pendingContainer)

        scroll.addView(layout)
        setContentView(scroll)

        loadPaymentNumbers()
        loadPendingTransactions()
    }

    private fun loadPaymentNumbers() {
        db.collection("paymentNumbers").get()
            .addOnSuccessListener { result ->
                numbersContainer.removeAllViews()
                for (doc in result) {
                    val method = doc.getString("method") ?: ""
                    val number = doc.getString("number") ?: ""
                    val docId = doc.id

                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.setPadding(0, 10, 0, 10)

                    val label = TextView(this)
                    label.text = "$method: $number"
                    label.textSize = 16f
                    label.layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )

                    val removeBtn = Button(this)
                    removeBtn.text = "Remove"
                    removeBtn.setOnClickListener {
                        db.collection("paymentNumbers").document(docId).delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show()
                                loadPaymentNumbers()
                            }
                    }

                    row.addView(label)
                    row.addView(removeBtn)
                    numbersContainer.addView(row)
                }
            }
    }

    private fun loadPendingTransactions() {
        db.collection("transactions")
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { result ->
                pendingContainer.removeAllViews()

                if (result.isEmpty) {
                    val none = TextView(this)
                    none.text = "No pending requests."
                    pendingContainer.addView(none)
                    return@addOnSuccessListener
                }

                for (doc in result) {
                    val docId = doc.id
                    val type = doc.getString("type") ?: ""
                    val amount = doc.getDouble("amount") ?: 0.0
                    val userEmail = doc.getString("userEmail") ?: ""
                    val userId = doc.getString("userId") ?: ""
                    val trxId = doc.getString("trxId") ?: ""
                    val withdrawNumber = doc.getString("withdrawNumber") ?: ""
                    val method = doc.getString("method") ?: ""

                    val box = LinearLayout(this)
                    box.orientation = LinearLayout.VERTICAL
                    box.setPadding(20, 20, 20, 20)

                    val info = TextView(this)
                    val typeLabel = if (type == "deposit") "DEPOSIT" else "WITHDRAW"
                    val extra = if (type == "deposit") "TrxID: $trxId" else "Send to: $withdrawNumber"
                    info.text = "$typeLabel - TK$amount\nUser: $userEmail\nMethod: $method\n$extra"
                    info.textSize = 15f

                    val btnRow = LinearLayout(this)
                    btnRow.orientation = LinearLayout.HORIZONTAL

                    val approveBtn = Button(this)
                    approveBtn.text = "Approve"

                    val rejectBtn = Button(this)
                    rejectBtn.text = "Reject"

                    approveBtn.setOnClickListener {
                        approveTransaction(docId, userId, type, amount)
                    }

                    rejectBtn.setOnClickListener {
                        db.collection("transactions").document(docId)
                            .update("status", "rejected")
                            .addOnSuccessListener {
                                Toast.makeText(this, "Rejected", Toast.LENGTH_SHORT).show()
                                loadPendingTransactions()
                            }
                    }

                    btnRow.addView(approveBtn)
                    btnRow.addView(rejectBtn)

                    box.addView(info)
                    box.addView(btnRow)
                    pendingContainer.addView(box)
                }
            }
    }

    private fun approveTransaction(docId: String, userId: String, type: String, amount: Double) {
        val userRef = db.collection("users").document(userId)
        val txRef = db.collection("transactions").document(docId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            val currentBalance = snapshot.getDouble("balance") ?: 0.0

            val newBalance = if (type == "deposit") {
                currentBalance + amount
            } else {
                if (currentBalance < amount) {
                    throw Exception("User has insufficient balance now")
                }
                currentBalance - amount
            }

            transaction.update(userRef, "balance", newBalance)
            transaction.update(txRef, "status", "approved")
            null
        }.addOnSuccessListener {
            Toast.makeText(this, "Approved!", Toast.LENGTH_SHORT).show()
            loadPendingTransactions()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}