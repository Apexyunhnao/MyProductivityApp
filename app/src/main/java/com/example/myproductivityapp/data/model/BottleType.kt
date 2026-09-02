package com.example.myproductivityapp.data.model

enum class BottleType(val displayName: String) {
    HEAVY("重瓶"),
    RENTAL("租瓶"),
    EXCHANGE("对瓶"),
    NEW("新瓶"),
    SMALL("小瓶")
}

/** 瓶型辅助：区分内置 5 种与用户自定义类型（自定义 = price_config 里的任意其他名字）。 */
object BottleTypes {
    val builtinNames: Set<String> = BottleType.values().map { it.name }.toSet()
    val builtinDisplayNames: Set<String> = BottleType.values().map { it.displayName }.toSet()

    fun isBuiltin(bottleType: String): Boolean = bottleType in builtinNames

    /** 内置类型显示中文名，自定义类型原样显示。 */
    fun displayName(bottleType: String): String =
        BottleType.values().find { it.name == bottleType }?.displayName ?: bottleType
}
