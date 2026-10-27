# 昨日 M00 / M01 清理记录

日期：2026-10-27。授权来源：用户本次书面指令“如果仓库里有昨天的旧m00、m01请帮我删除掉”。

本次从当前 main `2b1a5ad` 重新实施 M00；昨日 M01 草稿已在旧分支被 revert，不 cherry-pick 任何旧业务迁移。

|本地 worktree（已移除）|本地分支（已删除）|恢复提交|
|---|---|---|
|`D:/biancheng/RunRunCampus-worktrees/m00-baseline`|`chore/phase1-baseline`|`f3d4c2b1249e0dcf5738f4531e1c31159dcebd14`|
|`D:/biancheng/RunRunCampus-worktrees/integration-m00`|`integration/m00`|`a175f8b5337bb8491bfefe151704230c4bd2d98d`|
|`D:/biancheng/RunRunCampus-worktrees/m01-migrations`|`chore/m01-migrations`|`6f99718b04ed31ffc36365a25e5bc39a123a7dc7`|

远端 `origin/chore/phase1-baseline`、`origin/chore/m01-migrations` 已通过 `git push origin --delete chore/phase1-baseline chore/m01-migrations` 删除，退出码 0。第一次因网络失败未完成，随后重试成功；不重写 main 历史。

删除前为三个分支分别创建 Git bundle，存于 `D:/biancheng/work/backups/RunRunCampus/20261027-110127-old-m00-m01/`，对应关系见其中 `MANIFEST.tsv`。可用 `git bundle verify <bundle>` 校验，再从 bundle fetch 原分支恢复；这些 bundle 只保存 Git 对象，不保存构建缓存、未跟踪文件或运行日志。

从仓库移除昨日两份报告：

- `docs/reports/M00-baseline-audit.md`
- `docs/reports/M00-contract-inventory.md`

这两份报告的可恢复副本、原绝对路径及 SHA-256 清单位于主工作区忽略目录 `work/backups/RunRunCampus/20261027-111349-old-m00-reports/`。也可从原 main `2b1a5ad` 恢复。旧报告中历史兼容和旧迁移验证不能作为本次新版完成证据。

未改写或删除旧 V1–V43 脚本、主工作区旧交接代码或历史生产制品；未访问旧数据库或生产环境。备份与缓存不提交到 Git。
