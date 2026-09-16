# QRA 资源目录 Grover 脚本实测（2026-09-16）

输入来自 F:/IDEA_project/QRA_resource 根目录的 5 个 Grover_n*.txt 文件，源代码原样提交，在可视化输入区额外指定 :res;。根目录没有 7–9 qubit 测试脚本。

环境：Docker Linux x86_64，2 CPU，内存上限 2 GiB，无额外 swap；Java 17，-Xmx768m。通过实际 REST API 生成 Java 结果代码及概率数据，未修改计算代码。

| 文件 | 目标态总概率 | 请求耗时（秒） | Java 累计峰值（MiB） | 容器累计峰值（MiB） |
|---|---:|---:|---:|---:|
| Grover_n2_s2.txt | 1.0000000000 | 6.65 | 179.3 | 237.2 |
| Grover_n3_s5_6.txt | 1.0000000000 | 0.14 | 179.6 | 238.1 |
| Grover_n4_s8.txt | 0.9084472656 | 0.16 | 180.2 | 238.6 |
| Grover_n5_s4.txt | 0.8969365358 | 0.21 | 186.0 | 244.6 |
| Grover_n6_s17.txt | 0.9635154816 | 26.87 | 287.1 | 347.2 |

全部通过：按各脚本标记的目标态和迭代次数，用 Grover 振幅放大公式独立计算每个基态的预期概率，最大逐项误差约 2.36e-14。总概率均约为 1，残差均小于 1e-16。OOM 和 OOM kill 计数均为 0。

峰值为同一服务顺序执行至该请求结束时的累计值，不是每个脚本独立启动的峰值。Java 指标取 VmHWM，容器指标取 cgroup memory.peak（含容器缓存等）。首个请求包含冷初始化，不能直接按耗时比较算法规模。6 qubit 请求耗时约 26.87 秒，当前样本中时间开销比内存更突出。

这些结果仅覆盖提供的 2–6 qubit 原始脚本，不能用来推断 9 qubit Grover 的资源要求，也不包含并发负载或宿主系统占用。

复现脚本：native/qra/benchmark-resource-scripts.py。挂载约定同 benchmark-memory.py，另将原始资源目录只读挂载到 /resources。

原始记录：native/qra/target/docker/resource-results.json（含源文件 SHA256）；同目录 Grover_n*-request.json、Grover_n*-response.json 及 resource-api.log。

测试容器 gaalop-qra-resource-2gb-20260916 已停止，保留供用户删除；复用镜像 gaalop-qra-memory-python:20260916。
