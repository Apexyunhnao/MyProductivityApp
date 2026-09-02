package com.example.myproductivityapp.data.remote

/**
 * 无服务器时的空实现（纯本地模式）。
 * 所有网络操作直接返回空结果，不发起任何请求。
 * 这样入口不再依赖服务器：断网/未配置服务器也能正常使用全部本地功能。
 */
class NoopRemoteDataClient : RemoteDataClient {
    override suspend fun health(): Boolean = false
    override suspend fun list(table: String): List<Map<String, Any?>> = emptyList()
    override suspend fun add(table: String, data: Map<String, Any?>): String = ""
    override suspend fun update(table: String, docId: String, data: Map<String, Any?>) = Unit
    override suspend fun delete(table: String, docId: String) = Unit
}
