# 美颜免费商用组件筛选

核对日期：2026-11-13。

## 推荐候选

| 组件 | 可用能力 | 授权 | 本项目接入状态 |
| --- | --- | --- | --- |
| Android GPUImage 2.1.0 | OpenGL 实时滤镜、双边模糊、锐化、色温、曲线、LUT | Apache 2.0，允许商用；分发时保留许可、版权及适用声明 | 已接入真实 GPU 滤镜渲染，滤镜强度与美肤、美型、美体共用输出 |
| MediaPipe Face Landmarker / FaceMesh（Tasks Vision 0.10.35） | 密集人脸关键点，可用作美型定位基础 | SDK 与核对的 FaceMesh V2 模型卡标注 Apache 2.0 | 已替换 ML Kit 检测；眼部、嘴角、下巴、额头使用测量关键点 |
| MediaPipe Pose Landmarker / BlazePose GHUM（Tasks Vision 0.10.35） | 身体关键点，可用作美体定位基础 | SDK 与核对的 BlazePose GHUM 3D 模型卡标注 Apache 2.0 | 已接入身体定位及基础瘦腰、窄肩、收胯、瘦腿；未识别、部位不全或姿态不可靠时不变形 |

这些组件无需购买商业 SDK 授权。脸部、身体模型随包离线运行；两种直播管线和美颜预览共用同一处理器。美体是项目实现的局部变形，不包含商业 SDK 的人物分割保护或运动稳定算法；身体边缘附近的背景也可能发生局部形变，复杂动作仍需真机评估。授权、版本和模型 SHA-256 已记录在应用 assets/beauty/NOTICE.txt，完整许可随包分发。

## GPUPixel 暂列候选

GPUPixel 主项目为 Apache 2.0，提供美肤和人脸变形滤镜。当前源码的人脸检测器依赖随包分发的 Mars Face Kit 原生库与模型；其独立许可覆盖范围在上游 #253 中仍有人请求澄清。本次没有把该整套依赖加入应用，也不据主仓库许可就断言所有二进制、模型均已核对完成。

## 官方来源

- Android GPUImage 功能与许可：<https://github.com/wasabeef/android-gpuimage>
- MediaPipe SDK 许可：<https://github.com/google-ai-edge/mediapipe/blob/master/LICENSE>
- Face Landmarker 说明：<https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker>
- FaceMesh V2 模型卡：<https://storage.googleapis.com/mediapipe-assets/Model%20Card%20MediaPipe%20Face%20Mesh%20V2.pdf>
- Pose Landmarker 说明：<https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker>
- BlazePose GHUM 模型卡：<https://storage.googleapis.com/mediapipe-assets/Model%20Card%20BlazePose%20GHUM%203D.pdf>
- GPUPixel 主项目许可：<https://github.com/pixpark/gpupixel/blob/main/LICENSE>
- GPUPixel 人脸依赖源码：<https://github.com/pixpark/gpupixel/blob/main/src/face_detector/face_detector.cc>
- GPUPixel 依赖许可澄清：<https://github.com/pixpark/gpupixel/issues/253>

## 本次已完成

美颜面板使用统一圆角方框、紫色选中状态和按功能区分的原创矢量图标。分类为美肤、美型、美体、滤镜；新增琥珀、黑白、暗角滤镜。所有参数按账号持久化，恢复单个分类不影响其他分类；关闭美颜和按住对比使用原始画面。
