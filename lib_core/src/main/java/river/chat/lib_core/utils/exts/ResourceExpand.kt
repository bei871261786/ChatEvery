package river.chat.lib_core.utils.exts

import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import androidx.core.content.ContextCompat
import river.chat.lib_core.app.BaseApplication
import river.chat.lib_core.utils.system.DisplayUtil


/**
 * 根据id获取颜色
 */
fun Int.getColor(): Int {
    return try {
        ContextCompat.getColor(BaseApplication.getInstance(), this)
    } catch (ignore: Resources.NotFoundException) {
        0
    }
}

/**
 * 资源地址转drawable
 */
fun Int.getDrawable(): Drawable? {
    return try {
        ContextCompat.getDrawable(BaseApplication.getInstance(), this)
    } catch (ignore: Resources.NotFoundException) {
        ColorDrawable()
    }
}

/**
 * 资源地址转string
 */
fun Int.getString(): String {
    return try {
        BaseApplication.getInstance().resources.getString(this)
    } catch (ignore: Resources.NotFoundException) {
        ""
    }
}

/**
 * dp转px
 */
fun Float.dp2px(): Int {
    return DisplayUtil.dp2px(this)
}


/**
 * dp转px
 */
fun Int.dp2px(): Int {
    return DisplayUtil.dp2px(this.toFloat())
}

/**
 * 字符串比较
 */
fun String?.isEquals(string: String?): Boolean {
    return !(TextUtils.isEmpty(this) || !this.equals(string))
}

/**
 * 字符串判空
 */
fun String?.isStrEmpty(): Boolean {
    return TextUtils.isEmpty(this)
}