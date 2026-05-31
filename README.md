# 煤气站配送管理系统

一个专为乡镇煤气站设计的员工配送记录管理应用

## 项目简介

这是一个使用 Kotlin 和 Jetpack Compose 构建的现代化 Android 应用，专门用于管理煤气站员工的配送记录、价格设置和统计报表。系统可以记录每位员工每次配送的煤气罐数量、类型和金额，并提供详细的统计分析功能。

## 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **最低 SDK**: Android 7.0 (API 24)
- **目标 SDK**: Android 14 (API 34)
- **构建工具**: Gradle 8.2
- **架构**: Material Design 3

## 项目结构

```
MyProductivityApp/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/myproductivityapp/
│   │       │   ├── MainActivity.kt
│   │       │   ├── data/
│   │       │   │   ├── AppDatabase.kt
│   │       │   │   ├── dao/
│   │       │   │   │   ├── EmployeeDao.kt
│   │       │   │   │   ├── DeliveryRecordDao.kt
│   │       │   │   │   └── PriceConfigDao.kt
│   │       │   │   └── model/
│   │       │   │       ├── Employee.kt
│   │       │   │       ├── DeliveryRecord.kt
│   │       │   │       ├── PriceConfig.kt
│   │       │   │       └── BottleType.kt
│   │       │   ├── viewmodel/
│   │       │   │   └── EmployeeViewModel.kt
│   │       │   └── ui/
│   │       │       ├── screens/
│   │       │       │   ├── HomeScreen.kt
│   │       │       │   ├── EmployeeScreen.kt
│   │       │       │   ├── AddRecordScreen.kt
│   │       │       │   ├── PriceSettingsScreen.kt
│   │       │       │   └── StatisticsScreen.kt
│   │       │       └── theme/
│   │       │           ├── Color.kt
│   │       │           ├── Theme.kt
│   │       │           └── Type.kt
│   │       ├── res/
│   │       │   └── values/
│   │       │       ├── strings.xml
│   │       │       └── themes.xml
│   │       └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
└── .gitignore
```

## 开始使用

### 前置要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 8 或更高版本
- Android SDK API 34

### 安装步骤

1. 克隆或下载此项目到本地
2. 使用 Android Studio 打开项目
3. 等待 Gradle 同步完成
4. 连接 Android 设备或启动模拟器
5. 点击运行按钮或使用快捷键 Shift+F10

## 功能特性

### 核心功能
- ✅ **员工管理** - 添加、编辑、删除员工信息（姓名、编号、电话）
- ✅ **配送记录** - 记录每次配送的详细信息
  - 支持四种煤气罐类型：重瓶、租瓶、换瓶、新瓶
  - 自动计算总金额（数量 × 单价）
  - 可添加备注信息
- ✅ **价格设置** - 灵活的价格管理
  - 为每种煤气罐类型设置默认价格
  - 添加记录时可临时调整价格
- ✅ **统计报表** - 多维度数据分析
  - 按员工统计：查看每位员工的配送次数、总数量、总金额
  - 按类型统计：查看各类型煤气罐的配送数量和金额
  - 全部记录：查看所有配送记录的详细信息

### 技术特性
- ✅ 现代化 Material Design 3 界面
- ✅ 支持深色模式
- ✅ 动态颜色主题（Android 12+）
- ✅ Jetpack Compose 声明式 UI
- ✅ Room 数据库本地存储
- ✅ MVVM 架构模式

## 使用说明

### 首次使用

1. **添加员工信息**
   - 进入"员工"标签页
   - 点击右下角的"+"按钮
   - 填写员工姓名、编号和联系电话
   - 点击"确认"保存

2. **设置煤气罐价格**
   - 进入"价格"标签页
   - 为每种类型的煤气罐设置默认价格
   - 重瓶、租瓶、换瓶、新瓶各自独立设置

3. **添加配送记录**
   - 进入"记录"标签页
   - 选择配送员工
   - 选择煤气罐类型
   - 输入配送数量
   - 确认或修改单价（系统会自动填充默认价格）
   - 系统自动计算总金额
   - 可选填写备注信息
   - 点击"保存记录"

4. **查看统计报表**
   - 进入"统计"标签页
   - 切换不同的统计视图：
     - **按员工**：查看每位员工的业绩统计
     - **按类型**：查看各类型煤气罐的销售情况
     - **全部记录**：查看所有配送记录明细

## 开发计划

- [ ] 数据导出功能（Excel/CSV）
- [ ] 按日期范围筛选记录
- [ ] 数据备份与恢复
- [ ] 打印配送单据
- [ ] 云端同步支持

## 构建说明

### Debug 版本
```bash
./gradlew assembleDebug
```

### Release 版本
```bash
./gradlew assembleRelease
```

## 依赖库

- AndroidX Core KTX
- AndroidX Lifecycle
- Jetpack Compose BOM
- Material 3
- Room Database (数据持久化)
- Navigation Compose (页面导航)
- Kotlin Coroutines (异步处理)
- JUnit (测试)
- Espresso (UI 测试)

## 许可证

此项目仅供学习和个人使用。

## 联系方式

如有问题或建议，欢迎提出 Issue。
