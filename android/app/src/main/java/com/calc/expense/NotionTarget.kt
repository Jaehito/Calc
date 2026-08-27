package com.calc.expense

/** Notion DB 하나를 가리키는 좌표. 곳간마다 DB 가 다르므로 클라이언트는 이것만 받는다. */
data class NotionTarget(
    val token: String,
    val databaseId: String,
    val nameProp: String,
    val priceProp: String,
    val dateProp: String,
)
