package com.skyd.imomoe.bean

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.skyd.imomoe.config.Const
import com.skyd.imomoe.view.adapter.variety.Diff
import java.io.Serializable

@Entity(tableName = Const.Database.AppDataBase.FAVORITE_ANIME_TABLE_NAME)
class FavoriteAnimeBean(      //下面的url都是partUrl
    @ColumnInfo(name = "actionUrl")
    override var route: String,
    @PrimaryKey
    @ColumnInfo(name = "animeUrl")
    var animeUrl: String,
    @ColumnInfo(name = "animeTitle")
    var animeTitle: String,
    @ColumnInfo(name = "time")
    var time: Long,                 //收藏日期
    @ColumnInfo(name = "cover")
    var cover: ImageBean,           //封面
    @ColumnInfo(name = "lastEpisodeUrl")
    var lastEpisodeUrl: String? = null,        //上次看到哪一集
    @ColumnInfo(name = "lastEpisode")
    var lastEpisode: String? = null
) : BaseBean, Serializable, Diff {
    override fun contentSameAs(o: Any?): Boolean = when {
        o !is FavoriteAnimeBean -> false
        route == o.route && animeUrl == o.animeUrl && animeTitle == o.animeTitle &&
                time == o.time && cover == o.cover && lastEpisodeUrl == o.lastEpisodeUrl &&
                lastEpisode == o.lastEpisode -> true
        else -> false
    }
}
