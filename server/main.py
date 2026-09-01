from __future__ import annotations

import json
import os
import sqlite3
import time
import uuid
from pathlib import Path
from typing import Any

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, ConfigDict

BASE_DIR = Path(__file__).resolve().parent
DEFAULT_DB = BASE_DIR / "data" / "gas_station.db"
DB_PATH = Path(os.getenv("GAS_STATION_DB_PATH", str(DEFAULT_DB))).resolve()
API_KEY = os.getenv("GAS_STATION_API_KEY", "gas-station-local")

ALLOWED_RESOURCES = {
    "employees",
    "delivery_records",
    "price_config",
    "bottle_years",
    "delivery_tasks",
    "bottle_details",
    "products",
    "product_sale_items",
    "policies",
    "station_duties",
}

app = FastAPI(title="Gas Station Local Server", version="2.0")


class JsonObject(BaseModel):
    model_config = ConfigDict(extra="allow")


def now_ms() -> int:
    return int(time.time() * 1000)


def connect() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(DB_PATH, timeout=10, check_same_thread=False)
    con.row_factory = sqlite3.Row
    con.execute("PRAGMA journal_mode=WAL")
    con.execute("PRAGMA synchronous=NORMAL")
    con.execute("PRAGMA busy_timeout=10000")
    return con


def init_db() -> None:
    with connect() as con:
        con.execute(
            """
            CREATE TABLE IF NOT EXISTS sync_objects (
                resource TEXT NOT NULL,
                id TEXT NOT NULL,
                data TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (resource, id)
            )
            """
        )
        con.execute(
            "CREATE INDEX IF NOT EXISTS idx_sync_objects_resource_updated "
            "ON sync_objects(resource, updated_at DESC)"
        )


@app.on_event("startup")
def startup() -> None:
    init_db()


def require_key(x_api_key: str | None = Header(default=None)) -> None:
    if not API_KEY:
        return
    if x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="invalid api key")


def check_resource(resource: str) -> str:
    if resource not in ALLOWED_RESOURCES:
        raise HTTPException(status_code=404, detail="unknown resource")
    return resource


def row_to_object(row: sqlite3.Row) -> dict[str, Any]:
    payload = json.loads(row["data"])
    payload["id"] = row["id"]
    return payload


@app.get("/health")
def health() -> dict[str, Any]:
    init_db()
    return {"ok": True, "database": str(DB_PATH), "time": now_ms()}


@app.get("/api/{resource}", dependencies=[Depends(require_key)])
def list_objects(resource: str) -> list[dict[str, Any]]:
    resource = check_resource(resource)
    with connect() as con:
        rows = con.execute(
            "SELECT id, data, updated_at FROM sync_objects "
            "WHERE resource = ? ORDER BY updated_at DESC",
            (resource,),
        ).fetchall()
    return [row_to_object(row) for row in rows]


@app.post("/api/{resource}", dependencies=[Depends(require_key)])
def create_object(resource: str, body: dict[str, Any]) -> dict[str, str]:
    resource = check_resource(resource)
    object_id = str(uuid.uuid4())
    payload = dict(body)
    updated_at = int(payload.get("updatedAt") or now_ms())
    payload["updatedAt"] = updated_at
    with connect() as con:
        con.execute(
            "INSERT INTO sync_objects(resource, id, data, updated_at) VALUES(?, ?, ?, ?)",
            (resource, object_id, json.dumps(payload, ensure_ascii=False), updated_at),
        )
    return {"id": object_id}


@app.patch("/api/{resource}/{object_id}", dependencies=[Depends(require_key)])
def update_object(resource: str, object_id: str, body: dict[str, Any]) -> dict[str, Any]:
    resource = check_resource(resource)
    with connect() as con:
        row = con.execute(
            "SELECT data FROM sync_objects WHERE resource = ? AND id = ?",
            (resource, object_id),
        ).fetchone()
        if row is None:
            raise HTTPException(status_code=404, detail="object not found")
        payload = json.loads(row["data"])
        payload.update(body)
        updated_at = int(payload.get("updatedAt") or now_ms())
        payload["updatedAt"] = updated_at
        con.execute(
            "UPDATE sync_objects SET data = ?, updated_at = ? WHERE resource = ? AND id = ?",
            (json.dumps(payload, ensure_ascii=False), updated_at, resource, object_id),
        )
    return {"ok": True, "id": object_id}


@app.delete("/api/{resource}/{object_id}", dependencies=[Depends(require_key)])
def delete_object(resource: str, object_id: str) -> dict[str, Any]:
    resource = check_resource(resource)
    with connect() as con:
        cur = con.execute(
            "DELETE FROM sync_objects WHERE resource = ? AND id = ?",
            (resource, object_id),
        )
    return {"ok": True, "deleted": cur.rowcount > 0}


@app.get("/api-meta/stats", dependencies=[Depends(require_key)])
def stats() -> dict[str, int]:
    with connect() as con:
        rows = con.execute(
            "SELECT resource, COUNT(*) AS n FROM sync_objects GROUP BY resource"
        ).fetchall()
    return {row["resource"]: row["n"] for row in rows}
