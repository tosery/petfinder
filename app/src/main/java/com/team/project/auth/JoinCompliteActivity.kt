package com.team.project.auth

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import com.team.project.MainActivity
import com.team.project.R

class JoinCompliteActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_complite)

        val btnRevercode : Button = findViewById(R.id.btnRevercode)

        // 메인 화면으로 이동 ( MainActivity )
        btnRevercode.setOnClickListener(View.OnClickListener {
            val intent = Intent(applicationContext, MainActivity::class.java)

            // 펫저장에서 uid받아서 넘겨야함


            startActivity(intent)
        })
    }
}