package com.skyd.imomoe.model

import com.skyd.imomoe.appContext
import com.skyd.imomoe.bean.UpdateBean
import com.skyd.imomoe.ext.editor
import com.skyd.imomoe.ext.sharedPreferences
import com.skyd.imomoe.net.RetrofitManager
import com.skyd.imomoe.net.service.UpdateService
import com.skyd.imomoe.util.Util.isNewVersionByVersionCode
import com.skyd.imomoe.util.logD
import com.skyd.imomoe.util.update.AppUpdateHelper
import com.skyd.imomoe.util.update.AppUpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object AppUpdateModel {
    val status: MutableStateFlow<AppUpdateStatus> = MutableStateFlow(AppUpdateStatus.UNCHECK)
    var updateBean: UpdateBean? = null
        private set

    var updateServer: Int = AppUpdateHelper.GITHUB
        set(value) {
            if (value == field) return
            field = if (value in AppUpdateHelper.serverName.indices) {
                sharedPreferences("update").editor {
                    putInt(AppUpdateHelper.UPDATE_SERVER_SP_KEY, value)
                }
                value
            } else {
                0
            }
        }

    init {
        updateServer =
            appContext.sharedPreferences("update").getInt(AppUpdateHelper.UPDATE_SERVER_SP_KEY, 0)
    }

    fun checkUpdate() {
        if (status.value == AppUpdateStatus.CHECKING) {
            return
        }
        status.value = AppUpdateStatus.CHECKING
        val request = RetrofitManager.get().create(UpdateService::class.java)
        val check = request.checkUpdate()
        check.enqueue(object : Callback<UpdateBean> {
            override fun onFailure(call: Call<UpdateBean>, t: Throwable) {
                logD("checkUpdate", t.message ?: "")
                status.tryEmit(AppUpdateStatus.ERROR)
            }

            override fun onResponse(call: Call<UpdateBean>, response: Response<UpdateBean>) {
                updateBean = response.body()
                updateBean?.let {
                    status.tryEmit(
                        if (isNewVersionByVersionCode(updateBean?.tagName ?: "0"))
                            AppUpdateStatus.DATED
                        else AppUpdateStatus.VALID
                    )
                    return
                }
                status.tryEmit(AppUpdateStatus.ERROR)
            }
        })
    }
}