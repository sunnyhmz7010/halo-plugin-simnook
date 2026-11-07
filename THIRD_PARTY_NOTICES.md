# 第三方来源

## SIMerald

- 来源：https://github.com/BurningSky0306/SIMerald
- 固定提交：92f4223aaba48abcc2337c85b73978d0d2b182d9
- 0.2.0 的展示功能对照上游 v0.2.0（4e5f15a510a7c8af4a7c8f06b160690e35c34afa），同步了消费分类和币种预设。
- 0.3.0 以同一提交的中文模板为基准，将支付账户关系、有效期、首购日期、消费折算、续费紧急度和数据检查迁入 Halo。
- 本次双语适配固定对照上游 v0.3.0（471ca764d19fcf0c0b5115d8cdd662fa00ba9dce），移植语言包、语言选择与标准字段翻译；Notion 数据、独立登录和演示模式仍由 Halo 机制替代。
- 许可证：MIT，完整声明保留在 frontend/LICENSE。
- Copyright (c) 2026 Tokinx
- Copyright (c) 2026 BurningSky0306 and SIMerald contributors
- 移植：公开 Vue 前台；移除独立管理、登录与 Notion 服务连接；API、路由、资源路径和品牌适配 Halo。
- 卡片墙与页面样式、地图和图表来自上游，不作为本插件原创界面声明。

## Vue I18n

- 前台和 Console 双语文本使用 Vue I18n 11.4.16，与上游 v0.3.0 的依赖版本一致。
- Copyright (c) 2020 kazuya kawaguchi，MIT 许可全文位于 `src/main/resources/META-INF/LICENSE-VUE-I18N.txt`，随 JAR 打包。

## Halo 脚手架

- 使用官方 create-halo-plugin 1.7.2 生成，保留 Gradle、UI bundler 和项目许可。
- 目标 Halo 2.27.2；该版本源码内 UI 公共包版本为 2.27.0。

## 界面图标

- 菜单和页头使用项目现有 `@iconify-json/ri` 1.2.10 中的 `sim-card-2-line`，图标集为 Remix Icon 4.8.0，作者为 Remix Design。
- 插件 Logo 和 favicon 使用同一图形，调整为绿色背景、白色图形。
- 依据该包元数据采用 Apache-2.0，许可全文位于 `src/main/resources/META-INF/LICENSE-REMIXICON.txt`，随 JAR 打包。来源与许可见 [图标集仓库](https://github.com/cyberalien/RemixIcon) 和 [License](https://github.com/cyberalien/RemixIcon/blob/master/License)。

## 运营商识别图标

- 中国移动、中国联通、中国电信使用各自官网的 favicon，下载于 2026-11-07。来源为 [中国移动](https://www.10086.cn/favicon.ico)、[中国联通](https://www.10010.com/favicon.ico)、[中国电信](https://www.189.cn/favicon.ico)。
- Easy PASS、Club Sim、RedPocket、eSIM.GG、DITO 使用各自官网声明的 favicon，下载于 2026-11-07。Easy PASS 为中国电信澳门预付卡，使用其官网图标。来源为 [中国电信澳门](https://www.1888.com.mo/images/macau.ico)、[Club Sim](https://www.clubsim.com.hk/en/favicon-32x32.png)、[RedPocket 官方帮助中心](https://help.redpocket.com/hubfs/RP-Logo-Favicon.png)、[eSIM.GG](https://esim.gg/favicon.png)、[DITO](https://dito.ph/hubfs/favicon.png)。
- Vodafone、Orange、AT&T、Deutsche Telekom、O2、Telenor、Verizon、Airtel 的 SVG 路径来自 Simple Icons 16.36.0，按该版本元数据中的品牌色输出。项目使用 CC0-1.0，许可全文随图标打包。来源和品牌资料见 [官方数据文件](https://github.com/simple-icons/simple-icons/blob/16.36.0/data/simple-icons.json)。
- 图标用于标识对应运营商，品牌名称与商标权归各自权利人。品牌使用说明见 [Simple Icons 声明](https://github.com/simple-icons/simple-icons/blob/16.36.0/DISCLAIMER.md)。
