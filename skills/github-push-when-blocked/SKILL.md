---
name: github-push-when-blocked
description: 当 github.com 连不上导致 git push 失败时，用 GitHub API 通道把改动推上去。当用户说「push 失败」「推不上 GitHub」「Failed to connect to github.com」「Could not connect to server」，或 git push 报 443 超时但 gh 命令却能用时使用。核心是判断「github.com 被墙但 api.github.com 通」这个典型分裂，然后用 Git Data API 走一次提交。
agent_created: true
---

# github.com 被墙时如何把改动推上去

## 典型症状（先确诊）

```
$ git push origin main
fatal: unable to access 'https://github.com/user/repo.git/':
       Failed to connect to github.com:443 after 21123 ms: Could not connect to server
```

但 **`gh` 命令却完全正常**。这是国内很常见的**分裂式阻断**。

### 诊断三连

```bash
curl -s -o /dev/null -w "github.com     -> %{http_code}\n" --max-time 8 https://github.com
curl -s -o /dev/null -w "api.github.com -> %{http_code}\n" --max-time 8 https://api.github.com
curl -s -o /dev/null -w "codeload.github.com -> %{http_code}\n" --max-time 8 https://codeload.github.com
gh api user --jq '.login'
```

实测过的几种组合：

| github.com | api.github.com | 说明 |
|---|---|---|
| `000` | `200` | **典型分裂**：git push 不行，但 API 能走 → 用本文方案 |
| `200` | `200` | 正常，直接 `git push` |
| `000` | `000` | 整体断网，等网络恢复 |

> ⚠️ **是间歇性的**。实测同一台机器在 20 分钟内 `github.com` 时通时断
> （`200 → 000 → 200`）。**先重试几次再判定**，别急着上绕行方案。

---

## 方案 A：用 GitHub API 推送（推荐，最可靠）

`api.github.com` 通就能用。原理是走 **Git Data API** 手工造一次提交：

```
GET   /repos/{owner}/{repo}/git/ref/heads/{branch}     → 取当前 HEAD
GET   /repos/{owner}/{repo}/git/commits/{sha}          → 取 base tree
POST  /repos/{owner}/{repo}/git/blobs                  → 每个文件建 blob
POST  /repos/{owner}/{repo}/git/trees                  → 建新 tree（base_tree + 新条目）
POST  /repos/{owner}/{repo}/git/commits                → 建新 commit
PATCH /repos/{owner}/{repo}/git/refs/heads/{branch}    → 移动分支指针
```

### 现成脚本

```python
#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""当 github.com 被墙、git push 不可用时，用 GitHub API 推送文件。
用法: python gh_api_push.py <repo_dir> <owner/repo> <branch> <message_file> <file1> [file2...]
"""
import base64, json, subprocess, sys, urllib.error, urllib.request

API = "https://api.github.com"
TOKEN = subprocess.run(["gh", "auth", "token"], capture_output=True,
                       text=True, shell=True).stdout.strip()
if not TOKEN:
    raise SystemExit("无法获取 gh token，先跑 gh auth login")


def call(method, path, payload=None):
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(API + path, data=data, method=method)
    req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("Content-Type", "application/json")
    req.add_header("User-Agent", "gh-api-push")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            body = r.read().decode("utf-8")
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        raise SystemExit("HTTP %s on %s %s\n%s"
                         % (e.code, method, path, e.read().decode("utf-8", "replace")))


repo_dir, slug, branch, msg_file = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
files = sys.argv[5:]

message = open(msg_file, encoding="utf-8").read().strip()

head = call("GET", "/repos/%s/git/ref/heads/%s" % (slug, branch))["object"]["sha"]
base_tree = call("GET", "/repos/%s/git/commits/%s" % (slug, head))["tree"]["sha"]
print("base: %s / tree %s" % (head[:7], base_tree[:7]))

entries = []
for rel in files:
    raw = open("%s/%s" % (repo_dir.rstrip("/"), rel), "rb").read()
    blob = call("POST", "/repos/%s/git/blobs" % slug, {
        "content": base64.b64encode(raw).decode("ascii"), "encoding": "base64"})
    entries.append({"path": rel.replace("\\", "/"), "mode": "100644",
                    "type": "blob", "sha": blob["sha"]})
    print("  blob %-45s %7d bytes" % (rel, len(raw)))

tree = call("POST", "/repos/%s/git/trees" % slug,
            {"base_tree": base_tree, "tree": entries})["sha"]
commit = call("POST", "/repos/%s/git/commits" % slug,
              {"message": message, "tree": tree, "parents": [head]})["sha"]
call("PATCH", "/repos/%s/git/refs/heads/%s" % (slug, branch),
     {"sha": commit, "force": False})
print("\n✅ 推送成功 %s" % commit)
print("   https://github.com/%s/commit/%s" % (slug, commit))
```

调用：

```bash
python gh_api_push.py "D:\\myrepo" "owner/repo" "main" "D:\\msg.txt" \
  "README.md" "docs/新文档.md" "raw/数据.txt"
```

**优点**：
- 一次提交包含全部文件（不是 N 个零碎提交）
- 支持中文路径（JSON 里直接放字符串，不用 URL 编码）
- 支持二进制文件（base64）

### ⚠️ 副作用：本地 commit 与远端 SHA 会不一致

即使内容完全相同，**API 造出的 commit SHA 也和本地 `git commit` 的不一样**
（GitHub 会在 commit 对象里加额外头部，实测无法在本地复现）。

结果：`git status` 会显示 `ahead 1`，下次 `git push` 会被判为非快进。

**恢复网络后的对齐命令**：

```bash
git fetch origin && git reset --hard origin/main
```

（因为两边 tree 完全相同，这个 reset 不会丢任何内容。）

**先验证 tree 一致再放心 reset**：

```bash
git rev-parse HEAD^{tree}                                     # 本地
gh api repos/OWNER/REPO/git/commits/REMOTE_SHA --jq .tree.sha  # 远端
```

---

## 方案 B：改用 SSH over 443（条件满足时更省事）

GitHub 提供 `ssh.github.com:443`，有时能绕过对 `github.com:443` 的阻断。

```bash
ssh -T -p 443 git@ssh.github.com        # 测试连通与认证
```

`~/.ssh/config` 里可以固化成：

```
Host github.com
  Hostname ssh.github.com
  Port 443
  User git
  IdentityFile ~/.ssh/your_key
  IdentitiesOnly yes
  StrictHostKeyChecking accept-new
```

然后 `git remote set-url origin git@github.com:owner/repo.git` 即可。

**⚠️ 两个前提**：

1. **SSH key 必须属于该仓库的所有者/协作者**。
   实测踩过：本机 `~/.ssh/config` 把 github.com 指向了一个 key，
   认证成功但身份是 `zcstudy`，而仓库属于 `openclawzhangchong`
   → `ERROR: Permission to openclawzhangchong/repo.git denied to zcstudy`
2. 有些网络连 `ssh.github.com:443` 也一起封

> 判断认证身份：`ssh -T -p 443 git@ssh.github.com` 会回
> `Hi <username>! You've successfully authenticated...`

---

## 方案 C：什么都不做，等网络恢复

实测 github.com 的阻断是间歇性的。如果改动不急，**先 `git push` 重试几次**
（每次间隔 15 秒），经常第 2~5 次就通了。

---

## 选择顺序

1. **先重试 `git push` 3~5 次**（间歇性阻断，可能自己就好了）
2. 不行 → 看 `ssh -T -p 443 git@ssh.github.com` 的认证身份对不对
   - 对 → 用方案 B
   - 不对 → 用方案 A（API）
3. 都不行 → 等网络

---

## 其他注意

- **`gh api` 走的是 `api.github.com`**，和 `git push` 走 `github.com` 是两条路，
  所以经常出现「push 失败但 `gh` 一切正常」
- API 方式推送后，**记得把本地状态记清楚**（哪个 commit 是远端的），
  否则下次推送会困惑
- 别用 `git push --force` 去"修复"分叉——先用上面那条 `reset --hard` 对齐
