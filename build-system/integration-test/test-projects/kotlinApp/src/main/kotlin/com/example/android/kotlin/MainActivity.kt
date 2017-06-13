package com.example.android.kotlin

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    var text: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_layout)
        val tmpTxt = findViewById(R.id.someText)
        text = tmpTxt as TextView
        if (text != null) {
            text?.setText("testing kotlin")
            android.util.Log.d("kotlin", text?.getText().toString())
        }
        val tmpClick = findViewById(R.id.click)
        val click = tmpClick as Button
        click?.setOnClickListener { text?.setText("clicked!") }
    }
}
