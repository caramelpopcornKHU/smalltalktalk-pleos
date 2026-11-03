package com.smalltoktok.pleos.ui

import com.smalltoktok.pleos.data.ApiException

/** 서버·네트워크 오류를 사용자에게 보여줄 한 줄로 */
fun friendlyError(e: Throwable): String = when {
    e is ApiException && e.code == "quota_exceeded" -> "체험 횟수를 모두 썼어요."
    e is ApiException && e.code == "buddy_daily_limit" -> "오늘은 말동무와 충분히 수다를 떨었어요. 내일 또 만나요."
    e is ApiException && e.code == "adult_only" -> "설레는 썸 상대는 성인만 쓸 수 있어요. 설정에서 태어난 해를 확인해 주세요."
    e is ApiException -> "서버 오류가 났어요 (${e.code})."
    e is java.io.IOException -> "인터넷 연결이 불안정해요. 잠시 뒤 다시 시도해 주세요."
    else -> "문제가 생겼어요: ${e.message}"
}
