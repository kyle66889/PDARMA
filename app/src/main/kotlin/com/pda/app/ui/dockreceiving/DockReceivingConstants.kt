package com.pda.app.ui.dockreceiving

/** 抄自 web constants.ts，保持与 RMA web 端一致。 */
val CARRIERS = listOf("UPS", "FedEx", "USPS", "DHL", "Amazon", "OnTrac", "Other")
val CONDITIONS = listOf("Good", "Fair", "Damaged", "Unknown")

/**
 * 大小写不敏感匹配 CARRIERS，命中返回标准写法；未命中返回原值（trim 后）；
 * null/空白返回 ""。对齐 web PhotoTab 的归一化逻辑。
 */
fun normalizeCarrier(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return ""
    return CARRIERS.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: trimmed
}
