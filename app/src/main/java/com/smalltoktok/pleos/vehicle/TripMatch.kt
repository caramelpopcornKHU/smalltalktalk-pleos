package com.smalltoktok.pleos.vehicle

import com.smalltoktok.pleos.data.Scenario

/**
 * 목적지에 맞는 공유 상황 고르기 (2026-11-02, 앱 안 키워드 규칙).
 * 서버를 부르지 않아 바로 나오고, 같은 목적지면 늘 같은 상황이 나온다(시연에서 결과가 흔들리지 않게).
 * 상황은 제목으로 찾는다. 공유 상황을 다시 쓸 때도 제목은 그대로 두므로(scenarios:load --replace) 기준으로 삼을 수 있다.
 * 맞는 규칙이 없으면 null이고, 홈은 지금처럼 무작위 오늘의 상대를 보여준다.
 */
object TripMatch {
    private class Rule(val titles: List<String>, val keywords: List<String> = emptyList(), val test: ((String) -> Boolean)? = null)

    // 목적지 이름으로만 본다(주소의 "공원로"·"카페거리" 같은 길 이름에 걸리지 않게).
    // 위에서부터 먼저 맞는 규칙을 쓴다 (북카페가 카페보다, 국립공원이 공원보다 먼저)
    private val rules = listOf(
        Rule(listOf("미용실 의자에서"), listOf("미용실", "헤어", "살롱", "바버", "hair")),
        Rule(listOf("스터디 모임 첫날"), listOf("북카페", "도서관", "스터디")),
        Rule(listOf("꽃집에서 선물 고르기"), listOf("꽃집", "플라워", "flower")),
        Rule(listOf("클라이밍장 쉬는 시간"), listOf("클라이밍", "볼더링", "암장")),
        Rule(listOf("결혼식 하객 테이블에서"), listOf("웨딩", "예식", "컨벤션")),
        Rule(listOf("등산로 쉼터에서"), listOf("등산", "둘레길", "국립공원", "탐방지원센터", "산성")) { name ->
            // 북한산·관악산처럼 '산'으로 끝나는 이름 (부산·울산 같은 지명은 뺀다)
            name.endsWith("산") && name !in CITIES_ENDING_SAN
        },
        Rule(listOf("러닝 크루 첫날"), listOf("한강", "공원", "러닝", "운동장")),
        Rule(listOf("합동 회식 옆자리"), listOf("고깃집", "삼겹살", "갈비", "포차", "이자카야", "호프", "술집")),
        Rule(listOf("모임에서 처음 본 옆자리"), listOf("파티룸", "레스토랑", "다이닝")),
        Rule(listOf("거래처 미팅 시작 전"), listOf("(주)", "㈜", "주식회사", "본사", "사옥")),
        Rule(listOf("단골 카페 사장님"), listOf("카페", "커피", "cafe", "coffee", "스타벅스", "투썸", "이디야", "메가커피", "빽다방")),
    )

    /** 출근길(즐겨찾기 회사)에는 회사에서 만나는 상황 중 하나 */
    private val office = listOf(
        "월요일 아침 탕비실", "엘리베이터에서 단둘이", "회의 시작 5분 전",
        "새로 온 팀장님과 커피", "사내 카페 줄에서", "부장님과 점심 줄",
    )

    /** 퇴근길(즐겨찾기 집)에는 동네에서 만나는 상황 */
    private val home = listOf("아파트 엘리베이터 이웃", "단골 카페 사장님", "꽃집에서 선물 고르기")

    private val CITIES_ENDING_SAN = setOf("부산", "울산", "마산", "군산", "익산", "서산", "경산", "오산", "아산", "양산", "논산", "용산")

    fun pick(trip: Trip, shared: List<Scenario>): Scenario? {
        val titles = when (trip.kind) {
            TripKind.ToOffice -> office
            TripKind.ToHome -> home
            TripKind.Other -> {
                val name = trip.name.trim()
                val text = name.lowercase()
                rules.firstOrNull { r -> r.keywords.any { it.lowercase() in text } || r.test?.invoke(name) == true }?.titles
            }
        } ?: return null
        val pool = shared.filter { it.title in titles }
        // 같은 목적지에는 같은 상대가 나오게 이름으로 고른다
        return pool.takeIf { it.isNotEmpty() }?.let { it[Math.floorMod(trip.name.hashCode(), it.size)] }
    }
}
