package inu.timetable.enums;

/**
 * 사용자 행동 이벤트 종류. 자체 제품 분석용.
 * 새 이벤트를 추가할 때는 이 enum 에만 추가하면 집계/대시보드가 자동으로 포함한다.
 */
public enum AnalyticsEventType {
    SEARCH,               // 과목 검색 (label = 검색어)
    COMBINATION_GENERATE, // 시간표 조합 생성
    TIMETABLE_ADD,        // 시간표에 과목 추가 (label = 과목명)
    WISHLIST_ADD,         // 위시리스트에 과목 추가 (label = 과목명)
    COURSE_DETAIL_VIEW,   // 과목 상세 보기 (label = 과목명)
    LOGIN,                // 로그인
    REGISTER,             // 회원가입
    PAGE_VIEW             // 페이지 진입
}
