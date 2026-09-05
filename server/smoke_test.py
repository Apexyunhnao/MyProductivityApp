import json
import os
import urllib.request

BASE = os.getenv("GAS_STATION_URL", "http://127.0.0.1:8000").rstrip("/")
KEY = os.getenv("GAS_STATION_API_KEY", "gas-station-local")


def request(method: str, path: str, data=None):
    body = None if data is None else json.dumps(data).encode("utf-8")
    req = urllib.request.Request(
        BASE + path,
        data=body,
        method=method,
        headers={"Content-Type": "application/json", "X-API-Key": KEY},
    )
    with urllib.request.urlopen(req, timeout=5) as res:
        return json.loads(res.read().decode("utf-8"))


print("health:", request("GET", "/health"))
created = request("POST", "/api/delivery_tasks", {
    "customerName": "本地测试客户",
    "taskType": "DELIVERY",
    "deliveryQuantity": 1,
    "status": "PENDING",
    "updatedAt": 1,
})
object_id = created["id"]
print("created:", object_id)
print("list count:", len(request("GET", "/api/delivery_tasks")))
request("PATCH", f"/api/delivery_tasks/{object_id}", {"status": "COMPLETED", "updatedAt": 2})
request("DELETE", f"/api/delivery_tasks/{object_id}")
print("smoke test: OK")
