package cn.liveroom.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.io.File

/** Uses only local UI and fake callbacks: no calls, messages or balance changes. */
class DirectCallDeviceCheck : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val status = Bundle().apply { putString("class", this@DirectCallDeviceCheck.javaClass.name); putString("test", "callOptionsAndGiftDismissal"); putInt("numtests", 1); putInt("current", 1) }
        sendStatus(1, status)
        var activity: UiCheckActivity? = null
        try {
            activity = startActivitySync(Intent(targetContext, UiCheckActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as UiCheckActivity
            checkOptions(activity)
            checkGifts(activity)
            sendStatus(0, status)
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "Call options: free/paid validation and unapproved lock OK. Gifts: elevated footer, selection, outside tap, Back and reopen OK. Fake callbacks only.\n") })
        } catch (error: Throwable) {
            status.putString("stack", error.stackTraceToString()); sendStatus(-2, status)
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", error.stackTraceToString()) })
        } finally { activity?.let { runOnMainSync { it.finish() } } }
    }

    private fun checkOptions(activity: UiCheckActivity) {
        val approved = mutableStateOf(true)
        val paid = mutableStateOf(false)
        val price = mutableStateOf("")
        var starts = 0
        runOnMainSync { activity.setContent { LiveAppTheme {
            DirectVideoCallOptionsDialog(approved.value, false, paid.value, price.value, null, {},
                { paid.value = it }, { price.value = it }, { starts++ })
        } } }
        await("免费视频"); click("付费视频")
        await("本次价格（金币 / 次）")
        check(!clickable("发起通话").isEnabled) { "Empty price should prevent starting" }
        runOnMainSync { price.value = "0" }; await("请输入 1 至 1000000 金币")
        check(!clickable("发起通话").isEnabled)
        runOnMainSync { price.value = "52" }
        waitUntil { clickable("发起通话").isEnabled }
        capture("call-options.png")
        click("发起通话"); check(starts == 1)
        runOnMainSync { paid.value = false; approved.value = false }
        await("主播认证后开放")
        check(!clickable("付费视频").isEnabled) { "Unapproved user can choose paid calls" }
        check(clickable("免费视频").isEnabled && !paid.value)
    }

    private fun checkGifts(activity: UiCheckActivity) {
        var hangups = 0
        var sent = 0
        runOnMainSync { activity.setContent { LiveAppTheme {
            DirectCallSurface("演示主播", null, true, true, 65, "已接通", true, true, true, true,
                {}, {}, {}, {}, { hangups++ }, demoGiftCatalog, 100L, false, true, { sent++ },
                null, 0, "测试用户", null, {},
                remoteVideo = { Box(Modifier.fillMaxSize().background(Color(0xFFDDD2EE))) { Text("本地演示画面") } },
                localVideo = { Box(Modifier.fillMaxSize().background(Color(0xFF77658E))) })
        } } }
        await("送礼"); click("送礼"); await("全部")
        val footer = bounds(await("不扣金币"))
        val displayHeight = targetContext.resources.displayMetrics.heightPixels
        val density = targetContext.resources.displayMetrics.density
        capture("call-gifts.png")
        check(footer.bottom < displayHeight - 24 * density) { "Gift footer is too close to screen bottom: $footer / $displayHeight, density $density" }
        val rose = bounds(await("玫瑰"))
        tap(rose.exactCenterX(), rose.exactCenterY()); await("演示赠送")
        check(sent == 0) { "Selection unexpectedly sends gift" }
        capture("call-gifts.png")
        // The upper area is outside the panel. Inject an actual touch to verify hit testing.
        val all = bounds(await("全部"))
        tap(targetContext.resources.displayMetrics.widthPixels / 2f, (all.top / 2f).coerceAtLeast(70f))
        waitUntil { find("全部") == null }
        check(hangups == 0)
        click("送礼"); await("全部")
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitUntil { find("全部") == null }
        check(hangups == 0 && find("已接通 01:05") != null)
        click("送礼"); await("全部"); click("玫瑰"); click("演示赠送")
        waitUntil { find("全部") == null }
        check(sent == 1 && hangups == 0)
    }

    private fun find(label: String): AccessibilityNodeInfo? {
        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString()?.trim() == label || node.contentDescription?.toString()?.trim() == label) return node
            for (i in 0 until node.childCount) search(node.getChild(i))?.let { return it }
            return null
        }
        return search(uiAutomation.rootInActiveWindow)
    }
    private fun await(label: String): AccessibilityNodeInfo {
        waitUntil { find(label) != null }; return find(label) ?: error("Missing $label")
    }
    private fun clickable(label: String): AccessibilityNodeInfo {
        var node = await(label)
        while (!node.isClickable && node.parent != null) node = node.parent
        return node
    }
    private fun click(label: String) {
        check(clickable(label).performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Could not click $label" }
        waitForIdleSync(); SystemClock.sleep(200)
    }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 8_000
        while (!condition()) { check(SystemClock.elapsedRealtime() < deadline) { "UI condition timed out" }; SystemClock.sleep(100) }
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also { node.getBoundsInScreen(it) }
    private fun tap(x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        sendPointerSync(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0))
        sendPointerSync(MotionEvent.obtain(time, time + 80, MotionEvent.ACTION_UP, x, y, 0))
        waitForIdleSync()
    }
    private fun capture(name: String) {
        uiAutomation.takeScreenshot()?.let { bitmap ->
            val folder = File(targetContext.cacheDir, "direct-call-device-check").apply { mkdirs() }
            File(folder, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
