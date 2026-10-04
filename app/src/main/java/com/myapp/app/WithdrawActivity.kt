package com.myapp.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WithdrawActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentBalance = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 80, 60, 60)

        val title = TextView(this)
        title.text = "Withdraw Money"
        title.textSize = 26f

        val balanceText = TextView(this)
        balanceText.text = "Current Balance: Loading..."
        balanceText.textSize = 18f
        balanceText.setPadding(0, 10, 0, 20)

        val methodInput = EditText(this)
        methodInput.hint = "Method (bKash / Nagad)"

        val numberInput = EditText(this)
        numberInput.hint = "Your bKash/Nagad number"

        val amountInput = EditText(this)
        amountInput.hint = "Amount"
        amountInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        val submitBtn = Button(this)
        submitBtn.text = "Request Withdraw"

        submitBtn.setOnClickListener {
            val method = methodInput.text.toString().trim()
            val number = numberInput.text.toString().trim()
            val amountStr = amountInput.text.toString().trim()

            if (method.isEmpty() || number.isEmpty() || amountStr.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (amount > currentBalance) {
                Toast.makeText(this, "Insufficient balance", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            val email = auth.currentUser?.email ?: ""

            val request = hashMapOf(
                "userId" to uid,
                "userEmail" to email,
                "type" to "withdraw",
                "method" to method,
                "withdrawNumber" to number,
                "amount" to amount,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )

            submitBtn.isEnabled = false

            db.collection("transactions").add(request)
                .addOnSuccessListener {
                    Toast.makeText(this, "Withdraw request submitted!", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    submitBtn.isEnabled = true
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        layout.addView(title)
        layout.addView(balanceText)
        layout.addView(methodInput)
        layout.addView(numberInput)
        layout.addView(amountInput)
        layout.addView(submitBtn)

        setContentView(layout)

        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                currentBalance = doc.getDouble("balance") ?: 0.0
                balanceText.text = "Current Balance: TK$currentBalance"
            }
    }
}