#!/usr/bin/env python3
"""
A股实时行情 — 多源聚合脚本
  Sina: 指数 + 个股      (实时，稳定)
  东财: 板块排名 + 北向资金 (注意频率)
  用法: python3 stock_scraper.py <cmd>
"""
import requests
import json
import time

SINA_HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://finance.sina.com.cn/',
}
EM_HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://data.eastmoney.com/',
}

# === Sina 数据 ===
def index_realtime():
    """三大指数 (Sina)"""
    r = requests.get(
        'https://hq.sinajs.cn/list=s_sh000001,s_sz399001,s_sz399006',
        headers=SINA_HEADERS, timeout=10)
    results = []
    for line in r.text.strip().split('\n'):
        if not line.strip(): continue
        parts = line.split('"')[1].split(',')
        results.append({
            "name": parts[0],
            "price": float(parts[1]),
            "change": float(parts[2]),
            "change_pct": float(parts[3]),
        })
    return results

def stock_realtime(code):
    """个股实时 (Sina)"""
    prefix = "sh" if code.startswith("6") else "sz"
    r = requests.get(f'https://hq.sinajs.cn/list={prefix}{code}',
                     headers=SINA_HEADERS, timeout=10)
    parts = r.text.split('"')[1].split(',')
    if len(parts) < 32:
        return {"error": "无数据"}
    return {
        "code": code, "name": parts[0],
        "open": float(parts[1]), "close_y": float(parts[2]),
        "price": float(parts[3]), "high": float(parts[4]), "low": float(parts[5]),
        "volume": int(parts[8]), "turnover": float(parts[9]),
        "change": round(float(parts[3]) - float(parts[2]), 2),
        "change_pct": round((float(parts[3]) - float(parts[2])) / float(parts[2]) * 100, 2),
    }

# === 东方财富数据 ===
def _em_api(url):
    r = requests.get(url, headers=EM_HEADERS, timeout=10)
    try: return r.json()
    except:
        r.encoding = 'gbk'
        return r.json()

def sector_rank(stype="industry", top_n=15):
    """行业/概念板块排名"""
    fs = "m:90+t:2" if stype == "industry" else "m:90+t:3"
    url = f"https://push2.eastmoney.com/api/qt/clist/get?fid=f3&po=1&pz={top_n}&pn=1&np=1&fltt=2&invt=2&fs={fs}&fields=f3,f12,f14,f104,f105"
    data = _em_api(url)
    results = []
    for item in data.get("data", {}).get("diff", []):
        results.append({
            "name": item.get("f14", ""),
            "change_pct": item.get("f3", 0),
            "rise": item.get("f104", 0),
            "fall": item.get("f105", 0),
        })
    return results

def north_flow(days=5):
    """北向资金"""
    url = f"https://push2.eastmoney.com/api/qt/kamt.kline/get?fields1=f1,f2,f3,f4&fields2=f51,f52,f53&klt=101&lmt={days}"
    data = _em_api(url)
    results = []
    for item in data.get("data", {}).get("klines", []):
        parts = item.split(",")
        results.append({
            "date": parts[0],
            "net_in": round(float(parts[1]) / 10000, 2),
        })
    return results

def stock_list(top_n=20):
    """涨幅榜"""
    fs = "m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23"
    url = f"https://push2.eastmoney.com/api/qt/clist/get?fid=f3&po=0&pz={top_n}&pn=1&np=1&fltt=2&invt=2&fs={fs}&fields=f2,f3,f12,f14"
    data = _em_api(url)
    return [{"code": i["f12"], "name": i["f14"], "price": i["f2"], "change_pct": i["f3"]}
            for i in data.get("data", {}).get("diff", [])]


# ============ CLI ============
if __name__ == "__main__":
    import sys
    t0 = time.time()
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""

    try:
        if cmd == "index":
            for r in index_realtime():
                print(f'{r["name"]}: {r["price"]:.2f}  {r["change"]:+.2f}  {r["change_pct"]:+.2f}%')
        elif cmd == "sector":
            print(f"{'板块':<12} {'涨幅%':>8}  {'涨':>4}  {'跌':>4}\n" + "-"*38)
            for r in sector_rank("industry", 15):
                print(f"{r['name']:<14} {r['change_pct']:>+7.2f}  {r['rise']:>3}只  {r['fall']:>3}只")
        elif cmd == "concept":
            for r in sector_rank("concept", 15):
                print(f"{r['name']:<12} {r['change_pct']:>+7.2f}%")
        elif cmd == "north":
            for r in north_flow():
                print(f'{r["date"]}  净流入: {r["net_in"]:+.2f} 亿')
        elif cmd.startswith("stock:"):
            r = stock_realtime(cmd.split(":")[1])
            if "error" in r: print(r["error"])
            else: print(json.dumps(r, ensure_ascii=False, indent=2))
        elif cmd == "list":
            for r in stock_list():
                print(f"{r['code']} {r['name']:<10} {str(r['price']):>8} {r['change_pct']:>+7.2f}%")
        else:
            print("stock_scraper.py index|sector|concept|north|list|stock:CODE")
        print(f"\n# {time.time()-t0:.1f}s", file=sys.stderr)
    except Exception as e:
        print(f"ERROR: {e}")
