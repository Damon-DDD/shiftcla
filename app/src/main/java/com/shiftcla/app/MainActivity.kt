package com.shiftcla.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shiftcla.app.data.ApiKeyStore
import com.shiftcla.app.ui.dashboard.ShiftclaRoute
import com.shiftcla.app.ui.dashboard.ShiftclaTypography
import com.shiftcla.app.ui.dashboard.ShiftclaViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 必须在 setContent 之前：UI 一上来就要读已保存的 API Key
        ApiKeyStore.init(applicationContext)
        setContent {
            MaterialTheme(typography = ShiftclaTypography) {
                ShiftclaRoot()
            }
        }
    }
}

/**
 * 正式入口：ViewModel 驱动。
 *
 * 数据闭环：抽屉「导入教务数据」→ ImportBottomSheet 输入文本 →
 * `viewModel.importSchedule` → DeepSeekEngine 解析 → `courses` 刷新 → 瀑布流错落进场。
 *
 * 首屏课表为空（VM 默认空列表），界面中央会给出引导文案；
 * 想让它一进来就展示示例数据，把 `ShiftclaRoute` 里的 `copy(courses = courses)`
 * 换成 `copy(courses = courses.ifEmpty { 示例数据 })` 即可。
 */
@Composable
private fun ShiftclaRoot() {
    val viewModel: ShiftclaViewModel = viewModel()
    val context = LocalContext.current
    ShiftclaRoute(
        viewModel = viewModel,
        onError = { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        },
    )
}
