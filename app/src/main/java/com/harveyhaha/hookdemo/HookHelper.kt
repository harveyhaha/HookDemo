package com.harveyhaha.hookdemo

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class HookHelper {
    companion object {
        private val TAG = "HookHelper"
        val EXTRA_TARGET_INTENT = "extra_target_intent"

        //实用已注册的activity代替请求，显示时再替换回目标Activity
        fun hookAmsAidl() {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) hookIActivityTaskManager()
            else hookIActivityManager()
        }

        fun unHookAmsAidl() {

        }

        private fun hookIActivityTaskManager() {
            try {
                val activityManagerClass = Class.forName("android.app.ActivityTaskManager")
                val singleField: Field = activityManagerClass.getDeclaredField("IActivityTaskManagerSingleton")
                singleField.isAccessible = true
                val singleObject = singleField.get(null)

                //获取IActivityManager 对象
                val singletonClass = Class.forName("android.util.Singleton")
                val mInstanceField = singletonClass.getDeclaredField("mInstance")
                mInstanceField.isAccessible = true

                //原始的ActivityTaskManager
                val iActivityTaskManager = mInstanceField.get(singleObject)
                //动态代理
                val proxy = Proxy.newProxyInstance(Thread.currentThread().contextClassLoader, arrayOf(Class.forName("android.app.IActivityTaskManager")), object : InvocationHandler {
                    override fun invoke(
                        proxy: Any?, method: Method?, args: Array<Any>?
                    ): Any? {
//                            Log.i(TAG, "method: $method args: $args")
                        //真正要启动的Intent
                        var rawIntent: Intent? = null
                        var index = 0
                        if (args != null && "startActivity" == method?.name) {
                            Log.i(TAG, "${Thread.currentThread()} 准备开始启动Activity")
                            for (i in args.indices) {
                                Log.i(TAG, "arg $i: ${args[i]}")
                                if (args[i] is Intent) {
                                    rawIntent = args[i] as Intent
                                    index = i
                                }
                            }
                            //代替的Intent,实用清单中存在的Intent代替
                            val newIntent = Intent()
                            newIntent.setComponent(
                                ComponentName(
                                    "com.harveyhaha.hookdemo", RegisterActivity::class.java.name
                                )
                            )
                            newIntent.putExtra(EXTRA_TARGET_INTENT, rawIntent)
                            Log.i(TAG, "替代的Activity $newIntent")
                            args[index] = newIntent
                        }
                        args?.let {
                            return method?.invoke(iActivityTaskManager, *args)
                        } ?: run {
                            return method?.invoke(iActivityTaskManager)
                        }
                    }
                })
                mInstanceField.set(singleObject, proxy)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun hookIActivityManager() {
            try {
                val iActivityManagerSingletonField: Field = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val activityManager = Class.forName("android.app.ActivityManager")
                    activityManager.getDeclaredField("IActivityManagerSingleton")
                } else {
                    val activityManger = Class.forName("android.app.ActivityManagerNative")
                    activityManger.getDeclaredField("gDefault")
                }
                iActivityManagerSingletonField.isAccessible = true
                val iActivityManagerSingletonObject = iActivityManagerSingletonField.get(null)

                //拿ActivityManager对象
                val singletonClass = Class.forName("android.util.Singleton")
                val mIActivityManagerField: Field = singletonClass.getDeclaredField("mInstance")
                mIActivityManagerField.isAccessible = true

                //原始的ActivityTaskManager
                val iActivityManager = mIActivityManagerField.get(iActivityManagerSingletonObject)
                //动态代理
                val proxy = Proxy.newProxyInstance(Thread.currentThread().contextClassLoader, arrayOf(Class.forName("android.app.IActivityManager")), object : InvocationHandler {
                    override fun invoke(
                        proxy: Any?, method: Method?, args: Array<Any>?
                    ): Any? {
                        Log.i(TAG, "method: $method args size: ${args?.size}")
                        //真正要启动的Intent
                        var rawIntent: Intent? = null
                        var index = 0
                        if (args != null && "startActivity" == method?.name) {
                            Log.i(TAG, "${Thread.currentThread()} 准备开始启动Activity")
                            for (i in args.indices) {
                                Log.i(TAG, "arg $i: ${args[i]}")
                                if (args[i] is Intent) {
                                    rawIntent = args[i] as Intent
                                    index = i
                                }
                            }
                            Log.i(TAG, "真正要启动的Activity $rawIntent ${NoRegisterActivity::class.java.name} ${NoRegisterActivity::class.java.simpleName}")
                            //代替的Intent,实用清单中存在的Intent代替
                            val newIntent = Intent()
                            newIntent.setComponent(
                                ComponentName(
                                    "com.harveyhaha.hookdemo", RegisterActivity::class.java.name
                                )
                            )
                            newIntent.putExtra(EXTRA_TARGET_INTENT, rawIntent)
                            Log.i(TAG, "替代的Activity $newIntent")
                            args[index] = newIntent
                        }
                        args?.let {
                            return method?.invoke(iActivityManager, *args)
                        } ?: run {
                            return method?.invoke(iActivityManager)
                        }
                    }
                })
                mIActivityManagerField.set(iActivityManagerSingletonObject, proxy)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun hookHandler() {
            try {
                val atClass = Class.forName("android.app.ActivityThread")
                val sCurrentActivityThreadField = atClass.getDeclaredField("sCurrentActivityThread")
                sCurrentActivityThreadField.isAccessible = true
                val sCurrentActivityThread = sCurrentActivityThreadField[null]
                //ActivityThread 一个app进程 只有一个，获取它的mH
                val mHField = atClass.getDeclaredField("mH")
                mHField.isAccessible = true
                val mH = mHField[sCurrentActivityThread] as Handler

                //获取mCallback
                val mCallbackField = Handler::class.java.getDeclaredField("mCallback")
                mCallbackField.isAccessible = true
                mCallbackField[mH] = Handler.Callback { msg ->
                    Log.i(TAG, "handleMessage: " + msg.what)
                    when (msg.what) {
                        100 -> {
                            //7.0以前
                        }

                        159 -> {
                            val obj = msg.obj
//                            Log.i(TAG, "handleMessage: obj=$obj")
                            try {
                                val mActivityCallbacksField = obj.javaClass.getDeclaredField("mActivityCallbacks")
                                mActivityCallbacksField.isAccessible = true
                                val mActivityCallbacks = mActivityCallbacksField[obj] as List<*>
//                                Log.i(TAG, "handleMessage: mActivityCallbacks= $mActivityCallbacks")
                                //注意了 这里如果有同学debug调试会发现第一次size=0 原因如下
                                //在Android O之前
                                //public static final int LAUNCH_ACTIVITY         = 100;
                                //public static final int PAUSE_ACTIVITY          = 101;
                                //public static final int PAUSE_ACTIVITY_FINISHING= 102;
                                //public static final int STOP_ACTIVITY_SHOW      = 103;
                                //public static final int STOP_ACTIVITY_HIDE      = 104;
                                //public static final int SHOW_WINDOW             = 105;
                                //public static final int HIDE_WINDOW             = 106;
                                //public static final int RESUME_ACTIVITY         = 107;
                                //public static final int SEND_RESULT             = 108;
                                //public static final int DESTROY_ACTIVITY        = 109;
                                //end
                                //从AndroidP开始重构了状态模式
                                //public static final int EXECUTE_TRANSACTION = 159;
                                // 首先一个app 只有一个ActivityThread 然后就只有一个mH
                                //我们app所有的activity的生命周期的处理都在mH的handleMessage里面处理
                                //在Android 8.0之前，不同的生命周期对应不同的msg.what处理
                                //在Android 8.0 改成了全部由EXECUTE_TRANSACTION来处理
                                //所以这里第一次mActivityCallbacks是MainActivity的生命周期回调的
                                //                                handleMessage: 159
                                //                                handleMessage: obj=android.app.servertransaction.ClientTransaction@efd342
                                //                                handleMessage: mActivityCallbacks= []
                                //                                invoke: method activityPaused
                                //                                handleMessage: 159
                                //                                handleMessage: obj=android.app.servertransaction.ClientTransaction@4962
                                //                                handleMessage: mActivityCallbacks= [WindowVisibilityItem{showWindow=true}]
                                //                                handleMessage: size= 1
                                //                                handleMessage: 159
                                //                                handleMessage: obj=android.app.servertransaction.ClientTransaction@9e98c6b
                                //                                handleMessage: mActivityCallbacks= [LaunchActivityItem{intent=Intent { cmp=com.zero.activityhookdemo/.StubActivity (has extras) },ident=168243404,info=ActivityInfo{5b8d769 com.zero.activityhookdemo.StubActivity},curConfig={1.0 310mcc260mnc [en_US] ldltr sw411dp w411dp h659dp 420dpi nrml port finger qwerty/v/v -nav/h winConfig={ mBounds=Rect(0, 0 - 0, 0) mAppBounds=Rect(0, 0 - 1080, 1794) mWindowingMode=fullscreen mActivityType=undefined} s.6},overrideConfig={1.0 310mcc260mnc [en_US] ldltr sw411dp w411dp h659dp 420dpi nrml port finger qwerty/v/v -nav/h winConfig={ mBounds=Rect(0, 0 - 1080, 1794) mAppBounds=Rect(0, 0 - 1080, 1794) mWindowingMode=fullscreen mActivityType=standard} s.6},referrer=com.zero.activityhookdemo,procState=2,state=null,persistentState=null,pendingResults=null,pendingNewIntents=null,profilerInfo=null}]
                                //                                handleMessage: size= 1
                                if (mActivityCallbacks.size > 0) {
                                    Log.i(TAG, "handleMessage: size= " + mActivityCallbacks.size)
                                    val className = "android.app.servertransaction.LaunchActivityItem"
                                    if (mActivityCallbacks[0]!!.javaClass.getCanonicalName() == className) {
                                        val `object` = mActivityCallbacks[0]!!
                                        val intentField = `object`.javaClass.getDeclaredField("mIntent")
                                        intentField.isAccessible = true
                                        val intent = intentField[`object`] as Intent
                                        val targetIntent = intent.getParcelableExtra<Intent>(EXTRA_TARGET_INTENT)
                                        intent.setComponent(targetIntent!!.component)
                                        Log.i(TAG, "${Thread.currentThread()} 恢复启动 $targetIntent")
                                    }
                                }
                            } catch (e: java.lang.Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    mH.handleMessage(msg)
                    true
                }
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "hookHandler: " + e.message)
                e.printStackTrace()
            }
        }


        fun hookInstrumentation(activity: Activity) {
            //TODO:
            val activityClass: Class<*> = Activity::class.java
            //通过Activity.class 拿到 mInstrumentation字段
            var field: Field? = null
            try {
                field = activityClass.getDeclaredField("mInstrumentation")
                field.isAccessible = true
                //根据activity内mInstrumentation字段 获取Instrumentation对象
                val instrumentation = field.get(activity) as Instrumentation
                Log.i(TAG, "instrumentation $instrumentation $instrumentation")
                //创建代理对象,注意了因为Instrumentation是类，不是接口 所以我们只能用静态代理，
                val instrumentationProxy: Instrumentation = ProxyInstrumentation(instrumentation)
                //进行替换
                field.set(activity, instrumentationProxy)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }


        internal class ProxyInstrumentation(
            // ActivityThread中原始的对象, 保存起来
            var mBase: Instrumentation
        ) : Instrumentation() {

            fun execStartActivity(
                who: Context, contextThread: IBinder, token: IBinder, target: Activity, intent: Intent, requestCode: Int, options: Bundle?
            ): ActivityResult? {
                Log.d(
                    TAG, "执行了startActivity, 参数如下: " + "who = [" + who + "], " + "contextThread = [" + contextThread + "], token = [" + token + "], " + "target = [" + target + "], intent = [" + intent + "], requestCode = [" + requestCode + "], options = [" + options + "]"
                )

                // 由于这个方法是隐藏的,因此需要使用反射调用;首先找到这个方法
                //execStartActivity有重载，别找错了
                return try {
                    val execStartActivity = Instrumentation::class.java.getDeclaredMethod(
                        "execStartActivity", Context::class.java, IBinder::class.java, IBinder::class.java, Activity::class.java, Intent::class.java, Int::class.javaPrimitiveType, Bundle::class.java
                    )
                    execStartActivity.isAccessible = true
                    val registerIntent = Intent()
                    registerIntent.setComponent(ComponentName("com.harveyhaha.hookdemo", RegisterActivity::class.java.name))
                    registerIntent.putExtra(EXTRA_TARGET_INTENT, intent)
                    Log.i(HookHelper.TAG, "替代的Activity $registerIntent ${registerIntent.extras}")
                    execStartActivity.invoke(
                        mBase, who, contextThread, token, target, registerIntent, requestCode, options
                    ) as ActivityResult?
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw RuntimeException("do not support!!! pls adapt it")
                }
            }

            /**
             * 重写newActivity 因为newActivity 方法有变
             * 原来是：(Activity)cl.loadClass(className).newInstance();
             *
             * @param cl
             * @param className
             * @param intent
             * @return
             * @throws InstantiationException
             * @throws IllegalAccessException
             * @throws ClassNotFoundException
             */
            @Throws(InstantiationException::class, IllegalAccessException::class, ClassNotFoundException::class)
            override fun newActivity(
                cl: ClassLoader, className: String, intent: Intent
            ): Activity {
                return mBase.newActivity(cl, className, intent)
            }

            companion object {
                private const val TAG = "Zero"
            }
        }


        fun hookActivityThreadInstrumentation() {
            //TODO:
            try {
                // 先获取到当前的ActivityThread对象
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
                currentActivityThreadMethod.isAccessible = true
                //currentActivityThread是一个static函数所以可以直接invoke，不需要带实例参数
                val currentActivityThread = currentActivityThreadMethod.invoke(null)

                // 拿到原始的 mInstrumentation字段
                val mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation")
                mInstrumentationField.isAccessible = true
                val mInstrumentation = mInstrumentationField.get(currentActivityThread) as Instrumentation
                // 创建代理对象
                val proxyInstrumentation: Instrumentation = ProxyInstrumentation(mInstrumentation)
                // 偷梁换柱
                mInstrumentationField.set(currentActivityThread, proxyInstrumentation)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }
}