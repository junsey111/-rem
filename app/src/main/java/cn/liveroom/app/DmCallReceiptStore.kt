package cn.liveroom.app

import android.content.Context
import org.json.JSONObject

internal fun dmReceiptPreferences(context: Context, ownUserId: String) =
    context.getSharedPreferences("dm_call_receipts_$ownUserId", Context.MODE_PRIVATE)

internal fun cacheDmCallReceipt(context: Context, ownUserId: String, callId: String, mode: String, row: JSONObject) {
    val record = CallRecord(mode, row.optString("status"), row.optLong("duration_seconds").coerceAtLeast(0), row.optBoolean("connected"))
    if (record.terminal) dmReceiptPreferences(context, ownUserId).edit().putString(callId, record.encode()).apply()
}
