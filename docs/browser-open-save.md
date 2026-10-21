# 浏览器 Open / Save

GAALOPWeb 与 QuantumGAALOP 的脚本工具栏提供 **Open**、**Save**，生成代码区提供 **Save** 和 **Copy code**。

## 文件操作

Open 接受 `.gs`、`.txt` 和最多 1 MiB 的文件。读取成功后更新三个编辑区，并用文件名设置函数名。取消或解析失败时保留当前内容。

支持 File System Access API 的浏览器会保留用户授权的文件句柄，Save 写回该文件；首次保存时选择目标文件。其他浏览器使用文件输入和下载副本。源脚本与生成代码分别管理保存目标，生成代码使用本次成功运行的函数名和语言扩展名。

保存位置由用户在文件选择器中确定。自动使用源 `.gs` 所在目录属于后续目录访问功能。原生文件访问适用于浏览器支持的安全上下文，参见 [Chrome 文件访问说明](https://developer.chrome.com/docs/capabilities/web-apis/file-system-access)。

## 实现

- `frontend/src/fileAccess.js`：文件选择、授权、写入和下载回退。
- `fileDialogs.js`：下载文件名输入与反馈。
- `FileOpenButton.vue`、`FileSaveButton.vue`：两个工作台共享的按钮。

脚本按代数草稿绑定文件记录。打开另一份脚本或从 Hub 导入后清理旧绑定；生成代码按代数、函数名及扩展名绑定保存目标。句柄保存在页面内存中，刷新后重新选择目标，浏览器草稿继续保留。

写入成功以关闭文件流为准。取消、拒绝授权和写入失败分别处理；文件操作期间阻止冲突操作，空生成结果禁用 Save。

## 验证

`node scripts/test-file-access.mjs` 覆盖 15 组情景，包括真实 UTF-8 临时文件读写、首次保存与写回、取消、拒绝授权、流异常、下载回退、输出身份隔离和并发保护。`test-script-file.mjs`、`test-quantum.mjs`、`test-workspace-import.mjs` 与 `test-workspace-navigation.mjs` 覆盖脚本往返及工作区集成。

浏览器检查覆盖脚本打开、函数名更新、下载副本和生成代码文件名。原生选择器分支通过模拟句柄和真实磁盘文件的契约测试验证。
