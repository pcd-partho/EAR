package com.myapp.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 100, 60, 60)

        val title = TextView(this)
        title.text = "Register"
        title.textSize = 26f

        val emailInput = EditText(this)
        emailInput.hint = "Email"

        val passInput = EditText(this)
        passInput.hint = "Password"
        passInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        val registerBtn = Button(this)
        registerBtn.text = "Create Account"

        registerBtn.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val pass = passInput.text.toString().trim()

            if (email.isEmpty() || pass.length < 6) {
                Toast.makeText(this, "Password must be 6+ chars", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: return@addOnSuccessListener

                    val userDoc = hashMapOf(
                        "email" to email,
                        "balance" to 0.0,
                        "createdAt" to System.currentTimeMillis()
                    )

                    db.collection("users").document(uid).set(userDoc)
                        .addOnSuccessListener {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Registration failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        layout.addView(title)
        layout.addView(emailInput)
        layout.addView(passInput)
        layout.addView(registerBtn)

        setContentView(layout)
    }
}
