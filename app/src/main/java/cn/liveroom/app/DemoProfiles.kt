package cn.liveroom.app

import android.content.Context

internal data class DemoProfile(val publicId: Long, val name: String, val poster: Int)
internal val demoProfiles = listOf(
    DemoProfile(651287, "小橙", R.drawable.qumi_demo_live_51287),
    DemoProfile(648036, "阿宁", R.drawable.qumi_demo_live_48036),
    DemoProfile(647971, "小夏", R.drawable.qumi_demo_live_47971),
    DemoProfile(641181, "小伊", R.drawable.qumi_demo_live_41181),
)
internal fun demoLiveIndex(user: PublicUser): Int? = demoProfiles.indexOfFirst {
    it.publicId == user.publicId && user.description?.startsWith("【演示账号】") == true
}.takeIf { it >= 0 }
internal fun cacheDemoProfile(context: Context, user: PublicUser) {
    if (demoProfiles.none { it.publicId == user.publicId } || user.description?.startsWith("【演示账号】") != true) return
    context.getSharedPreferences("demo_profiles", Context.MODE_PRIVATE).edit()
        .putLong(user.id.toString(), user.publicId).apply()
}
internal fun demoProfileForUser(context: Context, id: String): DemoProfile? {
    val publicId = context.getSharedPreferences("demo_profiles", Context.MODE_PRIVATE).getLong(id, 0)
    return demoProfiles.firstOrNull { it.publicId == publicId }
}
