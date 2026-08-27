"""
Notion 지출 기록 CLI (표준 라이브러리만 사용 — pip install 불필요)

사용법:
    python notion_expense.py check              # 토큰/DB 연결 확인 + 속성 목록 출력
    python notion_expense.py add 커피 4500       # 지출 한 줄 추가
    python notion_expense.py add "점심 김밥" 6000 --category 식비

설정: 같은 폴더의 config.json 또는 환경변수 NOTION_TOKEN / NOTION_DB_ID
"""

import json
import os
import sys
import urllib.error
import urllib.request
from datetime import date
from pathlib import Path

NOTION_VERSION = "2022-06-28"
API = "https://api.notion.com/v1"
CONFIG_PATH = Path(__file__).with_name("config.json")


def load_config():
    cfg = {}
    if CONFIG_PATH.exists():
        cfg = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    token = os.environ.get("NOTION_TOKEN") or cfg.get("token")
    db_id = os.environ.get("NOTION_DB_ID") or cfg.get("database_id")
    if not token or not db_id:
        sys.exit(
            "설정이 없습니다.\n"
            f"  {CONFIG_PATH} 파일을 만들고 아래 내용을 넣으세요:\n"
            '  {"token": "ntn_...", "database_id": "...", '
            '"props": {"name": "이름", "price": "금액", "date": "날짜"}}'
        )
    props = cfg.get("props", {})
    return token, db_id, props


def request(method, path, token, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(
        f"{API}{path}",
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Notion-Version": NOTION_VERSION,
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")
        sys.exit(f"Notion API 오류 {e.code}: {detail}")
    except urllib.error.URLError as e:
        sys.exit(f"네트워크 오류: {e.reason}")


def cmd_check(token, db_id, props):
    db = request("GET", f"/databases/{db_id}", token)
    title = "".join(t.get("plain_text", "") for t in db.get("title", []))
    print(f"연결 성공 — DB: {title or '(제목 없음)'}\n")
    print("속성 목록:")
    for name, spec in db["properties"].items():
        print(f"  {name!r:20} -> {spec['type']}")

    print("\n필요한 속성 타입: title(이름) / number(금액) / date(날짜)")
    if props:
        print(f"\n현재 config.json 매핑: {props}")
        missing = [v for v in props.values() if v not in db["properties"]]
        if missing:
            print(f"  경고: DB에 없는 속성 -> {missing}")
        else:
            print("  매핑 이상 없음.")
    else:
        guess = {
            "name": next((n for n, s in db["properties"].items()
                          if s["type"] == "title"), None),
            "price": next((n for n, s in db["properties"].items()
                           if s["type"] == "number"), None),
            "date": next((n for n, s in db["properties"].items()
                          if s["type"] == "date"), None),
        }
        print("\nconfig.json 의 \"props\" 에 넣을 추천 매핑:")
        print(json.dumps(guess, ensure_ascii=False, indent=2))


def cmd_add(token, db_id, props, name, price, category=None):
    p_name = props.get("name", "이름")
    p_price = props.get("price", "금액")
    p_date = props.get("date", "날짜")

    payload = {
        "parent": {"database_id": db_id},
        "properties": {
            p_name: {"title": [{"text": {"content": name}}]},
            p_price: {"number": price},
            p_date: {"date": {"start": date.today().isoformat()}},
        },
    }
    if category and props.get("category"):
        payload["properties"][props["category"]] = {"select": {"name": category}}

    page = request("POST", "/pages", token, payload)
    print(f"기록 완료: {name} {price:,}원 ({date.today().isoformat()})")
    print(f"  {page.get('url', '')}")


def main():
    argv = sys.argv[1:]
    if not argv:
        sys.exit(__doc__)

    token, db_id, props = load_config()
    cmd = argv[0]

    if cmd == "check":
        cmd_check(token, db_id, props)
    elif cmd == "add":
        rest = argv[1:]
        category = None
        if "--category" in rest:
            i = rest.index("--category")
            category = rest[i + 1]
            rest = rest[:i] + rest[i + 2:]
        if len(rest) != 2:
            sys.exit('사용법: python notion_expense.py add "지출이름" 금액')
        name, raw_price = rest
        try:
            price = float(raw_price.replace(",", ""))
        except ValueError:
            sys.exit(f"금액이 숫자가 아닙니다: {raw_price}")
        cmd_add(token, db_id, props, name, price, category)
    else:
        sys.exit(f"알 수 없는 명령: {cmd}\n{__doc__}")


if __name__ == "__main__":
    main()
