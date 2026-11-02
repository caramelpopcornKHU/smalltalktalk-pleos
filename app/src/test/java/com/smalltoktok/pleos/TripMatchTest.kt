package com.smalltoktok.pleos

import com.smalltoktok.pleos.data.Scenario
import com.smalltoktok.pleos.vehicle.Trip
import com.smalltoktok.pleos.vehicle.TripKind
import com.smalltoktok.pleos.vehicle.TripMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripMatchTest {
    private val titles = listOf(
        "엘리베이터에서 단둘이", "월요일 아침 탕비실", "모임에서 처음 본 옆자리", "결혼식 하객 테이블에서", "러닝 크루 첫날",
        "사내 카페 줄에서", "합동 회식 옆자리", "회의 시작 5분 전", "새 팀원과 첫 점심", "미용실 의자에서",
        "단골 카페 사장님", "클라이밍장 쉬는 시간", "등산로 쉼터에서", "거래처 미팅 시작 전", "택시 뒷좌석에서",
        "아파트 엘리베이터 이웃", "출근길 지하철 지연", "새로 온 팀장님과 커피", "부장님과 점심 줄", "꽃집에서 선물 고르기",
        "스터디 모임 첫날",
    )
    private val shared = titles.mapIndexed { i, t -> Scenario("s$i", t, "", "", "", null, null) }

    private fun pick(name: String, kind: TripKind = TripKind.Other) = TripMatch.pick(Trip(name, "", kind, 20), shared)?.title

    @Test
    fun matchesPlaceNames() {
        assertEquals("미용실 의자에서", pick("준오헤어 강남점"))
        assertEquals("단골 카페 사장님", pick("스타벅스 역삼점"))
        assertEquals("스터디 모임 첫날", pick("책읽는 북카페"))
        assertEquals("등산로 쉼터에서", pick("북한산"))
        assertEquals("등산로 쉼터에서", pick("북한산국립공원"))
        assertEquals("러닝 크루 첫날", pick("여의도한강공원"))
        assertEquals("꽃집에서 선물 고르기", pick("소소플라워"))
        assertEquals("거래처 미팅 시작 전", pick("(주)한빛소프트 본사"))
    }

    @Test
    fun skipsCitiesAndUnknownPlaces() {
        assertNull(pick("부산"))
        assertNull(pick("서울역"))
    }

    @Test
    fun commuteUsesOfficeOrNeighborhood() {
        val office = setOf("월요일 아침 탕비실", "엘리베이터에서 단둘이", "회의 시작 5분 전", "새로 온 팀장님과 커피", "사내 카페 줄에서", "부장님과 점심 줄")
        assertTrue(pick("회사", TripKind.ToOffice) in office)
        assertTrue(pick("우리집", TripKind.ToHome) in setOf("아파트 엘리베이터 이웃", "단골 카페 사장님", "꽃집에서 선물 고르기"))
    }

    @Test
    fun sameDestinationSamePartner() {
        assertEquals(pick("회사", TripKind.ToOffice), pick("회사", TripKind.ToOffice))
    }
}
