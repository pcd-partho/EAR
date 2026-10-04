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

class AddMoneyActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var numbersContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val scroll = ScrollView(this)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 80, 60, 60)

        val title = TextView(this)
        title.text = "Add Money"
        title.textSize = 26f

        val infoText = TextView(this)
        infoText.text = "Send money to one of these numbers, then enter the details below:"
        infoText.textSize = 16f
        infoText.setPadding(0, 20, 0, 10)

        numbersContainer = LinearLayout(this)
        numbersContainer.orientation = LinearLayout.VERTICAL

        val methodInput = EditText(this)
        methodInput.hint = "Method used (bKash / Nagad)"

        val amountInput = EditText(this)
        amountInput.hint = "Amount"
        amountInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        val trxInput = EditText(this)
        trxInput.hint = "Transaction ID (TrxID)"

        val submitBtn = Button(this)
        submitBtn.text = "Submit Request"

        submitBtn.setOnClickListener {
            val method = methodInput.text.toString().trim()
            val amountStr = amountInput.text.toString().trim()
            val trxId = trxInput.text.toString().trim()

            if (method.isEmpty() || amountStr.isEmpty() || trxId.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            val email = auth.currentUser?.email ?: ""

            val request = hashMapOf(
                "userId" to uid,
                "userEmail" to email,
                "type" to "deposit",
                "method" to method,
                "amount" to amount,
                "trxId" to trxId,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )

            submitBtn.isEnabled = false

            db.collection("transactions").add(request)
                .addOnSuccessListener {
                    Toast.makeText(this, "Request submitted! Wait for approval.", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    submitBtn.isEnabled = true
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        layout.addView(title)
        layout.addView(infoText)
        layout.addView(numbersContainer)
        layout.addView(methodInput)
        layout.addView(amountInput)
        layout.addView(trxInput)
        layout.addView(submitBtn)

        scroll.addView(layout)
        setContentView(scroll)

        loadPaymentNumbers()
    }

    private fun loadPaymentNumbers() {
        db.collection("paymentNumbers").get()
            .addOnSuccessListener { result ->
                numbersContainer.removeAllViews()
                if (result.isEmpty) {
                    val none = TextView(this)
                    none.text = "No payment numbers available yet. Contact admin."
                    numbersContainer.addView(none)
                    return@addOnSuccessListener
                }
                for (doc in result) {
                    val method = doc.getString("method") ?: ""
                    val number = doc.getString("number") ?: ""
                    val line = TextView(this)
                    line.text = "$method: $number"
                    line.textSize = 18f
                    line.setPadding(0, 5, 0, 5)
                    numbersContainer.addView(line)
                }
            }
    }
}