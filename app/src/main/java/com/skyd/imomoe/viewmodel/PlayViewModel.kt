package com.skyd.imomoe.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skyd.imomoe.R
import com.skyd.imomoe.appContext
import com.skyd.imomoe.bean.*
import com.skyd.imomoe.database.getAppDataBase
import com.skyd.imomoe.ext.request
import com.skyd.imomoe.model.interfaces.IPlayModel
import com.skyd.imomoe.state.DataState
import com.skyd.imomoe.util.compare.EpisodeTitleSort.sortEpisodeTitle
import com.skyd.imomoe.util.showToast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class PlayViewModel @Inject constructor(
    private val playModel: IPlayModel
) : ViewModel() {
    var isFirstTimeToPlay = true
    lateinit var playBean: PlayBean
    var partUrl: String = ""
    var lastPlayPosition: Long = 0L
    val animeCover: MutableStateFlow<ImageBean?> = MutableStateFlow(null)
    val playDataList: MutableStateFlow<DataState<List<Any>>> = MutableStateFlow(DataState.Empty)

    // 当前播放集数的索引
    var currentEpisodeIndex: MutableStateFlow<Int> = MutableStateFlow(0)

    // 当前播放的集数
    val animeEpisodeDataBean = AnimeEpisodeDataBean("", "")
    val episodesList: MutableStateFlow<DataState<List<AnimeEpisodeDataBean>>> =
        MutableStateFlow(DataState.Empty)
    val playAnotherEpisodeEvent: MutableSharedFlow<Boolean> =
        MutableSharedFlow(extraBufferCapacity = 1)
    val animeDownloadUrl: MutableSharedFlow<AnimeEpisodeDataBean> =
        MutableSharedFlow(extraBufferCapacity = 1)
    val loadingEpisodeData: MutableSharedFlow<String> =
        MutableSharedFlow(extraBufferCapacity = 1)
    val favorite: MutableStateFlow<Boolean> = MutableStateFlow(false)

    fun setActivity(activity: Activity) {
        playModel.setActivity(activity)
    }

    fun clearActivity() {
        playModel.clearActivity()
    }

    /**
     * @return true if has next episode, false else.
     */
    fun playNextEpisode(): Boolean {
        val list = episodesList.value.readOrNull().orEmpty()
        if (currentEpisodeIndex.value + 1 in list.indices) {
            playAnotherEpisode(
                list[currentEpisodeIndex.value + 1].route,
                currentEpisodeIndex.value + 1
            )
            return true
        }
        return false
    }

    // 播放另一集（页面切换到另一集，因此partUrl要更新）
    fun playAnotherEpisode(partUrl: String, currentEpisodeIndex: Int) {
        this.partUrl = partUrl
        loadingEpisodeData.tryEmit(partUrl)
        request(request = {
            playModel.playAnotherEpisode(partUrl).let {
                it ?: throw RuntimeException("html play class not found")
            }
        }, success = {
            animeEpisodeDataBean.route = it.route.ifBlank { partUrl }
            animeEpisodeDataBean.title = it.title
            animeEpisodeDataBean.videoUrl = it.videoUrl
            playAnotherEpisodeEvent.tryEmit(true)
        }, error = {
            animeEpisodeDataBean.route = "animeEpisode1"
            animeEpisodeDataBean.title = ""
            animeEpisodeDataBean.videoUrl = ""
            playAnotherEpisodeEvent.tryEmit(false)
            "${appContext.getString(R.string.get_data_failed)}\n${it.message}".showToast()
        }, finish = { this.currentEpisodeIndex.tryEmit(currentEpisodeIndex) })
    }

    fun getAnimeDownloadUrl(partUrl: String, position: Int) {
        request(request = {
            playModel.getAnimeDownloadUrl(partUrl).let {
                it ?: throw RuntimeException("getAnimeEpisodeUrlData return null")
            }
        }, success = {
            val episode = episodesList.value.readOrNull().orEmpty()[position]
            episode.videoUrl = it
            animeDownloadUrl.tryEmit(episode)
        }, error = {
            "${appContext.getString(R.string.get_data_failed)}\n${it.message}".showToast()
        })
    }

    fun getPlayData() {
        request(request = {
            if (animeCover.value == null) {
                animeCover.tryEmit(playModel.getAnimeCoverImageBean(partUrl))
            }
            playModel.getPlayData(partUrl, animeEpisodeDataBean)
        }, success = {
            viewModelScope.launch {
                if (animeEpisodeDataBean.route.isBlank()) {
                    animeEpisodeDataBean.route = partUrl
                }
                val list = episodesList.value.readOrNull().orEmpty().toMutableList()
                list.clear()
                list.addAll(it.second)
                list.sortEpisodeTitle()
                playBean = it.third
                run loop@{
                    list.forEachIndexed { index, item ->
                        if (animeEpisodeDataBean == item ||
                            item.route.isNotBlank() && animeEpisodeDataBean.route == item.route
                        ) {
                            currentEpisodeIndex.tryEmit(index)
                            return@loop
                        }
                    }
                }
                episodesList.tryEmit(DataState.Success(list))
                playDataList.tryEmit(DataState.Success(it.first))
                queryFavorite()
            }
        }, error = {
            playDataList.tryEmit(DataState.Error(it.message.orEmpty()))
            "${appContext.getString(R.string.get_data_failed)}\n${it.message}".showToast()
        })
    }

    // 更新追番集数数据
    fun updateFavoriteData() {
        if (playBean.detailPartUrl.isNotBlank()) {
            request(request = {
                getAppDataBase().favoriteAnimeDao().getFavoriteAnime(playBean.detailPartUrl)
            }, success = {
                it ?: return@request
                it.lastEpisode = animeEpisodeDataBean.title
                it.lastEpisodeUrl = partUrl
                it.time = System.currentTimeMillis()
                request({ getAppDataBase().favoriteAnimeDao().updateFavoriteAnime(it) })
            })
        } else {
            appContext.getString(R.string.delete_favorite_failed_in_play_activity).showToast()
        }
    }

    // 插入观看历史记录
    fun insertHistoryData() {
        request(request = {
            animeCover.value.let {
                if (it == null) {
                    playModel.getAnimeCoverImageBean(partUrl).run {
                        val cover = this ?: ImageBean("", "", "")
                        HistoryBean(
                            "", playBean.detailPartUrl,
                            playBean.title.title,
                            System.currentTimeMillis(),
                            cover,
                            partUrl,
                            animeEpisodeDataBean.title
                        )
                    }
                } else {
                    HistoryBean(
                        "", playBean.detailPartUrl,
                        playBean.title.title,
                        System.currentTimeMillis(),
                        it,
                        partUrl,
                        animeEpisodeDataBean.title
                    )
                }
            }
        }, success = {
            request(request = { getAppDataBase().historyDao().insertHistory(it) })
        })
    }

    fun getAnimeCoverImageBean() {
        request(request = {
            playModel.getAnimeCoverImageBean(partUrl)
        }, success = {
            it ?: return@request
            val cover = animeCover.value
            if (cover == null) {
                animeCover.tryEmit(ImageBean("", it.url, it.referer))
            } else {
                cover.url = it.url
                cover.referer = it.referer
            }
        }, error = {
            "${appContext.getString(R.string.get_data_failed)}\n${it.message}".showToast()
        })
    }


    // 查询是否追番
    fun queryFavorite() {
        if (playBean.detailPartUrl.isNotBlank()) {
            request(request = {
                getAppDataBase().favoriteAnimeDao().getFavoriteAnime(playBean.detailPartUrl)
            }, success = { favorite.tryEmit(it != null) })
        } else {
            appContext.getString(R.string.delete_favorite_failed_in_play_activity).showToast()
        }
    }

    // 取消追番
    fun deleteFavorite() {
        if (playBean.detailPartUrl.isNotBlank()) {
            request(request = {
                getAppDataBase().favoriteAnimeDao().deleteFavoriteAnime(playBean.detailPartUrl)
            }, success = {
                appContext.getString(R.string.remove_favorite_succeed).showToast()
                favorite.tryEmit(false)
            })
        } else {
            appContext.getString(R.string.delete_favorite_failed_in_play_activity).showToast()
        }
    }

    // 追番
    fun insertFavorite() {
        val cover = animeCover.value            // 番剧封面
        if (this::playBean.isInitialized && cover != null && playBean.detailPartUrl.isNotBlank()) {
            val title = playBean.title.title      // 番剧名，非集数名
            request(request = {
                getAppDataBase().favoriteAnimeDao().insertFavoriteAnime(
                    FavoriteAnimeBean(
                        "",
                        playBean.detailPartUrl,
                        title,
                        System.currentTimeMillis(),
                        cover,
                        lastEpisodeUrl = partUrl,
                        lastEpisode = animeEpisodeDataBean.title
                    )
                )
            }, success = {
                appContext.getString(R.string.favorite_succeed).showToast()
                favorite.tryEmit(true)
            })
        } else {
            appContext.getString(R.string.insert_favorite_failed_in_play_activity).showToast()
        }
    }
}