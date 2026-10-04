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

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 100, 60, 60)

        val title = TextView(this)
        title.text = "Login"
        title.textSize = 26f

        val emailInput = EditText(this)
        emailInput.hint = "Email"

        val passInput = EditText(this)
        passInput.hint = "Password"
        passInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        val loginBtn = Button(this)
        loginBtn.text = "Login"

        val goRegisterBtn = Button(this)
        goRegisterBtn.text = "Don't have an account? Register"

        loginBtn.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val pass = passInput.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        goRegisterBtn.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        layout.addView(title)
        layout.addView(emailInput)
        layout.addView(passInput)
        layout.addView(loginBtn)
        layout.addView(goRegisterBtn)

        setContentView(layout)
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
