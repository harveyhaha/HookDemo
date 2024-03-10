package com.harveyhaha.hookdemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.harveyhaha.hookdemo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("Main","onCreate---")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setTitle(R.string.app_name)
        setSupportActionBar(binding.toolbar)
        //第一步，三种方法可选 将NoRegisterActivity更换为RegisterActivity
//        HookHelper.hookAmsAidl()
        HookHelper.hookInstrumentation(this)
//        HookHelper.hookActivityThreadInstrumentation()
        //第二步 将RegisterActivity回退为NoRegisterActivity
        HookHelper.hookHandler()

        binding.hookAmsBtn.setOnClickListener {
            intentToUnRegisterActivity()
        }
    }

    private fun intentToUnRegisterActivity() {
        startActivity(Intent(this, NoRegisterActivity::class.java))
    }
}