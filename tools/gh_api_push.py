#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
通过 GitHub Git Data API 推送文件（当 github.com 被墙、git push 不可用时的备用通道）。

用法: python gh_api_push.py <repo_dir> <owner/repo> <branch> <message_file> <file1> <file2> ...
"""
import base64
import json
import subprocess
import sys
import urllib.error
import urllib.request

API = "https://api.github.com"


def get_token():
    out = subprocess.run(["gh", "auth", "token"], capture_output=True, text=True, shell=True)
    tok = out.stdout.strip()
    if not tok:
        raise SystemExit("无法获取 gh token: " + out.stderr)
    return tok


TOKEN = get_token()


def call(method, path, payload=None):
    url = API + path
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("Content-Type", "application/json")
    req.add_header("User-Agent", "gh-api-push")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            body = r.read().decode("utf-8")
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")
        raise SystemExit("HTTP %s on %s %s\n%s" % (e.code, method, path, detail))


def main():
    repo_dir, slug, branch = sys.argv[1], sys.argv[2], sys.argv[3]
    msg_file = sys.argv[4]
    files = sys.argv[5:]

    with open(msg_file, "r", encoding="utf-8") as f:
        message = f.read().strip()

    # 1. 取当前分支头
    ref = call("GET", "/repos/%s/git/ref/heads/%s" % (slug, branch))
    head_sha = ref["object"]["sha"]
    print("当前 HEAD: %s" % head_sha[:7])

    # 2. 取当前 tree
    head_commit = call("GET", "/repos/%s/git/commits/%s" % (slug, head_sha))
    base_tree = head_commit["tree"]["sha"]
    print("base tree: %s" % base_tree[:7])

    # 3. 为每个文件创建 blob
    tree_entries = []
    for rel in files:
        full = "%s/%s" % (repo_dir.rstrip("/"), rel)
        with open(full, "rb") as f:
            raw = f.read()
        blob = call("POST", "/repos/%s/git/blobs" % slug, {
            "content": base64.b64encode(raw).decode("ascii"),
            "encoding": "base64",
        })
        tree_entries.append({
            "path": rel.replace("\\", "/"),
            "mode": "100644",
            "type": "blob",
            "sha": blob["sha"],
        })
        print("  blob %-50s %6d bytes  %s" % (rel, len(raw), blob["sha"][:7]))

    # 4. 建新 tree
    new_tree = call("POST", "/repos/%s/git/trees" % slug, {
        "base_tree": base_tree,
        "tree": tree_entries,
    })
    print("new tree: %s" % new_tree["sha"][:7])

    # 5. 建新 commit
    new_commit = call("POST", "/repos/%s/git/commits" % slug, {
        "message": message,
        "tree": new_tree["sha"],
        "parents": [head_sha],
    })
    print("new commit: %s" % new_commit["sha"][:7])

    # 6. 移动分支指针
    call("PATCH", "/repos/%s/git/refs/heads/%s" % (slug, branch), {
        "sha": new_commit["sha"],
        "force": False,
    })
    print("\n✅ 推送成功: %s" % new_commit["sha"])
    print("   https://github.com/%s/commit/%s" % (slug, new_commit["sha"]))


if __name__ == "__main__":
    main()
