package com.harveyhaha.hookdemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 未在清单中注册的Activity
 */
class NoRegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unregister)
    }
}