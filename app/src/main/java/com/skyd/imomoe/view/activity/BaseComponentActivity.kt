package com.skyd.imomoe.view.activity

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.flurry.android.FlurryAgent
import com.google.android.material.color.DynamicColors
import com.google.android.material.composethemeadapter3.Mdc3Theme
import com.skyd.imomoe.R
import com.skyd.imomoe.config.Const
import com.skyd.imomoe.ext.collectWithLifecycle
import com.skyd.imomoe.ext.initializeFlurry
import com.skyd.imomoe.ext.theme.appThemeRes
import com.skyd.imomoe.ext.theme.transparentSystemBar
import com.skyd.imomoe.util.Util
import com.skyd.imomoe.util.eventbus.EventBusSubscriber
import org.greenrobot.eventbus.EventBus

abstract class BaseComponentActivity : ComponentActivity() {
    protected open var activityThemeRes = appThemeRes.value

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(activityThemeRes)
        appThemeRes.collectWithLifecycle(this) {
            if (activityThemeRes != it) {
                // 壁纸取色
                if (it == R.style.Theme_Anime_Dynamic) {
                    DynamicColors.applyToActivityIfAvailable(this@BaseComponentActivity)
                }
                recreate()
            }
        }

        // 透明状态栏
        window.transparentSystemBar(window.decorView.findViewById<ViewGroup>(android.R.id.content))

        if (Util.lastReadUserNoticeVersion() >= Const.Common.USER_NOTICE_VERSION) {
            initializeFlurry(application)
        }
    }

    fun setContentBase(content: @Composable () -> Unit) {
        setContent {
            Mdc3Theme(
                setTextColors = true,
                setDefaultFontFamily = true
            ) {
                content.invoke()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (this is EventBusSubscriber) EventBus.getDefault().register(this)
        FlurryAgent.onStartSession(this)
    }

    override fun onStop() {
        super.onStop()
        if (this is EventBusSubscriber && EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this)
        FlurryAgent.onEndSession(this)
    }
}