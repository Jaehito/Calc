package com.calc.expense

import org.junit.Assert.assertEquals
import org.junit.Test

class NotionIdsTest {

    private val id = "27b8f4a19c3d4e5f8a0b1c2d3e4f5a6b"

    @Test fun `맨 ID 그대로`() = assertEquals(id, NotionIds.normalize(id))

    @Test fun `대문자는 소문자로`() = assertEquals(id, NotionIds.normalize(id.uppercase()))

    @Test fun `대시가 있는 UUID`() {
        val dashed = "27b8f4a1-9c3d-4e5f-8a0b-1c2d3e4f5a6b"
        assertEquals(id, NotionIds.normalize(dashed))
    }

    @Test fun `URL에서 추출`() =
        assertEquals(id, NotionIds.normalize("https://www.notion.so/myws/$id"))

    @Test fun `쿼리스트링의 뷰 ID는 무시`() {
        val viewId = "ffffffffffffffffffffffffffffffff"
        assertEquals(id, NotionIds.normalize("https://www.notion.so/myws/$id?v=$viewId"))
    }

    @Test fun `제목이 붙은 URL`() =
        assertEquals(id, NotionIds.normalize("https://www.notion.so/myws/지출-$id?pvs=4"))

    @Test fun `앞뒤 공백`() = assertEquals(id, NotionIds.normalize("  $id  "))

    @Test fun `해석 불가한 값은 그대로 반환`() =
        assertEquals("garbage", NotionIds.normalize("garbage"))

    @Test fun `빈 문자열`() = assertEquals("", NotionIds.normalize("   "))
}
