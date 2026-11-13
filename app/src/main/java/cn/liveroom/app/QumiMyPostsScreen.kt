package cn.liveroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val postPurple = Color(0xFF7044D8)

@Composable
internal fun QumiMyPostsScreen(token: String, onBack: () -> Unit) {
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var posts by remember(token, selectedTab) { mutableStateOf(emptyList<MomentPost>()) }
    var nextCursor by remember(token, selectedTab) { mutableStateOf("") }
    var loading by remember(token, selectedTab) { mutableStateOf(true) }
    var error by remember(token, selectedTab) { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<MomentPost?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val scopeName = if (selectedTab == 0) "mine_post" else "mine_video"

    LaunchedEffect(token, selectedTab, refresh) {
        loading = true
        error = null
        try {
            val (result, cursor) = api.moments(token, scopeName)
            posts = result
            nextCursor = cursor
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "发布内容加载失败"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF7F6FB))) {
        Row(Modifier.fillMaxWidth().height(60.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFFE8DFFF), Color(0xFFDDE8FF)))),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = Color(0xFF242130))
            }
            Text("我的发布", Modifier.weight(1f), color = Color(0xFF242130), fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            IconButton(onClick = { refresh++ }, enabled = !loading) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = postPurple)
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White)) {
            listOf("动态", "视频").forEachIndexed { index, label ->
                Column(Modifier.weight(1f).height(48.dp).clickable { selectedTab = index },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom) {
                    Text(label, color = if (selectedTab == index) postPurple else Color(0xFF8A8791),
                        fontSize = 16.sp,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                    Spacer(Modifier.height(9.dp))
                    Box(Modifier.width(31.dp).height(3.dp).clip(CircleShape)
                        .background(if (selectedTab == index) postPurple else Color.Transparent))
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (posts.isEmpty() && loading) item {
                Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = postPurple)
                }
            }
            if (posts.isEmpty() && !loading && error == null) item {
                Text(if (selectedTab == 0) "还没有发布动态" else "还没有发布视频",
                    Modifier.fillMaxWidth().padding(top = 85.dp), color = Color(0xFF9D99A5),
                    fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            items(posts, key = { it.id }) { post ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Color.White).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(post.createdAt.replace('T', ' ').take(16), Modifier.weight(1f),
                            color = Color(0xFF9995A2), fontSize = 12.sp)
                        if (post.reviewStatus != "APPROVED") Text(if (post.reviewStatus == "REJECTED") "未通过" else "审核中",
                            Modifier.clip(CircleShape).background(Color(0xFFF1EAFE)).padding(horizontal = 9.dp, vertical = 3.dp),
                            color = postPurple, fontSize = 11.sp)
                        if (post.isHidden) Text("已隐藏", Modifier.clip(CircleShape)
                            .background(Color(0xFFEDE7FB)).padding(horizontal = 9.dp, vertical = 3.dp),
                            color = postPurple, fontSize = 11.sp)
                    }
                    if (post.reviewStatus == "REJECTED" && post.reviewReason.isNotBlank())
                        Text("未通过原因：${post.reviewReason}", color = Color(0xFFAD5A6A), fontSize = 12.sp)
                    if (post.content.isNotBlank()) Text(post.content, color = Color(0xFF282530),
                        fontSize = 15.sp, lineHeight = 21.sp, maxLines = 4,
                        overflow = TextOverflow.Ellipsis)
                    if (post.mediaPath != null) QumiMomentMedia(post, token,
                        Modifier.width(if (post.mediaKind == "video") 120.dp else 170.dp))
                    HorizontalDivider(color = Color(0xFFF0EFF3))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("点赞 " + post.likeCount + "  ·  评论 " + post.commentCount,
                            Modifier.weight(1f), color = Color(0xFF9A96A1), fontSize = 12.sp)
                        TextButton(onClick = {
                            busyId = post.id
                            scope.launch {
                                try {
                                    api.setMomentHidden(token, post.id, !post.isHidden)
                                    posts = posts.map {
                                        if (it.id == post.id) it.copy(isHidden = !post.isHidden) else it
                                    }
                                } catch (failure: Exception) {
                                    error = failure.message ?: "操作失败，请重试"
                                } finally {
                                    busyId = null
                                }
                            }
                        }, enabled = busyId == null) {
                            Icon(if (post.isHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = null, modifier = Modifier.size(17.dp), tint = postPurple)
                            Text(if (post.isHidden) "恢复" else "隐藏", color = postPurple, fontSize = 13.sp)
                        }
                        TextButton(onClick = { deleteTarget = post }, enabled = busyId == null) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null,
                                modifier = Modifier.size(17.dp), tint = Color(0xFFE64C60))
                            Text("删除", color = Color(0xFFE64C60), fontSize = 13.sp)
                        }
                    }
                }
            }
            error?.let { message -> item {
                TextButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) {
                    Text(message + " · 重试", color = postPurple)
                }
            } }
            if (nextCursor.isNotBlank() && !loading) item {
                TextButton(onClick = {
                    val before = nextCursor
                    loading = true
                    scope.launch {
                        try {
                            val (result, cursor) = api.moments(token, scopeName, before)
                            posts = posts + result
                            nextCursor = cursor
                        } catch (failure: Exception) {
                            error = failure.message ?: "加载更多失败"
                        } finally {
                            loading = false
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("加载更多", color = postPurple) }
            }
        }
    }
    deleteTarget?.let { post ->
        AlertDialog(onDismissRequest = { if (busyId == null) deleteTarget = null },
            title = { Text("删除这条发布内容？") },
            text = { Text("删除后无法恢复，动态和视频都将从公开页面移除。") },
            confirmButton = { TextButton(onClick = {
                busyId = post.id
                scope.launch {
                    try {
                        api.deleteMoment(token, post.id)
                        posts = posts.filterNot { it.id == post.id }
                        deleteTarget = null
                    } catch (failure: Exception) {
                        error = failure.message ?: "删除失败，请重试"
                    } finally {
                        busyId = null
                    }
                }
            }, enabled = busyId == null) { Text("删除", color = Color(0xFFE64C60)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null },
                enabled = busyId == null) { Text("取消") } })
    }
}
