package com.smalltoktok.pleos.ui

/**
 * 아쉬운 대화 "이런 순간이었나요?" 장면 칩 풀 (폰 앱 smalltalktalk apps/mobile/src/features/reflection/sceneStarters.ts와 같다).
 * 2030이 자주 겪는 막히는 순간 + 그때의 구체적인 느낌까지 적어서 "맞아, 나도 그랬어" 하고 이어 말하게 한다.
 * 누르면 첫마디만 채우고, 나머지는 내 이야기로 이어 말한다.
 */
data class SceneStarter(val label: String, val starter: String)

val SCENE_STARTERS = listOf(
    SceneStarter("엘리베이터 단둘이", "엘리베이터에서 단둘이 타게 됐는데, 무슨 말을 꺼내야 할지 머리가 하얘졌어요…"),
    SceneStarter("주말 뭐 했냐는 질문", "주말에 뭐 했냐는 질문에 \"그냥 쉬었어요\"만 하고 말이 뚝 끊겼어요…"),
    SceneStarter("회식 옆자리", "회식 자리에서 옆 사람이랑 할 말이 떨어져서 괜히 휴대폰만 만지작거렸어요…"),
    SceneStarter("처음 만난 사람과 둘이", "처음 만난 사람이랑 둘만 남았는데, 어색한 침묵이 너무 길게 느껴졌어요…"),
    SceneStarter("팀 점심 대화", "팀 점심에서 다들 신나게 얘기하는데 끼어들 타이밍을 계속 놓쳤어요…"),
    SceneStarter("탕비실 마주침", "탕비실에서 다른 팀 사람과 마주쳤는데 인사만 하고 어색하게 서 있었어요…"),
    SceneStarter("새로 온 동료", "새로 온 동료에게 말을 걸고 싶었는데, 뭐라고 해야 할지 몰라 그냥 지나쳤어요…"),
    SceneStarter("소개팅 첫 10분", "소개팅에서 질문만 계속 던지다가 분위기가 면접처럼 돼버렸어요…"),
    SceneStarter("결혼식 하객 테이블", "결혼식 하객 테이블에 아는 사람이 없어서 밥만 먹고 조용히 나왔어요…"),
    SceneStarter("오랜만에 만난 동창", "오랜만에 만난 동창이 요즘 어떻게 지내냐고 물었는데 대답이 너무 짧았어요…"),
    SceneStarter("미팅 전 스몰토크", "미팅 시작 전에 날씨 얘기 한 마디 하고 나서 정적이 흘렀어요…"),
    SceneStarter("미용실 대화", "미용실에서 디자이너분이 말을 걸어 주셨는데 단답만 하다가 끝났어요…"),
    SceneStarter("동호회 뒤풀이", "동호회 뒤풀이에서 다들 친해 보이는데 나만 겉도는 느낌이었어요…"),
    SceneStarter("칭찬받았을 때", "칭찬을 들었는데 \"아, 아니에요\"만 하고 어색하게 웃고 말았어요…"),
    SceneStarter("퇴근길 같은 방향", "퇴근길에 같은 방향인 동료랑 걷게 됐는데 역까지 가는 길이 너무 길게 느껴졌어요…"),
    SceneStarter("같은 층 이웃", "같은 층 이웃이랑 엘리베이터를 기다리는데 눈을 어디 둬야 할지 몰랐어요…"),
    SceneStarter("화상회의 시작 전", "화상회의 시작 전에 다들 카메라만 켜고 기다리는데 아무도 말을 안 해서 숨이 막혔어요…"),
    SceneStarter("친구의 친구", "친구가 잠깐 자리를 비운 사이 친구의 친구랑 둘만 남아서 머릿속이 새하얘졌어요…"),
    SceneStarter("모임 자기소개", "모임에서 자기소개를 하는데 이름이랑 하는 일만 말하고 끝나서 아쉬웠어요…"),
    SceneStarter("동료의 농담", "동료가 농담을 던졌는데 웃기만 하고 받아치지 못해서 대화가 거기서 끝났어요…"),
    SceneStarter("명절 친척 질문", "명절에 친척 어른이 요즘 어떻게 지내냐고 물으셨는데 말문이 턱 막혔어요…"),
    SceneStarter("상사와 둘만 남은 자리", "회의가 끝나고 팀장님이랑 둘만 남았는데 무슨 말이라도 해야 할 것 같아 초조했어요…"),
    SceneStarter("카페 합석", "자리가 없어서 모르는 사람과 합석했는데 말을 걸까 말까 고민만 하다 끝났어요…"),
    SceneStarter("단톡방 말고 실제로", "단톡방에서는 잘 떠들던 사람을 실제로 만났더니 무슨 말을 해야 할지 모르겠더라고요…"),
)

/** 풀에서 n개를 무작위로 고른다 (방금 보여준 장면은 빼고) */
fun pickScenes(n: Int, exclude: List<SceneStarter> = emptyList()): List<SceneStarter> {
    val pool = SCENE_STARTERS.filter { it !in exclude }
    return (if (pool.size >= n) pool else SCENE_STARTERS).shuffled().take(n)
}
