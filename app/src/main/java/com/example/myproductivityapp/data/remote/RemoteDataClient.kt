package com.example.myproductivityapp.data.remote

/**
 * Android 业务层只依赖这个最小接口。
 * 后端可以是站内 FastAPI，也可以将来换成其他服务，不再绑死 CloudBase。
 */
interface RemoteDataClient {
    suspend fun health(): Boolean
    suspend fun list(table: String): List<Map<String, Any?>>
    suspend fun add(table: String, data: Map<String, Any?>): String
    suspend fun update(table: String, docId: String, data: Map<String, Any?>)
    suspend fun delete(table: String, docId: String)
}
